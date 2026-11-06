package com.ultikits.plugins.chat.config;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.entities.Language;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AutoMuteConfigInitTest {
    @TempDir Path directory;

    private UltiToolsPlugin module(Path file) throws Exception {
        UltiChat plugin = mock(UltiChat.class);
        Method accessor = UltiToolsPlugin.class.getDeclaredMethod("getConfigFile", String.class);
        accessor.setAccessible(true);
        accessor.invoke(doReturn(file.toFile()).when(plugin), anyString());
        when(plugin.getPluginName()).thenReturn("UltiTools-Chat");
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        return plugin;
    }

    private String shipped() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/resources/config/chat.yml")), StandardCharsets.UTF_8);
    }

    private Path write(String text) throws Exception {
        Path file = directory.resolve("chat.yml");
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void oldFileGetsOnlyMissingDefaultsAndLegacyKeyNeverEnablesMuting() throws Exception {
        String original = "# operator heading\nchat:\n  format: 'CUSTOM {player}: {message}'\n"
                + "anti-spam:\n  enabled: true\n  cooldown: 7\n  mute-duration: 99 # old and inert\n"
                + "custom-operator-key: 'keep me'\n";
        Path file = write(original);
        ChatConfig config = new ChatConfig();
        config.init(module(file));
        assertThat(ChatTestHelper.getField(config, "autoMuteEnabled")).isEqualTo(false);
        assertThat(config.getAntiSpamCooldown()).isEqualTo(7);
        assertThat(config.getChatFormat()).isEqualTo("CUSTOM {player}: {message}");
        String after = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int position = 0;
        for (String line : original.split("\n")) {
            int found = after.indexOf(line + "\n", position);
            assertThat(found).as("preserve original line in order: %s", line).isGreaterThanOrEqualTo(position);
            position = found + line.length() + 1;
        }
        assertThat(after).contains("auto-mute:").contains("enabled: false");
    }

    @Test
    void existingNewSettingsAndEveryOperatorByteStayUnchanged() throws Exception {
        String text = shipped().replace("enabled: false", "enabled: true")
                .replace("threshold: 3", "threshold: 5")
                .replace("window: 60", "window: 120")
                .replace("duration: 30", "duration: 55") + "# operator footer\nunknown: retain\n";
        Path file = write(text);
        ChatConfig config = new ChatConfig();
        config.init(module(file));
        assertThat(ChatTestHelper.getField(config, "autoMuteEnabled")).isEqualTo(true);
        assertThat(ChatTestHelper.getField(config, "autoMuteThreshold")).isEqualTo(5);
        assertThat(ChatTestHelper.getField(config, "autoMuteWindow")).isEqualTo(120);
        assertThat(ChatTestHelper.getField(config, "autoMuteDuration")).isEqualTo(55);
        assertThat(Files.readAllBytes(file)).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void outOfRangeValuesAreRefusedWithoutRepairingTheFile() throws Exception {
        for (String[] change : new String[][] {{"threshold: 3", "threshold: 1"},
                {"threshold: 3", "threshold: 21"}, {"window: 60", "window: 0"},
                {"window: 60", "window: 601"}, {"duration: 30", "duration: 4"},
                {"duration: 30", "duration: 601"}}) {
            String text = shipped().replace(change[0], change[1]);
            Path file = write(text);
            ChatConfig config = new ChatConfig();
            assertThatThrownBy(() -> config.init(module(file))).isInstanceOf(RuntimeException.class);
            assertThat(config.isLastInitIncomplete()).isTrue();
            assertThat(Files.readAllBytes(file)).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void malformedFileIsNotOverwrittenAndCannotOptIn() throws Exception {
        String text = "anti-spam: [unclosed\n";
        Path file = write(text);
        ChatConfig config = new ChatConfig();
        config.init(module(file));
        assertThat(config.isLastLoadUnparseable()).isTrue();
        assertThat(ChatTestHelper.getField(config, "autoMuteEnabled")).isEqualTo(false);
        assertThat(Files.readAllBytes(file)).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void failedRealReloadCannotReplaceTheLastValidAutoMutePolicy() throws Exception {
        for (String invalid : new String[] {shipped().replace("enabled: false", "enabled: true")
                .replace("duration: 30", "duration: 4"), "anti-spam: [unclosed\n"}) {
            Path file = write(shipped().replace("enabled: false", "enabled: true").replace("cooldown: 2", "cooldown: 0"));
            ChatConfig config = new ChatConfig();
            UltiToolsPlugin plugin = module(file);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
            config.init(plugin);
            com.ultikits.plugins.chat.service.AntiSpamService service =
                    new com.ultikits.plugins.chat.service.AntiSpamService();
            ChatTestHelper.setField(service, "config", config);
            ChatTestHelper.setField(service, "plugin", plugin);
            ChatTestHelper.setField(service, "clock", (java.util.function.LongSupplier) () -> 1_000_000L);
            service.refreshAutoMuteSettings();
            org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
            when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            service.checkSpam(player, "FIRST CAPS");
            service.checkSpam(player, "SECOND CAPS");
            Files.write(file, invalid.getBytes(StandardCharsets.UTF_8));
            try { config.init(plugin); }
            catch (RuntimeException refusedRange) { assertThat(config.isLastInitIncomplete()).isTrue(); }
            service.refreshAutoMuteSettings();
            assertThat(service.checkSpam(player, "THIRD CAPS")).isEqualTo("&cYou have been muted for 30 seconds.");
            assertThat(Files.readAllBytes(file)).isEqualTo(invalid.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void customLanguageKeepsItsTextAndGetsNewNoticesFromJarFallbackWithoutWrites() throws Exception {
        String custom = "{\"spam_cooldown\":\"My customized refusal\"}";
        Path file = directory.resolve("en.json");
        Files.write(file, custom.getBytes(StandardCharsets.UTF_8));
        Language jar = new Language(new String(Files.readAllBytes(
                Paths.get("src/main/resources/lang/en.json")), StandardCharsets.UTF_8));
        Language language = new Language(custom).withFallback(jar);
        assertThat(language.getLocalizedText("spam_cooldown")).isEqualTo("My customized refusal");
        assertThat(language.getLocalizedText("spam_auto_muted")).isEqualTo("&cYou have been muted for {0} seconds.");
        assertThat(language.getLocalizedText("spam_auto_mute_remaining"))
                .isEqualTo("&cYou are muted for another {0} seconds.");
        assertThat(Files.readAllBytes(file)).isEqualTo(custom.getBytes(StandardCharsets.UTF_8));
    }
}
