package com.ultikits.plugins.chat.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.Range;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Main chat settings.
 * <p>
 * The join, quit, welcome, title and first-join texts are written in the server's language when the
 * module starts and after {@code /ul reload} ({@link #materializeText}); see {@link ConfigTextDefaults}.
 */
@Getter
@Setter
@ConfigEntity("config/chat.yml")
public class ChatConfig extends AbstractConfigEntity {

    /** The join message every earlier version shipped. */
    static final String SHIPPED_JOIN_MESSAGE_FORMAT = "&a[+] &e%player_name% &7joined the server";

    /** The quit message every earlier version shipped. */
    static final String SHIPPED_QUIT_MESSAGE_FORMAT = "&c[-] &e%player_name% &7left the server";

    /** The welcome lines every earlier version shipped. */
    static final List<String> SHIPPED_WELCOME_LINES = Collections.unmodifiableList(Arrays.asList(
            "&7========================================",
            "&6Welcome, &e%player_name%&6!",
            "&7Online: &f%online_players%&7/&f%max_players%",
            "&7========================================"));

    /** The join title every earlier version shipped. */
    static final String SHIPPED_TITLE_MAIN = "&6Welcome Back";

    /** The first-join broadcast every earlier version shipped. */
    static final String SHIPPED_FIRST_JOIN_MESSAGE = "&6Welcome new player &e%player_name%&6!";

    // Chat format
    @ConfigEntry(path = "chat.format-enabled", comment = "Enable chat formatting / 启用聊天格式化")
    private boolean chatFormatEnabled = true;

    @NotEmpty
    @ConfigEntry(path = "chat.format", comment = "Chat format (PlaceholderAPI supported) / 聊天格式")
    private String chatFormat = "&7[&f%player_world%&7] &f{player}&7: &f{message}";

    // Join/quit
    @ConfigEntry(path = "join-quit.join-message-enabled", comment = "Enable custom join message / 启用自定义进入消息")
    private boolean joinMessageEnabled = true;

    @ConfigEntry(path = "join-quit.join-message-format", comment = "Join message format / 进入消息格式")
    private String joinMessageFormat = SHIPPED_JOIN_MESSAGE_FORMAT;

    @ConfigEntry(path = "join-quit.quit-message-enabled", comment = "Enable custom quit message / 启用自定义离开消息")
    private boolean quitMessageEnabled = true;

    @ConfigEntry(path = "join-quit.quit-message-format", comment = "Quit message format / 离开消息格式")
    private String quitMessageFormat = SHIPPED_QUIT_MESSAGE_FORMAT;

    @ConfigEntry(path = "join-quit.welcome-enabled", comment = "Enable welcome message on join / 启用入服欢迎消息")
    private boolean welcomeEnabled = true;

    @ConfigEntry(path = "join-quit.welcome-lines", comment = "Welcome message lines / 欢迎消息内容")
    private List<String> welcomeLines = new ArrayList<>(SHIPPED_WELCOME_LINES);

    @ConfigEntry(path = "join-quit.title.enabled", comment = "Show title on join / 显示进入标题")
    private boolean titleEnabled = true;

    @ConfigEntry(path = "join-quit.title.main", comment = "Title main text / 标题主文本")
    private String titleMain = SHIPPED_TITLE_MAIN;

    @ConfigEntry(path = "join-quit.title.sub", comment = "Title subtitle / 标题副文本")
    private String titleSub = "&7%player_name%";

    @ConfigEntry(path = "join-quit.first-join-message", comment = "First join broadcast / 首次加入广播")
    private String firstJoinMessage = SHIPPED_FIRST_JOIN_MESSAGE;

    // Mentions
    @ConfigEntry(path = "mentions.enabled", comment = "Enable @player mentions / 启用@提及")
    private boolean mentionsEnabled = true;

    @ConfigEntry(path = "mentions.format", comment = "Mention highlight format / 提及高亮格式")
    private String mentionFormat = "&e@{player}&r";

    @ConfigEntry(path = "mentions.sound", comment = "Sound when mentioned / 被提及时的音效")
    private String mentionSound = "ENTITY_EXPERIENCE_ORB_PICKUP";

    @ConfigEntry(path = "mentions.self-mention", comment = "Allow self-mention / 允许自我提及")
    private boolean selfMention = false;

    // Anti-spam
    @ConfigEntry(path = "anti-spam.enabled", comment = "Enable anti-spam / 启用反刷屏")
    private boolean antiSpamEnabled = true;

    @Range(min = 0, max = 60)
    @ConfigEntry(path = "anti-spam.cooldown", comment = "Cooldown between messages (seconds) / 消息间隔(秒)")
    private int antiSpamCooldown = 2;

    @Range(min = 1, max = 20)
    @ConfigEntry(path = "anti-spam.max-duplicate", comment = "Max identical messages / 最大重复消息数")
    private int antiSpamMaxDuplicate = 3;

    /**
     * The declared default duplicate window, in seconds: {@code 0}, meaning no time limit.
     * <p>
     * Before UltiKits/UltiChat#14 the window was ignored and a repeat counted however far apart its
     * copies were sent. Any positive window weakens that for slow repeats, so the default keeps the
     * old count-only rule exactly, and a positive value is an operator's choice to add a time limit.
     */
    public static final int DEFAULT_DUPLICATE_WINDOW_SECONDS = 0;

    @Range(min = 0, max = 600)
    @ConfigEntry(path = "anti-spam.duplicate-window", comment = "Duplicate detection window (seconds, 0 = no time limit) / 重复检测窗口(秒，0 = 不限时)")
    private int antiSpamDuplicateWindow = DEFAULT_DUPLICATE_WINDOW_SECONDS;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "anti-spam.caps-limit", comment = "Max uppercase percentage / 最大大写百分比")
    private int antiSpamCapsLimit = 70;

    public ChatConfig() {
        super("config/chat.yml");
    }

    /**
     * Replaces each join/quit text that is still built-in text with its text in the server's language
     * (maintainer decision 2026-09-25, UltiKits/UltiChat#18). The welcome lines are compared whole.
     *
     * @param text the module jar's text for a catalogue key, in the language the framework loads
     * @return whether any value changed, so the caller saves the file once
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(ChatConfig.class);
        String join = single(text, jar, "joinMessageFormat", joinMessageFormat,
                "config_join_message_format", SHIPPED_JOIN_MESSAGE_FORMAT);
        String quit = single(text, jar, "quitMessageFormat", quitMessageFormat,
                "config_quit_message_format", SHIPPED_QUIT_MESSAGE_FORMAT);
        List<String> welcome = ConfigTextDefaults.materializeLines(ChatConfig.class, "welcomeLines", welcomeLines,
                ConfigTextDefaults.currentLines(text, "config_welcome_lines"),
                ConfigTextDefaults.trackedLines(jar, "config_welcome_lines", SHIPPED_WELCOME_LINES));
        String title = single(text, jar, "titleMain", titleMain, "config_join_title_main", SHIPPED_TITLE_MAIN);
        String firstJoin = single(text, jar, "firstJoinMessage", firstJoinMessage,
                "config_first_join_message", SHIPPED_FIRST_JOIN_MESSAGE);
        boolean changed = join != joinMessageFormat || quit != quitMessageFormat || welcome != welcomeLines
                || title != titleMain || firstJoin != firstJoinMessage;
        joinMessageFormat = join;
        quitMessageFormat = quit;
        welcomeLines = welcome;
        titleMain = title;
        firstJoinMessage = firstJoin;
        return changed;
    }

    private static String single(Function<String, String> text, Map<String, Map<String, String>> jar,
                                 String field, String value, String key, String shipped) {
        return ConfigTextDefaults.materialize(ChatConfig.class, field, value,
                ConfigTextDefaults.currentText(text, "", key), ConfigTextDefaults.tracked(jar, "", key, shipped));
    }
}
