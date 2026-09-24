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
        injectByType(mock(AntiSpamService.class));
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
         * The quitter is still online for the rest of the quit event, so a chat record that lands
         * after the cleanup above but before the server drops the player is not caught by
         * {@code ChatListener}'s own after-write check. The handler therefore sweeps once more on
         * the next tick, when the player is gone (Codex review on PR #33).
         */
        @Test
        @DisplayName("A record landing after the quit cleanup is swept on the next tick once the player is gone")
        void lateRecordIsSweptOnTheNextTick() throws Exception {
            UUID quitter = UUID.randomUUID();
            org.bukkit.plugin.Plugin host = mock(org.bukkit.plugin.Plugin.class);
            when(org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools")).thenReturn(host);

            listener.onPlayerQuit(new PlayerQuitEvent(
                    ChatTestHelper.createMockPlayer("Quitter", quitter), "left"));
            // The async chat thread writes after the handler above has already cleaned up.
            antiSpam.recordMessage(quitter, "late");
            // Control: the late record really re-created the entries the sweep must remove.
            assertThat(map("lastMessageTime")).containsKey(quitter);
            assertThat(map("recentMessages")).containsKey(quitter);

            org.mockito.ArgumentCaptor<Runnable> sweep = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(org.bukkit.Bukkit.getScheduler()).runTask(eq(host), sweep.capture());
            sweep.getValue().run(); // Bukkit.getPlayer(quitter) is null: the player has left

            assertThat(map("lastMessageTime")).doesNotContainKey(quitter);
            assertThat(map("recentMessages")).doesNotContainKey(quitter);
        }

        @Test
        @DisplayName("The next-tick sweep leaves the entry of a player who is online again")
        void sweepSparesAPlayerWhoIsOnlineAgain() throws Exception {
            UUID quitter = UUID.randomUUID();
            org.bukkit.plugin.Plugin host = mock(org.bukkit.plugin.Plugin.class);
            when(org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools")).thenReturn(host);
            Player back = ChatTestHelper.createMockPlayer("Quitter", quitter);

            listener.onPlayerQuit(new PlayerQuitEvent(back, "left"));
            antiSpam.recordMessage(quitter, "after rejoining");
            doReturn(back).when(ChatTestHelper.getMockServer()).getPlayer(quitter);

            org.mockito.ArgumentCaptor<Runnable> sweep = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(org.bukkit.Bukkit.getScheduler()).runTask(eq(host), sweep.capture());
            sweep.getValue().run();

            assertThat(map("lastMessageTime")).containsKey(quitter);
            assertThat(map("recentMessages")).containsKey(quitter);
        }
    }
}
