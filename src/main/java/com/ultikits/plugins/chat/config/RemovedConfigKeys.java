package com.ultikits.plugins.chat.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

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
     * it, each mapped to what an operator should be told about it. Insertion order is the order the
     * warnings are emitted in.
     */
    private static final Map<String, Map<String, String>> REMOVED;

    static {
        Map<String, String> announcements = new LinkedHashMap<String, String>();
        announcements.put("announcements.chat.interval", fixedPeriod("chat announcement", 300));
        announcements.put("announcements.bossbar.interval", fixedPeriod("boss bar announcement", 60));
        announcements.put("announcements.title.interval", fixedPeriod("title announcement", 600));

        Map<String, String> chat = new LinkedHashMap<String, String>();
        chat.put("anti-spam.mute-duration",
                "Nothing ever used it: players were never muted automatically, whatever it said, "
                        + "and a spam trip still refuses only the offending message "
                        + "(UltiKits/UltiChat#15). Automatic muting is requested as a feature in "
                        + "UltiKits/UltiChat#30.");

        Map<String, Map<String, String>> removed = new LinkedHashMap<String, Map<String, String>>();
        removed.put("config/announcements.yml", Collections.unmodifiableMap(announcements));
        removed.put("config/chat.yml", Collections.unmodifiableMap(chat));
        REMOVED = Collections.unmodifiableMap(removed);
    }

    private RemovedConfigKeys() {
        // Utility class
    }

    private static String fixedPeriod(String what, int seconds) {
        return "Nothing ever read this key: the " + what + " has always run every " + seconds
                + " seconds whatever it said, and it still does -- the period is fixed in this "
                + "version (UltiKits/UltiChat#13). Making it configurable is requested in "
                + "UltiKits/UltiTools-Reborn#531.";
    }

    /**
     * The keys this class knows about, per file, in the order it reports them.
     *
     * @return an unmodifiable map of configuration file to (removed key path to guidance)
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
     * @param fileFor resolves a configuration path such as {@code config/announcements.yml} to the
     *                operator's own copy of that file
     * @param warn    where to send each warning, normally the module logger's warn method
     */
    public static void warnAboutLeftovers(Function<String, File> fileFor, Consumer<String> warn) {
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
                    warn.accept("UltiChat: " + configFile.getPath() + " still contains '"
                            + key.getKey() + "', which this version no longer reads. "
                            + key.getValue()
                            + " Delete the key from the file to silence this warning.");
                }
            }
        }
    }
}
