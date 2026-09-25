package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import org.bukkit.entity.Player;

import java.util.LinkedList;
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
    private final Map<UUID, LinkedList<RecentMessage>> recentMessages = new ConcurrentHashMap<>();

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
        long cooldownMs = config.getAntiSpamCooldown() * 1000L;
        if (elapsed < cooldownMs) {
            return plugin.i18n("spam_cooldown");
        }
        return null;
    }

    /**
     * Record a message for duplicate detection and update cooldown timestamp.
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

        LinkedList<RecentMessage> messages =
                recentMessages.computeIfAbsent(playerId, k -> new LinkedList<RecentMessage>());
        messages.addLast(new RecentMessage(message, now));

        int maxDuplicate = config.getAntiSpamMaxDuplicate();
        if (maxDuplicate <= 0) {
            maxDuplicate = 3;
        }
        while (messages.size() > maxDuplicate) {
            messages.removeFirst();
        }
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
     * Remove all tracked state for a player (call on quit).
     * 清除玩家的所有追踪状态（退出时调用）。
     *
     * @param playerId the player UUID
     */
    public void cleanup(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastMessageTime.remove(playerId);
        recentMessages.remove(playerId);
    }

    /**
     * Check if a message is a duplicate of recent messages within the configured window.
     * <p>
     * A message is a duplicate when at least {@code anti-spam.max-duplicate} of the player's
     * retained messages (the last {@code max-duplicate} accepted ones) are identical to it. With a
     * positive {@code anti-spam.duplicate-window}, a retained copy sent longer ago than that many
     * seconds stops counting. A window of {@code 0} (the default) means no time limit: every retained
     * copy counts however old, which is exactly the rule before UltiKits/UltiChat#14, when the window
     * was never read.
     */
    private boolean isDuplicate(UUID playerId, String message) {
        LinkedList<RecentMessage> messages = recentMessages.get(playerId);
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        int maxDuplicate = config.getAntiSpamMaxDuplicate();
        if (maxDuplicate <= 0) {
            return false;
        }

        // Count how many of the recent messages match and are still inside the window (if any)
        long windowMs = config.getAntiSpamDuplicateWindow() * 1000L;
        boolean timeLimited = windowMs > 0;
        long now = clock.getAsLong();
        int duplicateCount = 0;
        for (RecentMessage recent : messages) {
            boolean inWindow = !timeLimited || now - recent.sentAt <= windowMs;
            if (inWindow && message.equals(recent.text)) {
                duplicateCount++;
            }
        }
        return duplicateCount >= maxDuplicate;
    }
}
