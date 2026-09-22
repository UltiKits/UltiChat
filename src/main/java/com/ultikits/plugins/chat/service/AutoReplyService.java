package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;

import java.io.File;
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
     * Guards {@link #findMatch(String)}'s iteration and every mutation and rollback in this class.
     * <p>
     * Not every reader of the rule map: {@link #getRules()} hands the live map out unguarded and
     * {@code ChatAdminCommands#onAutoReplyList} iterates it (main thread only, and only reads);
     * {@code AbstractConfigEntity#save()} serialises the same map with this lock deliberately not
     * held (see below); and {@code AbstractConfigEntity#updateProperties} replaces the {@code rules}
     * field reflectively from the WebSocket thread, which no lock this class owns can guard -- if a
     * panel configuration write lands between a mutation here and its save, the save serialises the
     * panel's map and the command still reports success. That last one is narrow and is not
     * addressed here; it is a framework-side ownership question, not a module-side locking one.
     * <p>
     * {@link #findMatch(String)} iterates that map on a chat thread -- {@code AutoReplyListener}
     * handles {@code AsyncPlayerChatEvent} -- while a {@code /uchat autoreply} command mutates it on
     * the main thread. A rollback is the case that makes this matter: it empties the map and refills
     * it, so an unguarded reader would see an empty rule set, or a
     * {@link java.util.ConcurrentModificationException} thrown out of a {@code MONITOR} listener.
     * Holding one monitor across each mutation and rollback and across the whole of
     * {@code findMatch} removes that window, and the single-entry races between those same two
     * paths that were already possible with it.
     * <p>
     * Deliberately NOT held across {@code AbstractConfigEntity#save()}: the write is file I/O, and a
     * chat thread has no reason to wait for a disk write. A reader may therefore observe a mutation
     * that a failed save is about to roll back -- which is inherent, since the mutation has to be in
     * memory for the save to serialise it -- but never a half-rebuilt map.
     */
    private final Object rulesLock = new Object();

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

        synchronized (rulesLock) {
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
        final Map<String, Map<String, Object>> rules;
        final Map<String, Map<String, Object>> rulesBefore;
        final boolean rulesWereAbsent;
        synchronized (rulesLock) {
            Map<String, Map<String, Object>> live = config.getRules();
            rulesWereAbsent = live == null;
            if (rulesWereAbsent) {
                live = new HashMap<>();
                config.setRules(live);
            }

            if (live.get(name) != null) {
                return;
            }

            rules = live;
            rulesBefore = new LinkedHashMap<>(live);

            Map<String, Object> rule = new HashMap<>();
            rule.put("keyword", keyword);
            rule.put("response", response);
            rule.put("mode", "contains");
            rule.put("case-sensitive", false);
            live.put(name, rule);
        }

        saveOrRestore(() -> {
            if (rulesWereAbsent) {
                // Not an in-place restore, unlike every other rollback here: the field itself was
                // absent, so restoring it means putting the absence back. Unreachable in practice --
                // the field has a non-null initialiser and the framework never assigns null to it --
                // and reached only by a caller that set it to null by hand.
                config.setRules(null);
            } else {
                restore(rules, rulesBefore);
            }
        });
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
        final Map<String, Object> rule;
        final boolean hadKeyword;
        final Object keywordBefore;
        synchronized (rulesLock) {
            Map<String, Map<String, Object>> rules = config.getRules();
            if (rules == null) {
                return;
            }
            rule = rules.get(name);
            if (rule == null) {
                return;
            }

            hadKeyword = rule.containsKey("keyword");
            keywordBefore = rule.get("keyword");
            if (hadKeyword && Objects.equals(keywordBefore, keyword)) {
                // The rule already matches on this keyword. Writing the file here would change
                // nothing in it and would put an operator's concurrent hand-edit at risk for no
                // reason, so this call writes nothing -- as this method's contract says.
                return;
            }

            rule.put("keyword", keyword);
        }

        saveOrRestore(() -> {
            if (hadKeyword) {
                rule.put("keyword", keywordBefore);
            } else {
                rule.remove("keyword");
            }
        });
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
        final Map<String, Map<String, Object>> rules;
        final Map<String, Map<String, Object>> rulesBefore;
        synchronized (rulesLock) {
            Map<String, Map<String, Object>> live = config.getRules();
            if (live == null || !live.containsKey(name)) {
                // Returning here also skips the pattern-cache eviction below, which is observably
                // the same as running it: the cache is keyed by a mode-prefixed keyword ("i:" or
                // "s:" plus the pattern, see matchesRegex), never by a rule name, so evicting a rule
                // name was already a no-op -- and an eviction is in any case only a recompile, never
                // a behaviour change.
                return;
            }

            rules = live;
            rulesBefore = new LinkedHashMap<>(live);

            live.remove(name);
            // Also remove cached pattern if any. Nothing to undo on the rollback path for the same
            // reason the early return can skip it: this cache is never keyed by a rule name, so the
            // call removes nothing, and a removal that did happen would cost one recompile.
            patternCache.remove(name);
        }

        saveOrRestore(() -> restore(rules, rulesBefore));
    }

    /**
     * Writes the entity, and puts the rule set back as it was if the write fails.
     * <p>
     * One implementation for all three mutating methods, so the save, the rollback and the
     * operator-edit warning cannot drift apart between them. The rollback runs under
     * {@link #rulesLock}, so no chat thread observes a half-restored rule set.
     *
     * @param rollback undoes this method's caller's mutation; run only if the write fails
     * @throws IOException the write failure, rethrown after the rollback
     */
    private void saveOrRestore(Rollback rollback) throws IOException {
        final boolean overwritesOperatorEdit;
        try {
            // Read before the write, and both under the entity's own monitor, exactly as
            // ConfigManager#saveAll does. The ordering alone is not enough: a panel configuration
            // write reaches AbstractConfigEntity#updateProperties on the WebSocket thread and writes
            // the file under this same monitor, so between an unguarded read and the save it could
            // land, be overwritten, and leave the read reporting "nothing was changed" -- losing the
            // one warning this exists to produce. save() takes this monitor itself; holding it
            // across both makes the pair atomic against that thread.
            synchronized (config) {
                overwritesOperatorEdit = config.isFileModifiedSinceSnapshot();
                config.save();
            }
        } catch (IOException e) {
            // Outside the monitor above on purpose: the rollback needs rulesLock and nothing else,
            // so this class never holds the entity monitor and rulesLock at the same time and there
            // is no lock order to get wrong.
            synchronized (rulesLock) {
                rollback.run();
            }
            throw e;
        }
        if (overwritesOperatorEdit) {
            warnOperatorEditOverwritten();
        }
    }

    /**
     * Logs that this save wrote over a file somebody changed on disk while the server was running.
     * <p>
     * Overwriting is the contract -- a rule set changed by a command is what gets saved -- but it
     * must not be silent, because the edit that is lost was somebody's work. The framework already
     * says exactly this for the same event at shutdown
     * ({@code ConfigManager#warnOperatorEditOverwritten}); this is that line, for the saves this
     * module now performs during a command.
     * <p>
     * {@code isFileModifiedSinceSnapshot()} is the framework's own instrument and is marked
     * {@code @ApiStatus.Internal}, so calling it from a module is a deliberate, recorded exception:
     * the framework exposes no supported alternative, and the only other way to answer the question
     * is for this module to keep a second fingerprint of the same file, which would be a copy of
     * framework bookkeeping that drifts from it. Tracked as UltiKits/UltiTools-Reborn#527; when
     * that lands -- a supported accessor, or the warning moved inside {@code save()} itself --
     * this method goes away and the call with it. The call is safe either way -- it returns
     * {@code false} when no snapshot has been taken.
     */
    private void warnOperatorEditOverwritten() {
        UltiToolsPlugin plugin = config.getUltiToolsPlugin();
        File file = new File(plugin.getResourceFolderPath(), config.getConfigFilePath());
        plugin.getLogger().warn("Configuration file " + file.getAbsolutePath()
                + " was changed or removed on disk while the server was running, but an auto-reply"
                + " command also changed this configuration in memory. The in-memory configuration"
                + " was saved, so the changes made to the file while the server ran were"
                + " overwritten.");
    }

    /**
     * Undoes one mutating method's own change to the rule set. Not {@link Runnable}, only so the
     * name says what it is at the call site.
     */
    private interface Rollback {
        void run();
    }

    /**
     * Puts {@code snapshot}'s entries back into the live rules map, in {@code snapshot}'s own
     * iteration order, replacing whatever is there now.
     * <p>
     * The live map object is emptied and refilled rather than replaced, so every reference already
     * handed out by {@link #getRules()} still sees the restored set, and the snapshot holds the
     * original rule map instances rather than copies of them, so a restored rule is the same object
     * it was before. Callers hold {@link #rulesLock} across this, so the momentarily empty map is
     * never visible to {@link #findMatch(String)}. The one rollback that does not come through here
     * is {@code addRule}'s absent-rule-map branch, which restores the absence by replacing the
     * field; that branch is unreachable in production. Once the entity has been read from its file the rule map is a
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
