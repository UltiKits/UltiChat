package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.commands.ChatAdminCommands;
import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.config.EntryPresence;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Pins that {@code /uchat autoreply add} and {@code setkeyword} decide whether a rule is in
 * {@code config/autoreply.yml} through the framework's own write precondition
 * ({@code saveOperatorMapEntry(EntryPresence, ...)}, UltiKits/UltiTools-Reborn#623) - one parser, on the same
 * read as the write - and not through a second parser of the module's own (UltiKits/UltiChat#51).
 * <p>
 * Everything is driven through the real command on a real file in a temporary module folder, so a run that does
 * not use the precondition fails on the file or on the rule set, not only on a reply. Every reply is compared with
 * the text this module ships, read from its catalogue.
 */
@DisplayName("Auto-reply commands use the framework's presence precondition (UltiKits/UltiChat#51)")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class AutoReplyPresencePreconditionTest {

    @TempDir
    Path moduleFolder;

    private UltiToolsPlugin plugin;
    private AutoReplyConfig live;
    private AutoReplyService service;
    private String original;
    private Object previousUltiTools;

    @BeforeEach
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    void setUp() throws Exception {
        ChatTestHelper.setUp();
        // The framework logs a refused or unreadable write through UltiTools#getLogger(), as on a running server.
        Field instance = com.ultikits.ultitools.UltiTools.class.getDeclaredField("ultiTools");
        instance.setAccessible(true);
        previousUltiTools = instance.get(null);
        com.ultikits.ultitools.UltiTools ultiTools = mock(com.ultikits.ultitools.UltiTools.class);
        doReturn(java.util.logging.Logger.getLogger("UltiTools")).when(ultiTools).getLogger();
        instance.set(null, ultiTools);
        plugin = mock(UltiChat.class, CALLS_REAL_METHODS);
        Field folder = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        folder.setAccessible(true);
        folder.set(plugin, moduleFolder.toString());

        live = new AutoReplyConfig();
        live.init(plugin);
        service = new AutoReplyService();
        ChatTestHelper.setField(service, "config", live);
        original = new String(Files.readAllBytes(configFile().toPath()), StandardCharsets.UTF_8);
        assertThat(original).as("control: the file the module writes at start holds the two shipped rules")
                .contains("    server-ip:\n").contains("    rules-info:\n");
    }

    @AfterEach
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // puts back the UltiTools instance setUp replaced
    void tearDown() throws Exception {
        Field instance = com.ultikits.ultitools.UltiTools.class.getDeclaredField("ultiTools");
        instance.setAccessible(true);
        instance.set(null, previousUltiTools);
        ChatTestHelper.tearDown();
    }

    // ============================
    // The precondition decides: a rule added by hand since the load is never replaced
    // ============================

    @Test
    @DisplayName("add of a rule the operator wrote by hand since the load is refused as existing; the file and the live rule set are unchanged")
    void addOfAHandAddedRuleIsRefused() throws Exception {
        String edited = original.replace("rules:\n", "rules:\n    welcome:\n      keyword: welcome\n      response: Written by hand\n");
        assertThat(edited).as("control: the hand-written rule is in the file").contains("response: Written by hand");
        Files.write(configFile().toPath(), edited.getBytes(StandardCharsets.UTF_8));
        Map<String, Map<String, Object>> liveBefore = new LinkedHashMap<>(live.getRules());
        CommandSender sender = mock(CommandSender.class);

        commands().onAutoReplyAdd(sender, "welcome", new String[] {"Hello", "from", "the", "command"});

        assertThat(Files.readAllBytes(configFile().toPath())).as("the hand-written rule is not replaced")
                .isEqualTo(edited.getBytes(StandardCharsets.UTF_8));
        assertThat(live.getRules()).as("nothing was added in memory").containsExactlyEntriesOf(liveBefore);
        assertThat(lastReply(sender)).isEqualTo(colour(text("autoreply_exists").replace("{0}", "welcome")));
    }

    @Test
    @DisplayName("add asks the framework to write only a rule the file does not hold, and setkeyword only a keyword the file holds")
    void addAndSetKeywordNameTheirPreconditions() throws Exception {
        AutoReplyConfig quiet = spy(live);
        org.mockito.Mockito.doNothing().when(quiet).saveOperatorMapEntry(any(EntryPresence.class), anyString(), any(String[].class));
        ChatTestHelper.setField(service, "config", quiet);

        service.addRule("greeting", "hi", "Hello there!");
        service.setKeyword("server-ip", "ip please");

        verify(quiet).saveOperatorMapEntry(EntryPresence.MUST_BE_ABSENT, "autoreply.rules", "greeting");
        verify(quiet).saveOperatorMapEntry(EntryPresence.MUST_BE_PRESENT, "autoreply.rules", "server-ip", "keyword");
    }

    @Test
    @DisplayName("setkeyword of a rule the operator deleted by hand is refused as no longer in the file; nothing is written back")
    void setKeywordOfADeletedRuleIsRefused() throws Exception {
        String rulesInfo = "    rules-info:\n      mode: contains\n      case-sensitive: false\n"
                + "      response: Please check /rules for server rules.\n      keyword: rules\n";
        assertThat(original).as("control: the rule's block is in the file").contains(rulesInfo);
        byte[] deleted = original.replace(rulesInfo, "").getBytes(StandardCharsets.UTF_8);
        Files.write(configFile().toPath(), deleted);
        CommandSender sender = mock(CommandSender.class);

        commands().onAutoReplySetKeyword(sender, "rules-info", new String[] {"rrr"});

        assertThat(Files.readAllBytes(configFile().toPath())).as("the deletion stays").isEqualTo(deleted);
        assertThat(live.getRules().get("rules-info").get("keyword")).as("memory unchanged").isEqualTo("rules");
        assertThat(lastReply(sender)).isEqualTo(colour(text("autoreply_not_in_file").replace("{0}", "rules-info")));
    }

    // ============================
    // UltiKits/UltiChat#51 P3-1: valid files are never refused
    // ============================

    @Test
    @DisplayName("add writes the rule when the autoreply section is absent from the file")
    void addIntoAFileWithoutTheSection() throws Exception {
        assertAddWritesAndKeepsEveryOtherLine("# nothing about auto-reply here\nother: 1\n", null);
    }

    @Test
    @DisplayName("add writes the rule when the file has the section but no rules key")
    void addIntoAFileWithoutTheRulesKey() throws Exception {
        assertAddWritesAndKeepsEveryOtherLine("autoreply:\n  enabled: true\n  cooldown: 10\n", null);
    }

    @Test
    @DisplayName("add writes the rule when rules: was left empty by deleting every rule")
    void addIntoRulesLeftEmpty() throws Exception {
        assertAddWritesAndKeepsEveryOtherLine(original.replaceAll("(?s)  rules:\n.*", "  rules:\n"), null);
    }

    @Test
    @DisplayName("add writes the rule when rules is the empty map {}")
    void addIntoAnEmptyRulesMap() throws Exception {
        assertAddWritesAndKeepsEveryOtherLine(original.replaceAll("(?s)  rules:\n.*", "  rules: {}\n"), "  rules: {}");
    }

    @Test
    @DisplayName("add writes the rule when the file holds only comments")
    void addIntoACommentOnlyFile() throws Exception {
        assertAddWritesAndKeepsEveryOtherLine("# all rules removed by the operator\n", null);
    }

    @Test
    @DisplayName("add writes the rule when the settings are in the flat \"autoreply.rules\": form, and the rule already there stays")
    void addIntoTheFlatForm() throws Exception {
        String flat = "\"autoreply.rules\":\n  hello:\n    keyword: hello\n    response: Hi\n";
        assertAddWritesAndKeepsEveryOtherLine(flat, null);
        assertThat(readFromDisk()).as("the rule the operator wrote stays").containsKey("hello");
    }

    @Test
    @DisplayName("add writes the rule when the rules were deleted while the server runs and the file was not reloaded")
    void addAfterTheRulesWereDeletedWithoutAReload() throws Exception {
        String fixture = original.replaceAll("(?s)  rules:\n.*", "  rules:\n");
        Files.write(configFile().toPath(), fixture.getBytes(StandardCharsets.UTF_8));
        assertThat(live.getRules()).as("control: this server still holds the shipped rules, no reload happened").containsKey("server-ip");
        CommandSender sender = mock(CommandSender.class);

        commands().onAutoReplyAdd(sender, "greeting", new String[] {"hi"});

        assertThat(lastReply(sender)).isEqualTo(colour(text("autoreply_added").replace("{0}", "greeting")));
        Map<String, Map<String, Object>> onDisk = readFromDisk();
        assertThat(onDisk).containsKey("greeting");
        assertThat(onDisk).as("the rules the operator deleted are not brought back").doesNotContainKeys("server-ip", "rules-info");
    }

    // ============================
    // A refusal rolls the live rule set back, whatever the refusal
    // ============================

    @Test
    @DisplayName("a framework refusal other than the precondition rolls the live rule set back and names the refusal")
    void aFrameworkRefusalRollsBackAndNamesTheReason() throws Exception {
        String text = original.replace("keyword: server IP", "keyword: &ip server IP");
        assertThat(text).as("control: the anchor is in the file").contains("&ip");
        Files.write(configFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
        Map<String, Map<String, Object>> liveBefore = new LinkedHashMap<>(live.getRules());
        CommandSender sender = mock(CommandSender.class);

        commands().onAutoReplyAdd(sender, "greeting", new String[] {"hi"});

        assertThat(Files.readAllBytes(configFile().toPath())).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
        assertThat(live.getRules()).as("rolled back").containsExactlyEntriesOf(liveBefore);
        assertThat(lastReply(sender)).startsWith(colour("&cRule 'greeting' was not saved: ")).contains("anchors");
    }

    @Test
    @DisplayName("a failure that is not an IOException also rolls the rule set back, on add and on setkeyword")
    void anUncheckedFailureRollsBackToo() throws Exception {
        AutoReplyConfig failing = spy(live);
        doThrow(new IllegalStateException("simulated unchecked failure")).when(failing)
                .saveOperatorMapEntry(any(EntryPresence.class), anyString(), any(String[].class));
        ChatTestHelper.setField(service, "config", failing);
        Map<String, Object> serverIp = failing.getRules().get("server-ip");
        Map<String, Object> serverIpBefore = new LinkedHashMap<>(serverIp);
        Map<String, Map<String, Object>> before = new LinkedHashMap<>(failing.getRules());

        assertThatThrownBy(() -> service.addRule("greeting", "hi", "Hello there!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.setKeyword("server-ip", "changed"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(failing.getRules()).as("add rolled back").containsExactlyEntriesOf(before);
        assertThat(serverIp).as("setkeyword rolled back").containsExactlyEntriesOf(serverIpBefore);
    }

    @Test
    @DisplayName("a null placeholder with a comment, rules: foo: ~  # placeholder, is a rule of that name in the file: add is refused and the rule set is unchanged")
    void aNullPlaceholderIsARuleInTheFile() throws Exception {
        String placeholder = original.replaceAll("(?s)  rules:\n.*", "  rules:\n    foo: ~  # placeholder\n");
        Files.write(configFile().toPath(), placeholder.getBytes(StandardCharsets.UTF_8));
        live.init(plugin);
        Map<String, Map<String, Object>> liveBefore = new LinkedHashMap<>(live.getRules());
        CommandSender sender = mock(CommandSender.class);

        commands().onAutoReplyAdd(sender, "foo", new String[] {"hi"});

        assertThat(Files.readAllBytes(configFile().toPath())).isEqualTo(placeholder.getBytes(StandardCharsets.UTF_8));
        assertThat(live.getRules()).containsExactlyEntriesOf(liveBefore);
        assertThat(lastReply(sender)).isEqualTo(colour(text("autoreply_exists").replace("{0}", "foo")));
    }

    // ============================
    // A file the framework refuses to read
    // ============================

    @Test
    @DisplayName("a file that does not parse, or whose rules are not a map: add and setkeyword write nothing, change nothing in memory, and say the file cannot be used as it is")
    void anUnreadableFileIsRefusedByTheFrameworkAndTheCommandsSayWhy() throws Exception {
        for (String broken : new String[] {"autoreply:\n  rules: [unclosed\n", "autoreply:\n  rules: not a map\n"}) {
            Files.write(configFile().toPath(), broken.getBytes(StandardCharsets.UTF_8));
            AutoReplyConfig spyConfig = spy(live);
            ChatTestHelper.setField(service, "config", spyConfig);
            Map<String, Map<String, Object>> before = new LinkedHashMap<>(spyConfig.getRules());
            CommandSender keyword = mock(CommandSender.class);
            CommandSender add = mock(CommandSender.class);

            commands().onAutoReplySetKeyword(keyword, "server-ip", new String[] {"ip"});
            commands().onAutoReplyAdd(add, "greeting", new String[] {"hello"});

            // The framework was reached, with the precondition each command names, and it refused.
            verify(spyConfig).saveOperatorMapEntry(eq(EntryPresence.MUST_BE_PRESENT), eq("autoreply.rules"), eq("server-ip"), eq("keyword"));
            verify(spyConfig).saveOperatorMapEntry(eq(EntryPresence.MUST_BE_ABSENT), eq("autoreply.rules"), eq("greeting"));
            assertThat(new String(Files.readAllBytes(configFile().toPath()), StandardCharsets.UTF_8)).as(broken).isEqualTo(broken);
            assertThat(spyConfig.getRules()).as(broken).containsExactlyEntriesOf(before);
            assertThat(lastReply(keyword)).as(broken).startsWith(colour("&cRule 'server-ip' was not saved: "))
                    .contains("cannot be read").doesNotContain("no longer in");
            assertThat(lastReply(add)).as(broken).startsWith(colour("&cRule 'greeting' was not saved: "))
                    .contains("cannot be read").doesNotContain("already exists");
        }
    }

    @Test
    @DisplayName("the whole file deleted while running: add and setkeyword write nothing and the file stays absent")
    void aDeletedFileIsNeverRecreated() throws Exception {
        Files.delete(configFile().toPath());
        CommandSender keyword = mock(CommandSender.class);
        CommandSender add = mock(CommandSender.class);

        commands().onAutoReplySetKeyword(keyword, "server-ip", new String[] {"ip"});
        commands().onAutoReplyAdd(add, "greeting", new String[] {"hello"});

        assertThat(configFile()).as("the file the operator deleted is not recreated").doesNotExist();
        assertThat(live.getRules()).doesNotContainKey("greeting");
        assertThat(live.getRules().get("server-ip").get("keyword")).isEqualTo("server IP");
        assertThat(lastReply(keyword)).startsWith(colour("&cRule 'server-ip' "));
        assertThat(lastReply(add)).startsWith(colour("&cRule 'greeting' "));
    }

    // ============================
    // Helpers
    // ============================

    /**
     * Writes {@code fixture} as the file (no reload: an operator who edited it while the server runs), runs
     * {@code add greeting hi}, and asserts the rule was added, the reply says so, and every line of the fixture but
     * {@code lineThatMayChange} is still there, in order.
     */
    private void assertAddWritesAndKeepsEveryOtherLine(String fixture, String lineThatMayChange) throws Exception {
        Files.write(configFile().toPath(), fixture.getBytes(StandardCharsets.UTF_8));
        CommandSender sender = mock(CommandSender.class);

        commands().onAutoReplyAdd(sender, "greeting", new String[] {"hi"});

        assertThat(lastReply(sender)).isEqualTo(colour(text("autoreply_added").replace("{0}", "greeting")));
        String after = new String(Files.readAllBytes(configFile().toPath()), StandardCharsets.UTF_8);
        assertThat(readFromDisk()).containsKey("greeting");
        assertThat(readFromDisk().get("greeting").get("response")).isEqualTo("hi");
        assertThat(live.getRules()).containsKey("greeting");
        List<String> afterLines = Arrays.asList(after.split("\n", -1));
        int at = 0;
        for (String line : fixture.split("\n", -1)) {
            if (line.equals(lineThatMayChange)) {
                continue;
            }
            int found = afterLines.subList(at, afterLines.size()).indexOf(line);
            assertThat(found).as("line kept, in order: " + line).isGreaterThanOrEqualTo(0);
            at += found + 1;
        }
    }

    private ChatAdminCommands commands() {
        org.mockito.Mockito.doAnswer(CatalogueText.answer("en")).when(plugin).i18n(anyString());
        return new ChatAdminCommands(plugin, service);
    }

    private static String text(String key) {
        return CatalogueText.text("en", key);
    }

    private static String colour(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String lastReply(CommandSender sender) {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(sent.capture());
        return sent.getValue();
    }

    private File configFile() {
        return moduleFolder.resolve("config").resolve("autoreply.yml").toFile();
    }

    /** What {@code /uchat reload} would see: a config entity initialised from the file, with no memory carried over. */
    private Map<String, Map<String, Object>> readFromDisk() throws Exception {
        AutoReplyConfig fresh = new AutoReplyConfig();
        fresh.init(plugin);
        return fresh.getRules();
    }
}
