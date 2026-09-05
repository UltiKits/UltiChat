package com.ultikits.plugins.chat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for UltiChat's test-time Bukkit registry bootstrap.
 * <p>
 * Every assertion here depends on a live server, never a bare registry constant. This module's
 * own triggering class is {@code com.cryptomorin.xseries.XSound} — but {@code XSound}'s own
 * static initialiser runs the same {@code XRegistry}-backed chain, which resolves through
 * {@code java.util.ServiceLoader} from the classpath alone, independent of whether
 * {@link org.mockbukkit.mockbukkit.MockBukkit#mock()} was ever called. Asserting on an
 * {@code XSound} value here would therefore pass in exactly the state this guard exists to
 * detect, so it deliberately never appears below. If this class is ever edited to remove the
 * {@code @BeforeEach}/{@code @AfterEach} lifecycle pair, every test below must go red.
 */
class UltiChatRegistrySentinelTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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
