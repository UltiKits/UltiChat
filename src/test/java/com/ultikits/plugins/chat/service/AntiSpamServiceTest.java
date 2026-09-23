package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("AntiSpamService")
class AntiSpamServiceTest {

    private AntiSpamService service;
    private ChatConfig config;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();
        config = new ChatConfig();
        config.setAntiSpamEnabled(true);
        config.setAntiSpamCooldown(2);
        config.setAntiSpamMaxDuplicate(3);
        config.setAntiSpamDuplicateWindow(30);
        config.setAntiSpamCapsLimit(70);

        service = new AntiSpamService();
        ChatTestHelper.setField(service, "config", config);
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    private Player createPlayer() {
        return ChatTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    private Player createPlayerWithId(UUID uuid) {
        return ChatTestHelper.createMockPlayer("TestPlayer", uuid);
    }

    /** The retained-message history for a player, whatever its element type. */
    private Collection<?> retained(UUID playerId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<UUID, ? extends Collection<?>> recentMessages =
                (Map<UUID, ? extends Collection<?>>) ChatTestHelper.getField(service, "recentMessages");
        return recentMessages.get(playerId);
    }

    // -------------------------------------------------------------------------
    // checkSpam
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("checkSpam - disabled / null safety")
    class CheckSpamDisabledTests {

        @Test
        @DisplayName("should return null when anti-spam is disabled")
        void shouldReturnNullWhenDisabled() {
            config.setAntiSpamEnabled(false);
            Player player = createPlayer();
            assertThat(service.checkSpam(player, "hello")).isNull();
        }

        @Test
        @DisplayName("should return null when player is null")
        void shouldReturnNullWhenPlayerNull() {
            assertThat(service.checkSpam(null, "hello")).isNull();
        }

        @Test
        @DisplayName("should return null when message is null")
        void shouldReturnNullWhenMessageNull() {
            Player player = createPlayer();
            assertThat(service.checkSpam(player, null)).isNull();
        }

        @Test
        @DisplayName("should return null for normal message")
        void shouldReturnNullForNormalMessage() {
            Player player = createPlayer();
            assertThat(service.checkSpam(player, "hello world")).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Cooldown checks
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("checkSpam - cooldown")
    class CheckSpamCooldownTests {

        @Test
        @DisplayName("should return cooldown reason when sending too fast")
        void shouldReturnCooldownReasonWhenTooFast() throws Exception {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();

            // Simulate a recent message
            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            lastMessageTime.put(playerId, System.currentTimeMillis());

            String reason = service.checkSpam(player, "too fast");
            assertThat(reason).isEqualTo("发送消息太快了！");
        }

        @Test
        @DisplayName("should allow message after cooldown expires")
        void shouldAllowAfterCooldownExpires() throws Exception {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            // Set last message to 3 seconds ago (cooldown is 2s)
            lastMessageTime.put(playerId, System.currentTimeMillis() - 3000);

            String reason = service.checkSpam(player, "allowed now");
            assertThat(reason).isNull();
        }

        @Test
        @DisplayName("should allow first message from player")
        void shouldAllowFirstMessage() {
            Player player = createPlayer();
            String reason = service.checkSpam(player, "first message");
            assertThat(reason).isNull();
        }

        @Test
        @DisplayName("should use zero cooldown correctly")
        void shouldUseZeroCooldownCorrectly() throws Exception {
            config.setAntiSpamCooldown(0);
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            lastMessageTime.put(playerId, System.currentTimeMillis());

            // 0 second cooldown means message is never too fast
            String reason = service.checkSpam(player, "immediate");
            assertThat(reason).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Duplicate detection
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("checkSpam - duplicate detection")
    class CheckSpamDuplicateTests {

        /** Records {@code count} copies of {@code message} as accepted messages, with no cooldown in the way. */
        private void recordCopies(UUID playerId, String message, int count) {
            int cooldown = config.getAntiSpamCooldown();
            config.setAntiSpamCooldown(0);
            for (int i = 0; i < count; i++) {
                service.recordMessage(playerId, message);
            }
            config.setAntiSpamCooldown(cooldown);
        }

        @Test
        @DisplayName("should detect duplicate messages")
        void shouldDetectDuplicateMessages() {
            Player player = createPlayer();
            // 3 identical messages in history (maxDuplicate is 3)
            recordCopies(player.getUniqueId(), "spam", 3);
            config.setAntiSpamCooldown(0);

            String reason = service.checkSpam(player, "spam");
            assertThat(reason).isEqualTo("请不要发送重复消息！");
        }

        @Test
        @DisplayName("should allow message when below duplicate threshold")
        void shouldAllowBelowDuplicateThreshold() {
            Player player = createPlayer();
            // Only 2 duplicates, threshold is 3
            recordCopies(player.getUniqueId(), "spam", 2);
            config.setAntiSpamCooldown(0);

            String reason = service.checkSpam(player, "spam");
            assertThat(reason).isNull();
        }

        @Test
        @DisplayName("should allow different messages")
        void shouldAllowDifferentMessages() {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            recordCopies(playerId, "message1", 1);
            recordCopies(playerId, "message2", 1);
            recordCopies(playerId, "message3", 1);
            config.setAntiSpamCooldown(0);

            String reason = service.checkSpam(player, "message4");
            assertThat(reason).isNull();
        }

        @Test
        @DisplayName("should handle no recent messages entry")
        void shouldHandleNoRecentMessagesEntry() {
            Player player = createPlayer();
            String reason = service.checkSpam(player, "hello");
            assertThat(reason).isNull();
        }

        @Test
        @DisplayName("should handle maxDuplicate of zero")
        void shouldHandleMaxDuplicateOfZero() {
            Player player = createPlayer();
            recordCopies(player.getUniqueId(), "spam", 3);
            config.setAntiSpamMaxDuplicate(0);
            config.setAntiSpamCooldown(0);

            String reason = service.checkSpam(player, "spam");
            // maxDuplicate <= 0 means duplicate check is disabled
            assertThat(reason).isNull();
        }

        @Test
        @DisplayName("only the last max-duplicate accepted messages are retained: an older copy pushed out no longer counts")
        void oldestCopyIsPushedOut() {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            recordCopies(playerId, "spam", 3);
            recordCopies(playerId, "other", 1);
            config.setAntiSpamCooldown(0);

            // Retained: spam, spam, other -- two copies, below the threshold of 3.
            assertThat(service.checkSpam(player, "spam")).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Duplicate window (UltiKits/UltiChat#14)
    // -------------------------------------------------------------------------

    /**
     * UltiKits/UltiChat#14. {@code anti-spam.duplicate-window} was declared and never read, so a
     * repeat counted however long ago its earlier copies were sent. It is now wired: a retained copy
     * older than the window stops counting. A window of 0 -- the declared default -- means no time
     * limit, which is exactly the count-only detection of before. These tests use the real clock,
     * because the window is whole seconds and the only way to be outside it is to let time pass; the
     * one test that needs that waits just over one second.
     */
    @Nested
    @DisplayName("checkSpam - duplicate window (UltiKits/UltiChat#14)")
    class DuplicateWindowTests {

        @Test
        @DisplayName("A repeat inside the window counts as a duplicate")
        void repeatInsideTheWindowCounts() {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(60);

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");

            assertThat(service.checkSpam(player, "spam")).isEqualTo("请不要发送重复消息！");
        }

        @Test
        @DisplayName("Copies older than the window stop counting; copies inside it still count")
        void copiesOutsideTheWindowStopCounting() throws Exception {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(1);

            service.recordMessage(playerId, "spam");
            Thread.sleep(1100L);
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");

            // Three copies retained, but the first is older than the 1-second window: two count,
            // below the threshold of 3, so the repeat is accepted.
            assertThat(service.checkSpam(player, "spam")).isNull();

            // Control on the same history: the two copies inside the window DO count -- with a
            // threshold of 2 the same repeat is refused. Without this the null above could also
            // mean "duplicate detection stopped working".
            config.setAntiSpamMaxDuplicate(2);
            assertThat(service.checkSpam(player, "spam")).isEqualTo("请不要发送重复消息！");

            // Window 0, the declared default: no time limit, exactly the count-only rule of before --
            // the same history, with its copy older than a second, trips the threshold of 3 again.
            config.setAntiSpamMaxDuplicate(3);
            config.setAntiSpamDuplicateWindow(0);
            assertThat(service.checkSpam(player, "spam")).isEqualTo("请不要发送重复消息！");
        }

        @Test
        @DisplayName("Window 0 counts every retained copy, and still only identical ones")
        void zeroWindowIsCountOnly() {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(0);

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "other");

            // Retained: spam, spam, other -- two identical copies, below the threshold of 3.
            assertThat(service.checkSpam(player, "spam")).isNull();
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            // Retained: spam, spam, spam.
            assertThat(service.checkSpam(player, "spam")).isEqualTo("请不要发送重复消息！");
        }
    }

    // -------------------------------------------------------------------------
    // Caps detection
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("checkSpam - caps detection")
    class CheckSpamCapsTests {

        @Test
        @DisplayName("should detect excessive caps")
        void shouldDetectExcessiveCaps() {
            // 70% limit, 100% caps in a 10-char message
            Player player = createPlayer();
            String reason = service.checkSpam(player, "HELLOWORLD");
            assertThat(reason).isEqualTo("消息中大写字母过多！");
        }

        @Test
        @DisplayName("should allow message with acceptable caps ratio")
        void shouldAllowAcceptableCapsRatio() {
            Player player = createPlayer();
            // 2/10 = 20% caps, below 70%
            String reason = service.checkSpam(player, "HEllo worl");
            assertThat(reason).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // isExcessiveCaps
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isExcessiveCaps")
    class IsExcessiveCapsTests {

        @Test
        @DisplayName("should return false for null message")
        void shouldReturnFalseForNull() {
            assertThat(service.isExcessiveCaps(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for short messages (< 5 chars)")
        void shouldReturnFalseForShortMessages() {
            assertThat(service.isExcessiveCaps("ABCD")).isFalse();
        }

        @Test
        @DisplayName("should return false for exactly 4 char message")
        void shouldReturnFalseForFourChars() {
            assertThat(service.isExcessiveCaps("ABCD")).isFalse();
        }

        @Test
        @DisplayName("should detect caps in 5+ char message")
        void shouldDetectCapsInFiveCharMessage() {
            // 5/5 = 100% > 70%
            assertThat(service.isExcessiveCaps("ABCDE")).isTrue();
        }

        @Test
        @DisplayName("should return false when caps at exactly limit")
        void shouldReturnFalseWhenCapsAtExactLimit() {
            config.setAntiSpamCapsLimit(70);
            // 7/10 = 70% which is NOT > 70% (strictly greater)
            assertThat(service.isExcessiveCaps("ABCDEFGhij")).isFalse();
        }

        @Test
        @DisplayName("should return true when caps above limit")
        void shouldReturnTrueWhenCapsAboveLimit() {
            config.setAntiSpamCapsLimit(70);
            // 8/10 = 80% > 70%
            assertThat(service.isExcessiveCaps("ABCDEFGHij")).isTrue();
        }

        @Test
        @DisplayName("should ignore non-letter characters in ratio calculation")
        void shouldIgnoreNonLetterCharacters() {
            config.setAntiSpamCapsLimit(70);
            // Letters: A,B,C,D,E,F,G,h,i,j = 10, uppercase: 7, ratio = 70%, not > 70%
            assertThat(service.isExcessiveCaps("A1B2C3D4E5F6G7h8i9j0")).isFalse();
        }

        @Test
        @DisplayName("should return false for message with only non-letter characters")
        void shouldReturnFalseForOnlyNonLetters() {
            assertThat(service.isExcessiveCaps("12345 67890")).isFalse();
        }

        @Test
        @DisplayName("should return false when caps limit is zero")
        void shouldReturnFalseWhenCapsLimitZero() {
            config.setAntiSpamCapsLimit(0);
            assertThat(service.isExcessiveCaps("ABCDE")).isFalse();
        }

        @Test
        @DisplayName("should return false when caps limit is 100 or more")
        void shouldReturnFalseWhenCapsLimit100() {
            config.setAntiSpamCapsLimit(100);
            assertThat(service.isExcessiveCaps("ABCDE")).isFalse();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmptyString() {
            assertThat(service.isExcessiveCaps("")).isFalse();
        }

        @Test
        @DisplayName("should handle all lowercase")
        void shouldHandleAllLowercase() {
            assertThat(service.isExcessiveCaps("abcdefgh")).isFalse();
        }

        @Test
        @DisplayName("should handle mixed case below threshold")
        void shouldHandleMixedCaseBelowThreshold() {
            config.setAntiSpamCapsLimit(50);
            // 2/10 = 20% < 50%
            assertThat(service.isExcessiveCaps("ABcdefghij")).isFalse();
        }

        @Test
        @DisplayName("should handle mixed case above threshold")
        void shouldHandleMixedCaseAboveThreshold() {
            config.setAntiSpamCapsLimit(50);
            // 8/10 = 80% > 50%
            assertThat(service.isExcessiveCaps("ABCDEFGHij")).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // recordMessage
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("recordMessage")
    class RecordMessageTests {

        @Test
        @DisplayName("should update lastMessageTime")
        void shouldUpdateLastMessageTime() throws Exception {
            UUID playerId = UUID.randomUUID();
            long before = System.currentTimeMillis();
            service.recordMessage(playerId, "hello");
            long after = System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            assertThat(lastMessageTime.get(playerId)).isBetween(before, after);
        }

        @Test
        @DisplayName("should add message to recent messages")
        void shouldAddMessageToRecentMessages() throws Exception {
            UUID playerId = UUID.randomUUID();
            service.recordMessage(playerId, "hello");

            assertThat(retained(playerId)).hasSize(1);
        }

        @Test
        @DisplayName("should maintain bounded list of recent messages")
        void shouldMaintainBoundedList() throws Exception {
            UUID playerId = UUID.randomUUID();
            config.setAntiSpamMaxDuplicate(3);

            service.recordMessage(playerId, "msg1");
            service.recordMessage(playerId, "msg2");
            service.recordMessage(playerId, "msg3");
            service.recordMessage(playerId, "msg4");

            // Which three are kept is pinned behaviourally by oldestCopyIsPushedOut.
            assertThat(retained(playerId)).hasSize(3);
        }

        @Test
        @DisplayName("should handle null playerId gracefully")
        void shouldHandleNullPlayerId() {
            // Should not throw
            service.recordMessage(null, "hello");
        }

        @Test
        @DisplayName("should handle null message gracefully")
        void shouldHandleNullMessage() {
            // Should not throw
            service.recordMessage(UUID.randomUUID(), null);
        }

        @Test
        @DisplayName("should use default max duplicate when config is zero")
        void shouldUseDefaultMaxDuplicateWhenZero() throws Exception {
            config.setAntiSpamMaxDuplicate(0);
            UUID playerId = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                service.recordMessage(playerId, "msg" + i);
            }

            // Default is 3 when config is 0
            assertThat(retained(playerId)).hasSize(3);
        }
    }

    // -------------------------------------------------------------------------
    // cleanup
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("cleanup")
    class CleanupTests {

        @Test
        @DisplayName("should remove all state for player")
        void shouldRemoveAllState() throws Exception {
            UUID playerId = UUID.randomUUID();

            // Add state
            service.recordMessage(playerId, "hello");

            // Verify state exists
            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(service, "recentMessages");

            assertThat(lastMessageTime).containsKey(playerId);
            assertThat(recentMessages).containsKey(playerId);

            // Cleanup
            service.cleanup(playerId);

            assertThat(lastMessageTime).doesNotContainKey(playerId);
            assertThat(recentMessages).doesNotContainKey(playerId);
        }

        @Test
        @DisplayName("should handle null playerId gracefully")
        void shouldHandleNullPlayerId() {
            // Should not throw
            service.cleanup(null);
        }

        @Test
        @DisplayName("should handle cleanup of non-existent player")
        void shouldHandleCleanupOfNonExistentPlayer() {
            // Should not throw
            service.cleanup(UUID.randomUUID());
        }

        @Test
        @DisplayName("should not affect other players")
        void shouldNotAffectOtherPlayers() throws Exception {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            service.recordMessage(player1, "msg1");
            service.recordMessage(player2, "msg2");

            service.cleanup(player1);

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(service, "recentMessages");

            assertThat(lastMessageTime).doesNotContainKey(player1);
            assertThat(lastMessageTime).containsKey(player2);
            assertThat(recentMessages).doesNotContainKey(player1);
            assertThat(recentMessages).containsKey(player2);
        }
    }

    // -------------------------------------------------------------------------
    // Integration scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Integration scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("full flow: record, then trigger cooldown")
        void fullFlowCooldown() {
            Player player = createPlayer();

            // First message should pass
            assertThat(service.checkSpam(player, "hello")).isNull();
            service.recordMessage(player.getUniqueId(), "hello");

            // Immediate second message should trigger cooldown
            assertThat(service.checkSpam(player, "world")).isEqualTo("发送消息太快了！");
        }

        @Test
        @DisplayName("full flow: record duplicates then trigger spam")
        void fullFlowDuplicate() throws Exception {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            config.setAntiSpamCooldown(0); // Disable cooldown for this test

            // Record 3 "spam" messages
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");

            // 4th "spam" should be detected as duplicate
            String reason = service.checkSpam(player, "spam");
            assertThat(reason).isEqualTo("请不要发送重复消息！");
        }

        @Test
        @DisplayName("check order: cooldown before duplicate before caps")
        void checkOrderCooldownFirst() {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();

            // Duplicate history and an all-caps message, then a message still inside the cooldown:
            // every check would refuse, and the cooldown's reason is the one reported.
            service.recordMessage(playerId, "SPAMSPAM");
            service.recordMessage(playerId, "SPAMSPAM");
            service.recordMessage(playerId, "SPAMSPAM");

            assertThat(service.checkSpam(player, "SPAMSPAM")).isEqualTo("发送消息太快了！");

            // Cooldown off: duplicate is reported before caps.
            config.setAntiSpamCooldown(0);
            assertThat(service.checkSpam(player, "SPAMSPAM")).isEqualTo("请不要发送重复消息！");
        }

        @Test
        @DisplayName("multiple players are tracked independently")
        void multiplePlayersTrackedIndependently() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = createPlayerWithId(uuid1);
            Player player2 = createPlayerWithId(uuid2);

            // Player 1 sends a message
            assertThat(service.checkSpam(player1, "hello")).isNull();
            service.recordMessage(uuid1, "hello");

            // Player 2 should not be affected by player 1's cooldown
            assertThat(service.checkSpam(player2, "hello")).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // No automatic muting (UltiKits/UltiChat#15)
    // -------------------------------------------------------------------------

    /**
     * UltiKits/UltiChat#15. Automatic muting was declared and never happened -- {@code mutePlayer}
     * had no caller. The maintainer's ruling deletes the declaration, so a spam trip refuses exactly
     * the offending message and nothing more, as it always did in practice.
     */
    @Nested
    @DisplayName("A spam trip refuses only that message (UltiKits/UltiChat#15)")
    class NoAutomaticMute {

        @Test
        @DisplayName("After a duplicate refusal, a different message is accepted once the cooldown has passed")
        void duplicateTripDoesNotSilenceThePlayer() {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            config.setAntiSpamCooldown(0);

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            assertThat(service.checkSpam(player, "spam")).isEqualTo("请不要发送重复消息！");

            assertThat(service.checkSpam(player, "something else")).isNull();
        }

        @Test
        @DisplayName("After a caps refusal, a normal message is accepted once the cooldown has passed")
        void capsTripDoesNotSilenceThePlayer() {
            Player player = createPlayer();
            config.setAntiSpamCooldown(0);

            assertThat(service.checkSpam(player, "HELLOWORLD")).isEqualTo("消息中大写字母过多！");

            assertThat(service.checkSpam(player, "hello world")).isNull();
        }

        @Test
        @DisplayName("The service keeps no mute state and offers no way to mute")
        void noMuteStateOrEntryPoint() {
            List<String> fields = new ArrayList<String>();
            for (Field field : AntiSpamService.class.getDeclaredFields()) {
                fields.add(field.getName());
            }
            List<String> methods = new ArrayList<String>();
            for (Method method : AntiSpamService.class.getDeclaredMethods()) {
                methods.add(method.getName());
            }

            // Positive control: the reflection sees the state and entry points that do exist.
            assertThat(fields).contains("lastMessageTime", "recentMessages");
            assertThat(methods).contains("checkSpam", "recordMessage", "cleanup");
            assertThat(fields).doesNotContain("mutedUntil");
            assertThat(methods).doesNotContain("mutePlayer", "checkMute");
        }
    }
}
