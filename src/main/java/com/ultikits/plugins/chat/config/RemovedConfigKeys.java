package com.ultikits.plugins.chat.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reports configuration keys this module no longer reads but which are still sitting in the
 * operator's own configuration files.
 * <p>
 * Deleting a {@code @ConfigEntry} stops the framework writing the key into a fresh file, but it
 * does nothing to files already on disk: the framework only ever writes a declared default for a key
 * that is <em>missing</em>, so an upgraded install keeps the key, keeps whatever value the operator
 * gave it, and gets no indication that the value stopped meaning anything. This class is that
 * indication -- one warning per leftover key, naming the module, the file and the key, and saying
 * where the setting went.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public final class RemovedConfigKeys {

    /**
     * Configuration file (relative to this module's configuration folder) to the keys removed from
     * it, each mapped to the language-file key of what an operator should be told about it. Insertion
     * order is the order the warnings are emitted in. The values are informational: the text is read by
     * {@link #reasonFor}, whose literal lookups the language guard checks, so a key added here needs a
     * case there too (a missing case fails loudly).
     */
    private static final Map<String, Map<String, String>> REMOVED;

    static {
        Map<String, String> chat = new LinkedHashMap<String, String>();
        chat.put("anti-spam.mute-duration", "removed_key_reason_mute_duration");

        Map<String, Map<String, String>> removed = new LinkedHashMap<String, Map<String, String>>();
        removed.put("config/chat.yml", Collections.unmodifiableMap(chat));
        REMOVED = Collections.unmodifiableMap(removed);
    }

    private RemovedConfigKeys() {
        // Utility class
    }

    /**
     * The guidance printed for one removed key, from the language file. Each removed key names its
     * own entry here, so a key added to {@link #REMOVED} without a case fails loudly instead of being
     * given another key's explanation.
     */
    private static String reasonFor(String removedKey, UltiToolsPlugin plugin) {
        switch (removedKey) {
            case "anti-spam.mute-duration":
                return plugin.i18n("removed_key_reason_mute_duration");
            default:
                throw new IllegalStateException("No guidance for removed key " + removedKey);
        }
    }

    /**
     * The keys this class knows about, per file, in the order it reports them.
     *
     * @return an unmodifiable map of configuration file to (removed key path to the language-file key
     *         of its guidance)
     */
    public static Map<String, Map<String, String>> removedKeys() {
        return REMOVED;
    }

    /**
     * Emit one warning per removed key that is still present in the operator's configuration files.
     * <p>
     * Silent for a file that is absent or unparseable -- there is then nothing to report and nothing
     * to be sure of. A parse failure is deliberately not reported here: the framework's own config
     * loading already reports an unparseable file, and a second message from this check would only
     * add noise to it.
     *
     * @param fileFor resolves a configuration path such as {@code config/chat.yml} to the
     *                operator's own copy of that file
     * @param warn    where to send each warning, normally the module logger's warn method
     * @param plugin  the module, whose language file gives the warning its text
     */
    public static void warnAboutLeftovers(Function<String, File> fileFor, Consumer<String> warn,
                                          UltiToolsPlugin plugin) {
        for (Map.Entry<String, Map<String, String>> file : REMOVED.entrySet()) {
            File configFile = fileFor.apply(file.getKey());
            if (configFile == null || !configFile.isFile()) {
                continue;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(configFile);
            } catch (IOException | InvalidConfigurationException e) {
                continue;
            }
            for (Map.Entry<String, String> key : file.getValue().entrySet()) {
                if (yaml.contains(key.getKey())) {
                    warn.accept(plugin.i18n("removed_key_warning")
                            .replace("{FILE}", configFile.getPath())
                            .replace("{REASON}", reasonFor(key.getKey(), plugin))
                            .replace("{KEY}", key.getKey()));
                }
            }
        }
    }
}
