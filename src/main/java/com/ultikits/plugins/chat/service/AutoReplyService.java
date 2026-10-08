package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.AutoReplyConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.config.EntryPresence;

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
     * The one character a rule name may not contain. {@code config/autoreply.yml} stores each rule
     * under its name as a configuration path, so a name containing it is split into nested keys when
     * the file is written, and the next read finds a different rule, which never fires
     * (UltiKits/UltiChat#25).
     */
    public static final char UNUSABLE_NAME_CHARACTER = '.';

    /**
     * Thrown by a rule change whose save found the rule map replaced: a panel configuration update
     * replaces the whole {@code rules} field (the framework sets it reflectively, on the WebSocket
     * thread), and when that lands between a command's change and its save, or during the save, the
     * change went into a map the configuration no longer holds. The change is then neither active
     * nor on disk, and the command must say so rather than report success (UltiKits/UltiChat#29,
     * maintainer decision 2026-09-27). Nothing is merged: the panel's rules stay exactly as the
     * panel wrote them.
     */
    public static final class RulesReplacedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RulesReplacedException() {
            super("The auto-reply rule map was replaced while the change was being saved");
        }
    }

    /**
     * Guards {@link #findMatch(String)}'s iteration and every mutation and rollback in this class.
     * <p>
     * Not every reader of the rule map: {@link #getRules()} hands the live map out unguarded and
     * {@code ChatAdminCommands#onAutoReplyList} iterates it (main thread only, and only reads);
     * the rule write ({@code AbstractConfigEntity#saveOperatorMapEntry}) serialises the same map with this
     * lock deliberately not held (see below); and {@code AbstractConfigEntity#updateProperties} replaces the {@code rules}
     * field reflectively from the WebSocket thread, which no lock this class owns can guard -- if a
     * panel configuration write lands between a mutation here and its save, the save serialises the
     * panel's map. That is detected after the save rather than prevented: the change throws
     * {@link RulesReplacedException} and the command reports that it did not take effect
     * (UltiKits/UltiChat#29).
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
     * Deliberately NOT held across the rule write: the write is file I/O, and a
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
     * A name mapped to {@code null} in memory (e.g. malformed YAML such as {@code broken:}) is not a rule here,
     * but it is still a rule of that name <em>in the file</em>: the write below refuses it (see below).
     * Mirrors {@link #removeRule(String)}'s own present/absent distinction, which the command layer
     * already reports through a not-found message.
     *
     * <p>
     * The change reaches {@code config/autoreply.yml} before this method returns, so it survives
     * {@code /uchat reload} and {@code /ul reload}, both of which re-read the entity straight from
     * disk (UltiKits/UltiChat#17). If the write fails, the in-memory state is restored to exactly
     * what it was and the failure is rethrown -- no half-applied rule is left behind, and a caller
     * cannot report success for a change that is not on disk. A call that changes nothing writes
     * nothing.
     * <p>
     * Whether the file already holds the rule is decided by the framework, on the same read of the file as the write:
     * the write is made under {@link EntryPresence#MUST_BE_ABSENT}, so a rule the operator wrote by hand since the
     * last reload is never replaced, and no second reader of the file is kept here (UltiKits/UltiChat#51;
     * UltiKits/UltiTools-Reborn#623).
     *
     * @param name     the rule name (key); must not contain {@link #UNUSABLE_NAME_CHARACTER}
     * @param keyword  the keyword to match
     * @param response the response text
     * @throws com.ultikits.ultitools.config.ConfigEntryPresenceException if the file already holds a rule of that
     *                                  name (one the operator added by hand since the last reload, or a
     *                                  {@code null} placeholder); nothing is changed or written
     * @throws com.ultikits.ultitools.config.ConfigWriteRefusedException if the framework refused the write (a file it
     *                                  cannot read, one that changed since it was read, YAML anchors, ...); nothing is
     *                                  written and the rule set is unchanged
     * @throws IOException if the configuration could not be written; the rule set is left unchanged
     * @throws IllegalArgumentException if {@code name} contains {@link #UNUSABLE_NAME_CHARACTER};
     *                                  nothing is changed or written
     * @throws RulesReplacedException if a panel update replaced the rule map while the change was
     *                                being saved; the change did not take effect
     */
    public void addRule(String name, String keyword, String response) throws IOException {
        if (name.indexOf(UNUSABLE_NAME_CHARACTER) >= 0) {
            throw new IllegalArgumentException("Auto-reply rule name must not contain '"
                    + UNUSABLE_NAME_CHARACTER + "': " + name);
        }
        final Map<String, Map<String, Object>> rules;
        final Map<String, Map<String, Object>> rulesBefore;
        final boolean rulesWereAbsent;
        synchronized (rulesLock) {
            Map<String, Map<String, Object>> live = config.getRules();
            if (live != null && live.get(name) != null) {
                return;
            }
            rulesWereAbsent = live == null;
            if (rulesWereAbsent) {
                live = new HashMap<>();
                config.setRules(live);
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

        saveOrRestore(rules, () -> {
            if (rulesWereAbsent) {
                // Not an in-place restore, unlike every other rollback here: the field itself was
                // absent, so restoring it means putting the absence back. Reached when the file has
                // `rules:` with nothing under it (the framework then reads the setting as absent).
                config.setRules(null);
            } else {
                restore(rules, rulesBefore);
            }
        }, EntryPresence.MUST_BE_ABSENT, name);
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
     * <p>
     * Whether the file still holds the keyword is decided by the framework, on the same read of the file as the
     * write: the write is made under {@link EntryPresence#MUST_BE_PRESENT}, so a rule the operator deleted by hand
     * since the last reload is never written back, and no second reader of the file is kept here
     * (UltiKits/UltiChat#51; UltiKits/UltiTools-Reborn#623). The entry written is the rule's {@code keyword} key, so
     * a rule that has no {@code keyword} line in the file is refused as well: give it its first keyword by editing the
     * file.
     *
     * @param name    the rule name (key)
     * @param keyword the new keyword to match
     * @throws com.ultikits.ultitools.config.ConfigEntryPresenceException if the file no longer holds the rule's
     *                                keyword (the operator deleted the rule or the line by hand, or the rule never had
     *                                one); nothing is changed or written
     * @throws com.ultikits.ultitools.config.ConfigWriteRefusedException if the framework refused the write (a file it
     *                                cannot read, one that changed since it was read, YAML anchors, ...); nothing is
     *                                written and the rule is unchanged
     * @throws IOException if the configuration could not be written; the rule is left unchanged
     * @throws RulesReplacedException if a panel update replaced the rule map while the change was
     *                                being saved; the change did not take effect
     */
    public void setKeyword(String name, String keyword) throws IOException {
        final Map<String, Map<String, Object>> rules;
        final Map<String, Object> rule;
        final boolean hadKeyword;
        final Object keywordBefore;
        synchronized (rulesLock) {
            rules = config.getRules();
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

        // Only the keyword: a hand edit of this rule's response, mode or commands since the load stays
        // (orchestrator ruling of 2026-10-05 on the #50 review, P3-1).
        saveOrRestore(rules, () -> {
            if (hadKeyword) {
                rule.put("keyword", keywordBefore);
            } else {
                rule.remove("keyword");
            }
        }, EntryPresence.MUST_BE_PRESENT, name, "keyword");
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
     * @throws RulesReplacedException if a panel update replaced the rule map while the change was
     *                                being saved; the change did not take effect
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

        saveOrRestore(rules, () -> restore(rules, rulesBefore), null, name);
    }

    /**
     * Writes the one rule entry the command changed - the whole rule for {@code add} and {@code remove}, only its
     * keyword for {@code setkeyword} - and puts the rule set back as it was if the write does not happen.
     * <p>
     * One implementation for all three mutating methods, so the save and the rollback cannot
     * drift apart between them. The rollback runs under
     * {@link #rulesLock}, so no chat thread observes a half-restored rule set.
     * <p>
     * <b>Why it cannot overwrite other operator content.</b> The write is
     * {@code saveOperatorMapEntry(presence, "autoreply.rules", keys)} (the two-argument form for {@code remove}): the
     * command is the operator's explicit request to change what it names - a new rule, the removal of a rule, or one
     * rule's keyword - so exactly that entry is written, and nothing else. Every other rule, one the operator added or
     * edited by hand since the load included, and every other key, comment and byte of {@code config/autoreply.yml}
     * stay, because the framework's write gate publishes the file only when everything outside that entry is
     * byte-identical to it (maintainer decision 2026-10-04, "what code may write, by file type": {@code /autoreply}
     * writes only that rule; UltiKits/UltiChat#50). For {@code add} and {@code setkeyword} the entry's presence in the
     * file is a precondition the framework decides on the same read it verifies the write against
     * ({@link EntryPresence#MUST_BE_ABSENT}: the command creates a rule and never replaces one the operator wrote;
     * {@link EntryPresence#MUST_BE_PRESENT}: it changes the keyword of a rule and never writes back one the operator
     * deleted), so this class keeps no reader of the file of its own. A write the framework refuses throws
     * {@link com.ultikits.ultitools.config.ConfigWriteRefusedException}, an {@link IOException}
     * ({@link com.ultikits.ultitools.config.ConfigEntryPresenceException} for the precondition): nothing was written,
     * the rule set is rolled back, and the command says what happened (maintainer decision 2026-10-05).
     *
     * <p>
     * After a successful write, the rule map the caller changed must still be the one the
     * configuration holds. If a panel update replaced it in the meantime, the change is in a map
     * nothing refers to any more: {@link RulesReplacedException} says so, and nothing is merged or
     * rolled back, since the configuration now holds the panel's rules (UltiKits/UltiChat#29).
     *
     * @param changed  the rule map the caller changed
     * @param rollback undoes this method's caller's mutation; run on any failure of the write
     * @param presence the condition the write is made under, or {@code null} for none ({@code remove})
     * @param keys     the map keys from {@code autoreply.rules} down to the one entry written
     * @throws IOException the write failure or refusal, rethrown after the rollback
     * @throws RulesReplacedException if the configuration no longer holds {@code changed}
     */
    private void saveOrRestore(Map<String, Map<String, Object>> changed, Rollback rollback, EntryPresence presence,
                               String... keys) throws IOException {
        try {
            // The write takes the entity's own monitor and the framework's write gate checks the rest of the
            // file itself, so this method keeps no copy of that check and no monitor of its own.
            if (presence == null) {
                config.saveOperatorMapEntry("autoreply.rules", keys);
            } else {
                config.saveOperatorMapEntry(presence, "autoreply.rules", keys);
            }
        } catch (IOException | RuntimeException e) {
            // Any failure, not only an IOException: an unchecked one from the write must not leave the changed rule
            // in memory, answering chat until the next reload (UltiKits/UltiChat#51 P3-2).
            // The rollback needs rulesLock and nothing else, so this class never holds the entity
            // monitor and rulesLock at the same time and there is no lock order to get wrong.
            synchronized (rulesLock) {
                rollback.run();
            }
            throw e;
        }
        if (config.getRules() != changed) {
            throw new RulesReplacedException();
        }
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
     * field; that branch runs when the file's {@code rules:} holds nothing and the framework reads the setting
     * as absent (see {@link #addRule}). Once the entity has been read from its file the rule map is a
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
