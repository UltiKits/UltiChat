package com.ultikits.plugins.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The check that tells an operator a key this module deleted is still sitting in their own file.
 * <p>
 * Deleting a {@code @ConfigEntry} stops the framework writing the key into a fresh file, but does
 * nothing to a file already on disk, so an upgraded server keeps the key and whatever value it held,
 * silently. A check that never fires and a server with no leftover key print the same empty console,
 * so every assertion that the check stays quiet is paired with a positive control over the same file
 * shape with the key present.
 */
@DisplayName("RemovedConfigKeys")
class RemovedConfigKeysTest {

    private static final String ANNOUNCEMENTS_WITH_INTERVALS =
            "announcements:\n"
            + "  chat:\n    enabled: true\n    interval: 45\n    prefix: 'x'\n"
            + "  bossbar:\n    enabled: false\n    interval: 60\n    duration: 10\n"
            + "  title:\n    enabled: false\n    interval: 600\n    stay: 70\n";

    private static final String ANNOUNCEMENTS_WITHOUT_INTERVALS =
            "announcements:\n"
            + "  chat:\n    enabled: true\n    prefix: 'x'\n"
            + "  bossbar:\n    enabled: false\n    duration: 10\n"
            + "  title:\n    enabled: false\n    stay: 70\n";

    private static final String CHAT_WITH_MUTE_DURATION =
            "anti-spam:\n  enabled: true\n  cooldown: 2\n  mute-duration: 30\n  caps-limit: 70\n";

    private static final String CHAT_WITHOUT_MUTE_DURATION =
            "anti-spam:\n  enabled: true\n  cooldown: 2\n  caps-limit: 70\n";

    private final List<String> warnings = new ArrayList<String>();

    private File write(File dir, String relative, String body) throws IOException {
        File file = new File(dir, relative);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void check(final File dir) {
        check(dir, "en");
    }

    /**
     * Runs the check with a module whose language file is the real {@code language} catalogue. The
     * check takes the module since its text moved to the language file (UltiKits/UltiChat#18); it is
     * called by reflection, whichever signature this tree has, so this file compiles against both.
     */
    @SuppressWarnings("unchecked")
    private void check(final File dir, String language) {
        com.ultikits.ultitools.abstracts.UltiToolsPlugin plugin =
                org.mockito.Mockito.mock(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer(language));
        java.util.function.Function<String, File> fileFor = path -> new File(dir, path);
        java.util.function.Consumer<String> warn = warnings::add;
        try {
            for (java.lang.reflect.Method m : RemovedConfigKeys.class.getMethods()) {
                if (m.getName().equals("warnAboutLeftovers")) {
                    if (m.getParameterCount() == 3) {
                        m.invoke(null, fileFor, warn, plugin);
                    } else {
                        m.invoke(null, fileFor, warn);
                    }
                    return;
                }
            }
            throw new AssertionError("no warnAboutLeftovers method");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("A server path that contains a placeholder is named as written, not expanded (Codex P3)")
    void pathIsNotReExpanded(@TempDir File parent) throws IOException {
        File dir = new File(parent, "srv{REASON}{KEY}");
        File file = write(dir, "config/chat.yml", CHAT_WITH_MUTE_DURATION);

        check(dir);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains(file.getPath()).contains("'anti-spam.mute-duration'");
    }

    @Test
    @DisplayName("Under language: zh the warning is the Chinese catalogue text, naming the file and the key")
    void warningFollowsTheLanguageSetting(@TempDir File dir) throws IOException {
        File file = write(dir, "config/chat.yml", CHAT_WITH_MUTE_DURATION);
        String expected = com.ultikits.plugins.chat.i18n.CatalogueText.text("zh", "removed_key_warning").replace("{FILE}", file.getPath())
                .replace("{REASON}", com.ultikits.plugins.chat.i18n.CatalogueText.text("zh", "removed_key_reason_mute_duration"))
                .replace("{KEY}", "anti-spam.mute-duration");

        check(dir, "zh");

        assertThat(warnings).containsExactly(expected);
    }

    @Test
    @DisplayName("The announcement intervals are live keys again, so they are never reported (UltiKits/UltiChat#13)")
    void intervalsAreNotReported(@TempDir File dir) throws IOException {
        write(dir, "config/announcements.yml", ANNOUNCEMENTS_WITH_INTERVALS);
        // Control in the same run: a real leftover in the other file is still reported, so the
        // silence about the intervals is not a check that stopped reading files.
        write(dir, "config/chat.yml", CHAT_WITH_MUTE_DURATION);

        check(dir);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("'anti-spam.mute-duration'");
        assertThat(String.join("\n", warnings)).doesNotContain("interval");
        assertThat(RemovedConfigKeys.removedKeys()).doesNotContainKey("config/announcements.yml");
    }

    @Test
    @DisplayName("POSITIVE CONTROL: a leftover anti-spam.mute-duration gets one warning saying no player was ever muted (UltiKits/UltiChat#15)")
    void warnsAboutLeftoverMuteDuration(@TempDir File dir) throws IOException {
        File file = write(dir, "config/chat.yml", CHAT_WITH_MUTE_DURATION);

        check(dir);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("UltiChat").contains(file.getPath())
                .contains("'anti-spam.mute-duration'").contains("no longer reads")
                .contains("never muted").contains("UltiKits/UltiChat#30");
    }

    @Test
    @DisplayName("No warning when the files hold none of the removed keys")
    void quietOnACleanFile(@TempDir File dir) throws IOException {
        // Same file shapes as the positive controls, the removed keys taken out and nothing else changed.
        write(dir, "config/announcements.yml", ANNOUNCEMENTS_WITHOUT_INTERVALS);
        write(dir, "config/chat.yml", CHAT_WITHOUT_MUTE_DURATION);

        check(dir);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("No warning, and no failure, when the file does not exist")
    void quietWhenTheFileIsMissing(@TempDir File dir) {
        check(dir);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("An unparseable file is left to the framework's own load error rather than reported twice")
    void quietOnAnUnparseableFile(@TempDir File dir) throws IOException {
        write(dir, "config/announcements.yml", "announcements: [unclosed\n  chat: {");

        check(dir);

        assertThat(warnings).isEmpty();
    }
}
