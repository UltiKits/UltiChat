package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for matching chat messages against auto-reply rules.
 * <p>
 * Supports three match modes: contains, exact, and regex.
 * Regex patterns are compiled and cached for performance.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Service
public class AutoReplyService {

    @Autowired
    private AutoReplyConfig config;

    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /**
     * Find the first rule that matches the given message.
     *
     * @param message the chat message to match
     * @return the matching rule entry (name -> rule map), or null if no match
     */
    public Map.Entry<String, Map<String, Object>> findMatch(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        Map<String, Map<String, Object>> rules = config.getRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Map<String, Object>> entry : rules.entrySet()) {
            Map<String, Object> rule = entry.getValue();
            if (rule == null) {
                continue;
            }

            Object keywordObj = rule.get("keyword");
            if (keywordObj == null) {
                continue;
            }
            String keyword = keywordObj.toString();

            String mode = getMode(rule);
            boolean caseSensitive = isCaseSensitive(rule);

            if (matches(message, keyword, mode, caseSensitive)) {
                return entry;
            }
        }

        return null;
    }

    /**
     * Get the response from a rule. Can be a String or List of Strings.
     *
     * @param rule the rule map
     * @return the response object, or null
     */
    public Object getResponse(Map<String, Object> rule) {
        if (rule == null) {
            return null;
        }
        return rule.get("response");
    }

    /**
     * Get the commands list from a rule.
     *
     * @param rule the rule map
     * @return list of commands, or empty list if none
     */
    @SuppressWarnings("unchecked")
    public List<String> getCommands(Map<String, Object> rule) {
        if (rule == null) {
            return Collections.emptyList();
        }
        Object commands = rule.get("commands");
        if (commands instanceof List) {
            return (List<String>) commands;
        }
        return Collections.emptyList();
    }

    /**
     * Add a simple contains-mode rule to the config.
     * <p>
     * Refuses when {@code name} already names an existing, non-null rule: merging two rules
     * under one name has no defined semantics (which of keyword/response/mode/case-sensitivity
     * should win per field is undecided), so an existing rule is never silently overwritten.
     * A name mapped to {@code null} (e.g. malformed YAML such as {@code broken:}) is treated as
     * absent and repaired rather than refused, matching {@link #findMatch(String)}'s and
     * {@link #setKeyword(String, String)}'s existing tolerance of null rule maps. Mirrors
     * {@link #removeRule(String)}'s own present/absent distinction, which the command layer
     * already reports through a not-found message.
     *
     * <p>
     * The change reaches {@code config/autoreply.yml} before this method returns, so it survives
     * {@code /uchat reload} and {@code /ul reload}, both of which re-read the entity straight from
     * disk (UltiKits/UltiChat#17). If the write fails, the in-memory state is restored to exactly
     * what it was and the failure is rethrown -- no half-applied rule is left behind, and a caller
     * cannot report success for a change that is not on disk. A call that changes nothing writes
     * nothing.
     *
     * @param name     the rule name (key)
     * @param keyword  the keyword to match
     * @param response the response text
     * @throws IOException if the configuration could not be written; the rule set is left unchanged
     */
    public void addRule(String name, String keyword, String response) throws IOException {
        Map<String, Map<String, Object>> rules = config.getRules();
        boolean rulesWereAbsent = rules == null;
        if (rulesWereAbsent) {
            rules = new HashMap<>();
            config.setRules(rules);
        }

        if (rules.get(name) != null) {
            return;
        }

        Map<String, Map<String, Object>> rulesBefore = new LinkedHashMap<>(rules);

        Map<String, Object> rule = new HashMap<>();
        rule.put("keyword", keyword);
        rule.put("response", response);
        rule.put("mode", "contains");
        rule.put("case-sensitive", false);
        rules.put(name, rule);

        try {
            config.save();
        } catch (IOException e) {
            if (rulesWereAbsent) {
                config.setRules(null);
            } else {
                restore(rules, rulesBefore);
            }
            throw e;
        }
    }

    /**
     * Set the keyword of an existing rule, leaving its response, mode, and
     * case-sensitivity untouched. No-ops if {@code name} does not name an existing rule.
     *
     * <p>
     * The change reaches {@code config/autoreply.yml} before this method returns, so it survives
     * {@code /uchat reload} and {@code /ul reload}, both of which re-read the entity straight from
     * disk (UltiKits/UltiChat#17). If the write fails, the in-memory state is restored to exactly
     * what it was and the failure is rethrown -- no half-applied rule is left behind, and a caller
     * cannot report success for a change that is not on disk. A call that changes nothing writes
     * nothing.
     *
     * @param name    the rule name (key)
     * @param keyword the new keyword to match
     * @throws IOException if the configuration could not be written; the rule is left unchanged
     */
    public void setKeyword(String name, String keyword) throws IOException {
        Map<String, Map<String, Object>> rules = config.getRules();
        if (rules == null) {
            return;
        }
        Map<String, Object> rule = rules.get(name);
        if (rule == null) {
            return;
        }

        boolean hadKeyword = rule.containsKey("keyword");
        Object keywordBefore = rule.get("keyword");

        rule.put("keyword", keyword);

        try {
            config.save();
        } catch (IOException e) {
            if (hadKeyword) {
                rule.put("keyword", keywordBefore);
            } else {
                rule.remove("keyword");
            }
            throw e;
        }
    }

    /**
     * Remove a rule by name. No-ops if {@code name} does not name an existing rule.
     * <p>
     * The change reaches {@code config/autoreply.yml} before this method returns, so it survives
     * {@code /uchat reload} and {@code /ul reload}, both of which re-read the entity straight from
     * disk (UltiKits/UltiChat#17). If the write fails, the in-memory state is restored to exactly
     * what it was and the failure is rethrown -- no half-applied rule is left behind, and a caller
     * cannot report success for a change that is not on disk. A call that changes nothing writes
     * nothing.
     *
     * @param name the rule name to remove
     * @throws IOException if the configuration could not be written; the rule set is left unchanged
     */
    public void removeRule(String name) throws IOException {
        Map<String, Map<String, Object>> rules = config.getRules();
        if (rules == null || !rules.containsKey(name)) {
            // Returning here also skips the pattern-cache eviction below, which is observably the
            // same as running it: the cache is keyed by a mode-prefixed keyword ("i:" or "s:" plus
            // the pattern, see matchesRegex), never by a rule name, so evicting a rule name was
            // already a no-op -- and an eviction is in any case only a recompile, never a
            // behaviour change.
            return;
        }

        Map<String, Map<String, Object>> rulesBefore = new LinkedHashMap<>(rules);

        rules.remove(name);
        // Also remove cached pattern if any
        Pattern removedPattern = patternCache.remove(name);

        try {
            config.save();
        } catch (IOException e) {
            restore(rules, rulesBefore);
            if (removedPattern != null) {
                patternCache.put(name, removedPattern);
            }
            throw e;
        }
    }

    /**
     * Puts {@code snapshot}'s entries back into the live rules map, in {@code snapshot}'s own
     * iteration order, replacing whatever is there now.
     * <p>
     * The live map object is emptied and refilled rather than replaced, so every reference already
     * handed out by {@link #getRules()} still sees the restored set, and the snapshot holds the
     * original rule map instances rather than copies of them, so a restored rule is the same object
     * it was before. Once the entity has been read from its file the rule map is a
     * {@code LinkedHashMap} (that is what {@code DefaultConfigParser} builds), so this restores the
     * rule order the file preserves and not merely the rule set; before any such read it is the
     * field's own {@code HashMap} default, which has no order to restore.
     *
     * @param live     the live rules map to restore into
     * @param snapshot the entries to restore, in the order they are to be restored
     */
    private static void restore(Map<String, Map<String, Object>> live,
                                Map<String, Map<String, Object>> snapshot) {
        live.clear();
        live.putAll(snapshot);
    }

    /**
     * Get all rules.
     *
     * @return the rules map, or empty map if null
     */
    public Map<String, Map<String, Object>> getRules() {
        Map<String, Map<String, Object>> rules = config.getRules();
        if (rules == null) {
            return Collections.emptyMap();
        }
        return rules;
    }

    private boolean matches(String message, String keyword, String mode, boolean caseSensitive) {
        switch (mode) {
            case "exact":
                return caseSensitive ? message.equals(keyword) : message.equalsIgnoreCase(keyword);
            case "regex":
                return matchesRegex(message, keyword, caseSensitive);
            case "contains":
            default:
                if (caseSensitive) {
                    return message.contains(keyword);
                }
                return message.toLowerCase().contains(keyword.toLowerCase());
        }
    }

    private boolean matchesRegex(String message, String keyword, boolean caseSensitive) {
        String cacheKey = (caseSensitive ? "s:" : "i:") + keyword;
        Pattern pattern = patternCache.get(cacheKey);
        if (pattern == null) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = Pattern.compile(keyword, flags);
                patternCache.put(cacheKey, pattern);
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        return pattern.matcher(message).find();
    }

    private String getMode(Map<String, Object> rule) {
        Object mode = rule.get("mode");
        if (mode == null) {
            return "contains";
        }
        return mode.toString().toLowerCase();
    }

    private boolean isCaseSensitive(Map<String, Object> rule) {
        Object cs = rule.get("case-sensitive");
        if (cs instanceof Boolean) {
            return (Boolean) cs;
        }
        if (cs != null) {
            return Boolean.parseBoolean(cs.toString());
        }
        return false;
    }
}
