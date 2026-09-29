package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Anti-spam service that enforces cooldown, duplicate detection and caps limiting. A spam trip
 * refuses the offending message only; there is no automatic muting (the never-called declaration
 * of one was removed, UltiKits/UltiChat#15; the feature is requested in UltiKits/UltiChat#30).
 * 反垃圾消息服务，支持冷却、重复检测和大写字母限制。触发时只拦截该条消息，不会自动禁言。
 */
@Service
public class AntiSpamService {

    @Autowired
    private ChatConfig config;

    /** The module, whose language file gives each refusal its text (UltiKits/UltiChat#18). */
    @Autowired
    private UltiToolsPlugin plugin;

    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    /**
     * The one time source for the cooldown and the duplicate window, in epoch milliseconds.
     * Package-private and non-final so a test can move time explicitly instead of sleeping.
     */
    LongSupplier clock = System::currentTimeMillis;

    /**
     * Every value here is treated as immutable once published: {@link #recordMessage} and
     * {@link #isDuplicate} only ever install a brand new {@link List} via
     * {@link Map#compute(Object, java.util.function.BiFunction)}, never mutate a retained list in
     * place. This is deliberate, not merely simpler: a mutable, shared list let a concurrent append
     * and a read-then-delete self-eviction race with each other and orphan a freshly accepted
     * message (UltiKits/UltiChat#40 review, coordinator ruling 2026-09-29). {@code compute} holds
     * this map's per-key lock for its whole remapping function, so "decide whether this entry is
     * still valid" and "replace or remove it" happen as one atomic step, for both this table and
     * {@link #recentMessages} below -- there is no "read a snapshot, then separately delete it"
     * step anywhere in this class any more.
     */
    private final Map<UUID, List<RecentMessage>> recentMessages = new ConcurrentHashMap<>();

    /**
     * The longest {@code anti-spam.cooldown} this field's own {@code @Range} allows, in
     * milliseconds. An entry older than this cannot still be blocking anyone under ANY value that
     * setting could be reloaded to, so evicting it here loses nothing -- the same reasoning
     * {@code AutoReplyListener}'s own cooldown table already uses for its {@code KEEP_MS}. Pinned
     * against the annotation by {@code AntiSpamServiceTest#cooldownRangeMatchesTheSelfEvictionWindow}.
     */
    private static final long MAX_COOLDOWN_MS = 60_000L;

    /**
     * The longest {@code anti-spam.duplicate-window} this field's own {@code @Range} allows, in
     * milliseconds. {@code 0} (the shipped default) means duplicate matching among RETAINED messages
     * never times out on its own -- that comparison, in {@link #isDuplicate}, is unchanged by this
     * constant. But a player who has not sent an accepted message in longer than this, under ANY
     * duplicate-window value the setting could ever be reloaded to, can never have that comparison
     * reach their retained history again, so keeping it past this point is memory, not history.
     * Pinned by {@code AntiSpamServiceTest#duplicateWindowRangeMatchesTheSelfEvictionWindow}.
     */
    private static final long MAX_DUPLICATE_WINDOW_MS = 600_000L;

    /**
     * How often {@link #sweepExpiredEntries()} runs, in ticks (60 seconds). A literal period, not
     * config-bound: this module's {@code plugin.yml} does not declare the {@code api-version: 630}
     * a config-bound {@code @Scheduled} period needs (see the framework's own {@code @Scheduled}
     * javadoc), and this interval is not something an operator has any reason to tune, so no config
     * key is introduced for it (UltiKits/UltiChat#40 review, coordinator ruling 2026-09-29).
     */
    private static final int SWEEP_PERIOD_TICKS = 1200;

    /**
     * One accepted message kept for duplicate detection, with the time it was sent, so that a copy
     * older than {@code anti-spam.duplicate-window} can stop counting (UltiKits/UltiChat#14).
     */
    private static final class RecentMessage {
        private final String text;
        private final long sentAt;

        private RecentMessage(String text, long sentAt) {
            this.text = text;
            this.sentAt = sentAt;
        }
    }

    /**
     * Check whether a message should be considered spam.
     * 检查消息是否属于垃圾消息。
     *
     * @param player  the sending player
     * @param message the chat message
     * @return the refusal to show the player, in the server's language (the language file's
     *         {@code spam_cooldown}, {@code spam_duplicate} or {@code spam_caps}, colour codes not yet
     *         translated), or null if the message is not spam
     */
    public String checkSpam(Player player, String message) {
        if (!config.isAntiSpamEnabled()) {
            return null;
        }
        if (player == null || message == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();

        String cooldownReason = checkCooldown(playerId);
        if (cooldownReason != null) {
            return cooldownReason;
        }

        if (isDuplicate(playerId, message)) {
            return plugin.i18n("spam_duplicate");
        }

        if (isExcessiveCaps(message)) {
            return plugin.i18n("spam_caps");
        }

        return null;
    }

    private String checkCooldown(UUID playerId) {
        Long lastTime = lastMessageTime.get(playerId);
        if (lastTime == null) {
            return null;
        }
        long elapsed = clock.getAsLong() - lastTime;
        if (elapsed >= MAX_COOLDOWN_MS) {
            // Self-eviction on read, purely by elapsed time -- not a connection check of any kind
            // (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29). Safe as a plain
            // conditional remove, unlike recentMessages below: Long is immutable, so
            // remove(key, value) comparing by value can never discard a concurrently-written fresh
            // timestamp -- a racing put() installs a genuinely different Long, and the comparison
            // simply no-ops instead of deleting it.
            lastMessageTime.remove(playerId, lastTime);
            return null;
        }
        long cooldownMs = config.getAntiSpamCooldown() * 1000L;
        if (elapsed < cooldownMs) {
            return plugin.i18n("spam_cooldown");
        }
        return null;
    }

    /**
     * Record a message for duplicate detection and update cooldown timestamp.
     * <p>
     * Always writes, unconditionally: there is no connection or session check anywhere in this
     * method, or anywhere else in this class. The retained-message list for {@code playerId} is
     * replaced wholesale, inside {@link Map#compute}, rather than mutated in place -- appending and
     * trimming to {@code anti-spam.max-duplicate} both happen on the new copy, so no other reader
     * (in particular {@link #isDuplicate}'s own {@code compute} call) can ever observe a half-built
     * list. Table-wide eviction of players who stop chatting entirely is not this method's job any
     * more; see {@link #sweepExpiredEntries()}.
     * 记录消息用于重复检测，并更新冷却时间戳。
     *
     * @param playerId the player UUID
     * @param message  the chat message
     */
    public void recordMessage(UUID playerId, String message) {
        if (playerId == null || message == null) {
            return;
        }
        long now = clock.getAsLong();
        lastMessageTime.put(playerId, now);

        int configuredMaxDuplicate = config.getAntiSpamMaxDuplicate();
        int maxDuplicate = configuredMaxDuplicate <= 0 ? 3 : configuredMaxDuplicate;
        recentMessages.compute(playerId, (uuid, existing) -> {
            List<RecentMessage> updated = existing == null
                    ? new ArrayList<>(maxDuplicate + 1)
                    : new ArrayList<>(existing);
            updated.add(new RecentMessage(message, now));
            while (updated.size() > maxDuplicate) {
                updated.remove(0);
            }
            return updated;
        });
    }

    /**
     * Periodic, low-frequency sweep of both tables, replacing the full-table scan this class used
     * to run on every single accepted chat message -- a measured concern, since that made the
     * module's highest-frequency hot path linear in the tracked population. Neither table's
     * correctness depends on this method: {@link #checkCooldown} and {@link #isDuplicate} both
     * still self-evict a stale entry on their own read path regardless of how recently this last
     * ran, so a player sitting a few seconds past their own ceiling before this next fires is
     * observably no different from one already swept. This exists purely so a UUID that stops
     * chatting entirely -- and so never triggers its own read-side eviction again -- does not hold
     * either table open for the life of the server (UltiKits/UltiChat#20, closed here by elapsed
     * time instead of connection lifecycle; UltiKits/UltiChat#40 review, coordinator ruling
     * 2026-09-29, replacing the per-write sweep this same rule previously ran).
     */
    @Scheduled(period = SWEEP_PERIOD_TICKS, async = false)
    public void sweepExpiredEntries() {
        long now = clock.getAsLong();
        lastMessageTime.entrySet().removeIf(entry -> now - entry.getValue() >= MAX_COOLDOWN_MS);
        recentMessages.entrySet().removeIf(entry -> {
            List<RecentMessage> retained = entry.getValue();
            return retained.isEmpty() || now - retained.get(retained.size() - 1).sentAt >= MAX_DUPLICATE_WINDOW_MS;
        });
    }

    /**
     * Check if a message has excessive uppercase characters.
     * 检查消息中大写字母是否过多。
     *
     * @param message the chat message
     * @return true if the uppercase ratio exceeds the configured limit
     */
    public boolean isExcessiveCaps(String message) {
        if (message == null || message.length() < 5) {
            return false;
        }
        int capsLimit = config.getAntiSpamCapsLimit();
        if (capsLimit <= 0 || capsLimit >= 100) {
            return false;
        }
        int total = 0;
        int upper = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetter(c)) {
                total++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (total == 0) {
            return false;
        }
        int percentage = (upper * 100) / total;
        return percentage > capsLimit;
    }

    /**
     * Check if a message is a duplicate of recent messages within the configured window.
     * <p>
     * A message is a duplicate when at least {@code anti-spam.max-duplicate} of the player's
     * retained messages (the last {@code max-duplicate} accepted ones) are identical to it. With a
     * positive {@code anti-spam.duplicate-window}, a retained copy sent longer ago than that many
     * seconds stops counting. A window of {@code 0} (the default) means no time limit: every retained
     * copy counts however old, which is exactly the rule before UltiKits/UltiChat#14, when the window
     * was never read. That comparison is unchanged here.
     * <p>
     * The staleness check and the read it gates are one atomic {@link Map#compute} call: the
     * decision "is this entry too old to matter" and the action "remove it" happen inside the same
     * remapping function, which this map's {@code compute} runs under this key's own lock. There is
     * no separate "read a snapshot, then call {@code remove} on it later" step -- the shape that let
     * a concurrent {@link #recordMessage} append land, unobserved, between this method's read and
     * its own later removal (UltiKits/UltiChat#40 review, coordinator ruling 2026-09-29).
     */
    private boolean isDuplicate(UUID playerId, String message) {
        long now = clock.getAsLong();
        int configuredMaxDuplicate = config.getAntiSpamMaxDuplicate();
        long windowMs = config.getAntiSpamDuplicateWindow() * 1000L;
        boolean timeLimited = windowMs > 0;

        int[] matchCount = {0};
        recentMessages.compute(playerId, (uuid, messages) -> {
            if (messages == null || messages.isEmpty()) {
                return null;
            }
            if (now - messages.get(messages.size() - 1).sentAt >= MAX_DUPLICATE_WINDOW_MS) {
                return null;
            }
            if (configuredMaxDuplicate <= 0) {
                return messages;
            }
            int count = 0;
            for (RecentMessage recent : messages) {
                boolean inWindow = !timeLimited || now - recent.sentAt <= windowMs;
                if (inWindow && message.equals(recent.text)) {
                    count++;
                }
            }
            matchCount[0] = count;
            return messages;
        });
        return configuredMaxDuplicate > 0 && matchCount[0] >= configuredMaxDuplicate;
    }
}
