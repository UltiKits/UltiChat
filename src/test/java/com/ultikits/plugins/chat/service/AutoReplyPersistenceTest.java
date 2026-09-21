package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
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
 * "the rule is not there" assertion below is paired with a positive control asserting that the same
 * read does see the two shipped default rules, so an absent key can never be the reader failing to
 * read the file rather than the rule failing to be written.
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
        assertThat(readFromDisk()).doesNotContainKey("greeting");

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

        verify(quiet, never()).save();

        service.addRule("greeting", "hi", "Hello there!");
        verify(quiet).save();
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

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
