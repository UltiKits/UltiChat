package com.ultikits.plugins.chat.listener;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.service.ConnectionRegistry;
import com.ultikits.plugins.chat.service.EmojiService;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ChatListener — anti-spam, emoji, channel filtering, chat format, and mentions.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ChatListener Tests")
class ChatListenerTest {

    private ChatListener listener;
    private ChatConfig chatConfig;
    private ChannelConfig channelConfig;
    private AntiSpamService antiSpamService;
    private ChannelService channelService;
    private EmojiService emojiService;
    private ConnectionRegistry connectionRegistry;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();

        chatConfig = new ChatConfig();
        channelConfig = new ChannelConfig();
        antiSpamService = mock(AntiSpamService.class);
        channelService = mock(ChannelService.class);
        emojiService = mock(EmojiService.class);
        connectionRegistry = mock(ConnectionRegistry.class);
        // Default: the normal single-connection case -- whatever generation is captured is still
        // current by the time a write/cleanup checks it. Tests for the reconnect race override this.
        lenient().when(connectionRegistry.isCurrent(any(UUID.class), anyLong())).thenReturn(true);

        listener = new ChatListener(
                chatConfig, channelConfig,
                antiSpamService, channelService, emojiService, connectionRegistry
        );

        playerUuid = UUID.randomUUID();
        player = ChatTestHelper.createMockPlayer("TestPlayer", playerUuid);

