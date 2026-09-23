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
        RemovedConfigKeys.warnAboutLeftovers(this::operatorConfigFile, getLogger()::warn);
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
            String missing = !sender && !text ? "no sender name and no message text"
                    : !sender ? "no sender name" : "no message text";
            String add = !sender && !text ? "add {player} or {displayname}, and add {message}"
                    : !sender ? "add {player} or {displayname}" : "add {message}";
            getLogger().warn("UltiChat: " + operatorConfigFile("config/channels.yml").getPath()
                    + " gives channel '" + channel.getKey() + "' the format \"" + format + "\", "
                    + "which this version applies, so that channel's chat lines show " + missing
                    + ". To fix it, " + add + " to the format, or remove the format to use the "
                    + "global chat format (UltiKits/UltiChat#16).");
        }
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
        getLogger().warn("UltiChat: " + operatorConfigFile("config/chat.yml").getPath()
                + " sets 'anti-spam.duplicate-window' to " + window + " seconds, and this version "
                + "applies it: a repeated message now counts as a duplicate only while its earlier "
                + "copies are at most " + window + " seconds old. Before this version the setting "
                + "was ignored and repeats counted however far apart they were sent, so duplicate "
                + "detection is now more permissive than before the upgrade. To restore the previous "
                + "behaviour exactly, set it to " + ChatConfig.DEFAULT_DUPLICATE_WINDOW_SECONDS
                + " (no time limit, the new default) and run /uchat reload (UltiKits/UltiChat#14).");
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
