package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Manages player channel assignments on join/quit.
 * <p>
 * Does not touch anti-spam state (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29,
 * switching approach: the maintainer's earlier decisions on this same review both treated
 * connection lifecycle as something anti-spam tracking needed to react to, and both turned out
 * wrong in different ways -- clearing state on quit could be erased-then-recreated across a
 * reconnect race (UltiKits/UltiChat#20, #35), and later, the connection-scoped guards built to fix
 * that let a reconnect quietly bypass anti-spam by discarding history it should have kept. Anti-spam
 * exists precisely to combine what the SAME player sends before and after a reconnect, so quit is
 * not an event anti-spam tracking needs to know about at all: {@link
 * com.ultikits.plugins.chat.service.AntiSpamService} now expires its own entries purely by elapsed
 * time, with no join/quit dependency anywhere).
 * 管理玩家加入/退出时的频道分配。不再触碰反刷屏状态。
 */
@EventListener
public class PlayerChannelListener implements Listener {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelConfig channelConfig;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        channelService.setPlayerChannel(
                event.getPlayer().getUniqueId(),
                ChannelService.resolveDefaultChannel(channelConfig)
        );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        channelService.removePlayer(event.getPlayer().getUniqueId());
    }
}
