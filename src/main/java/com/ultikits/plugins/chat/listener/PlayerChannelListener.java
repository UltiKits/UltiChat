package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.service.ConnectionRegistry;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Manages player channel assignments on join/quit, evicts a quitting player's anti-spam tracking,
 * and maintains this player's {@link ConnectionRegistry} generation.
 * 管理玩家加入/退出时的频道分配，在玩家退出时清除其反刷屏追踪状态，并维护该玩家在
 * {@link ConnectionRegistry} 中的连接编号。
 */
@EventListener
public class PlayerChannelListener implements Listener {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelConfig channelConfig;

    @Autowired
    private AntiSpamService antiSpamService;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Assigned first: every other join-time write below is main-thread and does not race, but
        // an in-flight async chat task for this UUID's PREVIOUS connection must see a new generation
        // as soon as this join is observable at all (UltiKits/UltiChat#40 review, maintainer decision
        // 2026-09-29).
        connectionRegistry.onJoin(event.getPlayer().getUniqueId());
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
        antiSpamService.cleanup(event.getPlayer().getUniqueId());
        // Removed, not merely left un-bumped: a chat task belonging to this connection must find no
        // current generation at all once this fires, exactly like a task for a UUID that never
        // joined. Reused by ChatListener#handleAntiSpam and AutoReplyListener#onPlayerChat, both of
        // which capture this player's generation at the start of their own async processing and
        // re-check it before writing or cleaning up any per-UUID state, so neither can act on behalf
        // of a connection this quit has already ended (UltiKits/UltiChat#40 review, maintainer
        // decision 2026-09-29).
        connectionRegistry.onQuit(event.getPlayer().getUniqueId());
    }
}
