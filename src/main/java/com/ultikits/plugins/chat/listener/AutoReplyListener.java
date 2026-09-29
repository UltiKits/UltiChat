package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.plugins.chat.service.AutoReplyService;
import com.ultikits.plugins.chat.service.ConnectionRegistry;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for automatic chat replies based on keyword/regex triggers.
 * <p>
 * Supports contains, exact, and regex match modes with per-rule
 * case sensitivity, permissions, cooldowns, multi-line responses,
 * and console command execution.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@EventListener
public class AutoReplyListener implements Listener {

    /**
     * Records the last time each player triggered an auto-reply for cooldown. An entry older than
     * the longest cooldown the setting accepts can no longer block anyone, so it is dropped when it
     * is read and whenever a reply is recorded; the table holds only players who triggered a reply
     * within that window (UltiKits/UltiChat#34). An entry past the current cooldown but inside that
     * window is kept, so raising the cooldown and reloading still counts it.
     */
    static final Map<UUID, Long> LAST_REPLY_TIME = new ConcurrentHashMap<>();

    private static final long KEEP_MS = AutoReplyConfig.MAX_COOLDOWN_SECONDS * 1000L;

    @Autowired
    private AutoReplyConfig config;

    @Autowired
    private AutoReplyService autoReplyService;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        // Captured at the start of this connection's own async processing, for the same reason and
        // by the same mechanism as ChatListener#handleAntiSpam's anti-spam state (UltiKits/UltiChat#40
        // review, maintainer decision 2026-09-29): LAST_REPLY_TIME is exactly the same shape of
        // per-UUID state written from an async path that the anti-spam maps are, so it shares the
        // same reconnect race unless it is swept the same way.
        long generation = connectionRegistry.currentGeneration(playerId);

        if (player.hasPermission("ultichat.autoreply.bypass")) {
            return;
        }

        if (isOnCooldown(playerId)) {
            return;
        }

        Map.Entry<String, Map<String, Object>> match = autoReplyService.findMatch(event.getMessage());
        if (match == null) {
            return;
        }

        Map<String, Object> rule = match.getValue();

        if (!hasRulePermission(player, rule)) {
            return;
        }

        sendResponse(player, rule);
        executeCommands(player, autoReplyService.getCommands(rule));

        // Skipped, not written, if this connection has since been superseded or has ended: a stale
        // reply's own cooldown timestamp must not apply to whatever connection now owns this UUID.
        if (connectionRegistry.isCurrent(playerId, generation)) {
            long now = System.currentTimeMillis();
            LAST_REPLY_TIME.values().removeIf(time -> now - time >= KEEP_MS);
            LAST_REPLY_TIME.put(playerId, now);
        }
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastTime = LAST_REPLY_TIME.get(playerId);
        if (lastTime == null) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastTime;
        if (elapsed >= KEEP_MS) {
            LAST_REPLY_TIME.remove(playerId, lastTime);
            return false;
        }
        long cooldownMs = config.getCooldown() * 1000L;
        return elapsed < cooldownMs;
    }

    private boolean hasRulePermission(Player player, Map<String, Object> rule) {
        Object permission = rule.get("permission");
        if (permission == null || permission.toString().isEmpty()) {
            return true;
        }
        return player.hasPermission(permission.toString());
    }

    private void sendResponse(Player player, Map<String, Object> rule) {
        Object response = autoReplyService.getResponse(rule);
        if (response instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) response;
            for (String line : lines) {
                player.sendMessage(formatMessage(line, player));
            }
        } else if (response != null) {
            player.sendMessage(formatMessage(response.toString(), player));
        }
    }

    private void executeCommands(Player player, List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        Plugin bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (bukkitPlugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(bukkitPlugin, new Runnable() {
            @Override
            public void run() {
                for (String cmd : commands) {
                    String formatted = cmd.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
                }
            }
        });
    }

    private String formatMessage(String message, Player player) {
        String formatted = message.replace("{player}", player.getName());
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }
}
