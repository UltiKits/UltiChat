package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerChannelListener Tests")
class PlayerChannelListenerTest {

    private ChannelService channelService;
    private ChannelConfig channelConfig;
    private PlayerChannelListener listener;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();

        channelService = mock(ChannelService.class);
        channelConfig = mock(ChannelConfig.class);
        lenient().when(channelConfig.getDefaultChannel()).thenReturn("global");

        listener = new PlayerChannelListener();
        ChatTestHelper.setField(listener, "channelService", channelService);
        ChatTestHelper.setField(listener, "channelConfig", channelConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    // ==================== onPlayerJoin Tests ====================

    @Nested
    @DisplayName("onPlayerJoin Tests")
    class OnPlayerJoinTests {

        @Test
        @DisplayName("Should set default channel for joining player")
        void shouldSetDefaultChannel() {
            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("TestPlayer", uuid);
            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            verify(channelService).setPlayerChannel(uuid, "global");
        }

        @Test
        @DisplayName("Should use configured default channel")
        void shouldUseConfiguredDefault() {
            when(channelConfig.getDefaultChannel()).thenReturn("local");

            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("Player2", uuid);
            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            verify(channelService).setPlayerChannel(uuid, "local");
        }

        @Test
        @DisplayName("Should handle multiple players joining")
        void shouldHandleMultiplePlayers() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = ChatTestHelper.createMockPlayer("Player1", uuid1);
            Player player2 = ChatTestHelper.createMockPlayer("Player2", uuid2);

            listener.onPlayerJoin(new PlayerJoinEvent(player1, "joined"));
            listener.onPlayerJoin(new PlayerJoinEvent(player2, "joined"));

            verify(channelService).setPlayerChannel(uuid1, "global");
            verify(channelService).setPlayerChannel(uuid2, "global");
        }
    }

    // ==================== onPlayerQuit Tests ====================

    @Nested
    @DisplayName("onPlayerQuit Tests")
    class OnPlayerQuitTests {

        @Test
        @DisplayName("Should remove player from channel service on quit")
        void shouldRemovePlayerOnQuit() {
            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("TestPlayer", uuid);
            PlayerQuitEvent event = new PlayerQuitEvent(player, "left");

            listener.onPlayerQuit(event);

            verify(channelService).removePlayer(uuid);
        }

        @Test
        @DisplayName("Should handle multiple player quits")
        void shouldHandleMultipleQuits() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = ChatTestHelper.createMockPlayer("Player1", uuid1);
            Player player2 = ChatTestHelper.createMockPlayer("Player2", uuid2);

            listener.onPlayerQuit(new PlayerQuitEvent(player1, "left"));
            listener.onPlayerQuit(new PlayerQuitEvent(player2, "left"));

            verify(channelService).removePlayer(uuid1);
            verify(channelService).removePlayer(uuid2);
        }
    }

    // ==================== Join + Quit Lifecycle Tests ====================

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should set channel on join and remove on quit")
        void shouldSetAndRemove() {
            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("TestPlayer", uuid);

            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
            verify(channelService).setPlayerChannel(uuid, "global");

            listener.onPlayerQuit(new PlayerQuitEvent(player, "left"));
            verify(channelService).removePlayer(uuid);
        }
    }

    // ==================== Anti-spam state is untouched by quit (UltiKits/UltiChat#20, #35, #40 review, maintainer decision 2026-09-29) ====================

    /**
     * UltiKits/UltiChat#20 added {@code AntiSpamService#cleanup} and called it here, on quit, to stop
     * the per-player anti-spam maps from keeping an entry for every player who had ever chatted since
     * the server started. Two further rounds of this same review (#35, then a per-connection
     * generation number) each tried to make that cleanup safe against a reconnect race, and each one
     * failed differently -- clearing state on quit could itself be erased-then-recreated across a
     * race, and the connection-scoped guards built to close that raced against each other in turn.
     * <p>
     * The maintainer's final decision (2026-09-29) is that quit should not touch anti-spam state at
     * all: anti-spam exists to combine what the SAME player sends before and after a reconnect, so
     * clearing it on quit was always the wrong idea, not merely unsafe in its details.
     * {@link AntiSpamService} no longer has a {@code cleanup} method, and {@link PlayerChannelListener}
     * no longer holds a reference to it -- this class only tests that quit still does what it is
     * still responsible for (the channel assignment), and, as a class-level control, that
     * {@link AntiSpamService} has no method left for a quit handler to even call.
     */
    @Nested
    @DisplayName("Quit does not touch anti-spam state (UltiKits/UltiChat#20, #35, #40 review, maintainer decision 2026-09-29)")
    class AntiSpamNotTouchedOnQuit {

        @Test
        @DisplayName("Quit still removes the player's channel assignment (unaffected by this decision)")
        void quitStillRemovesChannelAssignment() {
            UUID quitter = UUID.randomUUID();

            listener.onPlayerQuit(new PlayerQuitEvent(
                    ChatTestHelper.createMockPlayer("Quitter", quitter), "left"));

            verify(channelService).removePlayer(quitter);
        }

        @Test
        @DisplayName("Recorded anti-spam state for a player survives that player's own quit")
        void antiSpamStateSurvivesQuit() throws Exception {
            ChatConfig chatConfig = new ChatConfig();
            AntiSpamService antiSpam = new AntiSpamService();
            ChatTestHelper.setField(antiSpam, "config", chatConfig);
            UUID quitter = UUID.randomUUID();
            antiSpam.recordMessage(quitter, "hello");

            // PlayerChannelListener no longer holds any reference to AntiSpamService at all -- there
            // is nothing to inject and nothing this quit event could call even if it wanted to.
            listener.onPlayerQuit(new PlayerQuitEvent(
                    ChatTestHelper.createMockPlayer("Quitter", quitter), "left"));

            @SuppressWarnings("unchecked")
            Map<UUID, ?> lastMessageTime = (Map<UUID, ?>) ChatTestHelper.getField(antiSpam, "lastMessageTime");
            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(antiSpam, "recentMessages");
            assertThat(lastMessageTime).as("untouched by a quit event this service never saw").containsKey(quitter);
            assertThat(recentMessages).containsKey(quitter);
        }

        @Test
        @DisplayName("PlayerChannelListener no longer declares any field of AntiSpamService's type")
        void listenerHoldsNoAntiSpamReference() {
            for (java.lang.reflect.Field field : PlayerChannelListener.class.getDeclaredFields()) {
                assertThat(field.getType())
                        .as("a leftover field would mean the class still depends on AntiSpamService "
                                + "for something, contradicting this decision")
                        .isNotEqualTo(AntiSpamService.class);
            }
        }

        @Test
        @DisplayName("AntiSpamService declares no cleanup-shaped method for a quit handler to call")
        void serviceHasNoCleanupMethod() {
            for (java.lang.reflect.Method method : AntiSpamService.class.getDeclaredMethods()) {
                assertThat(method.getName())
                        .as("a leftover cleanup method would be dead code once nothing calls it")
                        .isNotEqualTo("cleanup");
            }
        }
    }
}
