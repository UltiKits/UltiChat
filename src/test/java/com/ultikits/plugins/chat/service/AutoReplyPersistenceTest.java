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
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
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
    // An overwrite of an operator's hand edit is not silent
    // ============================

    @Test
    @DisplayName("Saving over a file that was edited on disk logs a warning naming the file")
    void savingOverAnOperatorEditIsNotSilent() throws Exception {
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        // The warning comes from the language file; the assertions quote its English text.
        org.mockito.Mockito.doAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en")).when(plugin).i18n(anyString());
        editTheFileBehindTheFrameworksBack();

        service.addRule("greeting", "hi", "Hello there!");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(captor.capture());
        assertThat(captor.getValue())
                .contains("autoreply.yml")
                .contains("changed or removed on disk");
        // The overwrite itself is still the contract, as it is for the framework's shutdown save.
        assertThat(readFromDisk()).containsKey("greeting");
    }

    @Test
    @DisplayName("Under language: zh the overwrite warning is the Chinese catalogue text")
    void overwriteWarningFollowsTheLanguageSetting() throws Exception {
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        org.mockito.Mockito.doAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("zh")).when(plugin).i18n(anyString());
        String template = com.ultikits.plugins.chat.i18n.CatalogueText.text("zh", "log_autoreply_file_overwritten");
        editTheFileBehindTheFrameworksBack();

        service.addRule("greeting", "hi", "Hello there!");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(captor.capture());
        String prefix = template.substring(0, template.indexOf("{FILE}"));
        String suffix = template.substring(template.indexOf("{FILE}") + "{FILE}".length());
        assertThat(captor.getValue()).startsWith(prefix).endsWith(suffix).contains("autoreply.yml");
    }

    @Test
    @DisplayName("Saving over an untouched file logs nothing -- and the same logger does see a warning once the file is touched")
    void savingOverAnUntouchedFileIsQuiet() throws Exception {
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        // The warning comes from the language file; the assertions quote its English text.
        org.mockito.Mockito.doAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en")).when(plugin).i18n(anyString());

        service.addRule("greeting", "hi", "Hello there!");

        verify(logger, never()).warn(anyString());

        // Control: the verification above could pass because nothing can ever reach this logger.
        // Edit the file and save again; the same mock must now see exactly one warning.
        editTheFileBehindTheFrameworksBack();
        service.addRule("second", "yo", "Hello again!");
        verify(logger).warn(anyString());
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
    // The read and the write are one step, as the framework's own shutdown save makes them
    // ============================

    @Test
    @DisplayName("The entity's monitor is already held when save() is entered, so the operator-edit read and the write cannot be split")
    void theOperatorEditReadAndTheSaveShareTheEntityMonitor() throws Exception {
        AutoReplyConfig probe = spy(live);
        AtomicBoolean heldWhenSaveWasEntered = new AtomicBoolean(false);
        doAnswer(invocation -> {
            heldWhenSaveWasEntered.set(Thread.holdsLock(probe));
            return invocation.callRealMethod();
        }).when(probe).save();
        ChatTestHelper.setField(service, "config", probe);

        service.addRule("greeting", "hi", "Hello there!");

        // save() takes this monitor itself, so a probe INSIDE the real method would read true
        // either way. This probe sits at the boundary, before the real method runs, which is the
        // only place the two revisions differ: without the synchronized (config) around the
        // fingerprint read and the write, saveOrRestore's own frame holds nothing here.
        assertThat(heldWhenSaveWasEntered.get()).isTrue();

        // Controls: the probe ran, and the real save ran behind it -- so the assertion above is
        // not passing because save() was never reached.
        verify(probe).save();
        assertThat(readFromDisk()).containsKey("greeting");
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
     * Appends a comment line straight to the file, the way an admin editing it over SSH would --
     * behind the framework's back, so its snapshot no longer matches what is on disk.
     */
    private void editTheFileBehindTheFrameworksBack() throws Exception {
        Files.write(configFile().toPath(),
                "\n# edited on disk while the server was running\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
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
