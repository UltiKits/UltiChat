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
 * Announcement settings.
 * <p>
 * The three {@code announcements.*.interval} keys are each broadcast's period, in seconds. They
 * were declared but never read before (UltiKits/UltiChat#13); they are now bound to the
 * {@code @Scheduled} methods on {@code AnnouncementService} through the framework's config-bound
 * periods (UltiKits/UltiTools-Reborn#531), so these fields hold the only copy of the defaults. A
 * changed value takes effect at {@code /ul reload} (or {@code /uchat reload}), keeping the task's
 * place in its cycle. The range -- at least 1 second, at most {@code Integer.MAX_VALUE / 20}
 * seconds -- is enforced by the framework's binding, not by {@code @Range} here: an invalid value
 * refuses the module at load and, on reload, is ignored with a warning while the running interval
 * is kept. A {@code @Range} would abort the whole reload instead, because the configuration's own
 * validation failure leaves {@code reloadSelf} before the binding step runs.
 * <p>
 * The chat prefix and the three message lists are written in the server's language when the module
 * starts and after {@code /ul reload} ({@link #materializeText}); see {@link ConfigTextDefaults}.
 */
@Getter
@Setter
@ConfigEntity("config/announcements.yml")
public class AnnouncementConfig extends AbstractConfigEntity {

    /** The chat prefix every earlier version shipped. */
    static final String SHIPPED_CHAT_PREFIX = "&6[Announcement] &f";

    /** The chat announcements every earlier version shipped. */
    static final List<String> SHIPPED_CHAT_MESSAGES = Collections.unmodifiableList(Arrays.asList(
            "Welcome! Type /help for assistance.",
            "Please follow server rules!"));

    /** The boss bar announcement every earlier version shipped. */
    static final List<String> SHIPPED_BOSSBAR_MESSAGES = Collections.singletonList("&eWelcome to the server!");

    /** The title announcement every earlier version shipped. */
    static final List<String> SHIPPED_TITLE_MESSAGES = Collections.singletonList("&6Welcome!||&7Enjoy your stay");

    // Chat announcements
    @ConfigEntry(path = "announcements.chat.enabled", comment = "Enable chat announcements / 启用聊天公告")
    private boolean chatEnabled = true;

    @ConfigEntry(path = "announcements.chat.interval", comment = "Chat announcement interval (seconds) / 聊天公告间隔(秒)")
    private int chatInterval = 300;

    @NotEmpty
    @ConfigEntry(path = "announcements.chat.prefix", comment = "Chat announcement prefix / 聊天公告前缀")
    private String chatPrefix = SHIPPED_CHAT_PREFIX;

    @ConfigEntry(path = "announcements.chat.messages", comment = "Chat announcement messages / 聊天公告内容")
    private List<String> chatMessages = new ArrayList<>(SHIPPED_CHAT_MESSAGES);

    // BossBar announcements
    @ConfigEntry(path = "announcements.bossbar.enabled", comment = "Enable boss bar announcements / 启用Boss栏公告")
    private boolean bossBarEnabled = false;

    @ConfigEntry(path = "announcements.bossbar.interval", comment = "Boss bar interval (seconds) / Boss栏间隔(秒)")
    private int bossBarInterval = 60;

    @Range(min = 1, max = 60)
    @ConfigEntry(path = "announcements.bossbar.duration", comment = "Boss bar display duration (seconds) / Boss栏显示时长(秒)")
    private int bossBarDuration = 10;

    @ConfigEntry(path = "announcements.bossbar.color", comment = "Boss bar color / Boss栏颜色")
    private String bossBarColor = "BLUE";

    @ConfigEntry(path = "announcements.bossbar.messages", comment = "Boss bar messages / Boss栏公告内容")
    private List<String> bossBarMessages = new ArrayList<>(SHIPPED_BOSSBAR_MESSAGES);

    // Title announcements
    @ConfigEntry(path = "announcements.title.enabled", comment = "Enable title announcements / 启用标题公告")
    private boolean titleEnabled = false;

    @ConfigEntry(path = "announcements.title.interval", comment = "Title interval (seconds) / 标题间隔(秒)")
    private int titleInterval = 600;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "announcements.title.fade-in", comment = "Title fade-in (ticks) / 标题淡入(tick)")
    private int titleFadeIn = 10;

    @Range(min = 1, max = 200)
    @ConfigEntry(path = "announcements.title.stay", comment = "Title stay (ticks) / 标题停留(tick)")
    private int titleStay = 70;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "announcements.title.fade-out", comment = "Title fade-out (ticks) / 标题淡出(tick)")
    private int titleFadeOut = 20;

    @ConfigEntry(path = "announcements.title.messages", comment = "Title messages (use || to separate title and subtitle) / 标题公告(用||分隔)")
    private List<String> titleMessages = new ArrayList<>(SHIPPED_TITLE_MESSAGES);

    public AnnouncementConfig() {
        super("config/announcements.yml");
    }

    /**
     * Replaces each announcement text that is still built-in text with its text in the server's
     * language (maintainer decision 2026-09-25, UltiKits/UltiChat#18). A list is compared whole.
     *
     * @param text the module jar's text for a catalogue key, in the language the framework loads
     * @return whether any value changed, so the caller saves the file once
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(AnnouncementConfig.class);
        String prefix = ConfigTextDefaults.materialize(AnnouncementConfig.class, "chatPrefix", chatPrefix,
                ConfigTextDefaults.currentText(text, "", "config_announcement_chat_prefix"),
                ConfigTextDefaults.tracked(jar, "", "config_announcement_chat_prefix", SHIPPED_CHAT_PREFIX));
        List<String> chat = lines(text, jar, "chatMessages", chatMessages,
                "config_announcement_chat_messages", SHIPPED_CHAT_MESSAGES);
        List<String> bossBar = lines(text, jar, "bossBarMessages", bossBarMessages,
                "config_announcement_bossbar_messages", SHIPPED_BOSSBAR_MESSAGES);
        List<String> title = lines(text, jar, "titleMessages", titleMessages,
                "config_announcement_title_messages", SHIPPED_TITLE_MESSAGES);
        boolean changed = prefix != chatPrefix || chat != chatMessages || bossBar != bossBarMessages
                || title != titleMessages;
        chatPrefix = prefix;
        chatMessages = chat;
        bossBarMessages = bossBar;
        titleMessages = title;
        return changed;
    }

    private static List<String> lines(Function<String, String> text, Map<String, Map<String, String>> jar,
                                      String field, List<String> value, String key, List<String> shipped) {
        return ConfigTextDefaults.materializeLines(AnnouncementConfig.class, field, value,
                ConfigTextDefaults.currentLines(text, key), ConfigTextDefaults.trackedLines(jar, key, shipped));
    }
}
