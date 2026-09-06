package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for UltiChat's shared test-time Bukkit registry bootstrap.
 * <p>
 * This sentinel deliberately bootstraps through {@link ChatTestHelper#setUp()} /
 * {@link ChatTestHelper#tearDown()} — the module's one shared test-time entry point — instead of
 * calling {@code MockBukkit.mock()} itself. A sentinel that mocks its own, unrelated live server
 * would stay green even if {@code ChatTestHelper} regressed to a bare {@code mock(Server.class)}
 * (no live server backing at all): it would simply be validating a server nothing in the module's
 * real test suite uses. Routing through {@code ChatTestHelper} means this guard shares fate with
 * every other test class in this module.
 * <p>
 * Every assertion here depends on a live server, never a bare registry constant. This module's
 * own triggering class is {@code com.cryptomorin.xseries.XSound} — but {@code XSound}'s own
 * static initialiser runs the same {@code XRegistry}-backed chain, which resolves through
 * {@code java.util.ServiceLoader} from the classpath alone, independent of whether a live server
 * is bootstrapped. Asserting on an {@code XSound} value here would therefore pass in exactly the
 * state this guard exists to detect, so it deliberately never appears below. If
 * {@code ChatTestHelper#setUp()} is ever edited to stop wrapping a live {@code MockBukkit.mock()}
 * server (for example, replacing {@code spy(MockBukkit.mock())} with a bare
 * {@code mock(Server.class)}), every test below must go red.
 * <p>
 * That last claim was measured, and holds only because {@link #liveServerIsBootstrapped()} asserts
 * the installed server's <em>identity</em>. A plain {@code assertNotNull(Bukkit.getServer())} is
 * satisfied by a bare {@code mock(Server.class)} too — it would have left this sentinel three
 * quarters sensitive, silently green on the one assertion that names the thing being guarded.
 */
class UltiChatRegistrySentinelTest {

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    @Test
    void liveServerIsBootstrapped() {
        // assertNotNull(Bukkit.getServer()) would NOT catch the regression this class
        // guards: a bare mock(Server.class) is also non-null, so that assertion stays
        // green in exactly the state this sentinel exists to detect. Assert the installed
        // server's identity instead. Mockito's spy() of a ServerMock is still a
        // ServerMock, so wrapping in ChatTestHelper.setUp() does not break this.
        assertInstanceOf(ServerMock.class, Bukkit.getServer(),
                "live server bootstrap must be present: Bukkit.getServer() must be a MockBukkit "
                        + "ServerMock, not a bare Mockito stub");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    @Test
    void itemStackConstructionResolvesRegistry() {
        // Item construction — unlike registry constant resolution — does need a live
        // server: new ItemStack(Material) goes through Material.asItemType() into a
        // registry lookup. Without one, MockBukkit's RegistryMock catches the resulting
        // ExceptionInInitializerError and rethrows it as IncompatiblePaperVersionException,
        // whose message reads "Version Mismatch!". The message is misleading and the
        // assertion message below exists so nobody acts on it.
        ItemStack stack = assertDoesNotThrow(() -> new ItemStack(Material.DIAMOND),
                "item construction must resolve the registry on a live server. If this failed with "
                        + "MockBukkit's IncompatiblePaperVersionException (\"Version Mismatch!\"), do NOT "
                        + "bump paper.version — it already matches the version that message names. "
                        + "MockBukkit rethrows ANY ExceptionInInitializerError raised while loading its "
                        + "registry as that exception, and here it means ChatTestHelper.setUp() stopped "
                        + "bootstrapping a live server, so Bukkit statics resolve to null.");
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
