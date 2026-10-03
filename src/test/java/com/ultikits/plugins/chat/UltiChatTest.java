package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.config.AnnouncementConfig;
import com.ultikits.plugins.chat.config.AutoReplyConfig;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
     * earlier versions) take effect, which makes detection more permissive than before; by design the
     * value takes effect and the operator is told so, loudly, at load. The new declared default is 0
     * -- no time limit, exactly the old behaviour -- so any positive window is announced.
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
                    .contains("no window-based expiry")
                    .contains("cleared after 24 hours without messages")
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
        @DisplayName("A server path that contains a placeholder is named as written, not expanded")
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
     * UltiKits/UltiChat#16. Channel formats now apply, and an operator may have edited one before
     * this version while it had no effect -- for example recoloured the shipped {@code {display}&f:
     * {message}}, which names no player. That edit is no longer one of the three legacy strings, so
     * it applies after the upgrade and the channel's lines lose their sender.
     * The format still applies, by design; the operator is told, once per channel, at load and on
     * every reload, whenever a format that is in effect lacks a sender token or the message token.
     */
    @Nested
    @DisplayName("A channel format in effect without a sender or message token is announced (UltiKits/UltiChat#16)")
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
            // A spy whose save writes nothing: the start may save channels.yml (it removes a formerly
            // shipped format from it, UltiKits/UltiChat#18), and this entity was never read from a file.
            channels = spy(new ChannelConfig());
            try {
                doNothing().when(channels).save();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
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
            // These tests are about formats: the default channel names a defined one, so the
            // default-channel check (UltiKits/UltiChat#44) stays quiet.
            if (nameThenFormat.length > 0) {
                channels.setDefaultChannel(nameThenFormat[0]);
            }
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
        @DisplayName("A channel ID that looks like a placeholder is named as written, not expanded")
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

    /**
     * An operator value this module cannot use as written is named, with the default used in its
     * place (maintainer decision 2026-09-27: refuse and name, the setting falls back to its default
     * with a warning). Before, a boss-bar colour or a mention sound that is not one silently became
     * blue or silence, an auto-reply mode that is not one silently matched as "contains", and a
     * regular expression that does not compile silently never fired.
     */
    @Nested
    @DisplayName("A configuration value the module cannot use is named at load")
    class UnusableValueWarning {

        private PluginLogger logger;
        private ChatConfig chat;
        private AnnouncementConfig announcements;
        private AutoReplyConfig autoReply;
        private UltiChat plugin;

        private void load(File dir) throws IOException {
            plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            // The warnings come from the language file; the assertions quote its English text.
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile(anyString()))
                    .thenAnswer(inv -> new File(dir, inv.<String>getArgument(0)));
            when(plugin.getConfig(ChatConfig.class)).thenReturn(chat);
            when(plugin.getConfig(AnnouncementConfig.class)).thenReturn(announcements);
            when(plugin.getConfig(AutoReplyConfig.class)).thenReturn(autoReply);
            doNothing().when(announcements).save();
            doNothing().when(autoReply).save();
            doCallRealMethod().when(plugin).onReload();
            plugin.onReload();
        }

        private void freshConfigs() {
            chat = new ChatConfig();
            announcements = spy(new AnnouncementConfig());
            autoReply = spy(new AutoReplyConfig());
        }

        private List<String> warnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(captor.capture());
            return captor.getAllValues();
        }

        private void rule(String name, String keyword, String mode) {
            Map<String, Map<String, Object>> rules = new LinkedHashMap<String, Map<String, Object>>();
            Map<String, Object> rule = new HashMap<String, Object>();
            rule.put("keyword", keyword);
            rule.put("response", "hi");
            rule.put("mode", mode);
            rules.put(name, rule);
            autoReply.setRules(rules);
        }

        @Test
        @DisplayName("control: the shipped values produce none of these warnings")
        void shippedValuesAreQuiet(@TempDir File dir) throws IOException {
            freshConfigs();
            load(dir);
            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("a boss-bar colour that is not one is named, and blue is used")
        void unknownBossBarColour(@TempDir File dir) throws IOException {
            freshConfigs();
            announcements.setBossBarColor("PURPLEISH");
            load(dir);
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains(new File(dir, "config/announcements.yml").getPath())
                    .contains("'announcements.bossbar.color'")
                    .contains("\"PURPLEISH\"")
                    .contains("BLUE");
        }

        @Test
        @DisplayName("a mention sound that is not one is named, and the default sound is used; an empty one is silence, not a warning")
        void unknownMentionSound(@TempDir File dir) throws IOException {
            freshConfigs();
            chat.setMentionSound("NOT_A_SOUND");
            load(dir);
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains(new File(dir, "config/chat.yml").getPath())
                    .contains("'mentions.sound'")
                    .contains("\"NOT_A_SOUND\"")
                    .contains("ENTITY_EXPERIENCE_ORB_PICKUP");

            freshConfigs();
            chat.setMentionSound("");
            load(dir);
            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("an auto-reply mode that is not contains, exact or regex is named with its rule")
        void unknownRuleMode(@TempDir File dir) throws IOException {
            freshConfigs();
            rule("greet", "hello", "startswith");
            load(dir);
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains(new File(dir, "config/autoreply.yml").getPath())
                    .contains("'greet'")
                    .contains("\"startswith\"")
                    .contains("contains");
        }

        @Test
        @DisplayName("an auto-reply regular expression that does not compile is named with its rule; a valid one is not")
        void invalidRuleRegex(@TempDir File dir) throws IOException {
            freshConfigs();
            rule("broken", "[unclosed", "regex");
            load(dir);
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains(new File(dir, "config/autoreply.yml").getPath())
                    .contains("'broken'")
                    .contains("\"[unclosed\"");

            freshConfigs();
            rule("fine", "^hel+o$", "REGEX");
            load(dir);
            assertThat(warnings()).isEmpty();
        }
    }

    /**
     * UltiKits/UltiChat#44: {@code channels.default-channel} naming a channel that is not defined used
     * to be accepted without a word, and new players were placed in a channel that does not exist.
     * It is now named at load and on every reload, together with the channel new players land in.
     */
    @Nested
    @DisplayName("A default channel that names no defined channel is refused and named (UltiKits/UltiChat#44)")
    class DefaultChannelWarning {

        private PluginLogger logger;

        private UltiChat pluginWith(File dir, String defaultChannel, boolean enabled, String... definedChannels) {
            UltiChat plugin = mock(UltiChat.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile(anyString()))
                    .thenAnswer(inv -> new File(dir, inv.<String>getArgument(0)));
            ChannelConfig channels = spy(new ChannelConfig());
            try {
                doNothing().when(channels).save();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Map<String, Map<String, Object>> defs = new LinkedHashMap<String, Map<String, Object>>();
            for (String name : definedChannels) {
                Map<String, Object> def = new HashMap<String, Object>();
                def.put("display-name", "&f[" + name + "]");
                defs.put(name, def);
            }
            channels.setChannels(defs);
            channels.setDefaultChannel(defaultChannel);
            channels.setEnabled(enabled);
            when(plugin.getConfig(ChatConfig.class)).thenReturn(new ChatConfig());
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
        @DisplayName("POSITIVE CONTROL: an undefined default with global defined warns once, naming file, key, the configured name and global")
        void undefinedDefaultFallsBackToGlobal(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "lobby", true, "local", "global", "staff");

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0))
                    .contains(new File(dir, "config/channels.yml").getPath())
                    .contains("channels.default-channel")
                    .contains("\"lobby\"")
                    .contains("channel 'global'");
        }

        @Test
        @DisplayName("With global removed, the first defined channel in file order is the one named")
        void undefinedDefaultFallsBackToTheFirstDefinedChannel(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "global", true, "local", "staff");

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("\"global\"").contains("channel 'local'");
        }

        @Test
        @DisplayName("With no channel defined at all the warning says every player shares the one undefined channel")
        void noChannelDefinedAtAll(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "global", true);

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("defines no channel").contains("\"global\"")
                    .contains("not filtered by range or world");
        }

        @Test
        @DisplayName("A default that names a defined channel is quiet")
        void definedDefaultIsQuiet(@TempDir File dir) {
            UltiChat control = pluginWith(dir, "staff", true, "local", "staff");
            control.registerSelf();
            assertThat(warnings()).isEmpty();

            // Control: the same wiring does warn once the default names nothing.
            UltiChat plugin = pluginWith(dir, "ghost", true, "local", "staff");
            plugin.registerSelf();
            assertThat(warnings()).hasSize(1);
        }

        @Test
        @DisplayName("With channels disabled the key is not used, so nothing is said")
        void disabledChannelsAreQuiet(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "ghost", false, "local");

            plugin.registerSelf();

            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("The warning is repeated on reload")
        void warnsOnReloadToo(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "ghost", true, "global");

            plugin.onReload();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("\"ghost\"").contains("channel 'global'");
        }

        @Test
        @DisplayName("A configured name that looks like a placeholder is named as written, not expanded")
        void configuredNameIsNotReExpanded(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "a{USED}b{CHANNEL}", true, "global");

            plugin.registerSelf();

            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).contains("\"a{USED}b{CHANNEL}\"").contains("channel 'global'");
        }

        @Test
        @DisplayName("Under language: zh the warning is the Chinese catalogue text")
        void followsTheLanguageSetting(@TempDir File dir) {
            UltiChat plugin = pluginWith(dir, "ghost", true, "global");
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("zh"));
            String expected = com.ultikits.plugins.chat.i18n.CatalogueText.text("zh", "log_default_channel_undefined")
                    .replace("{FILE}", new File(dir, "config/channels.yml").getPath())
                    .replace("{CHANNEL}", "ghost").replace("{USED}", "global");

            plugin.registerSelf();

            assertThat(warnings()).containsExactly(expected);
        }
    }
}
