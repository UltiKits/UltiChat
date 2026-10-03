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
import java.util.function.LongSupplier;
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
        // The refusals come from the language file (UltiKits/UltiChat#18): the module answers from
        // the real zh catalogue. A tree whose service has no plugin field yet is left as it is, so the
        // same tests run before and after the change.
        plugin = org.mockito.Mockito.mock(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("zh"));
        try {
            ChatTestHelper.setField(service, "plugin", plugin);
        } catch (NoSuchFieldException absent) {
            // the service does not read the language file yet
        }
    }

    /** The refusal texts under language: zh, as the language file gives them. */
    private static final String ZH_COOLDOWN = catalogueOrMissing("spam_cooldown");
    private static final String ZH_DUPLICATE = catalogueOrMissing("spam_duplicate");
    private static final String ZH_CAPS = catalogueOrMissing("spam_caps");

    private static String catalogueOrMissing(String key) {
        String text = com.ultikits.plugins.chat.i18n.CatalogueText.entries("zh").get(key);
        return text == null ? "<lang/zh has no " + key + ">" : text;
    }

    private com.ultikits.ultitools.abstracts.UltiToolsPlugin plugin;

    @Test
    @DisplayName("Under language: en each refusal is the English catalogue text (UltiKits/UltiChat#18)")
    void refusalsFollowTheLanguageSetting() throws Exception {
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
        Player player = createPlayer();
        service.recordMessage(player.getUniqueId(), "hello");

        assertThat(service.checkSpam(player, "hello again"))
                .isEqualTo(com.ultikits.plugins.chat.i18n.CatalogueText.text("en", "spam_cooldown"));
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
            assertThat(reason).isEqualTo(ZH_COOLDOWN);
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
            assertThat(reason).isEqualTo(ZH_DUPLICATE);
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
     * limit, which is exactly the count-only detection of before. The tests that need time to pass
     * replace the service's clock with a hand-set one (the earlier version slept 1.1 s against a
     * 1-second window, and a JVM stall of over a second between the record and the control
     * assertion would have failed it).
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

            assertThat(service.checkSpam(player, "spam")).isEqualTo(ZH_DUPLICATE);
        }

        /** Replaces the service's clock with a hand-set one; returns the cell that holds "now". */
        private long[] manualClock() throws Exception {
            final long[] now = {1_000_000L};
            ChatTestHelper.setField(service, "clock", (LongSupplier) () -> now[0]);
            return now;
        }

        @Test
        @DisplayName("Copies older than the window stop counting; copies inside it still count")
        void copiesOutsideTheWindowStopCounting() throws Exception {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(1);

            service.recordMessage(playerId, "spam");
            now[0] += 1500L;
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");

            // Three copies retained, but the first is 1.5 s old against a 1-second window: two
            // count, below the threshold of 3, so the repeat is accepted.
            assertThat(service.checkSpam(player, "spam")).isNull();

            // Control on the same history: the two copies inside the window DO count -- with a
            // threshold of 2 the same repeat is refused. Without this the null above could also
            // mean "duplicate detection stopped working".
            config.setAntiSpamMaxDuplicate(2);
            assertThat(service.checkSpam(player, "spam")).isEqualTo(ZH_DUPLICATE);

            // Window 0, the declared default: no time limit, exactly the count-only rule of before --
            // the same history, with its copy older than a second, trips the threshold of 3 again.
            config.setAntiSpamMaxDuplicate(3);
            config.setAntiSpamDuplicateWindow(0);
            assertThat(service.checkSpam(player, "spam")).isEqualTo(ZH_DUPLICATE);
        }

        @Test
        @DisplayName("A copy exactly as old as the window still counts; one millisecond older does not")
        void windowBoundaryIsInclusive() throws Exception {
            Player player = createPlayer();
            UUID playerId = player.getUniqueId();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(2);

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");

            now[0] += 2000L;
            assertThat(service.checkSpam(player, "spam")).isEqualTo(ZH_DUPLICATE);
            now[0] += 1L;
            assertThat(service.checkSpam(player, "spam")).isNull();
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
            assertThat(service.checkSpam(player, "spam")).isEqualTo(ZH_DUPLICATE);
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
            assertThat(reason).isEqualTo(ZH_CAPS);
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
    // Self-expiry, replacing connection-scoped cleanup entirely
    // (UltiKits/UltiChat#20, #35, #40 review, maintainer decision 2026-09-29)
    // -------------------------------------------------------------------------

    /**
     * {@code cleanup(UUID)} existed from UltiKits/UltiChat#20 through three further review rounds on
     * the same reconnect race, called from {@code PlayerChannelListener#onPlayerQuit} and, for one
     * round, gated by a per-connection generation check inside {@code ChatListener} itself. Every one
     * of those designs treated a reconnect as something anti-spam tracking needed to react to, and
     * each reacted to it wrong in a different way. The maintainer's final decision (2026-09-29) is
     * that quit is not an event this service needs to know about at all: both maps now expire an
     * entry purely by elapsed time, in {@link AntiSpamService#checkCooldown} and
     * {@link AntiSpamService#isDuplicate} (self-eviction on read) and by a sweep piggybacked on every
     * {@link AntiSpamService#recordMessage} call, so a UUID that never chats again is not held open
     * by nothing ever reading it. There is no {@code cleanup} method left to call.
     */
    @Nested
    @DisplayName("Self-expiry (UltiKits/UltiChat#20, #35, #40 review, maintainer decision 2026-09-29)")
    class SelfExpiryTests {

        /** Replaces the service's clock with a hand-set one; returns the cell that holds "now". */
        private long[] manualClock() throws Exception {
            final long[] now = {1_000_000L};
            ChatTestHelper.setField(service, "clock", (LongSupplier) () -> now[0]);
            return now;
        }

        @Test
        @DisplayName("Reconnecting does not bypass anti-spam: a cooldown timestamp survives however long the player is silent for less than the self-eviction ceiling")
        void cooldownSurvivesAGapShortOfTheCeiling() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(30);

            service.recordMessage(playerId, "hello");
            // A 10-second gap -- standing in for a disconnect-and-reconnect -- well inside the
            // 30-second cooldown, so still would block a message from either the same or a
            // "reconnected" connection; the point is that nothing cleared the entry across the gap.
            now[0] += 10_000L;

            assertThat(service.checkSpam(createPlayerWithId(playerId), "world"))
                    .as("the cooldown from before the gap still applies; a reconnect did not reset it")
                    .isEqualTo(ZH_COOLDOWN);
        }

        @Test
        @DisplayName("A cooldown entry is cleared once it is older than the longest cooldown this setting could ever be reloaded to")
        void cooldownEntryExpiresPastItsOwnCeiling() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(30);

            service.recordMessage(playerId, "hello");
            now[0] += 60_000L; // exactly the @Range(max = 60) ceiling in seconds

            assertThat(service.checkSpam(createPlayerWithId(playerId), "world"))
                    .as("no possible cooldown setting could still be blocking on an entry this old")
                    .isNull();

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            assertThat(lastMessageTime).as("cleared by the read above, not merely ignored").doesNotContainKey(playerId);
        }

        @Test
        @DisplayName("A duplicate-detection entry is cleared once older than the longest duplicate-window this setting could ever be reloaded to, while that window is finite")
        void duplicateEntryExpiresPastItsOwnCeilingWhileFinite() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(300); // a finite, positive value

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            now[0] += 600_001L; // one millisecond past the @Range(max = 600) ceiling in seconds

            assertThat(service.checkSpam(createPlayerWithId(playerId), "spam"))
                    .as("an entry this old cannot be reached by ANY finite duplicate-window value")
                    .isNull();

            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(service, "recentMessages");
            assertThat(recentMessages).doesNotContainKey(playerId);
        }

        /**
         * UltiKits/UltiChat#40 review, Codex round 6: applying the 600-second ceiling unconditionally,
         * regardless of the setting's own value, silently contradicted {@code anti-spam.duplicate-window:
         * 0}'s documented "no time limit" promise for the shipped default -- not a rare edge case, since
         * 0 is what every server ships with. Confirmed pre-existing (present unchanged since the prior
         * round, which only changed how eviction is performed, not this comparison), fixed directly.
         */
        @Test
        @DisplayName("With duplicate-window: 0 (unlimited), a retained history survives the 600-second ceiling -- 'no time limit' is honoured for any realistic gap")
        void unlimitedDuplicateWindowSurvivesTheFiniteCeiling() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(0); // the shipped default: no time limit on matching

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            now[0] += 600_000L; // exactly the FINITE ceiling -- must NOT evict while unlimited

            assertThat(service.checkSpam(createPlayerWithId(playerId), "spam"))
                    .as("'no time limit' must remain true for any gap a real player could produce")
                    .isEqualTo(ZH_DUPLICATE);
        }

        @Test
        @DisplayName("With duplicate-window: 0 (unlimited), a retained history is still eventually reclaimed for memory hygiene, past the far-longer housekeeping ceiling")
        void unlimitedDuplicateWindowExpiresPastTheHousekeepingCeiling() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(0);

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            now[0] += 86_400_001L; // one millisecond past the housekeeping ceiling, far longer than any realistic session

            assertThat(service.checkSpam(createPlayerWithId(playerId), "spam"))
                    .as("eventually reclaimed regardless -- this is memory hygiene, not a promise about "
                            + "how long \"no time limit\" lasts within a session")
                    .isNull();

            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(service, "recentMessages");
            assertThat(recentMessages).doesNotContainKey(playerId);
        }

        /**
         * UltiKits/UltiChat#41: eviction used {@code >=} against the ceiling while the window comparison
         * counts a copy that is at most {@code duplicate-window} seconds old ({@code <=}), so at
         * {@code duplicate-window: 600} a copy exactly 600 000 ms old was dropped although the window
         * would still count it.
         */
        @Test
        @DisplayName("At duplicate-window: 600 a copy exactly 600 000 ms old is still counted; one millisecond later it is not (#41)")
        void duplicateExactlyAtTheCeilingIsStillCounted() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(600);

            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            service.recordMessage(playerId, "spam");
            now[0] += 600_000L;

            assertThat(service.checkSpam(createPlayerWithId(playerId), "spam"))
                    .as("the inclusive window comparison still counts a copy exactly 600 000 ms old")
                    .isEqualTo(ZH_DUPLICATE);
            assertThat(retained(playerId)).as("and nothing evicted the history").hasSize(3);

            now[0] += 1L;

            assertThat(service.checkSpam(createPlayerWithId(playerId), "spam"))
                    .as("control: one millisecond later no copy is inside the window, so the message passes")
                    .isNull();
            assertThat(retained(playerId)).as("control: and the entry is evicted").isNull();
        }

        @Test
        @DisplayName("The scheduled sweep keeps a duplicate history exactly at the ceiling and removes it one millisecond later (#41)")
        void scheduledSweepKeepsAnEntryExactlyAtTheCeiling() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamDuplicateWindow(600);

            service.recordMessage(playerId, "spam");
            now[0] += 600_000L;
            service.sweepExpiredEntries();

            assertThat(retained(playerId)).as("exactly at the ceiling the history is kept").isNotNull();

            now[0] += 1L;
            service.sweepExpiredEntries();

            assertThat(retained(playerId)).as("control: one millisecond past the ceiling it is removed").isNull();
        }

        @Test
        @DisplayName("With duplicate-window: 0 the housekeeping ceiling is also strict: kept at exactly 24 h, removed one millisecond later (#41)")
        void unlimitedWindowHousekeepingCeilingIsStrict() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(0);
            config.setAntiSpamDuplicateWindow(0);

            service.recordMessage(playerId, "spam");
            now[0] += 86_400_000L;
            service.sweepExpiredEntries();

            assertThat(retained(playerId)).isNotNull();

            now[0] += 1L;
            service.sweepExpiredEntries();

            assertThat(retained(playerId)).as("control: one millisecond past it is removed").isNull();
        }

        /**
         * The cooldown table's {@code >= MAX_COOLDOWN_MS} checked against the cooldown comparison, as
         * #41 asks: a cooldown blocks only while the elapsed time is below {@code cooldown * 1000}, and
         * the longest cooldown is 60 s, so at exactly 60 000 ms nothing blocks whether or not the entry
         * is evicted -- the two comparisons agree and the table keeps its {@code >=}.
         */
        @Test
        @DisplayName("At the longest cooldown (60 s) a message exactly 60 000 ms later passes, with or without eviction (#41 cooldown check)")
        void cooldownBoundaryAgreesWithItsEviction() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            config.setAntiSpamCooldown(60);

            service.recordMessage(playerId, "hello");
            now[0] += 59_999L;
            assertThat(service.checkSpam(createPlayerWithId(playerId), "world"))
                    .as("control: one millisecond before the cooldown ends it still blocks")
                    .isEqualTo(ZH_COOLDOWN);

            now[0] += 1L;
            assertThat(service.checkSpam(createPlayerWithId(playerId), "world")).isNull();
        }

        @Test
        @DisplayName("The unlimited-duplicate-window housekeeping ceiling is far longer than any realistic play session")
        void unlimitedDuplicateWindowHousekeepingCeilingIsFarLongerThanAnyRealisticSession() throws Exception {
            long housekeepingMs = (Long) ChatTestHelper.getStaticField(AntiSpamService.class,
                    "UNLIMITED_DUPLICATE_WINDOW_HOUSEKEEPING_MS");
            long finiteCeilingMs = (Long) ChatTestHelper.getStaticField(AntiSpamService.class,
                    "MAX_DUPLICATE_WINDOW_MS");
            assertThat(housekeepingMs).isGreaterThan(finiteCeilingMs);
            assertThat(housekeepingMs).as("a day, not ten minutes").isEqualTo(86_400_000L);
        }

        @Test
        @DisplayName("recordMessage sweeps other players' expired entries too, so the table does not grow past who could still matter")
        void recordMessageSweepsExpiredEntriesForOtherPlayers() throws Exception {
            // Corrected 2026-09-29 (coordinator ruling): a per-write full-table sweep was a measured
            // performance concern in its own right (O(N) on the module's highest-frequency hot path)
            // and is removed; table-wide eviction of a UUID that stops chatting entirely is now the
            // scheduled sweep's job (#recordMessageDoesNotScanTheWholeTable, #scheduledSweepClearsAnUnrelatedStaleEntry
            // below), not something an unrelated write does as a side effect any more.
            UUID stale = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();
            long[] now = manualClock();

            service.recordMessage(stale, "hello");
            now[0] += 600_001L; // past both ceilings
            service.recordMessage(fresh, "hi"); // an unrelated write

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(service, "recentMessages");
            assertThat(lastMessageTime).as("an unrelated write no longer sweeps the whole table")
                    .containsKeys(stale, fresh);
            assertThat(recentMessages).containsKeys(stale, fresh);
        }

        @Test
        @DisplayName("recordMessage does not scan the whole table: an unrelated player's own entry is untouched by it, however many players are tracked")
        void recordMessageDoesNotScanTheWholeTable() throws Exception {
            UUID other = UUID.randomUUID();
            UUID writer = UUID.randomUUID();
            service.recordMessage(other, "hello");
            Object otherTimeBefore = ((Map<UUID, ?>) ChatTestHelper.getField(service, "lastMessageTime")).get(other);

            service.recordMessage(writer, "hi");

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            assertThat(lastMessageTime.get(other))
                    .as("an unrelated write does not re-touch another player's own entry at all")
                    .isEqualTo(otherTimeBefore);
        }

        @Test
        @DisplayName("The scheduled sweep clears a table-wide stale entry that an unrelated write no longer does")
        void scheduledSweepClearsAnUnrelatedStaleEntry() throws Exception {
            UUID stale = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();
            long[] now = manualClock();

            service.recordMessage(stale, "hello");
            now[0] += 600_001L; // past both ceilings
            service.recordMessage(fresh, "hi"); // no longer sweeps `stale`'s entry as a side effect

            service.sweepExpiredEntries();

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            @SuppressWarnings("unchecked")
            Map<UUID, ?> recentMessages = (Map<UUID, ?>) ChatTestHelper.getField(service, "recentMessages");
            assertThat(lastMessageTime).as("the table does not only grow -- the scheduled sweep still clears it")
                    .doesNotContainKey(stale).containsKey(fresh);
            assertThat(recentMessages).doesNotContainKey(stale).containsKey(fresh);
        }

        @Test
        @DisplayName("The scheduled sweep is annotated with a literal, fixed period -- not config-bound")
        void scheduledSweepIsLiteralNotConfigBound() throws Exception {
            java.lang.reflect.Method sweep = AntiSpamService.class.getDeclaredMethod("sweepExpiredEntries");
            com.ultikits.ultitools.annotations.Scheduled annotation =
                    sweep.getAnnotation(com.ultikits.ultitools.annotations.Scheduled.class);
            assertThat(annotation).as("must be @Scheduled at all").isNotNull();
            assertThat(annotation.period()).isEqualTo(1200);
            assertThat(annotation.async()).isFalse();
            assertThat(annotation.periodKey()).as("literal, not config-bound").isEmpty();
        }

        @Test
        @DisplayName("An async write is never gated by any check: recordMessage always writes, whatever else has happened")
        void recordMessageNeverGated() throws Exception {
            UUID playerId = UUID.randomUUID();

            service.recordMessage(playerId, "first");
            service.recordMessage(playerId, "second");

            @SuppressWarnings("unchecked")
            Map<UUID, Long> lastMessageTime = (Map<UUID, Long>) ChatTestHelper.getField(service, "lastMessageTime");
            assertThat(lastMessageTime).containsKey(playerId);
        }

        /**
         * UltiKits/UltiChat#40 review, Codex round 5: the prior version of this class's self-eviction
         * read a snapshot of the retained list, decided staleness from it, and only later called
         * {@code recentMessages.remove(playerId, snapshot)}. Because the list was a single, shared,
         * mutable object, a concurrent {@code recordMessage} append (mutating that SAME object in
         * place) could land between the read and the removal; the removal's identity check still
         * matched (same object reference, just mutated), so it deleted the mapping the fresh append
         * had just been written into. This test reproduces that interleaving directly and
         * deterministically -- not via real concurrent threads, which would make a RED/GREEN proof
         * flaky and timing-dependent -- since a real race is only ever a probabilistic reproduction of
         * exactly the sequence forced here.
         */
        @Test
        @DisplayName("A stale-check's own captured snapshot, removed after a concurrent append, does not lose the append (UltiKits/UltiChat#40 review, coordinator ruling 2026-09-29)")
        void concurrentAppendDuringStaleCheckDoesNotLoseTheMessage() throws Exception {
            UUID playerId = UUID.randomUUID();
            long[] now = manualClock();
            service.recordMessage(playerId, "old");

            @SuppressWarnings("unchecked")
            Map<UUID, Object> recentMessages = (Map<UUID, Object>) ChatTestHelper.getField(service, "recentMessages");
            // Captured the way a concurrent stale-check would: BEFORE the entry ages past the
            // ceiling and BEFORE the concurrent append below.
            Object staleSnapshot = recentMessages.get(playerId);

            now[0] += 600_000L; // stale, from the snapshot's own perspective
            service.recordMessage(playerId, "fresh"); // the "owner's" own concurrent append

            // The delayed removal a racing stale-check thread would perform, using the reference it
            // captured before the append -- UltiKits/UltiChat#40 review's own description: "this
            // predicate can observe the old tail and then remove the mapping after the owner has
            // appended the new message."
            recentMessages.remove(playerId, staleSnapshot);

            assertThat(recentMessages)
                    .as("the fresh append must survive a stale check that captured an older reference first")
                    .containsKey(playerId);
        }

        @Test
        @DisplayName("The cooldown self-eviction ceiling matches anti-spam.cooldown's own @Range maximum")
        void cooldownRangeMatchesTheSelfEvictionWindow() throws Exception {
            com.ultikits.ultitools.annotations.config.Range range = ChatConfig.class
                    .getDeclaredField("antiSpamCooldown")
                    .getAnnotation(com.ultikits.ultitools.annotations.config.Range.class);
            assertThat((long) range.max() * 1000L).isEqualTo(60_000L);
        }

        @Test
        @DisplayName("The duplicate-window self-eviction ceiling matches anti-spam.duplicate-window's own @Range maximum")
        void duplicateWindowRangeMatchesTheSelfEvictionWindow() throws Exception {
            com.ultikits.ultitools.annotations.config.Range range = ChatConfig.class
                    .getDeclaredField("antiSpamDuplicateWindow")
                    .getAnnotation(com.ultikits.ultitools.annotations.config.Range.class);
            assertThat((long) range.max() * 1000L).isEqualTo(600_000L);
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
            assertThat(service.checkSpam(player, "world")).isEqualTo(ZH_COOLDOWN);
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
            assertThat(reason).isEqualTo(ZH_DUPLICATE);
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

            assertThat(service.checkSpam(player, "SPAMSPAM")).isEqualTo(ZH_COOLDOWN);

            // Cooldown off: duplicate is reported before caps.
            config.setAntiSpamCooldown(0);
            assertThat(service.checkSpam(player, "SPAMSPAM")).isEqualTo(ZH_DUPLICATE);
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
     * had no caller. The declaration is deleted, so a spam trip refuses exactly the offending
     * message and nothing more, as it always did in practice.
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
            assertThat(service.checkSpam(player, "spam")).isEqualTo(ZH_DUPLICATE);

            assertThat(service.checkSpam(player, "something else")).isNull();
        }

        @Test
        @DisplayName("After a caps refusal, a normal message is accepted once the cooldown has passed")
        void capsTripDoesNotSilenceThePlayer() {
            Player player = createPlayer();
            config.setAntiSpamCooldown(0);

            assertThat(service.checkSpam(player, "HELLOWORLD")).isEqualTo(ZH_CAPS);

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
            assertThat(methods).contains("checkSpam", "recordMessage");
            assertThat(fields).doesNotContain("mutedUntil");
            assertThat(methods).doesNotContain("mutePlayer", "checkMute");
            // No cleanup method either -- removed as dead code once quit no longer calls it
            // (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29). See SelfExpiryTests.
            assertThat(methods).doesNotContain("cleanup");
        }
    }
}
