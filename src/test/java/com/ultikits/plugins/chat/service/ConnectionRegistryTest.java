package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.utils.ChatTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConnectionRegistry} — the per-UUID connection generation counter that closes
 * the whole class of reconnect race UltiKits/UltiChat#20, #35 and the #40 review each found and
 * fixed one instance of (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29).
 */
@DisplayName("ConnectionRegistry Tests")
class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        ChatTestHelper.setUp();
        registry = new ConnectionRegistry();
    }

    @AfterEach
    void tearDown() throws Exception {
        ChatTestHelper.tearDown();
    }

    @Test
    @DisplayName("A UUID that has never joined has generation 0, current for no captured value")
    void neverJoinedHasNoGeneration() {
        UUID uuid = UUID.randomUUID();

        assertThat(registry.currentGeneration(uuid)).isZero();
        assertThat(registry.isCurrent(uuid, 0L)).isFalse();
    }

    @Test
    @DisplayName("A join assigns a generation, and that generation is current immediately afterward")
    void joinAssignsACurrentGeneration() {
        UUID uuid = UUID.randomUUID();

        long generation = registry.onJoin(uuid);

        assertThat(generation).isEqualTo(1L);
        assertThat(registry.currentGeneration(uuid)).isEqualTo(1L);
        assertThat(registry.isCurrent(uuid, 1L)).isTrue();
    }

    @Test
    @DisplayName("A second join for the same UUID (a reconnect) strictly increases the generation, and the old one is no longer current")
    void reconnectIncreasesTheGeneration() {
        UUID uuid = UUID.randomUUID();
        long first = registry.onJoin(uuid);

        long second = registry.onJoin(uuid);

        assertThat(second).isGreaterThan(first);
        assertThat(registry.currentGeneration(uuid)).isEqualTo(second);
        assertThat(registry.isCurrent(uuid, first)).as("the superseded generation").isFalse();
        assertThat(registry.isCurrent(uuid, second)).as("the current generation").isTrue();
    }

    @Test
    @DisplayName("A quit removes the generation entirely: no generation is current for this UUID afterward, not even the one just assigned")
    void quitRemovesTheGeneration() {
        UUID uuid = UUID.randomUUID();
        long generation = registry.onJoin(uuid);

        registry.onQuit(uuid);

        assertThat(registry.currentGeneration(uuid)).isZero();
        assertThat(registry.isCurrent(uuid, generation)).isFalse();
    }

    @Test
    @DisplayName("Quit does not affect a different UUID's own generation")
    void quitAffectsOnlyItsOwnUuid() {
        UUID quitter = UUID.randomUUID();
        UUID stayer = UUID.randomUUID();
        registry.onJoin(quitter);
        long stayerGeneration = registry.onJoin(stayer);

        registry.onQuit(quitter);

        assertThat(registry.currentGeneration(quitter)).isZero();
        assertThat(registry.isCurrent(stayer, stayerGeneration)).isTrue();
    }

    @Test
    @DisplayName("The table does not grow past who is currently connected: after a quit, this UUID leaves no entry behind")
    void tableDoesNotGrowPastCurrentlyConnectedPlayers() throws Exception {
        UUID uuid = UUID.randomUUID();
        registry.onJoin(uuid);

        registry.onQuit(uuid);

        @SuppressWarnings("unchecked")
        java.util.Map<UUID, Long> generations =
                (java.util.Map<UUID, Long>) ChatTestHelper.getField(registry, "generations");
        assertThat(generations).as("removed on quit, not merely left un-bumped").doesNotContainKey(uuid);
    }
}
