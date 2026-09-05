package com.ultikits.plugins.chat.utils;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


/**
 * Test helper for mocking UltiChat framework singletons.
 * <p>
 * Call {@link #setUp()} in @BeforeEach and {@link #tearDown()} in @AfterEach.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
public final class ChatTestHelper {

    private ChatTestHelper() {
    }

    private static UltiChat mockPlugin;
    private static PluginLogger mockLogger;
    private static Server mockServer;

    @SuppressWarnings("unchecked")
    public static void setUp() throws Exception {
        mockPlugin = mock(UltiChat.class);
        mockLogger = mock(PluginLogger.class);
        lenient().when(mockPlugin.getLogger()).thenReturn(mockLogger);
        lenient().when(mockPlugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mockPlugin.getDataOperator(any())).thenReturn(mock(DataOperator.class));

        // Wrap a live MockBukkit server in a Mockito spy() rather than a bare
        // mock(Server.class): production code (ChatListener.playMentionSound -> XSound)
        // resolves org.bukkit.Registry, which needs a real, populated live server, not
        // a Mockito default-answers stub. The existing when(server.getX())-style stubs
        // below are rewritten as doReturn(...).when(spy) — when(spy.method()) would
        // invoke the real ServerMock method first, which Mockito rejects for methods
        // whose declared return type it cannot yet infer as a stub target.
        mockServer = spy(MockBukkit.mock());
        lenient().doReturn(Logger.getLogger("MockServer")).when(mockServer).getLogger();

        // ServerMock declares covariant concrete return types for these two methods
        // (BukkitSchedulerMock / PluginManagerMock, not the bare interfaces), so the
        // spy's stub must mock the concrete class Mockito reports it should return.
        BukkitSchedulerMock scheduler = mock(BukkitSchedulerMock.class);
        lenient().doReturn(scheduler).when(mockServer).getScheduler();

        PluginManagerMock pluginManager = mock(PluginManagerMock.class);
        lenient().doReturn(pluginManager).when(mockServer).getPluginManager();
        lenient().when(pluginManager.getPlugin(anyString())).thenReturn(null);

        lenient().doReturn(new ArrayList<>()).when(mockServer).getOnlinePlayers();
        lenient().doReturn(100).when(mockServer).getMaxPlayers();
        lenient().doReturn("MockServer").when(mockServer).getName();
        lenient().doReturn(null).when(mockServer).getPlayer(any(UUID.class));

        setStaticField(Bukkit.class, "server", mockServer);
    }

    public static void tearDown() throws Exception {
        mockPlugin = null;
        mockLogger = null;
        MockBukkit.unmock();
    }

    public static UltiChat getMockPlugin() {
        return mockPlugin;
    }

    public static PluginLogger getMockLogger() {
        return mockLogger;
    }

    public static Server getMockServer() {
        return mockServer;
    }

    /**
     * Create a mock Player with basic properties.
     */
    public static Player createMockPlayer(String name, UUID uuid) {
        Player player = mock(Player.class);
        lenient().when(player.getName()).thenReturn(name);
        lenient().when(player.getUniqueId()).thenReturn(uuid);
        lenient().when(player.hasPermission(anyString())).thenReturn(false);
        lenient().when(player.getDisplayName()).thenReturn(name);

        World world = mock(World.class);
        lenient().when(world.getName()).thenReturn("world");
        Location location = new Location(world, 100.5, 64.0, -200.5);
        lenient().when(player.getLocation()).thenReturn(location);
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.getServer()).thenReturn(mockServer);

        return player;
    }

    /**
     * Create a mock Player at a specific location.
     */
    public static Player createMockPlayerAt(String name, UUID uuid, World world, double x, double y, double z) {
        Player player = createMockPlayer(name, uuid);
        Location loc = new Location(world, x, y, z);
        lenient().when(player.getLocation()).thenReturn(loc);
        lenient().when(player.getWorld()).thenReturn(world);
        return player;
    }

    /**
     * Create a mock World.
     */
    public static World createMockWorld(String name) {
        World world = mock(World.class);
        lenient().when(world.getName()).thenReturn(name);
        return world;
    }

    // --- Reflection helpers ---

    public static void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true); // NOPMD
        field.set(null, value);
    }

    public static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true); // NOPMD
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found");
    }

    public static Object getField(Object target, String fieldName) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true); // NOPMD
                return field.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found");
    }

    public static Object getStaticField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true); // NOPMD
        return field.get(null);
    }
}
