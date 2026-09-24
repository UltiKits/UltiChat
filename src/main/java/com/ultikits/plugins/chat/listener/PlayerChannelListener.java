package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Manages player channel assignments on join/quit, and evicts a quitting player's anti-spam
 * tracking.
 * 管理玩家加入/退出时的频道分配，并在玩家退出时清除其反刷屏追踪状态。
 */
@EventListener
public class PlayerChannelListener implements Listener {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelConfig channelConfig;

    @Autowired
    private AntiSpamService antiSpamService;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        channelService.setPlayerChannel(
                event.getPlayer().getUniqueId(),
                channelConfig.getDefaultChannel()
        );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        channelService.removePlayer(event.getPlayer().getUniqueId());
        // Without this the anti-spam maps keep an entry for every player who has chatted since the
        // server started (UltiKits/UltiChat#20). This handler, not JoinQuitListener's, because that
        // one returns early when custom quit messages are disabled.
        final UUID playerId = event.getPlayer().getUniqueId();
        antiSpamService.cleanup(playerId);
        // A chat message is recorded off the main thread and can land after the cleanup above. The
        // quitter is still online until this quit event has finished, so ChatListener's own
        // after-write check does not catch a record written in that interval; sweep once more on
        // the next tick, when the server has dropped the player. A player who is online again by
        // then keeps their entry.
        Plugin bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (bukkitPlugin != null) {
            Bukkit.getScheduler().runTask(bukkitPlugin, new Runnable() {
                @Override
                public void run() {
                    if (Bukkit.getPlayer(playerId) == null) {
                        antiSpamService.cleanup(playerId);
                    }
                }
            });
        }
    }
}
