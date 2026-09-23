package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.config.RemovedConfigKeys;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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
     * Nothing is rescheduled here: every period this module uses is fixed.
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
    }

    /**
     * UltiKits/UltiChat#14: {@code anti-spam.duplicate-window} used to be ignored, so a repeat
     * counted however far apart its copies were sent. It now takes effect, and an upgraded server's
     * file still holds the value earlier versions wrote into it (60). A window shorter than the new
     * declared default makes duplicate detection more permissive than it was before the upgrade, so
     * the operator is told once per load, with the value in force and how to change it.
     */
    private void warnIfDuplicateWindowShortened() {
        ChatConfig chat = getConfig(ChatConfig.class);
        if (chat == null) {
            return;
        }
        int window = chat.getAntiSpamDuplicateWindow();
        if (window >= ChatConfig.DEFAULT_DUPLICATE_WINDOW_SECONDS) {
            return;
        }
        getLogger().warn("UltiChat: " + operatorConfigFile("config/chat.yml").getPath()
                + " sets 'anti-spam.duplicate-window' to " + window + " seconds, and this version "
                + "applies it: a repeated message now counts as a duplicate only while its earlier "
                + "copies are at most " + window + " seconds old. Before this version the setting "
                + "was ignored and repeats counted however far apart they were sent, so duplicate "
                + "detection is now more permissive than before the upgrade. To keep it as close to "
                + "the previous behaviour as the setting allows, set it to "
                + ChatConfig.DEFAULT_DUPLICATE_WINDOW_SECONDS + " (the new default and the longest "
                + "allowed) and run /uchat reload (UltiKits/UltiChat#14).");
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
     *             {@code config/announcements.yml}
     * @return the file that path resolves to for this installation
     */
    File operatorConfigFile(String path) {
        return getConfigFile(path);
    }
}
