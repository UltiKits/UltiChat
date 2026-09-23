package com.ultikits.plugins.chat.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.Range;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * Announcement settings.
 * <p>
 * There is deliberately no interval key: each broadcast runs on a period fixed in its
 * {@code @Scheduled} annotation on {@code AnnouncementService} -- chat every 300 seconds, boss bar
 * every 60, title every 600. The three {@code announcements.*.interval} keys this class used to
 * declare were never read and were removed (UltiKits/UltiChat#13); making the periods configurable
 * is requested of the framework in UltiKits/UltiTools-Reborn#531.
 */
@Getter
@Setter
@ConfigEntity("config/announcements.yml")
public class AnnouncementConfig extends AbstractConfigEntity {

    // Chat announcements
    @ConfigEntry(path = "announcements.chat.enabled", comment = "Enable chat announcements / 启用聊天公告")
    private boolean chatEnabled = true;

    @NotEmpty
    @ConfigEntry(path = "announcements.chat.prefix", comment = "Chat announcement prefix / 聊天公告前缀")
    private String chatPrefix = "&6[Announcement] &f";

    @ConfigEntry(path = "announcements.chat.messages", comment = "Chat announcement messages / 聊天公告内容")
    private List<String> chatMessages = Arrays.asList(
            "Welcome! Type /help for assistance.",
            "Please follow server rules!"
    );

    // BossBar announcements
    @ConfigEntry(path = "announcements.bossbar.enabled", comment = "Enable boss bar announcements / 启用Boss栏公告")
    private boolean bossBarEnabled = false;

    @Range(min = 1, max = 60)
    @ConfigEntry(path = "announcements.bossbar.duration", comment = "Boss bar display duration (seconds) / Boss栏显示时长(秒)")
    private int bossBarDuration = 10;

    @ConfigEntry(path = "announcements.bossbar.color", comment = "Boss bar color / Boss栏颜色")
    private String bossBarColor = "BLUE";

    @ConfigEntry(path = "announcements.bossbar.messages", comment = "Boss bar messages / Boss栏公告内容")
    private List<String> bossBarMessages = Arrays.asList("&eWelcome to the server!");

    // Title announcements
    @ConfigEntry(path = "announcements.title.enabled", comment = "Enable title announcements / 启用标题公告")
    private boolean titleEnabled = false;

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
    private List<String> titleMessages = Arrays.asList("&6Welcome!||&7Enjoy your stay");

    public AnnouncementConfig() {
        super("config/announcements.yml");
    }
}
