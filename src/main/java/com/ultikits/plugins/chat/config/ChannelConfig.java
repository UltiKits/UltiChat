package com.ultikits.plugins.chat.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Channel settings.
 * <p>
 * The display names of the three shipped channels are written in the server's language when the module
 * starts and after {@code /ul reload}, and a channel format earlier versions shipped is removed from the
 * file ({@link #materializeText}); see {@link ConfigTextDefaults}.
 */
@Getter
@Setter
@ConfigEntity("config/channels.yml")
public class ChannelConfig extends AbstractConfigEntity {

    /** The display name every earlier version shipped for each shipped channel, by channel ID. */
    static final Map<String, String> SHIPPED_DISPLAY_NAMES;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("global", "&f[Global]");
        names.put("local", "&a[Local]");
        names.put("staff", "&c[Staff]");
        SHIPPED_DISPLAY_NAMES = Collections.unmodifiableMap(names);
    }

    /**
     * The three channel formats earlier versions shipped, and wrote into every server's
     * {@code channels.yml} (taken from the shipped file and the default map at the module's initial
     * commit, the only version either ever had). They were never applied, so an upgraded server
     * holding one of them has never seen it: removing it from the file keeps that server's chat line
     * exactly as it was and makes the file say so (UltiKits/UltiChat#16, #18). Two of them name no
     * player at all. The cost, accepted by the maintainer: an operator cannot deliberately choose one
     * of these exact strings -- any change, even one character, makes a format count as custom.
     */
    static final Set<String> LEGACY_SHIPPED_FORMATS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "{display}&f: {message}",
                    "{display}&7: {message}",
                    "&c[Staff] &f{player}&7: {message}")));

    @ConfigEntry(path = "channels.enabled", comment = "Enable channel system / 启用频道系统")
    private boolean enabled = true;

    @ConfigEntry(path = "channels.default-channel", comment = "Default channel for new players / 默认频道")
    private String defaultChannel = "global";

    /**
     * Channel definitions. A channel may set {@code format:} to replace the chat line for its
     * members, with {@code {display}} for the channel's display name; without one the line is the
     * global {@code chat.format} with the display name in front. Either way, a line format is applied
     * only while {@code channels.enabled} and {@code chat.format-enabled} (in {@code chat.yml}) are
     * both true. The shipped channels set none (UltiKits/UltiChat#16); a format earlier versions
     * shipped is removed by {@link #materializeText}.
     */
    @ConfigEntry(path = "channels.channels", comment = "Channel definitions / 频道定义")
    private Map<String, Map<String, Object>> channels = new HashMap<String, Map<String, Object>>() {{
        HashMap<String, Object> global = new HashMap<>();
        global.put("display-name", SHIPPED_DISPLAY_NAMES.get("global"));
        global.put("permission", "");
        global.put("range", -1);
        global.put("cross-world", true);
        put("global", global);

        HashMap<String, Object> local = new HashMap<>();
        local.put("display-name", SHIPPED_DISPLAY_NAMES.get("local"));
        local.put("permission", "");
        local.put("range", 100);
        local.put("cross-world", false);
        put("local", local);

        HashMap<String, Object> staff = new HashMap<>();
        staff.put("display-name", SHIPPED_DISPLAY_NAMES.get("staff"));
        staff.put("permission", "ultichat.channel.staff");
        staff.put("range", -1);
        staff.put("cross-world", true);
        put("staff", staff);
    }};

    public ChannelConfig() {
        super("config/channels.yml");
    }

    /**
     * Brings the channel definitions in line with what the chat shows (maintainer decision 2026-09-25,
     * UltiKits/UltiChat#18): a shipped channel's display name that is still built-in text becomes its
     * text in the server's language, and a {@code format:} equal to one of
     * {@link #LEGACY_SHIPPED_FORMATS} is removed from any channel. A channel whose definition changes is
     * replaced by a changed copy holding every other key as it was; any other channel is not touched.
     *
     * @param text the module jar's text for a catalogue key, in the language the framework loads
     * @return whether any definition changed, so the caller saves the file once
     */
    public boolean materializeText(Function<String, String> text) {
        if (channels == null) {
            return false;
        }
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(ChannelConfig.class);
        boolean changed = false;
        for (Map.Entry<String, Map<String, Object>> channel : new LinkedHashMap<>(channels).entrySet()) {
            Map<String, Object> def = channel.getValue();
            if (def == null) {
                continue;
            }
            Map<String, Object> updated = new LinkedHashMap<>(def);
            Object format = def.get("format");
            if (format instanceof String && LEGACY_SHIPPED_FORMATS.contains(format)) {
                updated.remove("format");
            }
            String shipped = SHIPPED_DISPLAY_NAMES.get(channel.getKey());
            Object name = def.get("display-name");
            if (shipped != null && name instanceof String) {
                String key = "config_channel_" + channel.getKey() + "_display_name";
                String materialized = ConfigTextDefaults.materialize((String) name,
                        ConfigTextDefaults.currentText(text, "", key), ConfigTextDefaults.tracked(jar, "", key, shipped));
                updated.put("display-name", materialized);
            }
            if (!updated.equals(def)) {
                channels.put(channel.getKey(), updated);
                changed = true;
            }
        }
        return changed;
    }
}
