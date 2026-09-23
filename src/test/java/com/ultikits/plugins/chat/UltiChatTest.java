package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the UltiChat plugin main class.
 * <p>
 * Tests the lifecycle template-method contract.
 */
@DisplayName("UltiChat main class")
class UltiChatTest {

    @Nested
    @DisplayName("Lifecycle template methods (UltiKits/UltiChat#23)")
    class LifecycleTemplateMethodTests {

        /**
         * UltiTools 6.3.0 makes {@code unregisterSelf()} and {@code reloadSelf()} final
         * template methods that always run the framework's own steps (on unload: the module's
         * {@code onUnregister()} hook, then command and listener unregistration). This module's
         * former {@code unregisterSelf()} override was empty and never called {@code super}, so
         * {@code /upm uninstall} skipped command and listener unregistration (UltiKits/UltiChat#23). It is deleted outright;
         * this test pins that neither template method is declared again (zero-argument
         * declarations only).
         */
        @Test
        @DisplayName("UltiChat declares neither framework template method")
        void declaresNeitherTemplateMethod() {
            List<String> declared = new ArrayList<>();
            for (Method method : UltiChat.class.getDeclaredMethods()) {
                // Only a zero-argument declaration overrides a template method; an unrelated
                // overload such as unregisterSelf(String) is allowed.
                if (method.getParameterCount() == 0) {
                    declared.add(method.getName());
                }
            }

            assertThat(declared).doesNotContain("unregisterSelf", "reloadSelf");
        }
    }

    /**
     * The removed-key check's predicate is guarded by {@code RemovedConfigKeysTest}; these tests
     * guard its wiring, a separate claim. A check that is never called and a server with no leftover
     * key produce the same empty console, so both entry points -- module start and reload, which
     * {@code /uchat reload} and {@code /ul reload} both reach -- get a positive control, paired with
     * the same entry points over a file with the keys taken out and nothing else changed.
     */
    @Nested
    @DisplayName("The removed-key check is actually called (UltiKits/UltiChat#13)")
    class RemovedKeyCheckWiring {

        private static final String WITH_INTERVALS =
                "announcements:\n  chat:\n    enabled: true\n    interval: 300\n"
                + "  bossbar:\n    interval: 60\n  title:\n    interval: 600\n";

        private static final String WITHOUT_INTERVALS =
                "announcements:\n  chat:\n    enabled: true\n";

        private PluginLogger logger;

        private UltiChat pluginReading(final File dir, String announcements) throws IOException {
            File file = new File(dir, "config/announcements.yml");
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), announcements.getBytes(StandardCharsets.UTF_8));

            UltiChat plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.operatorConfigFile(anyString()))
                    .thenAnswer(inv -> new File(dir, inv.<String>getArgument(0)));
            return plugin;
        }

        private List<String> warnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("POSITIVE CONTROL: registerSelf warns about every leftover interval key")
        void registerSelfWarns(@TempDir File dir) throws IOException {
            UltiChat plugin = pluginReading(dir, WITH_INTERVALS);
            when(plugin.registerSelf()).thenCallRealMethod();

            assertThat(plugin.registerSelf()).isTrue();

            assertThat(warnings()).hasSize(3);
            assertThat(warnings()).anySatisfy(l -> assertThat(l).contains("announcements.chat.interval"));
            assertThat(warnings()).anySatisfy(l -> assertThat(l).contains("announcements.title.interval"));
        }

        @Test
        @DisplayName("POSITIVE CONTROL: onReload warns about every leftover interval key")
        void onReloadWarns(@TempDir File dir) throws IOException {
            UltiChat plugin = pluginReading(dir, WITH_INTERVALS);
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            assertThat(warnings()).hasSize(3);
            assertThat(warnings()).anySatisfy(l -> assertThat(l).contains("announcements.bossbar.interval"));
        }

        @Test
        @DisplayName("Neither entry point warns when the file holds no removed key")
        void neitherWarnsOnACleanFile(@TempDir File dir) throws IOException {
            UltiChat onEnable = pluginReading(dir, WITHOUT_INTERVALS);
            when(onEnable.registerSelf()).thenCallRealMethod();
            onEnable.registerSelf();
            assertThat(warnings()).isEmpty();

            UltiChat onReload = pluginReading(dir, WITHOUT_INTERVALS);
            doCallRealMethod().when(onReload).onReload();
            onReload.onReload();
            assertThat(warnings()).isEmpty();
        }
    }

    /**
     * UltiKits/UltiChat#14. The duplicate window used to be ignored, so a repeat counted however far
     * apart its copies were sent. Wiring it makes an upgraded server's on-disk value (60, written by
     * earlier versions) take effect, which makes detection more permissive than before; the ruling
     * is that the value takes effect and the operator is told so, loudly, at load. The new declared
     * default -- 600, the longest the key allows -- is the reference: anything shorter is announced.
     */
    @Nested
    @DisplayName("A duplicate window shorter than the new default is announced at load (UltiKits/UltiChat#14)")
    class DuplicateWindowWarning {

        private PluginLogger logger;

        private UltiChat pluginWithWindow(final File dir, Integer windowSeconds) {
            UltiChat plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.operatorConfigFile(anyString()))
                    .thenAnswer(inv -> new File(dir, inv.<String>getArgument(0)));
            ChatConfig config = null;
            if (windowSeconds != null) {
                config = new ChatConfig();
                config.setAntiSpamDuplicateWindow(windowSeconds);
            }
            when(plugin.getConfig(ChatConfig.class)).thenReturn(config);
            when(plugin.registerSelf()).thenCallRealMethod();
            doCallRealMethod().when(plugin).onReload();
            return plugin;
        }

        private List<String> warnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("POSITIVE CONTROL: an on-disk 60 at module start gives exactly one warning saying what changed and how to undo it")
        void sixtyAtStartWarns(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, 60);

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains("UltiChat")
                    .contains(new File(dir, "config/chat.yml").getPath())
                    .contains("'anti-spam.duplicate-window'")
                    .contains("60 seconds")
                    .contains("more permissive than before the upgrade")
                    .contains("set it to 600")
                    .contains("/uchat reload")
                    .contains("UltiKits/UltiChat#14");
        }

        @Test
        @DisplayName("POSITIVE CONTROL: the same warning on reload")
        void sixtyOnReloadWarns(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, 60);

            plugin.onReload();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("'anti-spam.duplicate-window'").contains("60 seconds");
        }

        @Test
        @DisplayName("One second short of the default still warns")
        void justBelowTheDefaultWarns(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, 599);

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("599 seconds");
        }

        @Test
        @DisplayName("The default itself is not announced")
        void theDefaultIsQuiet(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, 600);

            plugin.registerSelf();
            plugin.onReload();

            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("No configuration object, no warning and no failure")
        void noConfigIsQuiet(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, null);

            assertThat(plugin.registerSelf()).isTrue();

            assertThat(warnings()).isEmpty();
        }
    }
}
