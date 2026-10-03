package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Handles custom join/quit messages, welcome messages, titles, and first-join broadcasts.
 * 处理自定义进入/离开消息、欢迎消息、标题和首次加入广播。
 */
@EventListener
public class JoinQuitListener implements Listener {

    @Autowired
    private ChatConfig config;

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Custom join message
        if (config.isJoinMessageEnabled()) {
            String joinMsg = config.getJoinMessageFormat();
            joinMsg = parsePlaceholders(player, joinMsg);
            joinMsg = colorize(joinMsg);
            event.setJoinMessage(joinMsg);
        }

        // Welcome message lines
        if (config.isWelcomeEnabled()) {
            sendWelcomeMessage(player);
        }

        // Welcome title
        if (config.isTitleEnabled()) {
            sendWelcomeTitle(player);
        }

        // First join broadcast
        if (!player.hasPlayedBefore()) {
            String firstJoinMsg = config.getFirstJoinMessage();
            if (firstJoinMsg != null && !firstJoinMsg.isEmpty()) {
                firstJoinMsg = parsePlaceholders(player, firstJoinMsg);
                firstJoinMsg = colorize(firstJoinMsg);
                Bukkit.broadcastMessage(firstJoinMsg);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!config.isQuitMessageEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String quitMsg = config.getQuitMessageFormat();
        quitMsg = parsePlaceholders(player, quitMsg);
        quitMsg = colorize(quitMsg);
        event.setQuitMessage(quitMsg);
    }

    private void sendWelcomeMessage(Player player) {
        List<String> welcomeLines = config.getWelcomeLines();
        if (welcomeLines == null) {
            return;
        }
        for (String line : welcomeLines) {
            line = parsePlaceholders(player, line);
            line = colorize(line);
            player.sendMessage(line);
        }
    }

    private void sendWelcomeTitle(Player player) {
        String title = config.getTitleMain();
        String subtitle = config.getTitleSub();

        title = parsePlaceholders(player, title);
        subtitle = parsePlaceholders(player, subtitle);
        title = colorize(title);
        subtitle = colorize(subtitle);

        player.sendTitle(title, subtitle, 10, 70, 20);
    }

    /**
     * Stands in for {@code {displayname}} while PlaceholderAPI runs. It holds no '%', so no expansion
     * touches it, and no operator writes it.
     */
    private static final String DISPLAY_NAME_MARKER = "\u0000displayname\u0000";

    /**
     * Fills the module's own placeholders, then hands the text to PlaceholderAPI when it is installed
     * (UltiKits/UltiChat#42).
     * <p>
     * The module's own tokens ({@code %player_name%}, {@code {player}}, {@code {displayname}},
     * {@code %online_players%}, {@code %max_players%}) are replaced first and in one pass, because no
     * PlaceholderAPI expansion provides the last two: handing the text over first left them literal
     * in the shipped welcome line. The display name is a nickname the player may have chosen, so it
     * goes in as a marker while PlaceholderAPI runs and is put in as written afterwards: a
     * {@code %token%} inside it is shown as written, never expanded.
     */
    String parsePlaceholders(Player player, String text) {
        if (text == null) {
            return "";
        }

        String filled = UltiChat.fillOnce(text,
                "%player_name%", player.getName(),
                "{player}", player.getName(),
                "{displayname}", DISPLAY_NAME_MARKER,
                "%online_players%", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "%max_players%", String.valueOf(Bukkit.getMaxPlayers()));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            filled = PlaceholderAPI.setPlaceholders(player, filled);
        }
        return filled.replace(DISPLAY_NAME_MARKER, player.getDisplayName());
    }

    String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
