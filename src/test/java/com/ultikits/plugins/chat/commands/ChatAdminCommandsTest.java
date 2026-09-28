package com.ultikits.plugins.chat.commands;

import com.ultikits.plugins.chat.service.AutoReplyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ChatAdminCommands — reload, autoreply list/add/remove.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ChatAdminCommands Tests")
class ChatAdminCommandsTest {

    private UltiToolsPlugin mockPlugin;
    private AutoReplyService mockAutoReplyService;
    private ChatAdminCommands commands;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(UltiToolsPlugin.class);
        mockAutoReplyService = mock(AutoReplyService.class);
        when(mockPlugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockPlugin.i18n("autoreply_added")).thenReturn("Rule '{0}' added.");
        when(mockPlugin.i18n("autoreply_removed")).thenReturn("Rule '{0}' removed.");
        when(mockPlugin.i18n("autoreply_not_found")).thenReturn("Rule '{0}' not found.");
        when(mockPlugin.i18n("autoreply_list_entry")).thenReturn("{0}: {1} [{2}] -> {3}");
        when(mockPlugin.i18n("autoreply_keyword_set")).thenReturn("Rule '{0}' keyword set to '{1}'.");

        commands = new ChatAdminCommands(mockPlugin, mockAutoReplyService);
    }

    private void assertSentMessageContaining(CommandSender sender, String substring) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(msg -> msg.contains(substring));
    }

    private void assertNoSentMessageContaining(CommandSender sender, String substring) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(msg -> msg.contains(substring));
    }

    // ==================== Reload Tests ====================

    @Nested
    @DisplayName("Reload Command")
    class ReloadTests {

        @Test
        @DisplayName("Should call reloadConfigs on plugin")
        void shouldCallReloadConfigs() {
            CommandSender sender = mock(CommandSender.class);

            commands.onReload(sender);

            verify(mockPlugin).reloadSelf();
        }

        @Test
        @DisplayName("Should send reload success message")
        void shouldSendReloadMessage() {
            CommandSender sender = mock(CommandSender.class);

            commands.onReload(sender);

            assertSentMessageContaining(sender, "config_reloaded");
        }
    }

    // ==================== AutoReply List Tests ====================

    @Nested
    @DisplayName("AutoReply List Command")
    class AutoReplyListTests {

        @Test
        @DisplayName("Should show header and empty message when no rules")
        void shouldShowEmptyMessage() {
            CommandSender sender = mock(CommandSender.class);
            when(mockAutoReplyService.getRules()).thenReturn(Collections.<String, Map<String, Object>>emptyMap());

            commands.onAutoReplyList(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, times(2)).sendMessage(captor.capture());
            assertThat(captor.getAllValues().get(0)).contains("autoreply_list_header");
            assertThat(captor.getAllValues().get(1)).contains("autoreply_list_empty");
        }

        @Test
        @DisplayName("Should list all rules with details")
        void shouldListAllRules() {
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            Map<String, Object> rule1 = new HashMap<>();
            rule1.put("keyword", "hello");
            rule1.put("mode", "contains");
            rules.put("greeting", rule1);

            Map<String, Object> rule2 = new HashMap<>();
            rule2.put("keyword", "rules");
            rule2.put("mode", "exact");
            rules.put("rules-info", rule2);

            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplyList(sender);

            // header + 2 entries = 3 messages
            verify(sender, times(3)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show header even with rules")
        void shouldShowHeader() {
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new HashMap<>();
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "test");
            rule.put("mode", "contains");
            rules.put("test-rule", rule);
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplyList(sender);

            assertSentMessageContaining(sender, "autoreply_list_header");
        }

        @Test
        @DisplayName("Should include each rule's response in the listing")
        void shouldIncludeResponseInListing() {
            // `autoreply list` used to render only name -> keyword [mode] and never the
            // response, which also made silent-overwrite corruption harder to notice since the
            // field that visibly changed was the keyword rather than the reply.
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new HashMap<>();
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "server IP");
            rule.put("mode", "contains");
            rule.put("response", "Server address: play.example.com");
            rules.put("server-ip", rule);
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplyList(sender);

            assertSentMessageContaining(sender, "Server address: play.example.com");
        }
    }

    // ==================== AutoReply Add Tests ====================

    @Nested
    @DisplayName("AutoReply Add Command")
    class AutoReplyAddTests {

        @Test
        @DisplayName("Should add rule and send confirmation")
        void shouldAddRule() throws Exception {
            CommandSender sender = mock(CommandSender.class);

            commands.onAutoReplyAdd(sender, "greet", "Hello there!");

            verify(mockAutoReplyService).addRule("greet", "greet", "Hello there!");
            assertSentMessageContaining(sender, "greet");
        }

        @Test
        @DisplayName("Should repair a null-valued rule entry instead of reporting it already exists")
        void shouldRepairANullValuedRuleEntry() throws Exception {
            // Malformed YAML such as `broken:` leaves a name mapped to null. The command layer's own
            // containsKey guard mirrors the service's, so it must apply the same non-null check or
            // it blocks the repair before addRule is ever called.
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("broken", null);
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplyAdd(sender, "broken", "Repaired response");

            verify(mockAutoReplyService).addRule("broken", "broken", "Repaired response");
            assertSentMessageContaining(sender, "broken");
        }

        @Test
        @DisplayName("Should include rule name in success message")
        void shouldIncludeNameInMessage() {
            CommandSender sender = mock(CommandSender.class);

            commands.onAutoReplyAdd(sender, "my-rule", "response");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("my-rule");
        }
    }

    // ==================== AutoReply Remove Tests ====================

    @Nested
    @DisplayName("AutoReply Remove Command")
    class AutoReplyRemoveTests {

        @Test
        @DisplayName("Should remove existing rule")
        void shouldRemoveExistingRule() throws Exception {
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("greet", new HashMap<String, Object>());
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplyRemove(sender, "greet");

            verify(mockAutoReplyService).removeRule("greet");
            assertSentMessageContaining(sender, "greet");
        }

        @Test
        @DisplayName("Should send not-found message for missing rule")
        void shouldSendNotFoundForMissing() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            when(mockAutoReplyService.getRules()).thenReturn(Collections.<String, Map<String, Object>>emptyMap());

            commands.onAutoReplyRemove(sender, "nonexistent");

            verify(mockAutoReplyService, never()).removeRule(anyString());
            assertSentMessageContaining(sender, "nonexistent");
        }

        @Test
        @DisplayName("Should not call removeRule when rule not found")
        void shouldNotRemoveWhenNotFound() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            when(mockAutoReplyService.getRules()).thenReturn(Collections.<String, Map<String, Object>>emptyMap());

            commands.onAutoReplyRemove(sender, "missing");

            verify(mockAutoReplyService, never()).removeRule(anyString());
        }
    }

    // ==================== AutoReply Set Keyword Tests ====================

    @Nested
    @DisplayName("AutoReply Set Keyword Command")
    class AutoReplySetKeywordTests {

        @Test
        @DisplayName("Should set the keyword on an existing rule and send confirmation")
        void shouldSetKeywordOnExistingRule() throws Exception {
            // `autoreply add` still forces keyword = name with no parameter to say otherwise.
            // This closes that gap the smallest way that avoids an ambiguous overload against
            // the existing 2-arg `add` format -- a distinct `setkeyword` verb, unambiguous
            // under the framework's scored format matching since its second literal token
            // ("setkeyword") never matches "add"'s.
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("server-ip", new HashMap<String, Object>());
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplySetKeyword(sender, "server-ip", "server IP");

            verify(mockAutoReplyService).setKeyword("server-ip", "server IP");
            assertSentMessageContaining(sender, "server-ip");
        }

        @Test
        @DisplayName("Should send not-found message and not call setKeyword for a missing rule")
        void shouldSendNotFoundForMissingRule() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            when(mockAutoReplyService.getRules()).thenReturn(Collections.<String, Map<String, Object>>emptyMap());

            commands.onAutoReplySetKeyword(sender, "nonexistent", "anything");

            verify(mockAutoReplyService, never()).setKeyword(anyString(), anyString());
            assertSentMessageContaining(sender, "nonexistent");
        }

        @Test
        @DisplayName("Should send not-found, not a false success, for a null-valued rule entry")
        void shouldSendNotFoundForANullValuedRuleEntry() throws Exception {
            // Malformed YAML such as `broken:` makes containsKey(name) true even though the
            // value is null. This guard only checked key presence, so it would call the
            // now-null-safe setKeyword no-op and still report success -- a false positive. Must
            // check the value.
            CommandSender sender = mock(CommandSender.class);

            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("broken", null);
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplySetKeyword(sender, "broken", "anything");

            verify(mockAutoReplyService, never()).setKeyword(anyString(), anyString());
            assertSentMessageContaining(sender, "broken");
        }
    }

    // ==================== Save Failure Tests ====================

    @Nested
    @DisplayName("Save Failure Reporting (UltiKits/UltiChat#17)")
    class SaveFailureTests {

        private PluginLogger logger;

        @BeforeEach
        void stubFailureMessageAndLogger() {
            logger = mock(PluginLogger.class);
            when(mockPlugin.i18n("autoreply_save_failed")).thenReturn("Rule '{0}' was NOT saved.");
            // The console line comes from the language file; the assertions quote its English text.
            when(mockPlugin.i18n("log_autoreply_save_failed"))
                    .thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
            when(mockPlugin.getLogger()).thenReturn(logger);
        }

        @Test
        @DisplayName("add reports the failure and does not report success")
        void addReportsTheFailure() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            doThrow(new IOException("simulated write failure"))
                    .when(mockAutoReplyService).addRule("greet", "greet", "Hello there!");

            commands.onAutoReplyAdd(sender, "greet", "Hello there!");

            assertSentMessageContaining(sender, "was NOT saved");
            assertNoSentMessageContaining(sender, "added");
            // The cause is logged WITH its throwable, so the stack trace survives and
            // SystemLogHandler can collect the report -- a message-only SEVERE record cannot.
            ArgumentCaptor<String> logged = ArgumentCaptor.forClass(String.class);
            verify(logger).error(any(IOException.class), logged.capture());
            assertThat(logged.getValue()).contains("Could not save config/autoreply.yml");
        }

        @Test
        @DisplayName("setkeyword reports the failure and does not report success")
        void setKeywordReportsTheFailure() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("server-ip", new HashMap<String, Object>());
            when(mockAutoReplyService.getRules()).thenReturn(rules);
            doThrow(new IOException("simulated write failure"))
                    .when(mockAutoReplyService).setKeyword("server-ip", "server IP");

            commands.onAutoReplySetKeyword(sender, "server-ip", "server IP");

            assertSentMessageContaining(sender, "was NOT saved");
            assertNoSentMessageContaining(sender, "keyword set to");
        }

        @Test
        @DisplayName("remove reports the failure and does not report success")
        void removeReportsTheFailure() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("greet", new HashMap<String, Object>());
            when(mockAutoReplyService.getRules()).thenReturn(rules);
            doThrow(new IOException("simulated write failure"))
                    .when(mockAutoReplyService).removeRule("greet");

            commands.onAutoReplyRemove(sender, "greet");

            assertSentMessageContaining(sender, "was NOT saved");
            assertNoSentMessageContaining(sender, "removed");
        }

        @Test
        @DisplayName("A save that succeeds still reports success -- the control for the three above")
        void aSucceedingSaveStillReportsSuccess() throws Exception {
            CommandSender sender = mock(CommandSender.class);

            commands.onAutoReplyAdd(sender, "greet", "Hello there!");

            verify(mockAutoReplyService).addRule("greet", "greet", "Hello there!");
            assertSentMessageContaining(sender, "added");
            assertNoSentMessageContaining(sender, "was NOT saved");
            // Control for the verify(logger) assertions above: a succeeding save logs nothing,
            // so those are not passing because this logger can never receive anything.
            verify(logger, never()).error(any(IOException.class), anyString());
        }
    }

    // ==================== Help Tests ====================

    @Nested
    @DisplayName("Help Command")
    class HelpTests {

        @Test
        @DisplayName("Should display help message with all commands")
        void shouldDisplayHelp() {
            CommandSender sender = mock(CommandSender.class);

            commands.handleHelp(sender);

            // Header + 4 command lines = 5 messages
            verify(sender, atLeast(4)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should mention reload in help")
        void shouldMentionReload() {
            CommandSender sender = mock(CommandSender.class);

            commands.handleHelp(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, atLeast(1)).sendMessage(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(msg -> msg.contains("reload"));
        }
    }

    // ==================== UltiKits/UltiChat#25 ====================

    @Nested
    @DisplayName("A rule name containing '.' is refused, naming the character (UltiKits/UltiChat#25)")
    class DottedRuleName {

        @Test
        @DisplayName("add refuses my.rule, says why, and never reaches the service")
        void addRefusesADottedName() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            when(mockPlugin.i18n("autoreply_invalid_name"))
                    .thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));

            commands.onAutoReplyAdd(sender, "my.rule", "hi");

            verify(mockAutoReplyService, never()).addRule(anyString(), anyString(), anyString());
            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(sent.capture());
            assertThat(sent.getValue()).contains("my.rule").contains("'.'");
            assertThat(sent.getValue()).doesNotContain("added");
        }

        @Test
        @DisplayName("control: a name without a dot is added")
        void undottedNameIsAdded() throws Exception {
            CommandSender sender = mock(CommandSender.class);

            commands.onAutoReplyAdd(sender, "my-rule", "hi");

            verify(mockAutoReplyService).addRule("my-rule", "my-rule", "hi");
        }
    }

    // ==================== UltiKits/UltiChat#26 ====================

    /**
     * Driven through the framework's own {@code BaseCommandExecutor#onCommand} dispatch, so the
     * command's format decides how the arguments are bound -- a direct method call would bypass it.
     */
    @Nested
    @DisplayName("A response and a keyword can be sentences (UltiKits/UltiChat#26)")
    class SentenceArguments {

        private org.bukkit.command.ConsoleCommandSender console;
        private com.ultikits.ultitools.UltiTools ultiTools;

        @BeforeEach
        void liveServer() throws Exception {
            com.ultikits.plugins.chat.utils.ChatTestHelper.setUp();
            console = mock(org.bukkit.command.ConsoleCommandSender.class);
            when(console.hasPermission(anyString())).thenReturn(true);
            when(console.isOp()).thenReturn(true);
            when(console.getName()).thenReturn("CONSOLE");
            ultiTools = mock(com.ultikits.ultitools.UltiTools.class);
            when(ultiTools.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        }

        @AfterEach
        void stopServer() throws Exception {
            com.ultikits.plugins.chat.utils.ChatTestHelper.tearDown();
        }

        /**
         * Dispatches {@code /uchat <args>} through {@code onCommand}. The matched method, its
         * parameters as the framework built them from the format, and the validators all run for
         * real; only the final hand-off to the scheduler is replaced by calling the matched method
         * on the spot, since the scheduler's one-tick deferral is not what is under test.
         */
        private void run(String... args) throws Exception {
            ChatAdminCommands dispatched = spy(commands);
            java.lang.reflect.Method execute = com.ultikits.ultitools.abstracts.command.BaseCommandExecutor.class
                    .getDeclaredMethod("executeCommand",
                            com.ultikits.ultitools.abstracts.command.CommandContext.class,
                            java.lang.reflect.Method.class, Object[].class,
                            com.ultikits.ultitools.abstracts.command.validation.ValidatorChain.ChainValidationResult.class);
            execute.setAccessible(true);
            execute.invoke(doAnswer(invocation -> {
                java.lang.reflect.Method matched = invocation.getArgument(1);
                Object[] params = invocation.getArgument(2);
                return matched.invoke(dispatched, params);
            }).when(dispatched), any(), any(), any(), any());

            org.bukkit.command.Command bukkitCommand = mock(org.bukkit.command.Command.class);
            when(bukkitCommand.getName()).thenReturn("uchat");
            try (org.mockito.MockedStatic<com.ultikits.ultitools.UltiTools> ut =
                         mockStatic(com.ultikits.ultitools.UltiTools.class)) {
                ut.when(com.ultikits.ultitools.UltiTools::getInstance).thenReturn(ultiTools);
                dispatched.onCommand(console, bukkitCommand, "uchat", args);
            }
        }

        @Test
        @DisplayName("autoreply add welcome Welcome to the server stores the whole sentence")
        void addTakesTheRestOfTheLine() throws Exception {
            run("autoreply", "add", "welcome", "Welcome", "to", "the", "server");

            verify(mockAutoReplyService).addRule("welcome", "welcome", "Welcome to the server");
        }

        @Test
        @DisplayName("control: a one-word response is stored as before")
        void oneWordResponse() throws Exception {
            run("autoreply", "add", "greet", "hello");

            verify(mockAutoReplyService).addRule("greet", "greet", "hello");
        }

        @Test
        @DisplayName("autoreply setkeyword server-ip server IP sets the phrase")
        void setKeywordTakesAPhrase() throws Exception {
            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("server-ip", new HashMap<String, Object>());
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            run("autoreply", "setkeyword", "server-ip", "server", "IP");

            verify(mockAutoReplyService).setKeyword("server-ip", "server IP");
        }
    }

    // ==================== UltiKits/UltiChat#29 ====================

    /**
     * A panel configuration update replaces the whole rule map (the framework sets the field
     * reflectively). A spy whose {@code save()} swaps the map reproduces that landing between a
     * command's change and its save, single-threaded and without timing.
     */
    @Nested
    @DisplayName("A rule map replaced by the panel during the save is reported as not applied (UltiKits/UltiChat#29)")
    class ReplacedByPanel {

        private com.ultikits.plugins.chat.config.AutoReplyConfig config;
        private ChatAdminCommands realCommands;
        private Map<String, Map<String, Object>> panelRules;
        private CommandSender sender;

        @BeforeEach
        void realService() throws Exception {
            config = spy(new com.ultikits.plugins.chat.config.AutoReplyConfig());
            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            Map<String, Object> existing = new HashMap<>();
            existing.put("keyword", "old");
            existing.put("response", "Old");
            rules.put("existing", existing);
            config.setRules(rules);
            AutoReplyService service = new AutoReplyService();
            java.lang.reflect.Field field = AutoReplyService.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(service, config);
            realCommands = new ChatAdminCommands(mockPlugin, service);

            panelRules = new LinkedHashMap<>();
            Map<String, Object> panelRule = new HashMap<>();
            panelRule.put("keyword", "panel");
            panelRule.put("response", "From the panel");
            panelRules.put("from-panel", panelRule);

            when(mockPlugin.i18n("autoreply_not_applied"))
                    .thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
            sender = mock(CommandSender.class);
        }

        private void panelReplacesTheRulesDuringTheSave() throws Exception {
            doAnswer(invocation -> {
                config.setRules(panelRules);
                return null;
            }).when(config).save();
        }

        private String reply() {
            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(sent.capture());
            return sent.getValue();
        }

        private String notApplied(String rule) {
            return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    com.ultikits.plugins.chat.i18n.CatalogueText.text("en", "autoreply_not_applied").replace("{0}", rule));
        }

        @Test
        @DisplayName("add: not applied, and the panel's rules are left exactly as the panel wrote them")
        void add() throws Exception {
            panelReplacesTheRulesDuringTheSave();

            realCommands.onAutoReplyAdd(sender, "greet", "Hello");

            String reply = reply();
            assertThat(reply).as("no success is reported").doesNotContain("added");
            assertThat(reply).isEqualTo(notApplied("greet"));
            assertThat(config.getRules()).isSameAs(panelRules).containsOnlyKeys("from-panel");
        }

        @Test
        @DisplayName("setkeyword: not applied")
        void setKeyword() throws Exception {
            panelReplacesTheRulesDuringTheSave();

            realCommands.onAutoReplySetKeyword(sender, "existing", "new keyword");

            String reply = reply();
            assertThat(reply).as("no success is reported").doesNotContain("keyword set");
            assertThat(reply).isEqualTo(notApplied("existing"));
        }

        @Test
        @DisplayName("remove: not applied")
        void remove() throws Exception {
            panelReplacesTheRulesDuringTheSave();

            realCommands.onAutoReplyRemove(sender, "existing");

            String reply = reply();
            assertThat(reply).as("no success is reported").doesNotContain("removed");
            assertThat(reply).isEqualTo(notApplied("existing"));
        }

        @Test
        @DisplayName("control: a save the panel does not interrupt reports success")
        void uninterruptedSaveSucceeds() throws Exception {
            doNothing().when(config).save();

            realCommands.onAutoReplyAdd(sender, "greet", "Hello");

            assertThat(reply()).contains("added");
            assertThat(config.getRules()).containsKey("greet");
        }
    }

    // ==================== UltiKits/UltiChat#39 ====================

    @Nested
    @DisplayName("A rule name or keyword is shown as written, even when it contains a placeholder (UltiKits/UltiChat#39)")
    class PlaceholdersFilledOnce {

        @Test
        @DisplayName("autoreply list: a name containing {1} and a keyword containing {3} stay literal")
        void listLineIsFilledInOnePass() {
            CommandSender sender = mock(CommandSender.class);
            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "kw{3}");
            rule.put("mode", "contains");
            rule.put("response", "resp");
            rules.put("a{1}b", rule);
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplyList(sender);

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(sender, atLeastOnce()).sendMessage(sent.capture());
            assertThat(sent.getAllValues()).contains("a{1}b: kw{3} [contains] -> resp");
        }

        @Test
        @DisplayName("autoreply setkeyword: a name containing {1} stays literal")
        void keywordSetLineIsFilledInOnePass() throws Exception {
            CommandSender sender = mock(CommandSender.class);
            Map<String, Map<String, Object>> rules = new HashMap<>();
            rules.put("r{1}", new HashMap<String, Object>());
            when(mockAutoReplyService.getRules()).thenReturn(rules);

            commands.onAutoReplySetKeyword(sender, "r{1}", "k");

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(sent.capture());
            assertThat(sent.getValue()).isEqualTo("Rule 'r{1}' keyword set to 'k'.");
        }
    }
}
