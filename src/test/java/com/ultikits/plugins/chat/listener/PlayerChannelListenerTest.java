package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.service.ConnectionRegistry;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerChannelListener Tests")
class PlayerChannelListenerTest {

    private ChannelService channelService;
    private ChannelConfig channelConfig;
    private ConnectionRegistry connectionRegistry;
    private PlayerChannelListener listener;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();

        channelService = mock(ChannelService.class);
        channelConfig = mock(ChannelConfig.class);
        lenient().when(channelConfig.getDefaultChannel()).thenReturn("global");
        connectionRegistry = new ConnectionRegistry();

        listener = new PlayerChannelListener();
        ChatTestHelper.setField(listener, "channelService", channelService);
        ChatTestHelper.setField(listener, "channelConfig", channelConfig);
        injectByType(mock(AntiSpamService.class));
        injectByType(connectionRegistry);
    }

    /**
     * Sets every field of the listener whose type is the service's, as the container's by-type
     * {@code @Autowired} does, so no test depends on the field's name.
     */
    private void injectByType(AntiSpamService service) throws Exception {
        for (Field field : PlayerChannelListener.class.getDeclaredFields()) {
            if (field.getType() == AntiSpamService.class) {
                ChatTestHelper.setField(listener, field.getName(), service);
            }
        }
    }

    /**
     * Sets every field of the listener whose type is the registry's, as the container's by-type
     * {@code @Autowired} does, so no test depends on the field's name.
     */
    private void injectByType(ConnectionRegistry registry) throws Exception {
        for (Field field : PlayerChannelListener.class.getDeclaredFields()) {
            if (field.getType() == ConnectionRegistry.class) {
                ChatTestHelper.setField(listener, field.getName(), registry);
            }
        }
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

    // ==================== ConnectionRegistry wiring (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29) ====================

    @Nested
    @DisplayName("Join and quit maintain the player's ConnectionRegistry generation")
    class ConnectionRegistryWiring {

        @Test
        @DisplayName("Join assigns a generation that is current immediately afterward")
        void joinAssignsAGeneration() {
            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("TestPlayer", uuid);

            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));

            long generation = connectionRegistry.currentGeneration(uuid);
            assertThat(generation).isNotZero();
            assertThat(connectionRegistry.isCurrent(uuid, generation)).isTrue();
        }

        @Test
        @DisplayName("Quit removes the generation entirely, not merely leaves it un-bumped")
        void quitRemovesTheGeneration() {
            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("TestPlayer", uuid);
            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
            long generation = connectionRegistry.currentGeneration(uuid);

            listener.onPlayerQuit(new PlayerQuitEvent(player, "left"));

            assertThat(connectionRegistry.currentGeneration(uuid)).isZero();
            assertThat(connectionRegistry.isCurrent(uuid, generation)).isFalse();
        }

        @Test
        @DisplayName("A reconnect (join, quit, join again) assigns a strictly newer generation than the first")
        void reconnectAssignsANewerGeneration() {
            UUID uuid = UUID.randomUUID();
            Player player = ChatTestHelper.createMockPlayer("TestPlayer", uuid);

            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
            long first = connectionRegistry.currentGeneration(uuid);
            listener.onPlayerQuit(new PlayerQuitEvent(player, "left"));
            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
            long second = connectionRegistry.currentGeneration(uuid);

            assertThat(second).isGreaterThan(first);
            assertThat(connectionRegistry.isCurrent(uuid, first)).isFalse();
            assertThat(connectionRegistry.isCurrent(uuid, second)).isTrue();
        }
    }

    // ==================== Anti-spam eviction on quit (UltiKits/UltiChat#20) ====================

    /**
     * UltiKits/UltiChat#20. {@code AntiSpamService#cleanup} existed and was tested, but nothing
     * called it, so the per-player anti-spam maps kept an entry for every player who had ever chatted
     * since the server started. The quit handler here is the one that always runs -- it is registered
     * unconditionally and has no configuration switch, unlike {@code JoinQuitListener}'s, which
     * returns before doing anything when custom quit messages are disabled.
     * <p>
     * The service is a real one, injected by type the way the container does it, so these tests do
     * not depend on how the listener names its field.
     */
    @Nested
    @DisplayName("Quit evicts the player's anti-spam tracking (UltiKits/UltiChat#20)")
    class AntiSpamEviction {

        private AntiSpamService antiSpam;

        @BeforeEach
        void injectRealAntiSpamService() throws Exception {
            ChatConfig chatConfig = new ChatConfig();
            antiSpam = new AntiSpamService();
            ChatTestHelper.setField(antiSpam, "config", chatConfig);
            injectByType(antiSpam);
        }

        @SuppressWarnings("unchecked")
        private Map<UUID, ?> map(String name) throws Exception {
            return (Map<UUID, ?>) ChatTestHelper.getField(antiSpam, name);
        }

        @Test
        @DisplayName("After quit, neither anti-spam map holds the player; another player's entries stay")
        void quitEvictsOnlyTheQuitter() throws Exception {
            UUID quitter = UUID.randomUUID();
            UUID stayer = UUID.randomUUID();
            antiSpam.recordMessage(quitter, "hello");
            antiSpam.recordMessage(stayer, "hi");

            // Positive control: the state this test expects to disappear is really there first.
            assertThat(map("lastMessageTime")).containsKeys(quitter, stayer);
            assertThat(map("recentMessages")).containsKeys(quitter, stayer);

            listener.onPlayerQuit(new PlayerQuitEvent(
                    ChatTestHelper.createMockPlayer("Quitter", quitter), "left"));

            assertThat(map("lastMessageTime")).doesNotContainKey(quitter).containsKey(stayer);
            assertThat(map("recentMessages")).doesNotContainKey(quitter).containsKey(stayer);
            // The existing channel cleanup still happens alongside it.
            verify(channelService).removePlayer(quitter);
        }

        /**
         * A chat record that lands after the cleanup above removes itself, because its sender is
         * no longer connected ({@code ChatListenerTest$RecordAfterQuit}); the quit handler
         * therefore schedules nothing (UltiKits/UltiChat#35).
         */
        @Test
        @DisplayName("Quit schedules no follow-up sweep")
        void quitSchedulesNoSweep() throws Exception {
            UUID quitter = UUID.randomUUID();
            org.bukkit.plugin.Plugin host = mock(org.bukkit.plugin.Plugin.class);
            org.bukkit.plugin.PluginManager pluginManager = org.bukkit.Bukkit.getPluginManager();
            // lenient: once no sweep is scheduled, nothing looks the host up
            lenient().when(pluginManager.getPlugin("UltiTools")).thenReturn(host);
            antiSpam.recordMessage(quitter, "hello");

            listener.onPlayerQuit(new PlayerQuitEvent(
                    ChatTestHelper.createMockPlayer("Quitter", quitter), "left"));

            verify(org.bukkit.Bukkit.getScheduler(), never()).runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
            assertThat(map("lastMessageTime")).as("control: the cleanup itself still ran").doesNotContainKey(quitter);
        }
    }
}
