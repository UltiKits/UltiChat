package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AutoMuteStateTest {
    private ChatConfig config;
    private AntiSpamService service;
    private UltiToolsPlugin plugin;
    private Player player;
    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() throws Exception {
        config = new ChatConfig();
        config.setAntiSpamCooldown(0);
        ChatTestHelper.setField(config, "autoMuteEnabled", true);
        ChatTestHelper.setField(config, "autoMuteDuration", 5);
        service = new AntiSpamService();
        ChatTestHelper.setField(service, "config", config);
        plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        ChatTestHelper.setField(service, "plugin", plugin);
        ChatTestHelper.setField(service, "clock", (LongSupplier) now::get);
        player = player(UUID.randomUUID());
        refresh();
    }

    private Player player(UUID id) {
        Player result = mock(Player.class);
        when(result.getUniqueId()).thenReturn(id);
        return result;
    }

    private void refresh() throws Exception {
        service.refreshAutoMuteSettings();
    }

    private void field(String name, Object value) throws Exception {
        ChatTestHelper.setField(config, name, value);
        refresh();
    }

    private String caps() {
        return service.checkSpam(player, "ALL THESE LETTERS ARE CAPITALS");
    }

    private void mute() {
        assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
        assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
        assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
    }

    @Test
    void defaultOffKeepsLegacyBehaviorAndAllocatesNoStrikeState() throws Exception {
        field("autoMuteEnabled", false);
        for (int i = 0; i < 30; i++) {
            assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
        }
        assertThat(service.checkSpam(player, "acceptable text")).isNull();
        assertThat(states()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<UUID, ?> states() throws Exception {
        Object runtime = ChatTestHelper.getField(service, "autoMuteRuntime");
        return (java.util.Map<UUID, ?>) ChatTestHelper.getField(runtime, "states");
    }

    @Test
    void fixedDeadlineRoundsUpAndMutedAttemptsDoNotExtendIt() {
        mute();
        now.addAndGet(1);
        assertThat(service.checkSpam(player, "normal one")).isEqualTo("&cYou are muted for another 5 seconds.");
        now.addAndGet(3_999);
        assertThat(service.checkSpam(player, "normal two")).isEqualTo("&cYou are muted for another 1 seconds.");
        now.addAndGet(999);
        assertThat(caps()).isEqualTo("&cYou are muted for another 1 seconds.");
        now.incrementAndGet();
        assertThat(service.checkSpam(player, "normal at equality")).isNull();
        assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
    }

    @Test
    void allThreeChecksContributeOnlyOneStrikeEachAndAcceptedTextDoesNotReset() {
        config.setAntiSpamCooldown(2);
        service.recordMessage(player.getUniqueId(), "repeat");
        service.recordMessage(player.getUniqueId(), "repeat");
        service.recordMessage(player.getUniqueId(), "repeat");
        // Both cooldown and duplicate would reject: this still contributes only one strike.
        assertThat(service.checkSpam(player, "repeat")).isEqualTo(CatalogueText.text("en", "spam_cooldown"));
        now.addAndGet(2_000);
        assertThat(service.checkSpam(player, "accepted interleaving")).isNull();
        assertThat(service.checkSpam(player, "repeat")).isEqualTo(CatalogueText.text("en", "spam_duplicate"));
        assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
    }

    @Test
    void windowIncludesExactBoundaryButDropsOlderStrikes() throws Exception {
        field("autoMuteWindow", 1);
        caps();
        now.addAndGet(1_000);
        caps();
        assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
        field("autoMuteEnabled", false);
        field("autoMuteEnabled", true);
        caps();
        now.addAndGet(1_001);
        caps();
        assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
        assertThat(service.checkSpam(player, "still below threshold")).isNull();
    }

    @Test
    void windowIsRollingRatherThanAnchoredToFirstStrike() throws Exception {
        field("autoMuteWindow", 2);
        caps();
        now.addAndGet(1_500);
        caps();
        now.addAndGet(1_000);
        caps();
        now.addAndGet(500);
        assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
    }

    @Test
    void policyChangesResetPendingStrikesButNotAlreadyImposedDeadlines() throws Exception {
        caps();
        caps();
        refresh();
        assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
        field("autoMuteDuration", 30);
        field("autoMuteThreshold", 2);
        now.addAndGet(5_000);
        assertThat(service.checkSpam(player, "original deadline")).isNull();
        assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
        assertThat(caps()).isEqualTo("&cYou have been muted for 30 seconds.");
    }

    @Test
    void numericReloadResetsOnlyPendingStrikes() throws Exception {
        caps();
        caps();
        field("autoMuteWindow", 120);
        assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
        assertThat(service.checkSpam(player, "acceptable after policy change")).isNull();
    }

    @Test
    void bothDisableSwitchesClearStateAndReenableCannotResumeIt() throws Exception {
        mute();
        field("autoMuteEnabled", false);
        assertThat(service.checkSpam(player, "allowed while off")).isNull();
        field("autoMuteEnabled", true);
        assertThat(service.checkSpam(player, "not resumed")).isNull();
        mute();
        config.setAntiSpamEnabled(false);
        refresh();
        assertThat(service.checkSpam(player, "all anti-spam off")).isNull();
        config.setAntiSpamEnabled(true);
        refresh();
        assertThat(service.checkSpam(player, "not resumed either")).isNull();
    }

    @Test
    void invalidTupleKeepsLastValidPolicyAndNeverEnablesAnInitiallyDisabledRuntime() throws Exception {
        caps();
        caps();
        field("autoMuteThreshold", 1);
        assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
        field("autoMuteThreshold", 3);
        field("autoMuteEnabled", false);
        field("autoMuteEnabled", true);
        field("autoMuteWindow", 601);
        field("autoMuteDuration", 4);
        // The invalid values cannot replace the last enabled, 3/60/5 policy.
        mute();
        AntiSpamService fresh = new AntiSpamService();
        ChatTestHelper.setField(fresh, "config", config);
        ChatTestHelper.setField(fresh, "plugin", plugin);
        fresh.refreshAutoMuteSettings();
        for (int i = 0; i < 4; i++) {
            assertThat(fresh.checkSpam(player, "ALL CAPS AGAIN")).isEqualTo(CatalogueText.text("en", "spam_caps"));
        }
        assertThat(fresh.checkSpam(player, "normal from fresh service")).isNull();
    }

    @Test
    void invalidUpperDurationOrLowerWindowAlsoKeepPolicy() throws Exception {
        field("autoMuteDuration", 601);
        field("autoMuteWindow", 0);
        mute();
    }

    @Test
    void uuidsAreIsolatedAndNewPlayerObjectWithSameUuidRetainsMute() {
        mute();
        assertThat(service.checkSpam(player(UUID.randomUUID()), "acceptable other player")).isNull();
        assertThat(service.checkSpam(player(player.getUniqueId()), "acceptable rejoined player"))
                .isEqualTo("&cYou are muted for another 5 seconds.");
    }

    @Test
    void newlyCreatedAndEnabledServiceDoesNotInheritThePreviousInstancesMute() throws Exception {
        mute();
        AntiSpamService replacement = new AntiSpamService();
        ChatTestHelper.setField(replacement, "config", config);
        ChatTestHelper.setField(replacement, "plugin", plugin);
        ChatTestHelper.setField(replacement, "clock", (LongSupplier) now::get);
        replacement.refreshAutoMuteSettings();
        assertThat(replacement.checkSpam(player, "acceptable after recreation")).isNull();
        assertThat(replacement.checkSpam(player, "FIRST CAPS"))
                .isEqualTo(CatalogueText.text("en", "spam_caps"));
    }

    @Test
    void chineseNoticeComesFromTheLiveCatalogue() {
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
        caps();
        caps();
        assertThat(caps()).isEqualTo(CatalogueText.text("zh", "spam_auto_muted").replace("{0}", "5"));
        assertThat(service.checkSpam(player, "acceptable text"))
                .isEqualTo(CatalogueText.text("zh", "spam_auto_mute_remaining").replace("{0}", "5"));
    }

    @Test
    void sweepKeepsBoundaryStrikeAndActiveMuteButDropsExpiredEntries() throws Exception {
        field("autoMuteWindow", 1);
        caps();
        now.addAndGet(1_000);
        service.sweepExpiredEntries();
        assertThat(states()).hasSize(1);
        now.incrementAndGet();
        service.sweepExpiredEntries();
        assertThat(states()).isEmpty();
        mute();
        now.addAndGet(4_999);
        service.sweepExpiredEntries();
        assertThat(states()).hasSize(1);
        now.incrementAndGet();
        service.sweepExpiredEntries();
        assertThat(states()).isEmpty();
    }

    @Test
    void sweepDiscardsPendingHistoryFromReplacedPolicyButNotActiveDeadline() throws Exception {
        caps();
        field("autoMuteWindow", 120);
        service.sweepExpiredEntries();
        assertThat(states()).isEmpty();
        mute();
        field("autoMuteWindow", 60);
        service.sweepExpiredEntries();
        assertThat(states()).hasSize(1);
    }

    @Test
    void simultaneousRefusalsImposeExactlyOneFixedMute() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> replies = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                replies.add(executor.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return caps(); }));
            }
            start.countDown();
            List<String> observed = new ArrayList<>();
            for (Future<String> reply : replies) observed.add(reply.get(10, TimeUnit.SECONDS));
            assertThat(java.util.Collections.frequency(observed, "&cYou have been muted for 5 seconds.")).isEqualTo(1);
            assertThat(java.util.Collections.frequency(observed, "&cYou are muted for another 5 seconds.")).isEqualTo(5);
            now.addAndGet(5_000);
            assertThat(service.checkSpam(player, "acceptable at original deadline")).isNull();
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void inFlightPreDisableWorkCannotResurrectMuteAfterReenable() throws Exception {
        caps();
        caps();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch capturedRuntime = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        ChatTestHelper.setField(service, "clock", (LongSupplier) () -> {
            if (first.compareAndSet(true, false)) {
                capturedRuntime.countDown();
                await(resume);
            }
            return now.get();
        });
        try {
            Future<String> pending = executor.submit(this::caps);
            assertThat(capturedRuntime.await(10, TimeUnit.SECONDS)).isTrue();
            field("autoMuteEnabled", false);
            field("autoMuteEnabled", true);
            resume.countDown();
            assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo("&cYou have been muted for 5 seconds.");
            assertThat(service.checkSpam(player, "not resurrected")).isNull();
            assertThat(states()).isEmpty();
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @Test
    void concurrentFreshStrikeSurvivesSweepUsingAnOlderTimeSample() throws Exception {
        caps();
        now.addAndGet(60_001);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch sampled = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        ChatTestHelper.setField(service, "clock", (LongSupplier) () -> {
            long sample = now.get();
            if (first.compareAndSet(true, false)) { sampled.countDown(); await(resume); }
            return sample;
        });
        try {
            Future<?> sweeping = executor.submit(service::sweepExpiredEntries);
            assertThat(sampled.await(10, TimeUnit.SECONDS)).isTrue();
            now.incrementAndGet();
            caps();
            resume.countDown();
            sweeping.get(10, TimeUnit.SECONDS);
            assertThat(states()).hasSize(1);
            assertThat(caps()).isEqualTo(CatalogueText.text("en", "spam_caps"));
            assertThat(caps()).isEqualTo("&cYou have been muted for 5 seconds.");
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