        // Default: emojiService returns message unchanged
        when(emojiService.replaceEmojis(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    private AsyncPlayerChatEvent createChatEvent(String message) {
        Set<Player> recipients = new HashSet<>();
        recipients.add(player);
        return new AsyncPlayerChatEvent(false, player, message, recipients);
    }

    private AsyncPlayerChatEvent createChatEventWithRecipients(String message, Set<Player> recipients) {
        return new AsyncPlayerChatEvent(false, player, message, recipients);
    }

    /** As {@link #createChatEvent}, but for a specific player object rather than the outer {@code player}. */
    private AsyncPlayerChatEvent createChatEventFor(Player sender, String message) {
        Set<Player> recipients = new HashSet<>();
        recipients.add(sender);
        return new AsyncPlayerChatEvent(false, sender, message, recipients);
    }

    // ==================== Anti-Spam Tests ====================

    @Nested
    @DisplayName("Anti-Spam")
    class AntiSpamTests {

        @Test
        @DisplayName("Should cancel event when spam detected")
        void shouldCancelWhenSpamDetected() {
            chatConfig.setAntiSpamEnabled(true);
            when(antiSpamService.checkSpam(player, "spam message")).thenReturn("spam reason");

            AsyncPlayerChatEvent event = createChatEvent("spam message");
            listener.onChat(event);

            assertThat(event.isCancelled()).isTrue();
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("spam reason");
        }

        @Test
        @DisplayName("Should send reason to player when spam detected")
        void shouldSendReasonWhenSpam() {
            chatConfig.setAntiSpamEnabled(true);
            when(antiSpamService.checkSpam(player, "fast")).thenReturn("Too fast!");

            AsyncPlayerChatEvent event = createChatEvent("fast");
            listener.onChat(event);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("Too fast!");
        }

        @Test
        @DisplayName("The refusal's own '&' colour code is shown as colour, not as two characters (UltiKits/UltiChat#18)")
        void refusalColourCodeIsTranslated() {
            chatConfig.setAntiSpamEnabled(true);
            when(antiSpamService.checkSpam(player, "fast")).thenReturn("&cPlease wait before sending another message.");

            AsyncPlayerChatEvent event = createChatEvent("fast");
            listener.onChat(event);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).endsWith("\u00a7cPlease wait before sending another message.")
                    .doesNotContain("&c");
        }

        @Test
        @DisplayName("Should record message when not spam")
        void shouldRecordMessageWhenNotSpam() {
            chatConfig.setAntiSpamEnabled(true);
            when(antiSpamService.checkSpam(player, "hello")).thenReturn(null);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            listener.onChat(event);

            verify(antiSpamService).recordMessage(playerUuid, "hello");
            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should bypass anti-spam with permission")
        void shouldBypassWithPermission() {
            chatConfig.setAntiSpamEnabled(true);
            when(player.hasPermission("ultichat.spam.bypass")).thenReturn(true);

            AsyncPlayerChatEvent event = createChatEvent("message");
            listener.onChat(event);

            verify(antiSpamService, never()).checkSpam(any(), anyString());
            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should skip anti-spam when disabled")
        void shouldSkipWhenDisabled() {
            chatConfig.setAntiSpamEnabled(false);

            AsyncPlayerChatEvent event = createChatEvent("message");
            listener.onChat(event);

            verify(antiSpamService, never()).checkSpam(any(), anyString());
            verify(antiSpamService, never()).recordMessage(any(), anyString());
        }

        @Test
        @DisplayName("Should not record message when spam cancelled")
        void shouldNotRecordWhenCancelled() {
            chatConfig.setAntiSpamEnabled(true);
            when(antiSpamService.checkSpam(player, "spam")).thenReturn("blocked");

            AsyncPlayerChatEvent event = createChatEvent("spam");
            listener.onChat(event);

            verify(antiSpamService, never()).recordMessage(any(), anyString());
        }
    }

    // ==================== Emoji Tests ====================

    @Nested
    @DisplayName("Emoji Replacement")
    class EmojiTests {

        @Test
        @DisplayName("Should replace emojis when player has permission")
        void shouldReplaceEmojisWithPermission() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);
            when(player.hasPermission("ultichat.emoji")).thenReturn(true);
            when(emojiService.replaceEmojis(":heart:")).thenReturn("\u2764");

            AsyncPlayerChatEvent event = createChatEvent(":heart:");
            listener.onChat(event);

            assertThat(event.getMessage()).isEqualTo("\u2764");
        }

        @Test
        @DisplayName("Should not replace emojis without permission")
        void shouldNotReplaceWithoutPermission() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);
            when(player.hasPermission("ultichat.emoji")).thenReturn(false);

            AsyncPlayerChatEvent event = createChatEvent(":heart:");
            listener.onChat(event);

            verify(emojiService, never()).replaceEmojis(anyString());
            assertThat(event.getMessage()).isEqualTo(":heart:");
        }
    }

    // ==================== Channel Tests ====================

    @Nested
    @DisplayName("Channel Filtering")
    class ChannelTests {

        @Test
        @DisplayName("Should filter recipients by channel")
        void shouldFilterRecipientsByChannel() {
            channelConfig.setEnabled(true);
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);

            Player other = ChatTestHelper.createMockPlayer("Other", UUID.randomUUID());
            Set<Player> original = new HashSet<>(Arrays.asList(player, other));

            Set<Player> filtered = new HashSet<>();
            filtered.add(player);
            when(channelService.filterRecipients(eq(player), any())).thenReturn(filtered);

            AsyncPlayerChatEvent event = createChatEventWithRecipients("hello", original);
            listener.onChat(event);

            assertThat(event.getRecipients()).containsExactly(player);
        }

        @Test
        @DisplayName("Should not filter when channels disabled")
        void shouldNotFilterWhenDisabled() {
            channelConfig.setEnabled(false);
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);

            Player other = ChatTestHelper.createMockPlayer("Other", UUID.randomUUID());
            Set<Player> original = new HashSet<>(Arrays.asList(player, other));

            AsyncPlayerChatEvent event = createChatEventWithRecipients("hello", original);
            listener.onChat(event);

            verify(channelService, never()).filterRecipients(any(), any());
            assertThat(event.getRecipients()).hasSize(2);
        }
    }

    // ==================== Chat Format Tests ====================

    @Nested
    @DisplayName("Chat Format")
    class ChatFormatTests {

        @Test
        @DisplayName("Should apply chat format with player name placeholder")
        void shouldApplyFormatWithPlayerName() {
            chatConfig.setChatFormatEnabled(true);
            chatConfig.setChatFormat("{player}: {message}");
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            listener.onChat(event);

            assertThat(event.getFormat()).contains("%1$s");
            assertThat(event.getFormat()).contains("%2$s");
        }

        @Test
        @DisplayName("Should prepend channel display name when channels enabled")
        void shouldPrependChannelDisplayName() {
            chatConfig.setChatFormatEnabled(true);
            chatConfig.setChatFormat("{player}: {message}");
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(true);

            when(channelService.getPlayerChannel(playerUuid)).thenReturn("global");
            when(channelService.getChannelDisplayName("global")).thenReturn("[Global]");

            AsyncPlayerChatEvent event = createChatEvent("hello");
            listener.onChat(event);

            assertThat(event.getFormat()).startsWith("[Global] ");
        }

        @Test
        @DisplayName("Should translate color codes in format")
        void shouldTranslateColorCodesInFormat() {
            chatConfig.setChatFormatEnabled(true);
            chatConfig.setChatFormat("&a{player}&7: {message}");
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            listener.onChat(event);

            assertThat(event.getFormat()).contains("\u00a7a");
            assertThat(event.getFormat()).contains("\u00a77");
        }

        @Test
        @DisplayName("Should replace displayname placeholder")
        void shouldReplaceDisplayName() {
            chatConfig.setChatFormatEnabled(true);
            chatConfig.setChatFormat("{displayname} says: {message}");
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);

            when(player.getDisplayName()).thenReturn("FancyPlayer");

            AsyncPlayerChatEvent event = createChatEvent("hello");
            listener.onChat(event);

            assertThat(event.getFormat()).contains("FancyPlayer");
        }

        @Test
        @DisplayName("Should not apply format when disabled")
        void shouldNotApplyFormatWhenDisabled() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            String originalFormat = event.getFormat();
            listener.onChat(event);

            assertThat(event.getFormat()).isEqualTo(originalFormat);
        }

        @Test
        @DisplayName("Should translate color codes in message with permission")
        void shouldTranslateColorInMessage() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);
            when(player.hasPermission("ultichat.color")).thenReturn(true);

            AsyncPlayerChatEvent event = createChatEvent("&ahello");
            listener.onChat(event);

            assertThat(event.getMessage()).contains("\u00a7a");
        }

        @Test
        @DisplayName("Should not translate color codes without permission")
        void shouldNotTranslateColorWithoutPermission() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);
            when(player.hasPermission("ultichat.color")).thenReturn(false);

            AsyncPlayerChatEvent event = createChatEvent("&ahello");
            listener.onChat(event);

            assertThat(event.getMessage()).isEqualTo("&ahello");
        }
    }

    // ==================== Mention Tests ====================

    @Nested
    @DisplayName("Mentions")
    class MentionTests {

        @Test
        @DisplayName("Should highlight mentioned player name")
        void shouldHighlightMention() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(true);
            chatConfig.setMentionFormat("&e@{player}&r");
            chatConfig.setMentionSound("ENTITY_EXPERIENCE_ORB_PICKUP");
            chatConfig.setSelfMention(false);
            channelConfig.setEnabled(false);

            Player mentioned = ChatTestHelper.createMockPlayer("Alice", UUID.randomUUID());

            // Mock Bukkit.getOnlinePlayers()
            List<Player> onlinePlayers = Arrays.asList(player, mentioned);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("Hello @Alice!");
            listener.onChat(event);

            // The message should contain the formatted mention
            assertThat(event.getMessage()).contains("@Alice");
            assertThat(event.getMessage()).contains("\u00a7e");
        }

        @Test
        @DisplayName("Should play sound to mentioned player")
        void shouldPlaySoundToMentioned() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(true);
            chatConfig.setMentionFormat("&e@{player}&r");
            chatConfig.setMentionSound("ENTITY_EXPERIENCE_ORB_PICKUP");
            chatConfig.setSelfMention(false);
            channelConfig.setEnabled(false);

            Player mentioned = ChatTestHelper.createMockPlayer("Alice", UUID.randomUUID());

            List<Player> onlinePlayers = Arrays.asList(player, mentioned);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("Hey @Alice");
            listener.onChat(event);

            verify(mentioned).playSound(any(org.bukkit.Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());
        }

        @Test
        @DisplayName("Should block self-mention when disabled")
        void shouldBlockSelfMention() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(true);
            chatConfig.setMentionFormat("&e@{player}&r");
            chatConfig.setSelfMention(false);
            channelConfig.setEnabled(false);

            List<Player> onlinePlayers = Collections.singletonList(player);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("@TestPlayer hello");
            listener.onChat(event);

            // Self-mention should NOT be formatted
            assertThat(event.getMessage()).isEqualTo("@TestPlayer hello");
        }

        @Test
        @DisplayName("Should allow self-mention when enabled")
        void shouldAllowSelfMention() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(true);
            chatConfig.setMentionFormat("&e@{player}&r");
            chatConfig.setMentionSound("ENTITY_EXPERIENCE_ORB_PICKUP");
            chatConfig.setSelfMention(true);
            channelConfig.setEnabled(false);

            List<Player> onlinePlayers = Collections.singletonList(player);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("@TestPlayer hi");
            listener.onChat(event);

            assertThat(event.getMessage()).contains("\u00a7e");
        }

        @Test
        @DisplayName("Should not process mentions when disabled")
        void shouldNotProcessMentionsWhenDisabled() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);

            Player mentioned = ChatTestHelper.createMockPlayer("Alice", UUID.randomUUID());

            List<Player> onlinePlayers = Arrays.asList(player, mentioned);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("@Alice hi");
            listener.onChat(event);

            // Message should remain unchanged
            assertThat(event.getMessage()).isEqualTo("@Alice hi");
            verify(mentioned, never()).playSound(any(org.bukkit.Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());
        }

        @Test
        @DisplayName("A mention sound that is not one plays the default sound, which the load warning names")
        void shouldHandleInvalidSound() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(true);
            chatConfig.setMentionFormat("&e@{player}&r");
            chatConfig.setMentionSound("INVALID_SOUND_NAME");
            chatConfig.setSelfMention(false);
            channelConfig.setEnabled(false);

            Player mentioned = ChatTestHelper.createMockPlayer("Alice", UUID.randomUUID());

            List<Player> onlinePlayers = Arrays.asList(player, mentioned);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("@Alice hi");
            listener.onChat(event);

            verify(mentioned).playSound(any(org.bukkit.Location.class), eq(com.cryptomorin.xseries.XSound.ENTITY_EXPERIENCE_ORB_PICKUP.get()), anyFloat(), anyFloat());
        }

        @Test
        @DisplayName("Should not highlight when no matching player online")
        void shouldNotHighlightNoMatch() {
            chatConfig.setChatFormatEnabled(false);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(true);
            chatConfig.setMentionFormat("&e@{player}&r");
            channelConfig.setEnabled(false);

            // Only current player online, no "Bob"
            List<Player> onlinePlayers = Collections.singletonList(player);
            doReturn(onlinePlayers).when(ChatTestHelper.getMockServer()).getOnlinePlayers();

            AsyncPlayerChatEvent event = createChatEvent("@Bob hi");
            listener.onChat(event);

            assertThat(event.getMessage()).isEqualTo("@Bob hi");
        }
    }

    // ==================== Format String Escaping ====================

    @Nested
    @DisplayName("Format String Escaping")
    class FormatEscapingTests {

        @Test
        @DisplayName("Should preserve %1$s and %2$s format specifiers")
        void shouldPreserveFormatSpecifiers() {
            String result = ChatListener.escapeFormatString("%1$s: %2$s");
            assertThat(result).isEqualTo("%1$s: %2$s");
        }

        @Test
        @DisplayName("Should escape stray percent characters")
        void shouldEscapeStrayPercent() {
            String result = ChatListener.escapeFormatString("100% done %1$s");
            assertThat(result).isEqualTo("100%% done %1$s");
        }

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNull() {
            assertThat(ChatListener.escapeFormatString(null)).isNull();
        }

        @Test
        @DisplayName("Should handle string without percent")
        void shouldHandleNoPercent() {
            String result = ChatListener.escapeFormatString("hello world");
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("Should handle percent at end of string")
        void shouldHandlePercentAtEnd() {
            String result = ChatListener.escapeFormatString("test%");
            assertThat(result).isEqualTo("test%%");
        }
    }

    // ==================== Per-channel format (UltiKits/UltiChat#16) ====================

    /**
     * UltiKits/UltiChat#16. A channel's {@code format:} was declared and never applied. It is now
     * wired, with the maintainer's rule for upgraded servers: a format byte-for-byte equal to one of
     * the three strings earlier versions shipped (and wrote into every server's {@code channels.yml})
     * is removed from the file when the module starts, so those servers keep today's line
     * (UltiKits/UltiChat#18, pinned in {@code ChatConfigTextTest}); any format still in the file
     * applies, with {@code {display}} standing for the channel's display name.
     * <p>
     * These tests use a real {@link ChannelService} and {@link ChannelConfig}, so the substitution is
     * exercised together with the listener. Every sender is in {@code global}, whose shipped display
     * name is {@code &f[Global]}.
     */
    @Nested
    @DisplayName("Per-channel format (UltiKits/UltiChat#16)")
    class PerChannelFormatTests {

        /** Today's line for the global channel under the shipped chat.format, PlaceholderAPI absent. */
        private static final String TODAYS_GLOBAL_LINE =
                "§f[Global] §7[§f%%player_world%%§7] §f%1$s§7: §f%2$s";

        private ChatListener realListener;
        private ChannelConfig realChannelConfig;

        @BeforeEach
        void useRealChannelService() throws Exception {
            chatConfig.setChatFormatEnabled(true);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            realChannelConfig = new ChannelConfig();
            realChannelConfig.setEnabled(true);
            ChannelService realChannelService = new ChannelService();
            ChatTestHelper.setField(realChannelService, "config", realChannelConfig);
            realListener = new ChatListener(chatConfig, realChannelConfig,
                    antiSpamService, realChannelService, emojiService, connectionRegistry);
        }

        /** Sets (or, for null, removes) the global channel's format and returns the chat line. */
        private String lineWithGlobalFormat(String format) {
            Map<String, Map<String, Object>> channels = new HashMap<String, Map<String, Object>>();
            Map<String, Object> global = new HashMap<String, Object>();
            global.put("display-name", "&f[Global]");
            if (format != null) {
                global.put("format", format);
            }
            global.put("permission", "");
            global.put("range", -1);
            global.put("cross-world", true);
            channels.put("global", global);
            realChannelConfig.setChannels(channels);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            realListener.onChat(event);
            return event.getFormat();
        }

        @Test
        @DisplayName("Unset: today's line, byte for byte")
        void unsetIsTodaysLine() {
            assertThat(lineWithGlobalFormat(null)).isEqualTo(TODAYS_GLOBAL_LINE);
        }

        @Test
        @DisplayName("A format still in the configuration applies as written, a formerly shipped one included (the start removes those from the file)")
        void formatInTheConfigurationApplies() {
            assertThat(lineWithGlobalFormat("{display}&f: {message}")).isEqualTo("§f[Global]§f: %2$s");
            assertThat(lineWithGlobalFormat("&c[Staff] &f{player}&7: {message}")).isEqualTo("§c[Staff] §f%1$s§7: %2$s");
        }

        @Test
        @DisplayName("A one-character change to a shipped string is a custom format and applies")
        void oneCharacterVariantApplies() {
            // "&f" -> "&e": not one of the shipped strings any more.
            assertThat(lineWithGlobalFormat("{display}&e: {message}"))
                    .isEqualTo("§f[Global]§e: %2$s");
            // A trailing space is also a change.
            assertThat(lineWithGlobalFormat("&c[Staff] &f{player}&7: {message} "))
                    .isEqualTo("§c[Staff] §f%1$s§7: %2$s ");
        }

        @Test
        @DisplayName("{display} is the channel's display name, alongside the other placeholders")
        void displayIsTheChannelDisplayName() {
            when(player.getDisplayName()).thenReturn("Fancy");

            assertThat(lineWithGlobalFormat("{display} {player} ({displayname}) > {message}"))
                    .isEqualTo("§f[Global] %1$s (Fancy) > %2$s");
        }

        @Test
        @DisplayName("With channels disabled, a channel's format is not used: the global format alone, as today")
        void channelsDisabledIgnoresChannelFormat() {
            realChannelConfig.setEnabled(false);

            assertThat(lineWithGlobalFormat("{display} custom {message}"))
                    .isEqualTo("§7[§f%%player_world%%§7] §f%1$s§7: §f%2$s");
        }

        @Test
        @DisplayName("With chat.format-enabled false, a channel's format is not used either: no line format is set at all")
        void formattingDisabledIgnoresChannelFormat() {
            chatConfig.setChatFormatEnabled(false);
            AsyncPlayerChatEvent untouched = createChatEvent("hello");

            assertThat(lineWithGlobalFormat("{display} custom {message}")).isEqualTo(untouched.getFormat());
        }
    }

    /**
     * {@code onChat} runs off the main thread, so state it writes or cleans up can land out of
     * order relative to a reconnect. Two per-object/per-UUID checks were tried here in turn and each
     * eventually let a stale, superseded connection act on a newer one's behalf:
     * {@code Player#isConnected()} (UltiKits/UltiChat#35) only knows whether THIS specific connection
     * has ended; {@code Bukkit.getPlayer(UUID) == null} (the #40 review's own narrowing) only knows
     * whether SOMEBODY is online under this UUID, not whether it is still THIS connection. A
     * {@link ConnectionRegistry} generation, captured at the start of this connection's own async
     * processing and re-checked before every state-changing step, answers the right question
     * directly and closes the whole class at once (UltiKits/UltiChat#40 review, maintainer decision
     * 2026-09-29): a stale write can no longer land at all (closing UltiKits/UltiChat#20's failure
     * mode by construction, not by a reactive cleanup), and a stale cleanup can no longer erase a
     * newer connection's own state (UltiKits/UltiChat#47 review of PR #40, narrowing
     * UltiKits/UltiChat#35's "per session, not per UUID" rule to what it actually has to mean).
     */
    @Nested
    @DisplayName("Anti-spam state is generation-gated against a reconnect race (UltiKits/UltiChat#20, #35, #40 review)")
    class RecordAfterQuit {

        private AntiSpamService realAntiSpam;
        private ConnectionRegistry realConnectionRegistry;
        private ChatListener realListener;

        @BeforeEach
        void useRealServices() throws Exception {
            chatConfig.setAntiSpamEnabled(true);
            realAntiSpam = new AntiSpamService();
            ChatTestHelper.setField(realAntiSpam, "config", chatConfig);
            ChatTestHelper.setField(realAntiSpam, "plugin", ChatTestHelper.getMockPlugin());
            realConnectionRegistry = new ConnectionRegistry();
            realListener = new ChatListener(chatConfig, channelConfig,
                    realAntiSpam, channelService, emojiService, realConnectionRegistry);
        }

        @SuppressWarnings("unchecked")
        private Map<UUID, ?> map(String name) throws Exception {
            return (Map<UUID, ?>) ChatTestHelper.getField(realAntiSpam, name);
        }

        /** A listener wired to a registry that reports a fixed, stubbed generation state, simulating
         * a connection whose own async task is still running with whatever the registry reported at
         * some earlier moment, regardless of what has changed since. */
        private ChatListener listenerWithStubbedRegistry(ConnectionRegistry stubbed) {
            return new ChatListener(chatConfig, channelConfig,
                    realAntiSpam, channelService, emojiService, stubbed);
        }

        @Test
        @DisplayName("Normal single connection: the message is recorded (control)")
        void normalSingleConnectionIsRecorded() throws Exception {
            realConnectionRegistry.onJoin(playerUuid);
            when(ChatTestHelper.getMockServer().getPlayer(playerUuid)).thenReturn(player);

            realListener.onChat(createChatEvent("hello"));

            assertThat(map("lastMessageTime")).containsKey(playerUuid);
            assertThat(map("recentMessages")).containsKey(playerUuid);
        }

        @Test
        @DisplayName("Normal single connection, sender gone by the time the record would be written: neither map holds them afterwards")
        void disconnectedSenderLeavesNoEntry() throws Exception {
            realConnectionRegistry.onJoin(playerUuid);
            when(ChatTestHelper.getMockServer().getPlayer(playerUuid)).thenReturn(null);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            realListener.onChat(event);

            assertThat(event.isCancelled()).as("the message itself is not refused").isFalse();
            assertThat(map("lastMessageTime")).doesNotContainKey(playerUuid);
            assertThat(map("recentMessages")).doesNotContainKey(playerUuid);
        }

        @Test
        @DisplayName("An old connection's write lands after a newer connection has taken over: skipped, the new connection's own record survives untouched")
        void oldConnectionsWriteAfterReconnectIsSkipped() throws Exception {
            // Zeroed so the stale message below is not itself refused as spam by the cooldown the
            // first message just set -- this test is about the generation gate, not the cooldown
            // gate in front of it.
            chatConfig.setAntiSpamCooldown(0);

            // The new connection records its own message first, through the real registry -- this is
            // the state that must survive untouched.
            realConnectionRegistry.onJoin(playerUuid);
            when(ChatTestHelper.getMockServer().getPlayer(playerUuid)).thenReturn(player);
            realListener.onChat(createChatEvent("new connection message"));
            assertThat(map("lastMessageTime")).as("the new connection's own record").containsKey(playerUuid);
            Object newConnectionTime = map("lastMessageTime").get(playerUuid);

            // The old connection's chat task captured its generation before the reconnect, and by
            // the time it reaches its own write, the registry no longer reports that generation as
            // current -- simulated directly, since there is no way to pause the real registry
            // mid-method the way an actual delayed async task would be paused by thread scheduling.
            ConnectionRegistry staleView = mock(ConnectionRegistry.class);
            when(staleView.currentGeneration(playerUuid)).thenReturn(0L);
            when(staleView.isCurrent(playerUuid, 0L)).thenReturn(false);
            ChatListener staleListener = listenerWithStubbedRegistry(staleView);

            staleListener.onChat(createChatEvent("stale old connection message"));

            assertThat(map("lastMessageTime").get(playerUuid))
                    .as("the new connection's own timestamp is untouched")
                    .isEqualTo(newConnectionTime);
            Collection<?> recent = (Collection<?>) map("recentMessages").get(playerUuid);
            assertThat(recent).as("the stale message was never recorded").hasSize(1);
        }

        @Test
        @DisplayName("An old connection's cleanup lands after a newer connection has taken over: skipped, nothing recorded so far is erased")
        void oldConnectionsCleanupAfterReconnectIsSkipped() throws Exception {
            // Zeroed so the old connection's own message below is not itself refused as spam by the
            // cooldown the pre-populated record just set -- this test is about the generation gate on
            // cleanup, not the cooldown gate in front of it.
            chatConfig.setAntiSpamCooldown(0);
            // What must survive: the state already on record for this UUID (standing in for the new
            // connection's own already-recorded message, however it got there).
            realAntiSpam.recordMessage(playerUuid, "already on record");
            assertThat(map("lastMessageTime")).containsKey(playerUuid);

            // The old connection's task was still current when it reached its own write (so that
            // write is allowed to proceed), but a reconnect lands in the exact window between that
            // write and the check that decides whether to clean up -- the #40-review regression this
            // generation check exists to close. Bukkit.getPlayer(uuid) == null still holds (nobody
            // the old connection's own reference resolves is online), so the original,
            // generation-blind condition alone would have run cleanup here.
            when(ChatTestHelper.getMockServer().getPlayer(playerUuid)).thenReturn(null);
            ConnectionRegistry racyView = mock(ConnectionRegistry.class);
            when(racyView.currentGeneration(playerUuid)).thenReturn(1L);
            when(racyView.isCurrent(playerUuid, 1L)).thenReturn(true, false);
            ChatListener racyListener = listenerWithStubbedRegistry(racyView);

            racyListener.onChat(createChatEvent("old connection's own message"));

            assertThat(map("lastMessageTime")).as("nothing was erased").containsKey(playerUuid);
            Collection<?> recent = (Collection<?>) map("recentMessages").get(playerUuid);
            assertThat(recent).as("both messages are still recorded").hasSize(2);
        }
    }

    // ==================== UltiKits/UltiChat#32 ====================

    /**
     * The line a player sees is {@code String.format(format, displayName, message)}; these tests
     * apply that last step themselves, so they read what is shown, not an intermediate string.
     */
    @Nested
    @DisplayName("A display name is inserted as literal text, never as part of the template (UltiKits/UltiChat#32)")
    class DisplayNameIsLiteral {

        private String shownLine(String displayName, String format) {
            chatConfig.setChatFormatEnabled(true);
            chatConfig.setChatFormat(format);
            chatConfig.setAntiSpamEnabled(false);
            chatConfig.setMentionsEnabled(false);
            channelConfig.setEnabled(false);
            when(player.getDisplayName()).thenReturn(displayName);

            AsyncPlayerChatEvent event = createChatEvent("hello");
            listener.onChat(event);
            return String.format(event.getFormat(), displayName, event.getMessage());
        }

        @Test
        @DisplayName("a display name containing {message} does not repeat the message")
        void messageTokenStaysLiteral() {
            assertThat(shownLine("Evil {message}", "{displayname} says: {message}"))
                    .isEqualTo("Evil {message} says: hello");
        }

        @Test
        @DisplayName("a display name containing a format specifier or a percent sign is shown as written")
        void formatSpecifierStaysLiteral() {
            assertThat(shownLine("Nick %2$s 100%", "{displayname} says: {message}"))
                    .isEqualTo("Nick %2$s 100% says: hello");
        }

        @Test
        @DisplayName("with PlaceholderAPI installed, a placeholder in a display name is not expanded")
        void placeholderStaysLiteral() {
            org.bukkit.plugin.PluginManager pluginManager = Bukkit.getPluginManager();
            when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
            try (MockedStatic<me.clip.placeholderapi.PlaceholderAPI> papi =
                         mockStatic(me.clip.placeholderapi.PlaceholderAPI.class)) {
                papi.when(() -> me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(any(Player.class), anyString()))
                        .thenAnswer(inv -> ((String) inv.getArgument(1)).replace("%server_name%", "SECRET"));

                assertThat(shownLine("Nick %server_name%", "[%server_name%] {displayname}: {message}"))
                        .as("the operator's own placeholder expands; the player's does not")
                        .isEqualTo("[SECRET] Nick %server_name%: hello");
            }
        }

        @Test
        @DisplayName("control: '&' colour codes in a display name are still shown as colour")
        void colourCodesStillTranslate() {
            assertThat(shownLine("&cRed", "{displayname}: {message}")).isEqualTo("\u00a7cRed: hello");
        }

        @Test
        @DisplayName("control: an ordinary display name shows as before")
        void ordinaryDisplayName() {
            assertThat(shownLine("Fancy", "{displayname} says: {message}")).isEqualTo("Fancy says: hello");
        }
    }
}
