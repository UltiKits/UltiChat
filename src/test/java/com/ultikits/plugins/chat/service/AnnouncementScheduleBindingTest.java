package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.config.AnnouncementConfig;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.manager.ConfigManager;
import com.ultikits.ultitools.manager.PluginManager;
import com.ultikits.ultitools.manager.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiChat#13, reworked on the framework's config-bound {@code @Scheduled}
 * (UltiKits/UltiTools-Reborn#531): the three announcement periods come from
 * {@code announcements.{chat,bossbar,title}.interval}, in seconds.
 * <p>
 * These tests drive the framework's real {@link TaskManager} on MockBukkit's real scheduler, so
 * they prove this module's bindings against the code that runs them, not against a restatement of
 * it. Measured scheduler semantics (the framework's own binding tests rely on the same): a task
 * registered at tick 0 with the default delay 0 first runs at tick 1, then every period.
 */
@DisplayName("Announcement periods bound to config through the real TaskManager (UltiKits/UltiChat#13)")
class AnnouncementScheduleBindingTest {

    private ServerMock server;
    private TaskManager taskManager;
    private UltiChat module;
    private AnnouncementConfig config;
    private AnnouncementService service;
    private Object previousUltiTools;
    private final List<LogRecord> logs = new ArrayList<LogRecord>();
    private Handler capture;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        JavaPlugin host = MockBukkit.createMockPlugin();
        config = new AnnouncementConfig();
        service = new AnnouncementService(config);

        module = mock(UltiChat.class);
        lenient().when(module.getPluginName()).thenReturn("UltiTools-Chat");
        lenient().when(module.getMinUltiToolsVersion()).thenReturn(shippedApiVersion());

        ConfigManager configManager = mock(ConfigManager.class);
        lenient().when(configManager.getConfigEntities(module, AnnouncementConfig.class))
                .thenReturn(Collections.singletonList(config));
        UltiTools ultiTools = mock(UltiTools.class);
        lenient().when(ultiTools.getConfigManager()).thenReturn(configManager);
        previousUltiTools = swapUltiTools(ultiTools);

        taskManager = new TaskManager(host);
        capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
                // In-memory list; nothing to flush.
            }

            @Override
            public void close() {
                // Nothing to release.
            }
        };
        Bukkit.getLogger().addHandler(capture);
    }

    @AfterEach
    void tearDown() throws Exception {
        Bukkit.getLogger().removeHandler(capture);
        swapUltiTools(previousUltiTools);
        MockBukkit.unmock();
    }

    /**
     * Sets an interval field by name. Reflection rather than the Lombok setter so this class
     * compiles, and fails on a clear assertion, against a tree where the field does not exist.
     */
    private void setInterval(String field, int seconds) {
        try {
            com.ultikits.plugins.chat.utils.ChatTestHelper.setField(config, field, seconds);
        } catch (Exception e) {
            throw new AssertionError("AnnouncementConfig has no interval field '" + field + "'", e);
        }
    }

    private static Object swapUltiTools(Object value) throws Exception {
        Field field = UltiTools.class.getDeclaredField("ultiTools");
        field.setAccessible(true); // NOPMD - the framework singleton has no setter
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }

    private static int shippedApiVersion() throws Exception {
        InputStream in = AnnouncementScheduleBindingTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertThat(in).as("shipped plugin.yml").isNotNull();
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getInt("api-version", -1);
        } finally {
            in.close();
        }
    }

    private List<String> infoLines() {
        List<String> lines = new ArrayList<String>();
        for (LogRecord record : logs) {
            if (Level.INFO.equals(record.getLevel())) {
                lines.add(record.getMessage());
            }
        }
        return lines;
    }

    private List<String> warningLines() {
        List<String> lines = new ArrayList<String>();
        for (LogRecord record : logs) {
            if (Level.WARNING.equals(record.getLevel())) {
                lines.add(record.getMessage());
            }
        }
        return lines;
    }

    /** Advances the real scheduler one tick at a time to {@code until}, recording the ticks at which the chat line arrived. */
    private List<Integer> chatTicksUntil(PlayerMock player, int until) {
        List<Integer> ticks = new ArrayList<Integer>();
        while (Bukkit.getCurrentTick() < until) {
            server.getScheduler().performOneTick();
            String message;
            while ((message = player.nextMessage()) != null) {
                if (message.contains("Welcome! Type /help") || message.contains("Please follow server rules")) {
                    ticks.add(Bukkit.getCurrentTick());
                }
            }
        }
        return ticks;
    }

    @Test
    @DisplayName("Each period is its configured seconds times 20 ticks, and the chat broadcast really runs on it")
    void periodsComeFromConfig() {
        setInterval("chatInterval", 7);
        setInterval("bossBarInterval", 3);
        setInterval("titleInterval", 11);
        PlayerMock player = server.addPlayer();

        taskManager.registerScheduledMethods(module, service);

        assertThat(infoLines()).anySatisfy(line -> assertThat(line)
                .contains("AnnouncementService.broadcastChat").contains("period=140")
                .contains("periodKey=announcements.chat.interval=7s"));
        assertThat(infoLines()).anySatisfy(line -> assertThat(line)
                .contains("AnnouncementService.broadcastBossBar").contains("period=60")
                .contains("periodKey=announcements.bossbar.interval=3s"));
        assertThat(infoLines()).anySatisfy(line -> assertThat(line)
                .contains("AnnouncementService.broadcastTitle").contains("period=220")
                .contains("periodKey=announcements.title.interval=11s"));
        assertThat(server.getScheduler().getPendingTasks()).hasSize(3);

        assertThat(chatTicksUntil(player, 300)).containsExactly(1, 141, 281);
    }

    @Test
    @DisplayName("A reload with a changed interval reschedules from the last run, keeping the cycle's place")
    void reloadAppliesAChangedInterval() {
        setInterval("chatInterval", 5);
        PlayerMock player = server.addPlayer();
        taskManager.registerScheduledMethods(module, service);

        assertThat(chatTicksUntil(player, 110)).containsExactly(1, 101);

        // What /ul reload does after reloading the config file: the framework's reschedule step.
        setInterval("chatInterval", 2);
        taskManager.rescheduleBound(module);

        // Last run 101 + 40 ticks = 141, then every 40: not early, not restarted from tick 110.
        assertThat(chatTicksUntil(player, 190)).containsExactly(141, 181);
    }

    @Test
    @DisplayName("An invalid value on reload keeps the running interval and warns, naming the key")
    void invalidValueOnReloadKeepsTheRunningInterval() {
        setInterval("chatInterval", 5);
        PlayerMock player = server.addPlayer();
        taskManager.registerScheduledMethods(module, service);
        assertThat(chatTicksUntil(player, 110)).containsExactly(1, 101);

        setInterval("chatInterval", 0);
        taskManager.rescheduleBound(module);

        assertThat(warningLines()).anySatisfy(line -> assertThat(line).contains("announcements.chat.interval"));
        assertThat(chatTicksUntil(player, 210)).containsExactly(201);
    }

    @Test
    @DisplayName("An invalid value at load refuses the registration, naming the key")
    void invalidValueAtLoadRefuses() {
        setInterval("chatInterval", 0);

        assertThatThrownBy(() -> taskManager.registerScheduledMethods(module, service))
                .hasMessageContaining("announcements.chat.interval");
    }

    @Test
    @DisplayName("The shipped plugin.yml declares the api-version the framework requires of a binding module")
    void pluginYmlMeetsTheBindingFloor() throws Throwable {
        assertThat(shippedApiVersion()).isGreaterThanOrEqualTo(630);

        // The framework's own load-time check, on this module's own beans: passes at the shipped
        // api-version ...
        validateConfigBindings(module);

        // ... and, as the control that the check really sees this module's binding, refuses the
        // same module declaring 620.
        UltiChat old = mock(UltiChat.class);
        lenient().when(old.getPluginName()).thenReturn("UltiTools-Chat");
        when(old.getMinUltiToolsVersion()).thenReturn(620);
        assertThatThrownBy(() -> validateConfigBindings(old))
                .hasMessageContaining("api-version")
                .hasMessageContaining("630");
    }

    private void validateConfigBindings(UltiToolsPlugin plugin) throws Throwable {
        SimpleContainer container = mock(SimpleContainer.class);
        lenient().when(container.getSingletonValues()).thenReturn(Collections.<Object>singletonList(service));
        Method method = PluginManager.class.getDeclaredMethod("validateConfigBindings",
                UltiToolsPlugin.class, SimpleContainer.class);
        method.setAccessible(true); // NOPMD - package-private framework entry point
        try {
            method.invoke(null, plugin, container);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
