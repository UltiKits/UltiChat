package com.ultikits.plugins.chat.service;

import com.ultikits.ultitools.annotations.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns each player's connection an incrementing generation number, so state that is written or
 * cleaned up from an asynchronous path (chat processing, in particular) can tell whether it is
 * still acting on behalf of the connection that started it, rather than a connection that has since
 * ended or been superseded by a reconnect.
 * <p>
 * This closes a whole class of reconnect race, not one report of it. Before this registry existed,
 * two different reconnect races were reported and fixed one at a time against the same underlying
 * gap: an old session's async cleanup could erase a new session's already-legitimate anti-spam state
 * (UltiKits/UltiChat#35, #40 review), and — narrowing the check that fix introduced — an old
 * session's async write could still land after a reconnect and pollute the new session's state
 * (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29). Both were the same defect: "is
 * anyone online under this UUID" is not the same fact as "is THIS the connection that started this
 * task", and every per-UUID check built only on the former will eventually let a stale connection
 * act on a newer one's behalf. A generation captured at task start and re-checked before every
 * state-changing operation answers the right question directly.
 * <p>
 * The generation table is deliberately not append-only: {@link #onQuit(UUID)} removes the entry
 * (main-thread, on quit), matching this module's established "expire on schedule or remove on quit"
 * rule for its other per-player tables, so this table does not grow for the life of the server.
 * 反连接管理器：为每次连接分配一个递增的编号，使得来自异步路径（尤其是聊天处理）的状态写入或清理操作，
 * 能够判断自己是否仍然代表启动该任务时的那次连接，而不是已经结束或被重连取代的连接。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Service
public class ConnectionRegistry {

    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();

    /**
     * Draws every generation from one counter shared across every UUID, not a separate counter per
     * UUID. This is deliberate, not merely simpler: {@link #onQuit(UUID)} removes a UUID's entry
     * entirely, so a per-UUID counter would restart from the same value on that UUID's next join,
     * and a quit is exactly what a reconnect always goes through first (Bukkit ends the old
     * connection, which this registry observes as a quit, before the new one's join). Two of that
     * UUID's connections, separated by any number of reconnects, could then coincidentally be
     * assigned the identical number, defeating {@link #isCurrent(UUID, long)} for the one case it
     * exists to catch. A single shared counter never repeats a value for any UUID, ever, regardless
     * of how many times a UUID has quit and rejoined in between.
     */
    private final AtomicLong nextGeneration = new AtomicLong();

    /**
     * Called on join (main thread). Assigns the player's UUID a new generation, strictly greater
     * than any generation this registry has ever assigned to any UUID, and returns it.
     *
     * @param playerId the joining player's UUID
     * @return the new, current generation for this UUID
     */
    public long onJoin(UUID playerId) {
        long generation = nextGeneration.incrementAndGet();
        generations.put(playerId, generation);
        return generation;
    }

    /**
     * Called on quit (main thread). Removes the tracked generation for this UUID entirely, so the
     * table holds only currently-connected players — not merely a stale gap left un-bumped, which
     * would let a later join at the same generation number defeat the check this registry exists to
     * provide.
     *
     * @param playerId the quitting player's UUID
     */
    public void onQuit(UUID playerId) {
        generations.remove(playerId);
    }

    /**
     * The generation currently on record for this UUID, or {@code 0} if none is (never joined, or
     * already quit). Read this once, at the start of a task whose later steps must be able to tell
     * whether they are still acting for the same connection.
     *
     * @param playerId the player's UUID
     * @return the current generation, or {@code 0} if there is none
     */
    public long currentGeneration(UUID playerId) {
        return generations.getOrDefault(playerId, 0L);
    }

    /**
     * Whether {@code generation} (captured earlier, from {@link #currentGeneration(UUID)}) is still
     * the generation on record for this UUID. {@code false} whenever anything has changed since it
     * was captured: a newer connection joined (a higher generation is now on record), or this
     * connection has since quit (no generation is on record at all).
     *
     * @param playerId   the player's UUID
     * @param generation the generation captured earlier
     * @return {@code true} only if {@code generation} is still exactly the current one on record
     */
    public boolean isCurrent(UUID playerId, long generation) {
        Long current = generations.get(playerId);
        return current != null && current == generation;
    }
}
