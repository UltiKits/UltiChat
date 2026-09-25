package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.config.ChannelConfig;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @DisplayName("The removed-key check is actually called (UltiKits/UltiChat#15)")
    class RemovedKeyCheckWiring {

        private static final String WITH_MUTE_DURATION =
                "anti-spam:\n  enabled: true\n  mute-duration: 30\n";

        private static final String WITHOUT_MUTE_DURATION =
                "anti-spam:\n  enabled: true\n";

        private PluginLogger logger;

        private UltiChat pluginReading(final File dir, String chatYml) throws IOException {
            File file = new File(dir, "config/chat.yml");
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), chatYml.getBytes(StandardCharsets.UTF_8));

            UltiChat plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            // The warnings come from the language file; the assertions quote its English text.
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
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
        @DisplayName("POSITIVE CONTROL: registerSelf warns about the leftover key")
        void registerSelfWarns(@TempDir File dir) throws IOException {
            UltiChat plugin = pluginReading(dir, WITH_MUTE_DURATION);
            when(plugin.registerSelf()).thenCallRealMethod();

            assertThat(plugin.registerSelf()).isTrue();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("anti-spam.mute-duration");
        }

        @Test
        @DisplayName("POSITIVE CONTROL: onReload warns about the leftover key")
        void onReloadWarns(@TempDir File dir) throws IOException {
            UltiChat plugin = pluginReading(dir, WITH_MUTE_DURATION);
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("anti-spam.mute-duration");
        }

        @Test
        @DisplayName("Neither entry point warns when the file holds no removed key")
        void neitherWarnsOnACleanFile(@TempDir File dir) throws IOException {
            UltiChat onEnable = pluginReading(dir, WITHOUT_MUTE_DURATION);
            when(onEnable.registerSelf()).thenCallRealMethod();
            onEnable.registerSelf();
            assertThat(warnings()).isEmpty();

            UltiChat onReload = pluginReading(dir, WITHOUT_MUTE_DURATION);
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
     * default is 0 -- no time limit, exactly the old behaviour -- so any positive window is announced.
     */
    @Nested
    @DisplayName("Any positive duplicate window is announced at load (UltiKits/UltiChat#14)")
    class DuplicateWindowWarning {

        private PluginLogger logger;

        private UltiChat pluginWithWindow(final File dir, Integer windowSeconds) {
            UltiChat plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            // The warnings come from the language file; the assertions quote its English text.
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
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
                    .contains("set it to 0")
                    .contains("no time limit")
                    .contains("/uchat reload")
                    .contains("UltiKits/UltiChat#14");
        }

        @Test
        @DisplayName("Under language: zh the warning is the Chinese catalogue text")
        void warningFollowsTheLanguageSetting(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, 60);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("zh"));
            String expected = com.ultikits.plugins.chat.i18n.CatalogueText.text("zh", "log_duplicate_window_applied")
                    .replace("{FILE}", new File(dir, "config/chat.yml").getPath())
                    .replace("{SECONDS}", "60").replace("{DEFAULT}", "0");

            plugin.registerSelf();

            assertThat(warnings()).containsExactly(expected);
        }

        @Test
        @DisplayName("A server path that contains a placeholder is named as written, not expanded (Codex P3)")
        void pathIsNotReExpanded(@TempDir File parent) {
            File dir = new File(parent, "srv{SECONDS}{DEFAULT}");
            UltiChat plugin = pluginWithWindow(dir, 60);
            String path = new File(dir, "config/chat.yml").getPath();

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains(path).contains("to 60 seconds");
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
        @DisplayName("The smallest and the largest positive window both warn")
        void everyPositiveWindowWarns(@TempDir File dir) {
            UltiChat smallest = pluginWithWindow(dir, 1);
            smallest.registerSelf();
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("to 1 seconds");

            UltiChat largest = pluginWithWindow(dir, 600);
            largest.registerSelf();
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("to 600 seconds");
        }

        @Test
        @DisplayName("The default, 0 = no time limit, is not announced")
        void theDefaultIsQuiet(@TempDir File dir) {
            UltiChat plugin = pluginWithWindow(dir, 0);

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

    /**
     * Gate-1 WR-01 on UltiKits/UltiChat#16. Channel formats now apply, and an operator may have
     * edited one before this version while it had no effect -- for example recoloured the shipped
     * {@code {display}&f: {message}}, which names no player. That edit is no longer one of the three
     * legacy strings, so it applies after the upgrade and the channel's lines lose their sender.
     * The format still applies (the maintainer's ruling); the operator is told, once per channel, at
     * load and on every reload, whenever a format that is in effect lacks a sender token or the
     * message token.
     */
    @Nested
    @DisplayName("A channel format in effect without a sender or message token is announced (gate-1 WR-01, UltiKits/UltiChat#16)")
    class ChannelFormatTokenWarning {

        private PluginLogger logger;
        private ChannelConfig channels;
        private ChatConfig chat;

        private UltiChat pluginWithChannelFormats(final File dir, String... nameThenFormat) {
            UltiChat plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            // The warnings come from the language file; the assertions quote its English text.
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile(anyString()))
                    .thenAnswer(inv -> new File(dir, inv.<String>getArgument(0)));
            chat = new ChatConfig();
            channels = new ChannelConfig();
            Map<String, Map<String, Object>> defs = new LinkedHashMap<String, Map<String, Object>>();
            for (int i = 0; i < nameThenFormat.length; i += 2) {
                Map<String, Object> def = new HashMap<String, Object>();
                def.put("display-name", "&f[" + nameThenFormat[i] + "]");
                if (nameThenFormat[i + 1] != null) {
                    def.put("format", nameThenFormat[i + 1]);
                }
                defs.put(nameThenFormat[i], def);
            }
            channels.setChannels(defs);
            when(plugin.getConfig(ChatConfig.class)).thenReturn(chat);
            when(plugin.getConfig(ChannelConfig.class)).thenReturn(channels);
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
        @DisplayName("A channel ID that looks like a placeholder is named as written, not expanded (Codex P3)")
        void channelIdIsNotReExpanded(@TempDir File dir) {
            UltiChat plugin = pluginWithChannelFormats(dir, "a{FORMAT}b", "{display}&e: {message}");
            String template = com.ultikits.plugins.chat.i18n.CatalogueText.text("en", "log_channel_format_missing_sender");
            String expected = template.substring(0, template.indexOf("{FILE}"))
                    + new File(dir, "config/channels.yml").getPath()
                    + template.substring(template.indexOf("{FILE}") + 6, template.indexOf("{CHANNEL}"))
                    + "a{FORMAT}b"
                    + template.substring(template.indexOf("{CHANNEL}") + 9, template.indexOf("{FORMAT}"))
                    + "{display}&e: {message}"
                    + template.substring(template.indexOf("{FORMAT}") + 8);

            plugin.registerSelf();

            assertThat(warnings()).containsExactly(expected);
        }

        @Test
        @DisplayName("Under language: zh the channel-format warning is the Chinese catalogue text")
        void channelFormatWarningFollowsTheLanguageSetting(@TempDir File dir) {
            UltiChat plugin = pluginWithChannelFormats(dir, "global", "{display}&e: {message}");
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("zh"));
            String expected = com.ultikits.plugins.chat.i18n.CatalogueText.text("zh", "log_channel_format_missing_sender")
                    .replace("{FILE}", new File(dir, "config/channels.yml").getPath())
                    .replace("{CHANNEL}", "global").replace("{FORMAT}", "{display}&e: {message}");

            plugin.registerSelf();

            assertThat(warnings()).containsExactly(expected);
        }

        @Test
        @DisplayName("POSITIVE CONTROL: a recoloured legacy format with no player warns at start, naming file, channel and what to add")
        void recolouredLegacyFormatWarns(@TempDir File dir) {
            UltiChat plugin = pluginWithChannelFormats(dir, "global", "{display}&e: {message}");

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains("UltiChat")
                    .contains(new File(dir, "config/channels.yml").getPath())
                    .contains("channel 'global'")
                    .contains("no sender name")
                    .contains("add {player} or {displayname}")
                    .doesNotContain("add {message}")
                    .contains("UltiKits/UltiChat#16");
        }

        @Test
        @DisplayName("POSITIVE CONTROL: a format without {message} warns, on reload too")
        void missingMessageWarnsOnReload(@TempDir File dir) {
            UltiChat plugin = pluginWithChannelFormats(dir, "staff", "&c[Staff] {player}");

            plugin.onReload();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("channel 'staff'").contains("no message text")
                    .contains("add {message}").doesNotContain("add {player}");
        }

        @Test
        @DisplayName("A format missing both is one warning naming both")
        void missingBothIsOneWarning(@TempDir File dir) {
            UltiChat plugin = pluginWithChannelFormats(dir, "local", "{display} says hi");

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("add {player} or {displayname}").contains("add {message}");
        }

        @Test
        @DisplayName("One warning per offending channel; complete, unset and legacy formats are quiet")
        void onlyOffendingChannelsWarn(@TempDir File dir) {
            UltiChat plugin = pluginWithChannelFormats(dir,
                    "a", "{display}&e: {message}",
                    "b", "{display} {player}: {message}",
                    "c", "{displayname} > {message}",
                    "d", null,
                    "e", "{display}&f: {message}",
                    "f", "&c[Staff] {player}");

            plugin.registerSelf();

            assertThat(warnings()).hasSize(2);
            assertThat(warnings().get(0)).contains("channel 'a'");
            assertThat(warnings().get(1)).contains("channel 'f'");
        }

        @Test
        @DisplayName("Quiet when the format is not in effect: channels disabled, or chat.format-enabled false")
        void quietWhenFormatsAreNotApplied(@TempDir File dir) {
            UltiChat channelsOff = pluginWithChannelFormats(dir, "global", "{display}&e: {message}");
            channels.setEnabled(false);
            channelsOff.registerSelf();
            assertThat(warnings()).isEmpty();

            UltiChat formattingOff = pluginWithChannelFormats(dir, "global", "{display}&e: {message}");
            chat.setChatFormatEnabled(false);
            formattingOff.registerSelf();
            assertThat(warnings()).isEmpty();
        }
    }
}
