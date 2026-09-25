package com.ultikits.plugins.chat.config;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.plugins.chat.listener.ChatListener;
import com.ultikits.plugins.chat.listener.JoinQuitListener;
import com.ultikits.plugins.chat.service.AnnouncementService;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.AutoReplyService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.service.EmojiService;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The four text-bearing configuration files hold their built-in text in the server's language, and the
 * module shows exactly what the files hold (maintainer decision 2026-09-25; UltiKits/UltiChat#18).
 * <p>
 * UltiChat's shipped defaults are English and used to stay English whatever {@code language} said. Each
 * announcement text, join/quit text, shipped channel's display name and example auto-reply rule is now
 * built-in text while it equals a default an earlier version shipped or this jar's text in any language
 * ({@code en}, {@code zh}); such a value follows {@code language} at enable and on reload, in both
 * directions. Anything else -- a one-character edit included -- is the operator's and is kept byte for
 * byte. An auto-reply rule is built-in only when its keyword and response together are one language's
 * pair. The three channel formats earlier versions shipped are removed from {@code channels.yml} at start,
 * so the file matches the chat line, which is unchanged.
 * <p>
 * Every case runs the framework's real {@code AbstractConfigEntity#init} on a temporary folder, the
 * module's real {@code registerSelf()} and {@code onReload()}, and answers from the module's real
 * catalogues.
 */
@DisplayName("config text is written in the server's language (UltiKits/UltiChat#18)")
class ChatConfigTextTest {

    private static final String ANNOUNCEMENTS = "config/announcements.yml";
    private static final String CHAT = "config/chat.yml";
    private static final String CHANNELS = "config/channels.yml";
    private static final String AUTOREPLY = "config/autoreply.yml";
    private static final List<String> FILES = Arrays.asList(ANNOUNCEMENTS, CHAT, CHANNELS, AUTOREPLY);

    private static final String[] LANGUAGES = {"en", "zh"};

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    /** The three formats earlier versions shipped on the three shipped channels (the module's initial commit). */
    private static final Map<String, String> LEGACY_FORMATS = new LinkedHashMap<>();

    static {
        LEGACY_FORMATS.put("global", "{display}&f: {message}");
        LEGACY_FORMATS.put("local", "{display}&7: {message}");
        LEGACY_FORMATS.put("staff", "&c[Staff] &f{player}&7: {message}");
    }

    /** One text setting: its file, its path, its catalogue key, whether it is a list, its shipped default, its reader. */
    private static final class Setting {
        final String file;
        final String path;
        final String key;
        final boolean list;
        final Object shipped;
        final Function<ChatConfigTextTest, Object> getter;

        Setting(String file, String path, String key, Object shipped, Function<ChatConfigTextTest, Object> getter) {
            this.file = file;
            this.path = path;
            this.key = key;
            this.list = shipped instanceof List;
            this.shipped = shipped;
            this.getter = getter;
        }

        /** The jar's text for {@code code} ("shipped" gives the shipped default), as the file holds it. */
        Object text(String code) {
            if ("shipped".equals(code)) {
                return shipped;
            }
            String value = CatalogueText.text(code, key);
            return list ? new ArrayList<>(Arrays.asList(value.split("\n", -1))) : value;
        }
    }

    /** The 16 text settings, with the one default each shipped in every earlier version. */
    private static final List<Setting> SETTINGS = Arrays.asList(
            new Setting(ANNOUNCEMENTS, "announcements.chat.prefix", "config_announcement_chat_prefix",
                    "&6[Announcement] &f", t -> t.announcements().getChatPrefix()),
            new Setting(ANNOUNCEMENTS, "announcements.chat.messages", "config_announcement_chat_messages",
                    Arrays.asList("Welcome! Type /help for assistance.", "Please follow server rules!"),
                    t -> t.announcements().getChatMessages()),
            new Setting(ANNOUNCEMENTS, "announcements.bossbar.messages", "config_announcement_bossbar_messages",
                    Collections.singletonList("&eWelcome to the server!"), t -> t.announcements().getBossBarMessages()),
            new Setting(ANNOUNCEMENTS, "announcements.title.messages", "config_announcement_title_messages",
                    Collections.singletonList("&6Welcome!||&7Enjoy your stay"), t -> t.announcements().getTitleMessages()),
            new Setting(CHAT, "join-quit.join-message-format", "config_join_message_format",
                    "&a[+] &e%player_name% &7joined the server", t -> t.chat().getJoinMessageFormat()),
            new Setting(CHAT, "join-quit.quit-message-format", "config_quit_message_format",
                    "&c[-] &e%player_name% &7left the server", t -> t.chat().getQuitMessageFormat()),
            new Setting(CHAT, "join-quit.welcome-lines", "config_welcome_lines",
                    Arrays.asList("&7========================================", "&6Welcome, &e%player_name%&6!",
                            "&7Online: &f%online_players%&7/&f%max_players%", "&7========================================"),
                    t -> t.chat().getWelcomeLines()),
            new Setting(CHAT, "join-quit.title.main", "config_join_title_main",
                    "&6Welcome Back", t -> t.chat().getTitleMain()),
            new Setting(CHAT, "join-quit.first-join-message", "config_first_join_message",
                    "&6Welcome new player &e%player_name%&6!", t -> t.chat().getFirstJoinMessage()),
            new Setting(CHANNELS, "channels.channels.global.display-name", "config_channel_global_display_name",
                    "&f[Global]", t -> t.channels().getChannels().get("global").get("display-name")),
            new Setting(CHANNELS, "channels.channels.local.display-name", "config_channel_local_display_name",
                    "&a[Local]", t -> t.channels().getChannels().get("local").get("display-name")),
            new Setting(CHANNELS, "channels.channels.staff.display-name", "config_channel_staff_display_name",
                    "&c[Staff]", t -> t.channels().getChannels().get("staff").get("display-name")),
            new Setting(AUTOREPLY, "autoreply.rules.server-ip.keyword", "config_autoreply_server_ip_keyword",
                    "server IP", t -> t.autoReply().getRules().get("server-ip").get("keyword")),
            new Setting(AUTOREPLY, "autoreply.rules.server-ip.response", "config_autoreply_server_ip_response",
                    "Server address: play.example.com", t -> t.autoReply().getRules().get("server-ip").get("response")),
            new Setting(AUTOREPLY, "autoreply.rules.rules-info.keyword", "config_autoreply_rules_info_keyword",
                    "rules", t -> t.autoReply().getRules().get("rules-info").get("keyword")),
            new Setting(AUTOREPLY, "autoreply.rules.rules-info.response", "config_autoreply_rules_info_response",
                    "Please check /rules for server rules.", t -> t.autoReply().getRules().get("rules-info").get("response")));

    @TempDir
    Path tempDir;

    private final String[] language = {"en"};

    private final PluginLogger logger = mock(PluginLogger.class);

    /** Catalogue texts an operator changed in the extracted language file on disk, answered by i18n first. */
    private final Map<String, String> diskOverrides = new LinkedHashMap<>();

    /** The configurations the module double returns from {@code getConfig(...)}, by file. */
    private final Map<String, AbstractConfigEntity> current = new LinkedHashMap<>();

    private UltiChat plugin;

    @BeforeEach
    void setUp() {
        plugin = Mockito.mock(UltiChat.class, this::moduleAnswer);
    }

    @AfterEach
    void tearDown() {
        current.clear();
    }

    // ================================================================== catalogue

    @Test
    @DisplayName("the English catalogue holds each setting's shipped default byte for byte; the Chinese one translates it without an apostrophe")
    void catalogueTexts() {
        for (Setting s : SETTINGS) {
            assertThat(s.text("en")).as(s.path).isEqualTo(s.shipped);
            assertThat(String.valueOf(s.text("zh"))).as(s.path).matches("(?s).*" + CJK.pattern() + ".*");
            // UltiKits/UltiChat#37: this module's texts pass through a formatter that doubles apostrophes.
            assertThat(CatalogueText.text("en", s.key)).as(s.path).doesNotContain("'");
            assertThat(CatalogueText.text("zh", s.key)).as(s.path).doesNotContain("'");
            if (s.list) {
                assertThat((List<?>) s.text("zh")).as(s.path).hasSameSizeAs((List<?>) s.shipped);
            }
        }
        assertThat(String.valueOf(SETTINGS.get(3).text("zh"))).as("title keeps its separator").contains("||");
    }

    // ================================================================== fresh start

    @Test
    @DisplayName("fresh start under en: every extracted file stays byte-identical to the shipped resource, nothing is saved, each getter returns the file's value")
    void freshStartEnglishLeavesTheShippedFiles() throws Exception {
        language[0] = "en";
        extractShippedResources();
        loadAll();

        start();

        for (String file : FILES) {
            assertThat(new String(bytes(file), StandardCharsets.UTF_8)).as(file).isEqualTo(shippedResource(file));
            verify(current.get(file), never()).save();
        }
        for (Setting s : SETTINGS) {
            assertThat(onDisk(s)).as(s.path).isEqualTo(s.text("en"));
            assertThat(s.getter.apply(this)).as(s.path).isEqualTo(onDisk(s));
        }
    }

    @Test
    @DisplayName("fresh start under zh: every file holds the Chinese text, each saved once, and each getter returns the file's value")
    void freshStartChinese() throws Exception {
        language[0] = "zh";
        extractShippedResources();
        loadAll();

        start();

        for (Setting s : SETTINGS) {
            assertThat(onDisk(s)).as(s.path).isEqualTo(s.text("zh"));
            assertThat(s.getter.apply(this)).as(s.path).isEqualTo(onDisk(s));
        }
        for (String file : FILES) {
            verify(current.get(file), times(1)).save();
        }
    }

    // ================================================================== the tracked set

    @Test
    @DisplayName("every built-in text in a file (shipped default, jar en text, jar zh text) is replaced with the current language's text and saved, under en and zh")
    void everyTrackedValueFollowsTheLanguage() throws Exception {
        for (String code : LANGUAGES) {
            for (String member : new String[] {"shipped", "en", "zh"}) {
                language[0] = code;
                Map<String, Object> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    values.put(s.path, s.text(member));
                }
                prepare(values);
                loadAll();

                start();

                for (Setting s : SETTINGS) {
                    String what = "language " + code + ", file held the " + member + " text of " + s.path;
                    assertThat(onDisk(s)).as(what).isEqualTo(s.text(code));
                    assertThat(s.getter.apply(this)).as(what).isEqualTo(s.text(code));
                }
                boolean alreadyCurrent = member.equals(code) || ("shipped".equals(member) && "en".equals(code));
                for (String file : FILES) {
                    verify(current.get(file), times(alreadyCurrent ? 0 : 1)).save();
                }
            }
        }
    }

    @Test
    @DisplayName("an upgraded install under en: the shipped English stays, the three formerly shipped channel formats are removed, an edited setting is kept, a second start writes nothing")
    void upgradedInstallUnderEnglish() throws Exception {
        language[0] = "en";
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> legacy : LEGACY_FORMATS.entrySet()) {
            values.put("channels.channels." + legacy.getKey() + ".format", legacy.getValue());
        }
        values.put("join-quit.quit-message-format", "&c[-] &b%player_name% left");
        prepare(values);
        loadAll();

        start();

        // Pinned to literal text, not read from the catalogue.
        YamlConfiguration chat = yaml(CHAT);
        assertThat(chat.getString("join-quit.join-message-format")).isEqualTo("&a[+] &e%player_name% &7joined the server");
        assertThat(chat.getString("join-quit.title.main")).isEqualTo("&6Welcome Back");
        assertThat(chat.getString("join-quit.quit-message-format")).isEqualTo("&c[-] &b%player_name% left");
        assertThat(yaml(ANNOUNCEMENTS).getString("announcements.chat.prefix")).isEqualTo("&6[Announcement] &f");
        YamlConfiguration channels = yaml(CHANNELS);
        for (String id : LEGACY_FORMATS.keySet()) {
            assertThat(channels.contains("channels.channels." + id + ".format")).as(id + " format removed").isFalse();
            assertThat(channels.contains("channels.channels." + id + ".permission")).as(id + " keeps its other keys").isTrue();
        }
        assertThat(channels.getString("channels.channels.global.display-name")).isEqualTo("&f[Global]");
        assertThat(chat().getQuitMessageFormat()).isEqualTo("&c[-] &b%player_name% left");
        verify(current.get(CHANNELS), times(1)).save();
        verify(current.get(CHAT), never()).save();

        Map<String, String> afterFirst = contents();
        loadAll();
        start();
        assertThat(contents()).isEqualTo(afterFirst);
        for (String file : FILES) {
            verify(current.get(file), never()).save();
        }
    }

    @Test
    @DisplayName("a file a zh server wrote reads exactly the English text after a switch to en (pinned, not read from the catalogue)")
    void chineseTextSwitchedToEnglishIsExactEnglish() throws Exception {
        language[0] = "zh";
        extractShippedResources();
        loadAll();
        start();

        language[0] = "en";
        loadAll();
        start();

        assertThat(yaml(CHAT).getString("join-quit.join-message-format")).isEqualTo("&a[+] &e%player_name% &7joined the server");
        assertThat(yaml(CHAT).getStringList("join-quit.welcome-lines")).containsExactly(
                "&7========================================", "&6Welcome, &e%player_name%&6!",
                "&7Online: &f%online_players%&7/&f%max_players%", "&7========================================");
        assertThat(yaml(ANNOUNCEMENTS).getStringList("announcements.title.messages")).containsExactly("&6Welcome!||&7Enjoy your stay");
        assertThat(yaml(CHANNELS).getString("channels.channels.staff.display-name")).isEqualTo("&c[Staff]");
        assertThat(yaml(AUTOREPLY).getString("autoreply.rules.server-ip.keyword")).isEqualTo("server IP");
        assertThat(yaml(AUTOREPLY).getString("autoreply.rules.server-ip.response")).isEqualTo("Server address: play.example.com");
        assertThat(chat().getJoinMessageFormat()).isEqualTo("&a[+] &e%player_name% &7joined the server");
    }

    @Test
    @DisplayName("a customised value, or built-in text changed by one character, is kept byte for byte under both languages and nothing is saved")
    void customisedValuesAreKept() throws Exception {
        for (String code : LANGUAGES) {
            for (String variant : new String[] {"shipped!", "en!", "zh!", "own"}) {
                language[0] = code;
                Map<String, Object> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    values.put(s.path, variant(s, variant));
                }
                prepare(values);
                loadAll();
                Map<String, String> before = contents();

                start();

                assertThat(contents()).as(code + " " + variant).isEqualTo(before);
                for (Setting s : SETTINGS) {
                    assertThat(s.getter.apply(this)).as(code + " " + variant + " " + s.path).isEqualTo(values.get(s.path));
                }
                for (String file : FILES) {
                    verify(current.get(file), never()).save();
                }
            }
        }
    }

    @Test
    @DisplayName("an auto-reply rule is built-in only as a whole pair of one language: a mixed or half-edited pair is kept")
    void autoReplyRulesAreComparedAsPairs() throws Exception {
        String[][] pairs = {
                {"shipped", "zh"},
                {"zh", "en"},
        };
        for (String code : LANGUAGES) {
            for (String[] pair : pairs) {
                language[0] = code;
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("autoreply.rules.server-ip.keyword", setting("autoreply.rules.server-ip.keyword").text(pair[0]));
                values.put("autoreply.rules.server-ip.response", setting("autoreply.rules.server-ip.response").text(pair[1]));
                values.put("autoreply.rules.rules-info.keyword", setting("autoreply.rules.rules-info.keyword").text(pair[1]));
                values.put("autoreply.rules.rules-info.response", setting("autoreply.rules.rules-info.response").text(pair[0]));
                prepare(values);
                loadAll();
                String before = new String(bytes(AUTOREPLY), StandardCharsets.UTF_8);

                start();

                assertThat(new String(bytes(AUTOREPLY), StandardCharsets.UTF_8)).as(code + " " + Arrays.toString(pair)).isEqualTo(before);
                verify(current.get(AUTOREPLY), never()).save();
            }
        }
    }

    @Test
    @DisplayName("a second enable with the same language writes nothing")
    void secondEnableWritesNothing() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            extractShippedResources();
            loadAll();
            start();
            Map<String, String> afterFirst = contents();

            loadAll();
            start();

            assertThat(contents()).as(code).isEqualTo(afterFirst);
            for (String file : FILES) {
                verify(current.get(file), never()).save();
            }
        }
    }

    // ================================================================== class B: formerly shipped channel formats

    @Test
    @DisplayName("each formerly shipped channel format is removed from any channel and saved; a one-character variant is kept")
    void legacyChannelFormatsAreRemoved() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, Object> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.text(code));
            }
            values.put("channels.channels.global.format", "&c[Staff] &f{player}&7: {message}");
            values.put("channels.channels.local.format", "{display}&7: {message} ");
            values.put("channels.channels.trade.display-name", "&6[Trade]");
            values.put("channels.channels.trade.format", "{display}&f: {message}");
            values.put("channels.channels.trade.range", 50);
            prepare(values);
            loadAll();

            start();

            YamlConfiguration channels = yaml(CHANNELS);
            assertThat(channels.contains("channels.channels.global.format")).as(code).isFalse();
            assertThat(channels.contains("channels.channels.trade.format")).as(code).isFalse();
            assertThat(channels.getString("channels.channels.local.format")).as(code).isEqualTo("{display}&7: {message} ");
            assertThat(channels.getString("channels.channels.trade.display-name")).as(code).isEqualTo("&6[Trade]");
            assertThat(channels.getInt("channels.channels.trade.range")).as(code).isEqualTo(50);
            assertThat(channels().getChannels().get("global")).as(code).doesNotContainKey("format");
            verify(current.get(CHANNELS), times(1)).save();
        }
    }

    @Test
    @DisplayName("a materializing save keeps every operator channel and rule, with every key inside it, and every shipped channel's other keys")
    void operatorChannelsAndRulesSurviveASave() throws Exception {
        language[0] = "zh";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("channels.channels.global.format", "{display}&f: {message}");
        values.put("channels.channels.global.extra-key", "kept");
        values.put("channels.channels.trade.display-name", "&6[Trade]");
        values.put("channels.channels.trade.permission", "server.trade");
        values.put("channels.channels.trade.range", 250);
        values.put("channels.channels.trade.cross-world", false);
        values.put("channels.channels.trade.format", "{display} {player}: {message}");
        values.put("channels.channels.trade.unknown", "anything");
        values.put("autoreply.rules.discord.keyword", "discord");
        values.put("autoreply.rules.discord.response", Arrays.asList("&bJoin us:", "&fexample.invalid"));
        values.put("autoreply.rules.discord.mode", "regex");
        values.put("autoreply.rules.discord.case-sensitive", true);
        values.put("autoreply.rules.discord.permission", "chat.discord");
        values.put("autoreply.rules.discord.commands", Collections.singletonList("say hi"));
        values.put("autoreply.rules.server-ip.mode", "exact");
        prepare(values);
        YamlConfiguration channelsBefore = yaml(CHANNELS);
        YamlConfiguration rulesBefore = yaml(AUTOREPLY);
        loadAll();

        start();

        verify(current.get(CHANNELS), times(1)).save();
        verify(current.get(AUTOREPLY), times(1)).save();
        YamlConfiguration channelsAfter = yaml(CHANNELS);
        for (String key : channelsBefore.getKeys(true)) {
            if (channelsBefore.isConfigurationSection(key) || key.endsWith(".display-name")
                    || "channels.channels.global.format".equals(key)) {
                continue;
            }
            assertThat(channelsAfter.get(key)).as(key).isEqualTo(channelsBefore.get(key));
        }
        assertThat(channelsAfter.getString("channels.channels.trade.display-name")).isEqualTo("&6[Trade]");
        assertThat(channelsAfter.getConfigurationSection("channels.channels").getKeys(false))
                .containsExactly("global", "local", "staff", "trade");
        YamlConfiguration rulesAfter = yaml(AUTOREPLY);
        for (String key : rulesBefore.getKeys(true)) {
            if (rulesBefore.isConfigurationSection(key) || key.endsWith("server-ip.keyword") || key.endsWith("server-ip.response")
                    || key.endsWith("rules-info.keyword") || key.endsWith("rules-info.response")) {
                continue;
            }
            assertThat(rulesAfter.get(key)).as(key).isEqualTo(rulesBefore.get(key));
        }
        assertThat(rulesAfter.getConfigurationSection("autoreply.rules").getKeys(false))
                .containsExactly("server-ip", "rules-info", "discord");
        assertThat(rulesAfter.getString("autoreply.rules.server-ip.keyword"))
                .isEqualTo(setting("autoreply.rules.server-ip.keyword").text("zh"));
    }

    // ================================================================== reload

    @Test
    @DisplayName("onReload() after a language switch rewrites every built-in text in the new language, in both directions")
    void reloadFollowsALanguageSwitchBothWays() throws Exception {
        for (String[] direction : new String[][] {{"en", "zh"}, {"zh", "en"}}) {
            language[0] = direction[0];
            extractShippedResources();
            loadAll();
            start();

            language[0] = direction[1];
            for (AbstractConfigEntity config : current.values()) {
                config.init(plugin);
            }
            reload();

            for (Setting s : SETTINGS) {
                String what = direction[0] + " -> " + direction[1] + ": " + s.path;
                assertThat(onDisk(s)).as(what).isEqualTo(s.text(direction[1]));
                assertThat(s.getter.apply(this)).as(what).isEqualTo(s.text(direction[1]));
            }
        }
    }

    @Test
    @DisplayName("no configuration change listener rewrites the text (the framework fires them before it reloads the language)")
    void changeListenersDoNotMaterialize() throws Exception {
        language[0] = "en";
        extractShippedResources();
        loadAll();
        start();
        Map<String, String> before = contents();

        language[0] = "zh";
        for (AbstractConfigEntity config : current.values()) {
            // This module registers no change listener, and the framework registers none for it, so
            // nothing can write a file before the framework rebuilds the language; pinned so that adding
            // one is seen.
            assertThat(config.getChangeListeners()).as(config.getConfigFilePath()).isEmpty();
            for (com.ultikits.ultitools.interfaces.ConfigChangeListener listener : new ArrayList<>(config.getChangeListeners())) {
                listener.onConfigReload(config);
            }
        }

        assertThat(contents()).isEqualTo(before);
        assertThat(chat().getJoinMessageFormat()).isEqualTo("&a[+] &e%player_name% &7joined the server");
    }

    // ================================================================== the text source

    @Test
    @DisplayName("an operator-edited language file on disk does not widen what counts as built-in text")
    void diskCatalogueDoesNotWidenTheTrackedSet() throws Exception {
        Path lang = Files.createDirectories(tempDir.resolve("lang"));
        StringBuilder json = new StringBuilder("{\n");
        for (int i = 0; i < SETTINGS.size(); i++) {
            Setting s = SETTINGS.get(i);
            json.append("  \"").append(s.key).append("\": \"Edited ").append(s.key).append('"')
                    .append(i + 1 < SETTINGS.size() ? ",\n" : "\n");
            diskOverrides.put(s.key, "Edited " + s.key);
        }
        json.append("}\n");
        for (String code : LANGUAGES) {
            Files.write(lang.resolve(code + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, Object> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                String edited = "Edited " + s.key;
                values.put(s.path, s.list ? new ArrayList<>(Collections.singletonList(edited)) : edited);
            }
            prepare(values);
            loadAll();
            Map<String, String> before = contents();

            start();

            assertThat(contents()).as(code).isEqualTo(before);
            for (String file : FILES) {
                verify(current.get(file), never()).save();
            }
        }
    }

    @Test
    @DisplayName("an edit of the extracted language file is not written into the config files, so each value keeps following a language switch (the text source decision of 2026-09-25)")
    void diskCatalogueEditDoesNotReachTheFile() throws Exception {
        for (Setting s : SETTINGS) {
            diskOverrides.put(s.key, "Edited " + s.key);
        }
        language[0] = "zh";
        extractShippedResources();
        loadAll();
        start();

        for (Setting s : SETTINGS) {
            assertThat(onDisk(s)).as("zh, " + s.path + ": the jar's text, not the disk edit").isEqualTo(s.text("zh"));
        }

        language[0] = "en";
        for (AbstractConfigEntity config : current.values()) {
            config.init(plugin);
        }
        reload();

        for (Setting s : SETTINGS) {
            assertThat(onDisk(s)).as("after a switch to en, " + s.path + " follows").isEqualTo(s.text("en"));
        }
    }

    // ================================================================== save failure

    @Test
    @DisplayName("a file that cannot be saved is reported in the server's language, naming the file, and the module still uses the new text")
    void saveFailureIsReported() throws Exception {
        language[0] = "zh";
        extractShippedResources();
        loadAll();
        doThrow(new IOException("read-only")).when(current.get(CHAT)).save();

        assertThat(plugin.registerSelf()).isTrue();

        String expected = CatalogueText.text("zh", "log_config_text_save_failed")
                .replace("{FILE}", new File(tempDir.toFile(), CHAT).getPath())
                .replace("{ERROR}", "read-only");
        verify(logger).warn(expected);
        assertThat(chat().getJoinMessageFormat()).isEqualTo(setting("join-quit.join-message-format").text("zh"));
        assertThat(yaml(CHAT).getString("join-quit.join-message-format")).isEqualTo("&a[+] &e%player_name% &7joined the server");
        verify(current.get(ANNOUNCEMENTS), times(1)).save();
    }

    // ================================================================== validation and Java defaults

    @Test
    @DisplayName("@NotEmpty is on exactly the fields that carried it at origin/master, and each text setting's Java default is its shipped default")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void validationAndJavaDefaults() throws Exception {
        Map<Class<?>, Set<String>> atMaster = new LinkedHashMap<>();
        atMaster.put(AnnouncementConfig.class, new TreeSet<>(Collections.singletonList("chatPrefix")));
        atMaster.put(ChatConfig.class, new TreeSet<>(Collections.singletonList("chatFormat")));
        atMaster.put(ChannelConfig.class, new TreeSet<String>());
        atMaster.put(AutoReplyConfig.class, new TreeSet<String>());
        for (Map.Entry<Class<?>, Set<String>> e : atMaster.entrySet()) {
            Set<String> notEmpty = new TreeSet<>();
            for (Field f : e.getKey().getDeclaredFields()) {
                if (f.isAnnotationPresent(ConfigEntry.class) && f.isAnnotationPresent(NotEmpty.class)) {
                    notEmpty.add(f.getName());
                }
            }
            assertThat(notEmpty).as(e.getKey().getSimpleName()).isEqualTo(e.getValue());
        }

        current.put(ANNOUNCEMENTS, new AnnouncementConfig());
        current.put(CHAT, new ChatConfig());
        current.put(CHANNELS, new ChannelConfig());
        current.put(AUTOREPLY, new AutoReplyConfig());
        for (Setting s : SETTINGS) {
            assertThat(s.getter.apply(this)).as(s.path).isEqualTo(s.shipped);
        }
    }

    // ================================================================== readers

    @Test
    @DisplayName("JoinQuitListener's join, quit, welcome, title and first-join texts are rendered from chat.yml")
    void joinQuitListenerRendersTheFile() throws Exception {
        language[0] = "zh";
        extractShippedResources();
        loadAll();
        start();
        YamlConfiguration disk = yaml(CHAT);
        ChatTestHelper.setUp();
        try {
            Player player = ChatTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(player.hasPlayedBefore()).thenReturn(false);
            JoinQuitListener listener = new JoinQuitListener();
            ChatTestHelper.setField(listener, "config", chat());

            PlayerJoinEvent join = new PlayerJoinEvent(player, "x");
            listener.onPlayerJoin(join);
            PlayerQuitEvent quit = new PlayerQuitEvent(player, "x");
            listener.onPlayerQuit(quit);

            assertThat(join.getJoinMessage()).isEqualTo(render(disk.getString("join-quit.join-message-format"), "Steve"));
            assertThat(quit.getQuitMessage()).isEqualTo(render(disk.getString("join-quit.quit-message-format"), "Steve"));
            for (String line : disk.getStringList("join-quit.welcome-lines")) {
                verify(player, Mockito.atLeastOnce()).sendMessage(render(line, "Steve"));
            }
            verify(player).sendTitle(render(disk.getString("join-quit.title.main"), "Steve"), render("&7%player_name%", "Steve"), 10, 70, 20);
            verify(ChatTestHelper.getMockServer()).broadcastMessage(render(disk.getString("join-quit.first-join-message"), "Steve"));
            assertThat(join.getJoinMessage()).matches("(?s).*" + CJK.pattern() + ".*");
        } finally {
            ChatTestHelper.tearDown();
        }
    }

    @Test
    @DisplayName("ChatListener shows the file's display name; a removed legacy format gives the unchanged global line; a customised format applies with {display}")
    void chatListenerRendersTheFile() throws Exception {
        language[0] = "zh";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("channels.channels.global.format", "{display}&f: {message}");
        values.put("channels.channels.local.format", "{display} {player} > {message}");
        prepare(values);
        loadAll();
        start();
        YamlConfiguration disk = yaml(CHANNELS);
        ChatTestHelper.setUp();
        try {
            UUID id = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("Steve", id);
            ChatConfig chat = chat();
            chat.setAntiSpamEnabled(false);
            chat.setMentionsEnabled(false);
            ChannelService channelService = new ChannelService();
            ChatTestHelper.setField(channelService, "config", channels());
            ChatListener listener = new ChatListener(chat, channels(), mock(AntiSpamService.class), channelService,
                    mock(EmojiService.class));

            AsyncPlayerChatEvent global = chatEvent(player);
            listener.onChat(global);
            channelService.setPlayerChannel(id, "local");
            AsyncPlayerChatEvent local = chatEvent(player);
            listener.onChat(local);

            String globalDisplay = disk.getString("channels.channels.global.display-name");
            assertThat(globalDisplay).isEqualTo(setting("channels.channels.global.display-name").text("zh"));
            assertThat(global.getFormat()).isEqualTo(ChatColor.translateAlternateColorCodes('&',
                    globalDisplay + " &7[&f%%player_world%%&7] &f%1$s&7: &f%2$s"));
            assertThat(local.getFormat()).isEqualTo(ChatColor.translateAlternateColorCodes('&',
                    disk.getString("channels.channels.local.display-name") + " %1$s > %2$s"));
        } finally {
            ChatTestHelper.tearDown();
        }
    }

    @Test
    @DisplayName("AnnouncementService broadcasts the prefix and message announcements.yml holds")
    void announcementServiceRendersTheFile() throws Exception {
        language[0] = "zh";
        extractShippedResources();
        loadAll();
        start();
        YamlConfiguration disk = yaml(ANNOUNCEMENTS);
        ChatTestHelper.setUp();
        try {
            Player player = ChatTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            doReturn(Collections.singletonList(player)).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            new AnnouncementService(announcements()).broadcastChat();

            verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&',
                    disk.getString("announcements.chat.prefix") + disk.getStringList("announcements.chat.messages").get(0)));
        } finally {
            ChatTestHelper.tearDown();
        }
    }

    @Test
    @DisplayName("AutoReplyService matches the keyword and answers the response autoreply.yml holds")
    void autoReplyServiceUsesTheFile() throws Exception {
        language[0] = "zh";
        extractShippedResources();
        loadAll();
        start();
        YamlConfiguration disk = yaml(AUTOREPLY);
        AutoReplyService service = new AutoReplyService();
        ChatTestHelper.setField(service, "config", autoReply());

        Map.Entry<String, Map<String, Object>> match =
                service.findMatch("hi " + disk.getString("autoreply.rules.server-ip.keyword") + "?");

        assertThat(match).isNotNull();
        assertThat(match.getKey()).isEqualTo("server-ip");
        assertThat(service.getResponse(match.getValue())).isEqualTo(disk.getString("autoreply.rules.server-ip.response"));
        assertThat(service.findMatch("what is the server IP")).isNull();
    }

    // ================================================================== harness

    private AnnouncementConfig announcements() {
        return (AnnouncementConfig) current.get(ANNOUNCEMENTS);
    }

    private ChatConfig chat() {
        return (ChatConfig) current.get(CHAT);
    }

    private ChannelConfig channels() {
        return (ChannelConfig) current.get(CHANNELS);
    }

    private AutoReplyConfig autoReply() {
        return (AutoReplyConfig) current.get(AUTOREPLY);
    }

    private static Setting setting(String path) {
        for (Setting s : SETTINGS) {
            if (s.path.equals(path)) {
                return s;
            }
        }
        throw new IllegalArgumentException(path);
    }

    /** A value that is not built-in text: a built-in text changed by one character, or the operator's own. */
    private static Object variant(Setting s, String variant) {
        if ("own".equals(variant)) {
            String own = "&dOperator text for " + s.path;
            return s.list ? new ArrayList<>(Collections.singletonList(own)) : own;
        }
        Object base = s.text(variant.substring(0, variant.length() - 1));
        if (!s.list) {
            return base + "!";
        }
        List<String> lines = new ArrayList<>(castList(base));
        lines.set(lines.size() - 1, lines.get(lines.size() - 1) + "!");
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object o) {
        return (List<String>) o;
    }

    private static String render(String text, String name) {
        return ChatColor.translateAlternateColorCodes('&', text.replace("%player_name%", name).replace("{player}", name)
                .replace("{displayname}", name).replace("%online_players%", "0").replace("%max_players%", "100"));
    }

    private static AsyncPlayerChatEvent chatEvent(Player player) {
        Set<Player> recipients = new HashSet<>();
        recipients.add(player);
        return new AsyncPlayerChatEvent(false, player, "hello", recipients);
    }

    private File file(String path) {
        return new File(tempDir.toFile(), path);
    }

    private byte[] bytes(String path) throws IOException {
        return Files.readAllBytes(file(path).toPath());
    }

    private Map<String, String> contents() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String f : FILES) {
            result.put(f, new String(bytes(f), StandardCharsets.UTF_8));
        }
        return result;
    }

    private YamlConfiguration yaml(String path) {
        return YamlConfiguration.loadConfiguration(file(path));
    }

    private Object onDisk(Setting s) {
        return yaml(s.file).get(s.path);
    }

    private static String shippedResource(String path) throws IOException {
        try (InputStream in = UltiChat.class.getResourceAsStream("/" + path)) {
            assertThat(in).as("shipped resource " + path).isNotNull();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) {
                out.write(chunk, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** The framework's first start: each shipped {@code config/*.yml} copied into the module's folder as it is. */
    private void extractShippedResources() throws IOException {
        for (String f : FILES) {
            Files.createDirectories(file(f).getParentFile().toPath());
            Files.write(file(f).toPath(), shippedResource(f).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The shipped files with {@code values} (path to value) set, as an earlier version or an operator left them. */
    private void prepare(Map<String, Object> values) throws IOException {
        extractShippedResources();
        Map<String, YamlConfiguration> yamls = new LinkedHashMap<>();
        for (String f : FILES) {
            yamls.put(f, yaml(f));
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String key = e.getKey();
            String f = key.startsWith("announcements.") ? ANNOUNCEMENTS : key.startsWith("join-quit.") ? CHAT
                    : key.startsWith("channels.") ? CHANNELS : AUTOREPLY;
            yamls.get(f).set(key, e.getValue());
        }
        for (Map.Entry<String, YamlConfiguration> e : yamls.entrySet()) {
            e.getValue().save(file(e.getKey()));
        }
    }

    /** The framework's own load of each entity: {@code init} fills missing keys, saves, validates. */
    private void loadAll() throws IOException {
        current.clear();
        current.put(ANNOUNCEMENTS, load(new AnnouncementConfig()));
        current.put(CHAT, load(new ChatConfig()));
        current.put(CHANNELS, load(new ChannelConfig()));
        current.put(AUTOREPLY, load(new AutoReplyConfig()));
    }

    private AbstractConfigEntity load(AbstractConfigEntity config) throws IOException {
        config.init(plugin);
        return spy(config);
    }

    /** The module's enable path: {@code UltiChat#registerSelf()} with {@link #current} as its configuration. */
    private void start() {
        assertThat(plugin.registerSelf()).isTrue();
    }

    /** The module's {@code onReload()} (protected), as the framework calls it after rebuilding the language. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void reload() throws Exception {
        Method onReload = UltiChat.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true);
        onReload.invoke(plugin);
    }

    /**
     * The module double: {@code registerSelf()} and {@code onReload()} are the real ones, the configuration
     * folder is the temporary directory, {@code i18n} answers from the module's real catalogue for the
     * language in {@link #language} (an entry of {@link #diskOverrides} first, as an edited language file on
     * disk would), and {@code getConfig(...)} returns the matching entry of {@link #current}.
     */
    private Object moduleAnswer(InvocationOnMock invocation) throws Throwable {
        switch (invocation.getMethod().getName()) {
            case "registerSelf":
            case "onReload":
                return invocation.callRealMethod();
            case "getConfigFolder":
                return tempDir.toString();
            case "getConfigFile":
            case "operatorConfigFile":
                return file(invocation.<String>getArgument(0));
            case "i18n": {
                String key = invocation.getArgument(invocation.getArguments().length - 1);
                return diskOverrides.containsKey(key) ? diskOverrides.get(key)
                        : CatalogueText.answer(language[0]).answer(invocation);
            }
            case "getLanguageCode":
                return language[0];
            case "getLogger":
                return logger;
            case "getConfig": {
                for (AbstractConfigEntity config : current.values()) {
                    if (invocation.getArgument(0) == config.getClass().getSuperclass()
                            || invocation.getArgument(0) == config.getClass()) {
                        return config;
                    }
                }
                return null;
            }
            default:
                return Answers.RETURNS_DEFAULTS.answer(invocation);
        }
    }
}
