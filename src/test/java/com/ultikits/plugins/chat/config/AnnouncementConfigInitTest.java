package com.ultikits.plugins.chat.config;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * UltiKits/UltiChat#13. The interval fields carry no {@code @Range} because the framework's binding
 * owns their range: a bound field carries no range check of the module's own. The reason is
 * behavioural: {@code AbstractConfigEntity#init} -- the method {@code ConfigManager#reloadConfigs}
 * calls for every entity on {@code /ul reload} -- throws on a {@code @Range} violation after writing the
 * file's values into the fields, and that exception aborts the whole reload. These tests drive the
 * real {@code init} on a real file, so a {@code @Range} put back on an interval field fails here on
 * behaviour, not only on the annotation check in {@code AnnouncementConfigTest}.
 */
@DisplayName("AnnouncementConfig#init with an out-of-range interval (UltiKits/UltiChat#13)")
class AnnouncementConfigInitTest {

    private static final String WITH_ZERO_INTERVAL =
            "announcements:\n  chat:\n    enabled: true\n    interval: 0\n";

    private static final String WITH_ZERO_BOSSBAR_DURATION =
            "announcements:\n  bossbar:\n    duration: 0\n";

    /** A module whose config folder is {@code dir}, so {@code init} reads the file written there. */
    private static UltiToolsPlugin moduleWithConfigIn(final File dir) throws Exception {
        UltiChat plugin = mock(UltiChat.class);
        // getConfigFile is protected final on the framework class; stub it through reflection.
        Method getConfigFile = UltiToolsPlugin.class.getDeclaredMethod("getConfigFile", String.class);
        getConfigFile.setAccessible(true); // NOPMD - framework accessor, not visible to this package
        getConfigFile.invoke(doReturn(new File(dir, "config/announcements.yml")).when(plugin), anyString());
        doReturn("UltiTools-Chat").when(plugin).getPluginName();
        return plugin;
    }

    private static void write(File dir, String body) throws IOException {
        File file = new File(dir, "config/announcements.yml");
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("An interval of 0 loads without throwing, so a reload goes on to the framework's binding step")
    void zeroIntervalDoesNotAbortInit(@TempDir File dir) throws Exception {
        write(dir, WITH_ZERO_INTERVAL);
        AnnouncementConfig config = new AnnouncementConfig();

        config.init(moduleWithConfigIn(dir));

        // The value reached the field; judging it is the binding's job (keep the running period and
        // warn on reload, refuse at load).
        assertThat(config.getChatInterval()).isZero();
        assertThat(config.isLastInitIncomplete()).isFalse();
    }

    @Test
    @DisplayName("POSITIVE CONTROL: the same path does throw for a field that has a @Range (bossbar.duration: 0)")
    void rangedFieldDoesAbortInit(@TempDir File dir) throws Exception {
        write(dir, WITH_ZERO_BOSSBAR_DURATION);
        AnnouncementConfig config = new AnnouncementConfig();
        UltiToolsPlugin module = moduleWithConfigIn(dir);

        assertThatThrownBy(() -> config.init(module)).hasMessageContaining("bossBarDuration");
        assertThat(config.isLastInitIncomplete()).isTrue();
    }
}
