package com.ultikits.plugins.chat.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.ultikits.ultitools.annotations.ConfigEntry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnouncementConfig Tests")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AnnouncementConfigTest {

    private AnnouncementConfig config;

    @BeforeEach
    void setUp() {
        config = new AnnouncementConfig();
    }

    @Nested
    @DisplayName("Chat Announcement Defaults")
    class ChatAnnouncementDefaults {

        @Test
        @DisplayName("Should have chat enabled by default")
        void shouldHaveChatEnabled() {
            assertThat(config.isChatEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have default chat prefix")
        void shouldHaveDefaultChatPrefix() {
            assertThat(config.getChatPrefix()).isEqualTo("&6[Announcement] &f");
        }

        @Test
        @DisplayName("Should have default chat messages")
        void shouldHaveDefaultChatMessages() {
            assertThat(config.getChatMessages()).isNotNull();
            assertThat(config.getChatMessages()).hasSize(2);
            assertThat(config.getChatMessages().get(0)).contains("Welcome");
        }
    }

    @Nested
    @DisplayName("BossBar Announcement Defaults")
    class BossBarAnnouncementDefaults {

        @Test
        @DisplayName("Should have boss bar disabled by default")
        void shouldHaveBossBarDisabled() {
            assertThat(config.isBossBarEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should have default boss bar duration of 10 seconds")
        void shouldHaveDefaultBossBarDuration() {
            assertThat(config.getBossBarDuration()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should have default boss bar color BLUE")
        void shouldHaveDefaultBossBarColor() {
            assertThat(config.getBossBarColor()).isEqualTo("BLUE");
        }

        @Test
        @DisplayName("Should have default boss bar messages")
        void shouldHaveDefaultBossBarMessages() {
            assertThat(config.getBossBarMessages()).isNotNull();
            assertThat(config.getBossBarMessages()).hasSize(1);
            assertThat(config.getBossBarMessages().get(0)).contains("Welcome");
        }
    }

    @Nested
    @DisplayName("Title Announcement Defaults")
    class TitleAnnouncementDefaults {

        @Test
        @DisplayName("Should have title disabled by default")
        void shouldHaveTitleDisabled() {
            assertThat(config.isTitleEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should have default title fade-in of 10 ticks")
        void shouldHaveDefaultTitleFadeIn() {
            assertThat(config.getTitleFadeIn()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should have default title stay of 70 ticks")
        void shouldHaveDefaultTitleStay() {
            assertThat(config.getTitleStay()).isEqualTo(70);
        }

        @Test
        @DisplayName("Should have default title fade-out of 20 ticks")
        void shouldHaveDefaultTitleFadeOut() {
            assertThat(config.getTitleFadeOut()).isEqualTo(20);
        }

        @Test
        @DisplayName("Should have default title messages with separator")
        void shouldHaveDefaultTitleMessages() {
            assertThat(config.getTitleMessages()).isNotNull();
            assertThat(config.getTitleMessages()).hasSize(1);
            assertThat(config.getTitleMessages().get(0)).contains("||");
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("Should update chat enabled")
        void shouldUpdateChatEnabled() {
            config.setChatEnabled(false);
            assertThat(config.isChatEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should update chat prefix")
        void shouldUpdateChatPrefix() {
            config.setChatPrefix("&c[Notice] &f");
            assertThat(config.getChatPrefix()).isEqualTo("&c[Notice] &f");
        }

        @Test
        @DisplayName("Should update chat messages")
        void shouldUpdateChatMessages() {
            List<String> newMessages = Arrays.asList("Message 1", "Message 2", "Message 3");
            config.setChatMessages(newMessages);
            assertThat(config.getChatMessages()).hasSize(3);
            assertThat(config.getChatMessages()).containsExactly("Message 1", "Message 2", "Message 3");
        }

        @Test
        @DisplayName("Should update boss bar enabled")
        void shouldUpdateBossBarEnabled() {
            config.setBossBarEnabled(true);
            assertThat(config.isBossBarEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should update boss bar duration")
        void shouldUpdateBossBarDuration() {
            config.setBossBarDuration(15);
            assertThat(config.getBossBarDuration()).isEqualTo(15);
        }

        @Test
        @DisplayName("Should update boss bar color")
        void shouldUpdateBossBarColor() {
            config.setBossBarColor("RED");
            assertThat(config.getBossBarColor()).isEqualTo("RED");
        }

        @Test
        @DisplayName("Should update boss bar messages")
        void shouldUpdateBossBarMessages() {
            config.setBossBarMessages(Collections.singletonList("New bar msg"));
            assertThat(config.getBossBarMessages()).hasSize(1);
            assertThat(config.getBossBarMessages().get(0)).isEqualTo("New bar msg");
        }

        @Test
        @DisplayName("Should update title enabled")
        void shouldUpdateTitleEnabled() {
            config.setTitleEnabled(true);
            assertThat(config.isTitleEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should update title fade-in")
        void shouldUpdateTitleFadeIn() {
            config.setTitleFadeIn(20);
            assertThat(config.getTitleFadeIn()).isEqualTo(20);
        }

        @Test
        @DisplayName("Should update title stay")
        void shouldUpdateTitleStay() {
            config.setTitleStay(100);
            assertThat(config.getTitleStay()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should update title fade-out")
        void shouldUpdateTitleFadeOut() {
            config.setTitleFadeOut(30);
            assertThat(config.getTitleFadeOut()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should update title messages")
        void shouldUpdateTitleMessages() {
            List<String> newMessages = Arrays.asList("Title1||Sub1", "Title2||Sub2");
            config.setTitleMessages(newMessages);
            assertThat(config.getTitleMessages()).hasSize(2);
            assertThat(config.getTitleMessages().get(0)).isEqualTo("Title1||Sub1");
        }
    }

    /**
     * UltiKits/UltiChat#13, reworked on the framework's config-bound {@code @Scheduled}
     * (UltiKits/UltiTools-Reborn#531). The three interval keys are declared again and now drive the
     * broadcasts. Their defaults live only here, in the fields (the annotations carry no literal
     * period), in seconds.
     * <p>
     * The fields deliberately carry no {@code @Range}. The framework enforces the binding's own range
     * (at least 1 second, at most Integer.MAX_VALUE / 20) with the maintainer's two rules: refuse the
     * module at load, and on reload keep the running value with a WARNING. A {@code @Range} would
     * pre-empt the second rule: {@code ConfigManager#reloadConfigs} catches only {@code IOException},
     * so the {@code ConfigurationException} a range violation throws from {@code init} would abort
     * the whole reload ({@code UltiToolsPlugin#reloadSelf} never reaches its binding step, the other
     * settings are not reloaded and {@code onReload} does not run) instead of keeping one timer.
     */
    @Nested
    @DisplayName("Announcement intervals are configurable keys (UltiKits/UltiChat#13, UltiTools-Reborn#531)")
    class IntervalKeys {

        @Test
        @DisplayName("The three interval keys are declared, int seconds, defaults 300 / 60 / 600, range left to the framework")
        void intervalKeysAreDeclared() throws Exception {
            assertInterval("chatInterval", "announcements.chat.interval", 300);
            assertInterval("bossBarInterval", "announcements.bossbar.interval", 60);
            assertInterval("titleInterval", "announcements.title.interval", 600);
            assertThat(field("chatInterval").getInt(config)).isEqualTo(300);
            assertThat(field("bossBarInterval").getInt(config)).isEqualTo(60);
            assertThat(field("titleInterval").getInt(config)).isEqualTo(600);
        }

        @Test
        @DisplayName("The shipped announcements.yml carries the three intervals at their defaults")
        void shippedFileCarriesIntervals() throws Exception {
            YamlConfiguration shipped = shipped("config/announcements.yml");

            assertThat(shipped.getInt("announcements.chat.interval", -1)).isEqualTo(300);
            assertThat(shipped.getInt("announcements.bossbar.interval", -1)).isEqualTo(60);
            assertThat(shipped.getInt("announcements.title.interval", -1)).isEqualTo(600);
        }

        private Field field(String name) throws Exception {
            Field field = AnnouncementConfig.class.getDeclaredField(name);
            field.setAccessible(true); // NOPMD - reads the default without depending on the accessor
            return field;
        }

        private void assertInterval(String fieldName, String path, int defaultSeconds) throws Exception {
            Field field = field(fieldName);
            assertThat(field.getType()).isEqualTo(int.class);
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            assertThat(entry).as(path).isNotNull();
            assertThat(entry.path()).isEqualTo(path);
            assertThat(field.getAnnotation(com.ultikits.ultitools.annotations.config.Range.class))
                    .as(path + " must leave its range to the framework binding (see the class note)").isNull();
            // Control: the reflection does see @Range where the class declares one.
            assertThat(AnnouncementConfig.class.getDeclaredField("bossBarDuration")
                    .getAnnotation(com.ultikits.ultitools.annotations.config.Range.class)).isNotNull();
            assertThat(defaultSeconds).isBetween(1, Integer.MAX_VALUE / 20);
        }
    }

    static YamlConfiguration shipped(String resource) throws Exception {
        InputStream in = AnnouncementConfigTest.class.getClassLoader().getResourceAsStream(resource);
        assertThat(in).as("shipped resource " + resource).isNotNull();
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
    }
}
