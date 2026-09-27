package com.ultikits.plugins.chat.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.Range;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Auto-reply settings.
 * <p>
 * The keyword and response of the two example rules are written in the server's language when the
 * module starts and after {@code /ul reload} ({@link #materializeText}); see {@link ConfigTextDefaults}.
 */
@Getter
@Setter
@ConfigEntity("config/autoreply.yml")
public class AutoReplyConfig extends AbstractConfigEntity {

    /** The keyword and response every earlier version shipped for each example rule, by rule name. */
    static final Map<String, List<String>> SHIPPED_RULES;

    static {
        Map<String, List<String>> rules = new LinkedHashMap<>();
        rules.put("server-ip", Collections.unmodifiableList(Arrays.asList("server IP", "Server address: play.example.com")));
        rules.put("rules-info", Collections.unmodifiableList(Arrays.asList("rules", "Please check /rules for server rules.")));
        SHIPPED_RULES = Collections.unmodifiableMap(rules);
    }

    @ConfigEntry(path = "autoreply.enabled", comment = "Enable auto-reply / 启用自动回复")
    private boolean enabled = true;

    @Range(min = 0, max = 300)
    @ConfigEntry(path = "autoreply.cooldown", comment = "Global cooldown between auto-replies (seconds) / 全局冷却(秒)")
    private int cooldown = 10;

    @ConfigEntry(path = "autoreply.rules", comment = "Auto-reply rules / 自动回复规则")
    private Map<String, Map<String, Object>> rules = new HashMap<String, Map<String, Object>>() {{
        HashMap<String, Object> rule1 = new HashMap<>();
        rule1.put("keyword", SHIPPED_RULES.get("server-ip").get(0));
        rule1.put("response", SHIPPED_RULES.get("server-ip").get(1));
        rule1.put("mode", "contains");
        rule1.put("case-sensitive", false);
        put("server-ip", rule1);

        HashMap<String, Object> rule2 = new HashMap<>();
        rule2.put("keyword", SHIPPED_RULES.get("rules-info").get(0));
        rule2.put("response", SHIPPED_RULES.get("rules-info").get(1));
        rule2.put("mode", "contains");
        rule2.put("case-sensitive", false);
        put("rules-info", rule2);
    }};

    public AutoReplyConfig() {
        super("config/autoreply.yml");
    }

    /**
     * Replaces each example rule whose keyword and response are still built-in text with the pair in
     * the server's language (maintainer decision 2026-09-25, UltiKits/UltiChat#18). The two are compared
     * together: a rule is built-in only when its pair is exactly the pair an earlier version shipped or
     * the pair of one language this jar ships; any other rule is the operator's. A changed rule is
     * replaced by a copy holding every other key as it was.
     *
     * @param text the module jar's text for a catalogue key, in the language the framework loads
     * @return whether any rule changed, so the caller saves the file once
     */
    public boolean materializeText(Function<String, String> text) {
        if (rules == null) {
            return false;
        }
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(AutoReplyConfig.class);
        boolean changed = false;
        for (Map.Entry<String, List<String>> shipped : SHIPPED_RULES.entrySet()) {
            Map<String, Object> rule = rules.get(shipped.getKey());
            if (rule == null || !(rule.get("keyword") instanceof String) || !(rule.get("response") instanceof String)) {
                continue;
            }
            String prefix = "config_autoreply_" + shipped.getKey().replace('-', '_');
            String keywordKey = prefix + "_keyword";
            String responseKey = prefix + "_response";
            String keyword = ConfigTextDefaults.currentText(text, "", keywordKey);
            String response = ConfigTextDefaults.currentText(text, "", responseKey);
            if (keyword == null || response == null) {
                continue;
            }
            Set<List<String>> tracked = new LinkedHashSet<>();
            tracked.add(shipped.getValue());
            for (Map<String, String> catalogue : jar.values()) {
                if (catalogue.containsKey(keywordKey) && catalogue.containsKey(responseKey)) {
                    tracked.add(Arrays.asList(catalogue.get(keywordKey), catalogue.get(responseKey)));
                }
            }
            List<String> value = Arrays.asList((String) rule.get("keyword"), (String) rule.get("response"));
            List<String> current = Arrays.asList(keyword, response);
            if (value.equals(current) || !tracked.contains(value)) {
                continue;
            }
            Map<String, Object> updated = new LinkedHashMap<>(rule);
            updated.put("keyword", keyword);
            updated.put("response", response);
            rules.put(shipped.getKey(), updated);
            changed = true;
        }
        return changed;
    }
}
