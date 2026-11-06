package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.service.EmojiService;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoMuteListenerTest {
    private ChatConfig config;
    private ChannelConfig channels;
    private AntiSpamService service;
    private ChannelService channelService;
    private EmojiService emojis;
    private ChatListener listener;
    private Player player;
    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();
        config = new ChatConfig();
        config.setAntiSpamCooldown(0);
        config.setChatFormatEnabled(false);
        config.setMentionsEnabled(false);
        ChatTestHelper.setField(config, "autoMuteEnabled", true);
        ChatTestHelper.setField(config, "autoMuteDuration", 5);
        channels = new ChannelConfig();
        channels.setEnabled(false);
        service = spy(new AntiSpamService());
        ChatTestHelper.setField(service, "config", config);
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        ChatTestHelper.setField(service, "plugin", plugin);
        ChatTestHelper.setField(service, "clock", (LongSupplier) now::get);
        service.refreshAutoMuteSettings();
        channelService = mock(ChannelService.class);
        emojis = mock(EmojiService.class);
        listener = new ChatListener(config, channels, service, channelService, emojis);
        player = ChatTestHelper.createMockPlayer("Offender", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    private AsyncPlayerChatEvent chat(Player sender, String text) {
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, sender, text, new HashSet<>());
        listener.onChat(event);
        return event;
    }

    private void mute() {
        assertThat(chat(player, "FIRST CAPS").isCancelled()).isTrue();
        assertThat(chat(player, "SECOND CAPS").isCancelled()).isTrue();
        assertThat(chat(player, "THIRD CAPS").isCancelled()).isTrue();
    }

    @Test
    void actualServiceCancelsThresholdAndNextNormalMessageBeforeAnyDownstreamWork() {
        mute();
        assertThat(chat(player, "ordinary acceptable text").isCancelled()).isTrue();
        verify(service, never()).recordMessage(any(), anyString());
        verifyNoInteractions(channelService, emojis);
        verify(player).sendMessage(contains("You have been muted for 5 seconds."));
        verify(player).sendMessage(contains("You are muted for another 5 seconds."));
    }

    @Test
    void permissionBypassesBothAccumulationAndActiveEnforcementWithoutErasingDeadline() {
        when(player.hasPermission("ultichat.spam.bypass")).thenReturn(true);
        for (int i = 0; i < 5; i++) {
            assertThat(chat(player, "BYPASS CAPS").isCancelled()).isFalse();
        }
        verify(service, never()).checkSpam(any(), anyString());
        when(player.hasPermission("ultichat.spam.bypass")).thenReturn(false);
        assertThat(chat(player, "FIRST CAPS").isCancelled()).isTrue();
        assertThat(chat(player, "SECOND CAPS").isCancelled()).isTrue();
        assertThat(chat(player, "acceptable before threshold").isCancelled()).isFalse();
        assertThat(chat(player, "THIRD CAPS").isCancelled()).isTrue();
        when(player.hasPermission("ultichat.spam.bypass")).thenReturn(true);
        assertThat(chat(player, "acceptable while bypassed").isCancelled()).isFalse();
        when(player.hasPermission("ultichat.spam.bypass")).thenReturn(false);
        assertThat(chat(player, "acceptable after revoking bypass").isCancelled()).isTrue();
        now.addAndGet(5_000);
        assertThat(chat(player, "acceptable after expiry").isCancelled()).isFalse();
    }

    @Test
    void disabledAntiSpamNeverCallsService() {
        config.setAntiSpamEnabled(false);
        assertThat(chat(player, "ALL CAPS").isCancelled()).isFalse();
        verify(service, never()).checkSpam(any(), anyString());
        verify(service, never()).recordMessage(any(), anyString());
    }

    @Test
    void cancelledEventIsIgnoredBothByRegistrationAndDirectInvocation() throws Exception {
        EventHandler handler = ChatListener.class.getMethod("onChat", AsyncPlayerChatEvent.class)
                .getAnnotation(EventHandler.class);
        assertThat(handler).isNotNull();
        assertThat(handler.ignoreCancelled()).isTrue();
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "ALL CAPS", new HashSet<>());
        event.setCancelled(true);
        listener.onChat(event);
        assertThat(event.isCancelled()).isTrue();
        verify(service, never()).checkSpam(any(), anyString());
        verifyNoInteractions(channelService, emojis);
    }

    @Test
    void realJoinQuitHandlersCannotLiftMuteWhenSameUuidUsesANewPlayerObject() throws Exception {
        mute();
        PlayerChannelListener connection = new PlayerChannelListener();
        ChatTestHelper.setField(connection, "channelConfig", channels);
        ChatTestHelper.setField(connection, "channelService", channelService);
        connection.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
        Player rejoined = ChatTestHelper.createMockPlayer("Offender", player.getUniqueId());
        connection.onPlayerJoin(new PlayerJoinEvent(rejoined, "joined"));
        verify(channelService).removePlayer(player.getUniqueId());
        assertThat(chat(rejoined, "acceptable after reconnect").isCancelled()).isTrue();
        now.addAndGet(5_000);
        assertThat(chat(rejoined, "acceptable at exact expiry").isCancelled()).isFalse();
    }
}
