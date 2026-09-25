package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.config.RemovedConfigKeys;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@UltiToolsModule
public class UltiChat extends UltiToolsPlugin {
    @Override
    public boolean registerSelf() {
        warnAboutConfiguration();
        return true;
    }

    /**
     * Runs after the framework has reloaded this module's configuration files, so an operator who
     * edits a file and reloads ({@code /uchat reload} or {@code /ul reload}) is told about it again.
     * Nothing is rescheduled here: the announcement periods are config-bound, and the framework's
     * own reload step reschedules a changed one before this hook runs (UltiTools-Reborn#531).
     */
    @Override
    protected void onReload() {
        warnAboutConfiguration();
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }

    /**
     * Tells the operator about configuration that no longer means what their own file says --
     * keys this version deleted but which an upgraded install still carries (the framework writes
     * a declared default only for a key that is missing, so it never removes one), and a value this
     * version now honours for the first time in a way that loosens detection.
     */
    private void warnAboutConfiguration() {
        RemovedConfigKeys.warnAboutLeftovers(this::operatorConfigFile, getLogger()::warn, this);
        warnIfDuplicateWindowShortened();
        warnAboutIncompleteChannelFormats();
    }

    /**
     * Gate-1 WR-01 on UltiKits/UltiChat#16: channel formats now apply. A format an operator edited
     * while it had no effect -- a recoloured copy of a shipped format that names no player, say --
     * is not one of the legacy strings, so it now applies as written. For each channel whose format
     * is in effect (channels enabled, {@code chat.format-enabled} true) and lacks a sender token or
     * the message token, the operator is told once per load which channel and what to add.
     */
    private void warnAboutIncompleteChannelFormats() {
        ChannelConfig channels = getConfig(ChannelConfig.class);
        ChatConfig chat = getConfig(ChatConfig.class);
        if (channels == null || chat == null || !channels.isEnabled() || !chat.isChatFormatEnabled()
                || channels.getChannels() == null) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> channel : channels.getChannels().entrySet()) {
            String format = ChannelService.ownFormat(channel.getValue());
            if (format == null) {
                continue;
            }
            boolean sender = format.contains("{player}") || format.contains("{displayname}")
                    || format.contains("%1$s");
            boolean text = format.contains("{message}") || format.contains("%2$s");
            if (sender && text) {
                continue;
            }
            String line = !sender && !text ? i18n("log_channel_format_missing_both")
                    : !sender ? i18n("log_channel_format_missing_sender")
                    : i18n("log_channel_format_missing_message");
            // The channel ID and the format are the operator's own text, so the three placeholders
            // are filled in one pass: a value that happens to contain a placeholder is shown as
            // written, never expanded by a later substitution.
            getLogger().warn(fillOnce(line,
                    "{FILE}", operatorConfigFile("config/channels.yml").getPath(),
                    "{CHANNEL}", channel.getKey(),
                    "{FORMAT}", format));
        }
    }

    /**
     * {@code template} with each placeholder replaced by its value in a single left-to-right pass, so
     * text inserted for one placeholder is never scanned for another.
     *
     * @param template          the language file's text
     * @param placeholdersValues placeholder, value, placeholder, value, ...
     * @return the filled text
     */
    static String fillOnce(String template, String... placeholdersValues) {
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        outer:
        while (i < template.length()) {
            for (int p = 0; p + 1 < placeholdersValues.length; p += 2) {
                String placeholder = placeholdersValues[p];
                if (template.startsWith(placeholder, i)) {
                    out.append(placeholdersValues[p + 1]);
                    i += placeholder.length();
                    continue outer;
                }
            }
            out.append(template.charAt(i));
            i++;
        }
        return out.toString();
    }

    /**
     * UltiKits/UltiChat#14: {@code anti-spam.duplicate-window} used to be ignored, so a repeat
     * counted however far apart its copies were sent. It now takes effect, and an upgraded server's
     * file still holds the value earlier versions wrote into it (60). Any positive window makes
     * duplicate detection more permissive than it was before the upgrade -- the new default, 0, is
     * the old unlimited rule -- so the operator is told once per load, with the value in force and
     * how to restore the old behaviour.
     */
    private void warnIfDuplicateWindowShortened() {
        ChatConfig chat = getConfig(ChatConfig.class);
        if (chat == null) {
            return;
        }
        int window = chat.getAntiSpamDuplicateWindow();
        if (window <= 0) {
            return;
        }
        // One pass: the server path is inserted as written, never re-read for {SECONDS}/{DEFAULT}.
        getLogger().warn(fillOnce(i18n("log_duplicate_window_applied"),
                "{FILE}", operatorConfigFile("config/chat.yml").getPath(),
                "{SECONDS}", String.valueOf(window),
                "{DEFAULT}", String.valueOf(ChatConfig.DEFAULT_DUPLICATE_WINDOW_SECONDS)));
    }

    /**
     * The operator's own copy of one of this module's configuration files.
     * <p>
     * A seam, package-private on purpose. {@code UltiToolsPlugin#getConfigFile} is {@code protected}
     * and {@code final}, so a test can neither call it nor stub it, and a mocked plugin returns
     * {@code null} from it -- without this method the check's wiring could not be asserted at all,
     * only its predicate.
     *
     * @param path a path relative to this module's configuration folder, such as
     *             {@code config/chat.yml}
     * @return the file that path resolves to for this installation
     */
    File operatorConfigFile(String path) {
        return getConfigFile(path);
    }
}
