package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
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
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
