package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Proves that a rule added, re-keyed or removed through {@link AutoReplyService} reaches
 * {@code config/autoreply.yml} immediately, so it survives {@code /uchat reload} and
 * {@code /ul reload} (both of which re-{@code init()} the entity straight from disk), and that a
 * failed write leaves no half-applied rule behind (UltiKits/UltiChat#17).
 * <p>
 * The reload instrument is a second {@link AutoReplyConfig} instance {@code init()}-ed against the
 * same folder, which is what {@code ConfigManager#reloadConfigs} does to the live instance. Every
 * {@code readFromDisk()} assertion that a rule is absent is paired, in the same test, with a
 * positive control asserting that the same read does see the two shipped default rules, so an
 * absent key can never be the reader failing to read the file rather than the rule failing to be
 * written. The in-memory assertions carry their own controls instead: each rollback test re-runs the
 * same call with the save succeeding, so a restored state cannot be mistaken for a call that never
 * did anything.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Auto-reply rule mutations are persisted (UltiKits/UltiChat#17)")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class AutoReplyPersistenceTest {

    @TempDir
    Path moduleFolder;

    private UltiToolsPlugin plugin;
    private AutoReplyConfig live;
    private AutoReplyService service;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();
        plugin = mock(UltiChat.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());

        live = new AutoReplyConfig();
        live.init(plugin);

        service = new AutoReplyService();
        ChatTestHelper.setField(service, "config", live);
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    // ============================
    // The rule reaches disk
    // ============================

    @Test
    @DisplayName("An added rule is on disk, so a reload from disk still has it")
    void addedRuleSurvivesAReloadFromDisk() throws Exception {
        assertThat(configFile()).exists();

        Map<String, Map<String, Object>> before = readFromDisk();
        assertThat(before).containsKeys("server-ip", "rules-info");
        assertThat(before).doesNotContainKey("greeting");

        service.addRule("greeting", "hi", "Hello there!");

        Map<String, Map<String, Object>> after = readFromDisk();
        assertThat(after).containsKeys("server-ip", "rules-info");
        assertThat(after).containsKey("greeting");

        Map<String, Object> reloaded = after.get("greeting");
        assertThat(reloaded.get("keyword")).isEqualTo("hi");
        assertThat(reloaded.get("response")).isEqualTo("Hello there!");
        assertThat(reloaded.get("mode")).isEqualTo("contains");
        assertThat(reloaded.get("case-sensitive")).isEqualTo(false);
    }

    @Test
    @DisplayName("A changed keyword is on disk, and only the keyword changed")
    void changedKeywordSurvivesAReloadFromDisk() throws Exception {
        service.addRule("greeting", "hi", "Hello there!");
        assertThat(readFromDisk().get("greeting").get("keyword")).isEqualTo("hi");

        service.setKeyword("greeting", "good morning");

        Map<String, Object> reloaded = readFromDisk().get("greeting");
        assertThat(reloaded.get("keyword")).isEqualTo("good morning");
        assertThat(reloaded.get("response")).isEqualTo("Hello there!");
        assertThat(reloaded.get("mode")).isEqualTo("contains");
        assertThat(reloaded.get("case-sensitive")).isEqualTo(false);
    }

    @Test
    @DisplayName("A removed rule is gone from disk, and the other rules are not")
    void removedRuleStaysRemovedAfterAReloadFromDisk() throws Exception {
        assertThat(readFromDisk()).containsKeys("server-ip", "rules-info");

        service.removeRule("server-ip");

        Map<String, Map<String, Object>> after = readFromDisk();
        assertThat(after).doesNotContainKey("server-ip");
        assertThat(after).containsKey("rules-info");
    }

    // ============================
    // A failed write leaves nothing half-applied
    // ============================

    @Test
    @DisplayName("A failed save on add rolls the rule set back and reports the failure")
    void failedSaveOnAddRollsBackAndReports() throws Exception {
        AutoReplyConfig failing = failingConfig();
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>(failing.getRules());

        assertThatThrownBy(() -> service.addRule("greeting", "hi", "Hello there!"))
                .isInstanceOf(IOException.class)
                .hasMessage("simulated write failure");

        verify(failing).save();
        assertThat(failing.getRules()).containsExactlyEntriesOf(snapshot);
        Map<String, Map<String, Object>> onDisk = readFromDisk();
        assertThat(onDisk).containsKeys("server-ip", "rules-info");
        assertThat(onDisk).doesNotContainKey("greeting");

        doNothing().when(failing).save();
        service.addRule("greeting", "hi", "Hello there!");
        assertThat(failing.getRules()).containsKey("greeting");
    }

    @Test
    @DisplayName("A failed save on setkeyword restores the exact previous keyword")
    void failedSaveOnSetKeywordRestoresThePreviousKeyword() throws Exception {
        AutoReplyConfig failing = failingConfig();
        Map<String, Object> rule = failing.getRules().get("server-ip");
        Map<String, Object> ruleSnapshot = new LinkedHashMap<>(rule);

        assertThatThrownBy(() -> service.setKeyword("server-ip", "changed"))
                .isInstanceOf(IOException.class)
                .hasMessage("simulated write failure");

        verify(failing).save();
        assertThat(rule).containsExactlyEntriesOf(ruleSnapshot);
        assertThat(rule.get("keyword")).isEqualTo("server IP");

        doNothing().when(failing).save();
        service.setKeyword("server-ip", "changed");
        assertThat(failing.getRules().get("server-ip").get("keyword")).isEqualTo("changed");
    }

    @Test
    @DisplayName("A failed save on setkeyword leaves a rule that had no keyword without one")
    void failedSaveOnSetKeywordLeavesAnAbsentKeywordAbsent() throws Exception {
        AutoReplyConfig failing = failingConfig();
        Map<String, Object> bare = new LinkedHashMap<>();
        bare.put("response", "Nothing to match on");
        failing.getRules().put("bare", bare);

        assertThatThrownBy(() -> service.setKeyword("bare", "anything"))
                .isInstanceOf(IOException.class)
                .hasMessage("simulated write failure");

        verify(failing).save();
        assertThat(bare).doesNotContainKey("keyword");
        assertThat(bare).containsExactly(org.assertj.core.api.Assertions.entry("response", "Nothing to match on"));

        doNothing().when(failing).save();
        service.setKeyword("bare", "anything");
        assertThat(bare.get("keyword")).isEqualTo("anything");
    }

    @Test
    @DisplayName("A failed save on remove puts the same rule instance back in the same position")
    void failedSaveOnRemoveRestoresTheRuleSet() throws Exception {
        AutoReplyConfig failing = failingConfig();
        Map<String, Object> serverIpInstance = failing.getRules().get("server-ip");
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>(failing.getRules());

        assertThatThrownBy(() -> service.removeRule("server-ip"))
                .isInstanceOf(IOException.class)
                .hasMessage("simulated write failure");

        verify(failing).save();
        assertThat(failing.getRules()).containsExactlyEntriesOf(snapshot);
        assertThat(failing.getRules().get("server-ip")).isSameAs(serverIpInstance);

        doNothing().when(failing).save();
        service.removeRule("server-ip");
        assertThat(failing.getRules()).doesNotContainKey("server-ip");
    }

    // ============================
    // Nothing is written when nothing changed
    // ============================

    @Test
    @DisplayName("A call that changes no rule writes nothing")
    void callsThatChangeNothingDoNotWrite() throws Exception {
        AutoReplyConfig quiet = spy(live);
        doNothing().when(quiet).save();
        ChatTestHelper.setField(service, "config", quiet);

        service.addRule("server-ip", "anything", "Refused, the name is taken");
        service.setKeyword("no-such-rule", "anything");
        service.removeRule("no-such-rule");
        // The rule exists and the keyword is the one it already has: still no change, still no write.
        assertThat(quiet.getRules().get("server-ip").get("keyword")).isEqualTo("server IP");
        service.setKeyword("server-ip", "server IP");

        verify(quiet, never()).save();

        service.addRule("greeting", "hi", "Hello there!");
        verify(quiet).save();
    }

    // ============================
    // A command writes the rule it names, and only that rule (maintainer decision 2026-10-04, "what code may
    // write, by file type": /autoreply writes only that rule). This replaces the 17-50 tests that pinned the
    // framework's #527 "a save warns and overwrites" warning, which the 2026-10-04 table superseded
    // (UltiKits/UltiChat#50).
    // ============================

    @Test
    @DisplayName("A rule the operator edited by hand since the load is replaced by the command's change to it, with no warning")
    void aHandEditedRuleIsReplacedByTheCommandThatNamesIt() throws Exception {
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        org.mockito.Mockito.doAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en")).when(plugin).i18n(anyString());
        List<String> frameworkWarnings = captureFrameworkWarnings();
        try {
            editAKeyBehindTheFrameworksBack();

            service.setKeyword("server-ip", "ip please");
        } finally {
            releaseFrameworkWarnings();
        }

        // The operator asked for this rule's keyword: the command's value is written for it.
        Map<String, Object> onDisk = readFromDisk().get("server-ip");
        assertThat(onDisk.get("keyword")).isEqualTo("ip please");
        assertThat(onDisk.get("response")).isEqualTo("Server address: play.example.com");
        assertThat(readFromDisk()).containsKey("rules-info");
        assertThat(frameworkWarnings).as("an explicit operator change is not a warning").isEmpty();
        verify(logger, never()).warn(anyString());
    }

    @Test
    @DisplayName("A rule the operator added by hand since the load stays when a command adds another -- quietly, and the same capture does see a framework warning")
    void aHandAddedRuleStaysQuietly() throws Exception {
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        org.mockito.Mockito.doAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en")).when(plugin).i18n(anyString());
        List<String> frameworkWarnings = captureFrameworkWarnings();
        try {
            addARuleBehindTheFrameworksBack();

            service.addRule("greeting", "hi", "Hello there!");

            Map<String, Map<String, Object>> onDisk = readFromDisk();
            assertThat(onDisk).containsKeys("server-ip", "rules-info", "custom", "greeting");
            assertThat(onDisk.get("custom").get("response")).isEqualTo("Written by hand");
            assertThat(frameworkWarnings).isEmpty();
            verify(logger, never()).warn(anyString());

            // Control: the capture can see a framework warning -- a module change to a value the operator has
            // since edited on disk is not written by the module's own save(), and the framework says so.
            editAKeyBehindTheFrameworksBack();
            live.getRules().get("server-ip").put("keyword", "changed by the module");
            live.save();
            assertThat(frameworkWarnings).hasSize(1);
        } finally {
            releaseFrameworkWarnings();
        }
    }

    // ============================
    // The rule map is not read while it is being rebuilt
    // ============================

    @Test
    @DisplayName("findMatch and a rollback hold the same monitor, so no chat thread sees a half-restored rule set")
    void findMatchAndRollbackShareOneMonitor() throws Exception {
        AutoReplyConfig failing = failingConfig();
        Object rulesLock = ChatTestHelper.getField(service, "rulesLock");
        assertThat(rulesLock).isNotNull();

        assertBlockedWhileLockHeld(rulesLock, () -> service.findMatch("what is the server IP?"));
        assertBlockedWhileLockHeld(rulesLock, () -> service.removeRule("server-ip"));

        // The rollback still happened once the lock was free, so the blocking above did not
        // silently swallow the call.
        assertThat(failing.getRules()).containsKey("server-ip");
    }

    @Test
    @DisplayName("The rollback itself waits for the monitor, not just the forward mutation")
    void theRollbackWaitsForTheMonitor() throws Exception {
        CountDownLatch insideSave = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        AutoReplyConfig failing = spy(live);
        doAnswer(invocation -> {
            insideSave.countDown();
            assertThat(releaseSave.await(5, TimeUnit.SECONDS)).isTrue();
            throw new IOException("simulated write failure");
        }).when(failing).save();
        ChatTestHelper.setField(service, "config", failing);

        Object rulesLock = ChatTestHelper.getField(service, "rulesLock");
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread command = new Thread(() -> {
            try {
                service.removeRule("server-ip");
            } catch (Exception expected) {
                // The failing save rethrows after the rollback; reaching here means the rollback ran.
            }
            finished.set(true);
        }, "ultichat-rollback-probe");
        command.start();

        // Wait until the forward mutation is done and the command thread is parked inside save(),
        // so the monitor is free and the only acquisition left is the rollback's.
        assertThat(insideSave.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(failing.getRules()).doesNotContainKey("server-ip");

        synchronized (rulesLock) {
            releaseSave.countDown();
            Thread.sleep(300);
            assertThat(finished.get()).isFalse();
        }

        command.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(finished.get()).isTrue();
        assertThat(failing.getRules()).containsKey("server-ip");
    }

    // ============================
    // Helpers
    // ============================

    /**
     * A spy over the live config whose {@code save()} always fails, standing in for a read-only
     * config directory or a full disk.
     */
    private AutoReplyConfig failingConfig() throws Exception {
        AutoReplyConfig failing = spy(live);
        doThrow(new IOException("simulated write failure")).when(failing).save();
        ChatTestHelper.setField(service, "config", failing);
        return failing;
    }

    /**
     * What {@code /uchat reload} and {@code /ul reload} would see: a config entity initialised
     * from the file on disk, with no in-memory state carried over.
     */
    private Map<String, Map<String, Object>> readFromDisk() throws Exception {
        AutoReplyConfig fresh = new AutoReplyConfig();
        fresh.init(plugin);
        return fresh.getRules();
    }

    private File configFile() {
        return moduleFolder.resolve("config").resolve("autoreply.yml").toFile();
    }

    /**
     * Changes a rule's keyword straight in the file, the way an admin editing it over SSH would --
     * behind the framework's back, so the file no longer holds what the framework last wrote.
     */
    private void editAKeyBehindTheFrameworksBack() throws Exception {
        String text = new String(Files.readAllBytes(configFile().toPath()), StandardCharsets.UTF_8);
        assertThat(text).as("control: the keyword this edit replaces is in the file").contains("server IP");
        Files.write(configFile().toPath(), text.replace("server IP", "server address").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Adds a rule {@code custom} straight to the file, the way an admin editing it over SSH would, behind the
     * framework's back.
     */
    private void addARuleBehindTheFrameworksBack() throws Exception {
        String text = new String(Files.readAllBytes(configFile().toPath()), StandardCharsets.UTF_8);
        String anchor = "rules:\n";
        assertThat(text).as("control: the rules section is in the file").contains(anchor);
        String rule = "    custom:\n      keyword: custom\n      response: Written by hand\n";
        Files.write(configFile().toPath(), text.replace(anchor, anchor + rule).getBytes(StandardCharsets.UTF_8));
        assertThat(readFromDisk()).as("control: the hand-added rule parses").containsKey("custom");
    }

    private java.util.logging.Handler frameworkHandler;

    /** Collects the framework's own warnings for this entity class, which the framework logs itself. */
    private List<String> captureFrameworkWarnings() {
        List<String> messages = new ArrayList<>();
        frameworkHandler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    messages.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
                // nothing is buffered
            }

            @Override
            public void close() {
                // nothing is held
            }
        };
        java.util.logging.Logger.getLogger(com.ultikits.ultitools.abstracts.AbstractConfigEntity.class.getName())
                .addHandler(frameworkHandler);
        return messages;
    }

    private void releaseFrameworkWarnings() {
        java.util.logging.Logger.getLogger(com.ultikits.ultitools.abstracts.AbstractConfigEntity.class.getName())
                .removeHandler(frameworkHandler);
    }

    /**
     * Runs {@code body} on another thread while this thread holds {@code lock}, and asserts it does
     * not get past the lock -- then releases the lock and asserts it completes. The "did not
     * complete" half cannot be flaky: the lock is held for the whole of that wait, so a correct
     * implementation cannot finish, and an implementation that does not take the lock finishes
     * immediately.
     */
    private void assertBlockedWhileLockHeld(Object lock, ThrowingRunnable body) throws Exception {
        AtomicBoolean finished = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            started.countDown();
            try {
                body.run();
                finished.set(true);
            } catch (Exception expected) {
                // A failing save rethrows; reaching the throw still means the lock was acquired.
                finished.set(true);
            }
        }, "ultichat-lock-probe");

        synchronized (lock) {
            reader.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            assertThat(finished.get()).isFalse();
        }

        reader.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(finished.get()).isTrue();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
