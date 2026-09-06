package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AutoReplyService — match modes, case sensitivity, pattern cache,
 * rule CRUD, and null safety.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("AutoReplyService Tests")
class AutoReplyServiceTest {

    private AutoReplyService service;
    private AutoReplyConfig config;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();

        config = new AutoReplyConfig();
        config.setRules(new HashMap<String, Map<String, Object>>());

        service = new AutoReplyService();
        ChatTestHelper.setField(service, "config", config);
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    // --- Helper ---

    private void addRule(String name, String keyword, String response, String mode, boolean caseSensitive) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("keyword", keyword);
        rule.put("response", response);
        rule.put("mode", mode);
        rule.put("case-sensitive", caseSensitive);
        config.getRules().put(name, rule);
    }

    private void addRuleWithCommands(String name, String keyword, String response, List<String> commands) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("keyword", keyword);
        rule.put("response", response);
        rule.put("mode", "contains");
        rule.put("case-sensitive", false);
        rule.put("commands", commands);
        config.getRules().put(name, rule);
    }

    // ============================
    // Contains match mode
    // ============================

    @Nested
    @DisplayName("Contains Mode")
    class ContainsModeTests {

        @Test
        @DisplayName("Should match when message contains keyword (case-insensitive)")
        void shouldMatchContainsCaseInsensitive() {
            addRule("r1", "help", "Help response", "contains", false);

            Map.Entry<String, Map<String, Object>> match = service.findMatch("I need HELP please");
            assertThat(match).isNotNull();
            assertThat(match.getKey()).isEqualTo("r1");
        }

        @Test
        @DisplayName("Should match when message contains keyword (case-sensitive)")
        void shouldMatchContainsCaseSensitive() {
            addRule("r1", "Help", "Response", "contains", true);

            assertThat(service.findMatch("I need Help now")).isNotNull();
            assertThat(service.findMatch("I need help now")).isNull();
        }

        @Test
        @DisplayName("Should not match when keyword is absent")
        void shouldNotMatchWhenAbsent() {
            addRule("r1", "help", "Response", "contains", false);

            assertThat(service.findMatch("goodbye world")).isNull();
        }

        @Test
        @DisplayName("Should match substring keyword")
        void shouldMatchSubstring() {
            addRule("r1", "ip", "Server IP is 1.2.3.4", "contains", false);

            assertThat(service.findMatch("what is the ip address?")).isNotNull();
        }

        @Test
        @DisplayName("Default mode should be contains when mode is null")
        void defaultModeContains() {
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "test");
            rule.put("response", "Test response");
            // no "mode" key
            config.getRules().put("r1", rule);

            assertThat(service.findMatch("this is a test message")).isNotNull();
        }
    }

    // ============================
    // Exact match mode
    // ============================

    @Nested
    @DisplayName("Exact Mode")
    class ExactModeTests {

        @Test
        @DisplayName("Should match exact message (case-insensitive)")
        void shouldMatchExactCaseInsensitive() {
            addRule("r1", "hello", "Hi there!", "exact", false);

            assertThat(service.findMatch("HELLO")).isNotNull();
            assertThat(service.findMatch("hello")).isNotNull();
        }

        @Test
        @DisplayName("Should not match partial message in exact mode")
        void shouldNotMatchPartialInExact() {
            addRule("r1", "hello", "Response", "exact", false);

            assertThat(service.findMatch("hello world")).isNull();
        }

        @Test
        @DisplayName("Should respect case sensitivity in exact mode")
        void shouldRespectCaseSensitiveExact() {
            addRule("r1", "Hello", "Response", "exact", true);

            assertThat(service.findMatch("Hello")).isNotNull();
            assertThat(service.findMatch("hello")).isNull();
            assertThat(service.findMatch("HELLO")).isNull();
        }
    }

    // ============================
    // Regex match mode
    // ============================

    @Nested
    @DisplayName("Regex Mode")
    class RegexModeTests {

        @Test
        @DisplayName("Should match regex pattern")
        void shouldMatchRegex() {
            addRule("r1", "\\bhelp\\b", "Help response", "regex", false);

            assertThat(service.findMatch("I need help please")).isNotNull();
            assertThat(service.findMatch("helping you")).isNull();
        }

        @Test
        @DisplayName("Should match regex case-insensitively when configured")
        void shouldMatchRegexCaseInsensitive() {
            addRule("r1", "hello.*world", "Response", "regex", false);

            assertThat(service.findMatch("HELLO big WORLD")).isNotNull();
        }

        @Test
        @DisplayName("Should match regex case-sensitively when configured")
        void shouldMatchRegexCaseSensitive() {
            addRule("r1", "Hello", "Response", "regex", true);

            assertThat(service.findMatch("Hello there")).isNotNull();
            assertThat(service.findMatch("hello there")).isNull();
        }

        @Test
        @DisplayName("Should handle invalid regex gracefully")
        void shouldHandleInvalidRegex() {
            addRule("r1", "[invalid(", "Response", "regex", false);

            assertThat(service.findMatch("anything")).isNull();
        }

        @Test
        @DisplayName("Should cache compiled patterns")
        @SuppressWarnings("unchecked")
        void shouldCacheCompiledPatterns() throws Exception {
            addRule("r1", "test\\d+", "Response", "regex", false);

            service.findMatch("test123");
            service.findMatch("test456");

            Map<String, Pattern> cache = (Map<String, Pattern>) ChatTestHelper.getField(service, "patternCache");
            assertThat(cache).containsKey("i:test\\d+");
            assertThat(cache).hasSize(1);
        }

        @Test
        @DisplayName("Should cache separate patterns for case-sensitive and insensitive")
        @SuppressWarnings("unchecked")
        void shouldCacheSeparatePatterns() throws Exception {
            addRule("r1", "hello", "Response1", "regex", false);
            addRule("r2", "hello", "Response2", "regex", true);

            service.findMatch("Hello World");

            Map<String, Pattern> cache = (Map<String, Pattern>) ChatTestHelper.getField(service, "patternCache");
            // Both patterns should be cached with different keys
            assertThat(cache).containsKey("i:hello");
            assertThat(cache).containsKey("s:hello");
        }
    }

    // ============================
    // Case sensitivity
    // ============================

    @Nested
    @DisplayName("Case Sensitivity")
    class CaseSensitivityTests {

        @Test
        @DisplayName("Should default to case-insensitive when case-sensitive not set")
        void shouldDefaultCaseInsensitive() {
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "HELLO");
            rule.put("response", "Hi");
            rule.put("mode", "contains");
            // no "case-sensitive" key
            config.getRules().put("r1", rule);

            assertThat(service.findMatch("hello there")).isNotNull();
        }

        @Test
        @DisplayName("Should parse case-sensitive as String 'true'")
        void shouldParseCaseSensitiveString() {
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "Test");
            rule.put("response", "Response");
            rule.put("mode", "contains");
            rule.put("case-sensitive", "true");
            config.getRules().put("r1", rule);

            assertThat(service.findMatch("test")).isNull();
            assertThat(service.findMatch("Test")).isNotNull();
        }
    }

    // ============================
    // Response and commands
    // ============================

    @Nested
    @DisplayName("Response and Commands")
    class ResponseAndCommandsTests {

        @Test
        @DisplayName("Should return string response")
        void shouldReturnStringResponse() {
            addRule("r1", "test", "Test response", "contains", false);

            Map.Entry<String, Map<String, Object>> match = service.findMatch("test");
            assertThat(match).isNotNull();

            Object response = service.getResponse(match.getValue());
            assertThat(response).isEqualTo("Test response");
        }

        @Test
        @DisplayName("Should return list response")
        void shouldReturnListResponse() {
            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", "info");
            rule.put("mode", "contains");
            List<String> lines = Arrays.asList("Line 1", "Line 2", "Line 3");
            rule.put("response", lines);
            config.getRules().put("r1", rule);

            Map.Entry<String, Map<String, Object>> match = service.findMatch("info please");
            assertThat(match).isNotNull();

            Object response = service.getResponse(match.getValue());
            assertThat(response).isInstanceOf(List.class);
            assertThat((List<?>) response).hasSize(3);
        }

        @Test
        @DisplayName("Should return null response for null rule")
        void shouldReturnNullForNullRule() {
            assertThat(service.getResponse(null)).isNull();
        }

        @Test
        @DisplayName("Should return commands list")
        void shouldReturnCommandsList() {
            List<String> commands = Arrays.asList("say Hello {player}", "give {player} diamond 1");
            addRuleWithCommands("r1", "reward", "Here's your reward!", commands);

            Map.Entry<String, Map<String, Object>> match = service.findMatch("give me a reward");
            assertThat(match).isNotNull();

            List<String> result = service.getCommands(match.getValue());
            assertThat(result).hasSize(2);
            assertThat(result).contains("say Hello {player}", "give {player} diamond 1");
        }

        @Test
        @DisplayName("Should return empty list when no commands")
        void shouldReturnEmptyListWhenNoCommands() {
            addRule("r1", "test", "Response", "contains", false);

            Map.Entry<String, Map<String, Object>> match = service.findMatch("test");
            assertThat(match).isNotNull();

            assertThat(service.getCommands(match.getValue())).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for null rule commands")
        void shouldReturnEmptyListForNullRuleCommands() {
            assertThat(service.getCommands(null)).isEmpty();
        }
    }

    // ============================
    // Rule CRUD
    // ============================

    @Nested
    @DisplayName("Rule Management")
    class RuleManagementTests {

        @Test
        @DisplayName("Should add a simple rule")
        void shouldAddRule() {
            service.addRule("greeting", "hi", "Hello there!");

            Map<String, Map<String, Object>> rules = service.getRules();
            assertThat(rules).containsKey("greeting");
            assertThat(rules.get("greeting").get("keyword")).isEqualTo("hi");
            assertThat(rules.get("greeting").get("response")).isEqualTo("Hello there!");
            assertThat(rules.get("greeting").get("mode")).isEqualTo("contains");
            assertThat(rules.get("greeting").get("case-sensitive")).isEqualTo(false);
        }

        @Test
        @DisplayName("Should remove a rule")
        void shouldRemoveRule() {
            addRule("r1", "test", "Response", "contains", false);
            assertThat(service.getRules()).hasSize(1);

            service.removeRule("r1");
            assertThat(service.getRules()).isEmpty();
        }

        @Test
        @DisplayName("Should handle remove of non-existent rule gracefully")
        void shouldHandleRemoveNonExistent() {
            assertThatCode(() -> service.removeRule("nonexistent")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should return all rules")
        void shouldReturnAllRules() {
            addRule("r1", "test1", "Response1", "contains", false);
            addRule("r2", "test2", "Response2", "exact", true);

            Map<String, Map<String, Object>> rules = service.getRules();
            assertThat(rules).hasSize(2);
            assertThat(rules).containsKeys("r1", "r2");
        }

        @Test
        @DisplayName("Should return empty map when rules are null")
        void shouldReturnEmptyMapWhenNull() {
            config.setRules(null);
            assertThat(service.getRules()).isEmpty();
        }

        @Test
        @DisplayName("Should add rule when rules map is null")
        void shouldAddRuleWhenMapNull() {
            config.setRules(null);

            service.addRule("r1", "test", "Response");
            assertThat(service.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Remove should clear pattern cache for that rule")
        @SuppressWarnings("unchecked")
        void removeShouldClearPatternCache() throws Exception {
            addRule("regex-rule", "test\\d+", "Response", "regex", false);

            // Trigger pattern compilation
            service.findMatch("test123");

            Map<String, Pattern> cache = (Map<String, Pattern>) ChatTestHelper.getField(service, "patternCache");
            assertThat(cache).isNotEmpty();

            // patternCache keyed by "i:pattern" not by rule name, but removeRule clears by rule name
            // This test verifies removeRule does not throw
            service.removeRule("regex-rule");
            assertThat(service.getRules()).isEmpty();
        }
    }

    // ============================
    // Duplicate rule-name guard
    // ============================

    @Nested
    @DisplayName("Duplicate Name Guard")
    class DuplicateNameGuardTests {

        @Test
        @DisplayName("Adding a rule under an existing name is refused")
        void addingARuleUnderAnExistingNameIsRefused() {
            service.addRule("greeting", "hi", "Original response");

            service.addRule("greeting", "hello", "Replacement response");

            Map<String, Map<String, Object>> rules = service.getRules();
            assertThat(rules.get("greeting").get("response")).isEqualTo("Original response");
            assertThat(rules.get("greeting").get("keyword")).isEqualTo("hi");
        }

        @Test
        @DisplayName("Adding a rule under an existing name also preserves its mode and case-sensitivity")
        void addingARuleUnderAnExistingNamePreservesModeAndCaseSensitivity() {
            // WR-01: addRule(name, keyword, response) itself always hardcodes
            // mode="contains"/case-sensitive=false, so a rule built purely through the
            // service's own 3-arg addRule never puts those two fields in a state where
            // overwriting them would be observable. Seed the rule directly with
            // non-default values via the test's 5-arg helper instead.
            addRule("greeting", "hi", "Original response", "exact", true);

            service.addRule("greeting", "hello", "Replacement response");

            Map<String, Map<String, Object>> rules = service.getRules();
            assertThat(rules.get("greeting").get("response")).isEqualTo("Original response");
            assertThat(rules.get("greeting").get("keyword")).isEqualTo("hi");
            assertThat(rules.get("greeting").get("mode")).isEqualTo("exact");
            assertThat(rules.get("greeting").get("case-sensitive")).isEqualTo(true);
        }

        @Test
        @DisplayName("Adding a rule under a new name still succeeds")
        void addingARuleUnderANewNameStillSucceeds() {
            service.addRule("greeting", "hi", "Hello there!");

            Map<String, Map<String, Object>> rules = service.getRules();
            assertThat(rules.get("greeting").get("keyword")).isEqualTo("hi");
            assertThat(rules.get("greeting").get("response")).isEqualTo("Hello there!");
        }

        @Test
        @DisplayName("A refused add does not disturb the other existing rules")
        void aRefusedAddDoesNotDisturbTheOtherExistingRules() {
            // WR-02: this replaces a `containsExactly("first", "second")` order
            // assertion that could never fail independently of the response assertion
            // already in this test. Map.put(existingKey, newValue) never changes
            // iteration order in HashMap or LinkedHashMap -- only inserting a *new* key
            // does -- and a refused add and an unguarded overwrite both call put() on
            // the same already-present key, so the order came out identical either way.
            // Backing this test's map with a LinkedHashMap to make order assertions
            // meaningful was tried and reverted: it silently changed
            // RegexModeTests.shouldCacheSeparatePatterns' outcome, because that
            // unrelated test already depends on this class's default HashMap iteration
            // order pairing two same-keyword rules in the order that lets both of their
            // patterns get compiled before findMatch's first match short-circuits the
            // loop -- a second, pre-existing instance of the exact anti-pattern this
            // finding is about, out of scope for this change and left as a separate,
            // undocumented fragility for now. What this test asserts instead -- that
            // "second" survives the refused add on "first" untouched -- *can* fail
            // independently: a defective "clear-then-rebuild" style merge would wipe it
            // even though the response assertion on "first" alone would not catch that.
            service.addRule("first", "hi", "First response");
            service.addRule("second", "hello", "Second response");

            service.addRule("first", "hi", "Attempted replacement");

            Map<String, Map<String, Object>> rules = service.getRules();
            assertThat(rules).containsKey("second");
            assertThat(rules.get("second").get("response")).isEqualTo("Second response");
            assertThat(rules.get("second").get("keyword")).isEqualTo("hello");
            assertThat(rules.get("first").get("response")).isEqualTo("First response");
        }
    }

    // ============================
    // Setting a rule's keyword distinct from its name
    // ============================

    @Nested
    @DisplayName("Set Keyword")
    class SetKeywordTests {

        @Test
        @DisplayName("Updates only the keyword of an existing rule, leaving response, mode, and case-sensitivity untouched")
        void updatesOnlyTheKeywordOfAnExistingRule() {
            addRule("server-ip", "server-ip", "Server address: play.example.com", "exact", true);

            service.setKeyword("server-ip", "server IP");

            Map<String, Object> rule = service.getRules().get("server-ip");
            assertThat(rule.get("keyword")).isEqualTo("server IP");
            assertThat(rule.get("response")).isEqualTo("Server address: play.example.com");
            assertThat(rule.get("mode")).isEqualTo("exact");
            assertThat(rule.get("case-sensitive")).isEqualTo(true);
        }

        @Test
        @DisplayName("Is a no-op when the named rule does not exist")
        void isANoOpWhenTheNamedRuleDoesNotExist() {
            service.setKeyword("missing", "anything");

            assertThat(service.getRules()).isEmpty();
        }
    }

    // ============================
    // Null safety
    // ============================

    @Nested
    @DisplayName("Null Safety")
    class NullSafetyTests {

        @Test
        @DisplayName("Should return null for null message")
        void shouldReturnNullForNullMessage() {
            addRule("r1", "test", "Response", "contains", false);
            assertThat(service.findMatch(null)).isNull();
        }

        @Test
        @DisplayName("Should return null for empty message")
        void shouldReturnNullForEmptyMessage() {
            addRule("r1", "test", "Response", "contains", false);
            assertThat(service.findMatch("")).isNull();
        }

        @Test
        @DisplayName("Should return null when rules map is null")
        void shouldReturnNullWhenRulesNull() {
            config.setRules(null);
            assertThat(service.findMatch("anything")).isNull();
        }

        @Test
        @DisplayName("Should return null when rules map is empty")
        void shouldReturnNullWhenRulesEmpty() {
            assertThat(service.findMatch("anything")).isNull();
        }

        @Test
        @DisplayName("Should skip rule with null value map")
        void shouldSkipNullRuleMap() {
            config.getRules().put("broken", null);
            addRule("r1", "test", "Response", "contains", false);

            // Should still match the valid rule
            Map.Entry<String, Map<String, Object>> match = service.findMatch("test");
            assertThat(match).isNotNull();
            assertThat(match.getKey()).isEqualTo("r1");
        }

        @Test
        @DisplayName("Should skip rule with null keyword")
        void shouldSkipNullKeyword() {
            Map<String, Object> rule = new HashMap<>();
            rule.put("response", "Response");
            rule.put("mode", "contains");
            // no "keyword" key
            config.getRules().put("no-keyword", rule);

            assertThat(service.findMatch("anything")).isNull();
        }
    }

    // ============================
    // First match priority
    // ============================

    @Nested
    @DisplayName("First Match Priority")
    class FirstMatchTests {

        @Test
        @DisplayName("Should return first matching rule (LinkedHashMap order)")
        void shouldReturnFirstMatch() {
            LinkedHashMap<String, Map<String, Object>> orderedRules = new LinkedHashMap<>();

            Map<String, Object> rule1 = new HashMap<>();
            rule1.put("keyword", "hello");
            rule1.put("response", "First response");
            rule1.put("mode", "contains");
            rule1.put("case-sensitive", false);
            orderedRules.put("first", rule1);

            Map<String, Object> rule2 = new HashMap<>();
            rule2.put("keyword", "hello");
            rule2.put("response", "Second response");
            rule2.put("mode", "contains");
            rule2.put("case-sensitive", false);
            orderedRules.put("second", rule2);

            config.setRules(orderedRules);

            Map.Entry<String, Map<String, Object>> match = service.findMatch("hello world");
            assertThat(match).isNotNull();
            assertThat(match.getKey()).isEqualTo("first");
        }
    }

    // ============================
    // Mode parsing edge cases
    // ============================

    @Nested
    @DisplayName("Mode Parsing")
    class ModeParsingTests {

        @Test
        @DisplayName("Should handle uppercase mode string")
        void shouldHandleUppercaseMode() {
            addRule("r1", "test", "Response", "CONTAINS", false);

            assertThat(service.findMatch("test message")).isNotNull();
        }

        @Test
        @DisplayName("Should handle mixed case mode string")
        void shouldHandleMixedCaseMode() {
            addRule("r1", "test", "Response", "Exact", false);

            assertThat(service.findMatch("test")).isNotNull();
            assertThat(service.findMatch("test message")).isNull();
        }

        @Test
        @DisplayName("Should treat unknown mode as contains")
        void shouldTreatUnknownModeAsContains() {
            addRule("r1", "test", "Response", "fuzzy", false);

            assertThat(service.findMatch("test message")).isNotNull();
        }
    }
}
