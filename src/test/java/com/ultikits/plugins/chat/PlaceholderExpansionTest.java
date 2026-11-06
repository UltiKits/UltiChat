package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.manager.LocalExpansionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PlaceholderExpansionTest {
    private static final String EXPANSION = "com.ultikits.plugins.placeholder.chat.UltiChatExpansion";

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        com.ultikits.plugins.chat.utils.ChatTestHelper.setUp();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        com.ultikits.plugins.chat.utils.ChatTestHelper.tearDown();
    }

    private PlaceholderExpansion expansion(UltiToolsPlugin plugin, ChannelConfig config,
                                            ChannelService service) throws Exception {
        return (PlaceholderExpansion) Class.forName(EXPANSION)
                .getConstructor(UltiToolsPlugin.class, ChannelConfig.class, ChannelService.class)
                .newInstance(plugin, config, service);
    }

    private Runnable register(UltiToolsPlugin plugin, ChannelConfig config,
                              ChannelService service) throws Exception {
        return (Runnable) Class.forName(EXPANSION).getMethod("register", UltiToolsPlugin.class,
                ChannelConfig.class, ChannelService.class).invoke(null, plugin, config, service);
    }

    private UltiToolsPlugin plugin() {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.getVersion()).thenReturn("2.0.0");
        when(plugin.getAuthors()).thenReturn(Arrays.asList("wisdomme", "Contributor"));
        when(plugin.getLogger()).thenReturn(mock(com.ultikits.ultitools.interfaces.impl.logger.PluginLogger.class));
        when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        return plugin;
    }

    @Test
    void exportsLiveChannelIdAndColorizedDisplayThroughPapi() throws Exception {
        ChannelConfig config = new ChannelConfig();
        ChannelService service = new ChannelService();
        Field field = ChannelService.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(service, config);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getPlayer()).thenReturn(player);
        PlaceholderExpansion expansion = expansion(plugin(), config, service);
        PlaceholderAPIPlugin papi = mock(PlaceholderAPIPlugin.class);
        LocalExpansionManager manager = mock(LocalExpansionManager.class);
        when(papi.getLocalExpansionManager()).thenReturn(manager);
        when(manager.getExpansion("ultichat")).thenReturn(expansion);
        try (MockedStatic<PlaceholderAPIPlugin> api = mockStatic(PlaceholderAPIPlugin.class)) {
            api.when(PlaceholderAPIPlugin::getInstance).thenReturn(papi);
            assertThat(PlaceholderAPI.setPlaceholders(player, "%ultichat_channel%")) .isEqualTo("global");
            service.setPlayerChannel(id, "local");
            assertThat(PlaceholderAPI.setPlaceholders(player, "%ultichat_channel%")) .isEqualTo("local");
            assertThat(PlaceholderAPI.setPlaceholders(player, "%ultichat_channel_display%"))
                    .isEqualTo("§a[Local]");
            config.getChannels().get("local").put("display-name", "&bNearby");
            assertThat(expansion.onRequest(player, "channel_display")).isEqualTo("§bNearby");
            service.setPlayerChannel(id, "undefined");
            assertThat(expansion.onRequest(player, "channel_display")).isEqualTo("undefined");
            assertThat(PlaceholderAPI.setPlaceholders(player, "%ultichat_unknown%"))
                    .isEqualTo("%ultichat_unknown%");
            config.setEnabled(false);
            assertThat(expansion.onRequest(player, "channel")).isEmpty();
            assertThat(expansion.onRequest(player, "channel_display")).isEmpty();
        }
    }

    @Test
    void followsPapiUnavailablePlayerAndUnknownParameterContracts() throws Exception {
        ChannelConfig config = new ChannelConfig();
        ChannelService service = mock(ChannelService.class);
        PlaceholderExpansion expansion = expansion(plugin(), config, service);
        assertThat(expansion.onRequest(null, "channel")).isEmpty();
        assertThat(expansion.onRequest(mock(OfflinePlayer.class), "channel_display")).isEmpty();
        assertThat(expansion.onRequest(null, "unknown")).isNull();
        assertThat(expansion.onRequest(null, "CHANNEL")).isNull();
        verifyNoInteractions(service);
        assertThat(expansion.getIdentifier()).isEqualTo("ultichat");
        assertThat(expansion.getAuthor()).isEqualTo("wisdomme, Contributor");
        assertThat(expansion.getVersion()).isEqualTo("2.0.0");
        assertThat(expansion.persist()).isTrue();
    }

    @Test
    void registrationOwnsOnlyTheCurrentInstanceNotAnEqualReplacement() throws Exception {
        UltiToolsPlugin plugin = plugin();
        ChannelConfig config = new ChannelConfig();
        ChannelService service = mock(ChannelService.class);
        PlaceholderAPIPlugin papi = mock(PlaceholderAPIPlugin.class);
        LocalExpansionManager manager = mock(LocalExpansionManager.class);
        when(papi.getLocalExpansionManager()).thenReturn(manager);
        AtomicReference<PlaceholderExpansion> current = new AtomicReference<>();
        when(manager.register(any(PlaceholderExpansion.class))).thenAnswer(inv -> {
            current.set(inv.getArgument(0));
            return true;
        });
        when(manager.findExpansionByIdentifier("ultichat"))
                .thenAnswer(inv -> Optional.ofNullable(current.get()));
        try (MockedStatic<PlaceholderAPIPlugin> api = mockStatic(PlaceholderAPIPlugin.class)) {
            api.when(PlaceholderAPIPlugin::getInstance).thenReturn(papi);
            Runnable first = register(plugin, config, service);
            PlaceholderExpansion old = current.get();
            Runnable second = register(plugin, config, service);
            PlaceholderExpansion replacement = current.get();
            assertThat(old).isEqualTo(replacement).isNotSameAs(replacement);
            first.run();
            verify(manager, never()).unregister(any(PlaceholderExpansion.class));
            second.run();
            verify(manager).unregister(same(replacement));
            current.set(null);
            second.run();
            verifyNoMoreInteractionsAfterCleanup(manager);
        }
    }

    private void verifyNoMoreInteractionsAfterCleanup(LocalExpansionManager manager) {
        verify(manager, times(1)).unregister(any(PlaceholderExpansion.class));
    }

    @Test
    void refusedRegistrationReturnsNoCleanupAndWarns() throws Exception {
        UltiToolsPlugin plugin = plugin();
        PlaceholderAPIPlugin papi = mock(PlaceholderAPIPlugin.class);
        LocalExpansionManager manager = mock(LocalExpansionManager.class);
        when(papi.getLocalExpansionManager()).thenReturn(manager);
        try (MockedStatic<PlaceholderAPIPlugin> api = mockStatic(PlaceholderAPIPlugin.class)) {
            api.when(PlaceholderAPIPlugin::getInstance).thenReturn(papi);
            assertThat(register(plugin, new ChannelConfig(), mock(ChannelService.class))).isNull();
            verify(plugin.getLogger()).warn("log_placeholder_registration_failed");
            verify(manager, never()).unregister(any(PlaceholderExpansion.class));
        }
    }

    @Test
    void moduleHooksRegisterOnceKeepOnConfigReloadAndReleaseOnUnload() throws Exception {
        UltiChat module = mock(UltiChat.class);
        when(module.getLogger()).thenReturn(mock(com.ultikits.ultitools.interfaces.impl.logger.PluginLogger.class));
        when(module.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(module.registerSelf()).thenCallRealMethod();
        Method unload = UltiChat.class.getDeclaredMethod("onUnregister");
        unload.setAccessible(true);
        doCallRealMethod().when(module).onReload();
        unload.invoke(doCallRealMethod().when(module));
        SimpleContainer context = mock(SimpleContainer.class);
        ChannelConfig config = new ChannelConfig();
        ChannelService service = mock(ChannelService.class);
        when(module.getContext()).thenReturn(context);
        when(module.getConfig(ChannelConfig.class)).thenReturn(config);
        when(context.getBean(ChannelService.class)).thenReturn(service);
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);
        PlaceholderAPIPlugin papi = mock(PlaceholderAPIPlugin.class);
        LocalExpansionManager manager = mock(LocalExpansionManager.class);
        when(papi.getLocalExpansionManager()).thenReturn(manager);
        AtomicReference<PlaceholderExpansion> current = new AtomicReference<>();
        when(manager.register(any(PlaceholderExpansion.class))).thenAnswer(inv -> {
            current.set(inv.getArgument(0));
            return true;
        });
        when(manager.findExpansionByIdentifier("ultichat"))
                .thenAnswer(inv -> Optional.ofNullable(current.get()));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PlaceholderAPIPlugin> api = mockStatic(PlaceholderAPIPlugin.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            api.when(PlaceholderAPIPlugin::getInstance).thenReturn(papi);
            assertThat(module.registerSelf()).isTrue();
            module.onReload();
            verify(manager, times(1)).register(any(PlaceholderExpansion.class));
            assertThat(current.get().persist()).isTrue();
            unload.invoke(module);
            unload.invoke(module);
            verify(manager, times(1)).unregister(same(current.get()));
        }
    }

    @Test
    void absentOrDisabledPapiNeverTouchesItsRegistry() throws Exception {
        UltiChat module = mock(UltiChat.class);
        when(module.getLogger()).thenReturn(mock(com.ultikits.ultitools.interfaces.impl.logger.PluginLogger.class));
        when(module.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(module.registerSelf()).thenCallRealMethod();
        when(module.getContext()).thenReturn(mock(SimpleContainer.class));
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PlaceholderAPIPlugin> api = mockStatic(PlaceholderAPIPlugin.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            assertThat(module.registerSelf()).isTrue();
            api.verifyNoInteractions();
        }
    }

    @Test
    void scannedModuleClassesLinkWithoutPlaceholderApi(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        scanWithoutPapi(true, temp);
    }

    @Test
    void directoryScanDoesNotLinkOptionalImplementation(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        scanWithoutPapi(false, temp);
    }

    private void scanWithoutPapi(boolean jar, Path temp) throws Exception {
        URL root = UltiChat.class.getProtectionDomain().getCodeSource().getLocation();
        if (jar) {
            Path source = Paths.get(root.toURI());
            Path archive = temp.resolve("module.jar");
            try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(Files.newOutputStream(archive));
                 Stream<Path> paths = Files.walk(source)) {
                for (Path path : (Iterable<Path>) paths.filter(p -> !p.equals(source))::iterator) {
                    String name = source.relativize(path).toString().replace('\\', '/');
                    out.putNextEntry(new java.util.jar.JarEntry(Files.isDirectory(path) ? name + "/" : name));
                    if (Files.isRegularFile(path)) {
                        Files.copy(path, out);
                    }
                    out.closeEntry();
                }
            }
            root = archive.toUri().toURL();
        }
        java.util.Set<String> attempted = new java.util.HashSet<>();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root}, getClass().getClassLoader()) {
            @Override
            public URL getResource(String name) {
                return name.startsWith("com/ultikits/plugins/") ? findResource(name) : super.getResource(name);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("me.clip.placeholderapi.")) {
                    throw new ClassNotFoundException("PAPI deliberately absent");
                }
                if (name.startsWith("com.ultikits.plugins.")) {
                    attempted.add(name);
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        return loaded == null ? findClass(name) : loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            SimpleContainer container = mock(SimpleContainer.class);
            when(container.getClassLoader()).thenReturn(loader);
            // Exercise the actual framework scanner, in both production JAR and exploded modes.
            new com.ultikits.ultitools.context.ComponentScanner(container)
                    .scanPackage(UltiChat.class.getPackage().getName());
            assertThat(attempted).contains("com.ultikits.plugins.chat.listener.ChatListener")
                    .doesNotContain(EXPANSION);
            assertThat(loader.loadClass(UltiChat.class.getName()).getClassLoader()).isSameAs(loader);
        }
    }
}
