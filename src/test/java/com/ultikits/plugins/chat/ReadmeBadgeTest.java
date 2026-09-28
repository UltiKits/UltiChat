package com.ultikits.plugins.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The README's UltiTools-API badge is the first thing a server owner reads about which framework
 * this module needs. A server on an older framework refuses the module, so the badge must name at
 * least the {@code api-version} the shipped {@code plugin.yml} declares.
 * <p>
 * The badge is compared as a {@code (major, minor)} pair against the level {@code api-version}
 * encodes ({@code 630} is 6.3), so a two-digit component cannot collide with the next minor version
 * ({@code 6.2.10} is not 6.3), and the README is found from the project directory Surefire reports,
 * not from whatever directory the runner started in (UltiKits/UltiChat#36).
 */
@DisplayName("The README's UltiTools-API badge names at least the declared api-version (UltiKits/UltiChat#36)")
class ReadmeBadgeTest {

    private static final Pattern BADGE = Pattern.compile("UltiTools--API-(\\d+\\.\\d+\\.\\d+)-");

    /** The shipped {@code plugin.yml}'s {@code api-version}, e.g. {@code 630}. */
    private static int shippedApiVersion() throws IOException {
        InputStream in = ReadmeBadgeTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertThat(in).as("shipped plugin.yml").isNotNull();
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getInt("api-version", -1);
        } finally {
            in.close();
        }
    }

    /** The README in the project directory ({@code basedir}, set by Surefire), else the working directory. */
    private static Path readme() {
        String basedir = System.getProperty("basedir");
        if (basedir != null) {
            Path candidate = Paths.get(basedir, "README.md");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return Paths.get("README.md");
    }

    /**
     * Whether the badge version {@code major.minor.patch} is at least the framework level
     * {@code apiVersion} encodes: {@code 630} is major 6, minor 3. The patch never counts.
     */
    static boolean badgeMeets(String version, int apiVersion) {
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int requiredMajor = apiVersion / 100;
        int requiredMinor = (apiVersion % 100) / 10;
        return major > requiredMajor || (major == requiredMajor && minor >= requiredMinor);
    }

    @Test
    @DisplayName("the README's badge names at least the shipped api-version")
    void readmeBadgeAdvertisesTheDeclaredFloor() throws Exception {
        String text = new String(Files.readAllBytes(readme()), StandardCharsets.UTF_8);
        Matcher badge = BADGE.matcher(text);
        // Control: the badge is really there and parsed, so the comparison below is not vacuous.
        assertThat(badge.find()).as("README carries the UltiTools-API badge").isTrue();
        int apiVersion = shippedApiVersion();
        assertThat(apiVersion).as("control: plugin.yml declares an api-version").isPositive();

        assertThat(badgeMeets(badge.group(1), apiVersion))
                .as("badge %s against api-version %d", badge.group(1), apiVersion)
                .isTrue();
    }

    @Test
    @DisplayName("a two-digit patch does not count as the next minor version: 6.2.10 is below 630")
    void twoDigitPatchIsNotTheNextMinor() {
        assertThat(badgeMeets("6.2.10", 630)).isFalse();
    }

    @Test
    @DisplayName("control: 6.3.0, 6.3.12, 6.10.0 and 7.0.0 all meet 630; 6.2.9 and 5.9.9 do not")
    void pairComparison() {
        assertThat(badgeMeets("6.3.0", 630)).isTrue();
        assertThat(badgeMeets("6.3.12", 630)).isTrue();
        assertThat(badgeMeets("6.10.0", 630)).isTrue();
        assertThat(badgeMeets("7.0.0", 630)).isTrue();
        assertThat(badgeMeets("6.2.9", 630)).isFalse();
        assertThat(badgeMeets("5.9.9", 630)).isFalse();
    }
}
