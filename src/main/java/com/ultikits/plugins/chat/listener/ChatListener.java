package com.ultikits.plugins.chat.listener;

import com.cryptomorin.xseries.XSound;
import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.AntiSpamService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.plugins.chat.service.ConnectionRegistry;
import com.ultikits.plugins.chat.service.EmojiService;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Main chat event listener that integrates anti-spam, emoji, channel, format, and mention features.
 * 主聊天事件监听器，集成反垃圾消息、表情、频道、格式化和@提及功能。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@EventListener
public class ChatListener implements Listener {

    private final ChatConfig chatConfig;
    private final ChannelConfig channelConfig;
    private final AntiSpamService antiSpamService;
    private final ChannelService channelService;
    private final EmojiService emojiService;
    private final ConnectionRegistry connectionRegistry;

    public ChatListener(ChatConfig chatConfig, ChannelConfig channelConfig,
                        AntiSpamService antiSpamService, ChannelService channelService,
                        EmojiService emojiService, ConnectionRegistry connectionRegistry) {
        this.chatConfig = chatConfig;
        this.channelConfig = channelConfig;
        this.antiSpamService = antiSpamService;
        this.channelService = channelService;
        this.emojiService = emojiService;
        this.connectionRegistry = connectionRegistry;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 1. Anti-spam check
        if (handleAntiSpam(player, message, event)) {
            return;
        }

        // 2. Emoji replacement
        if (player.hasPermission("ultichat.emoji")) {
            message = emojiService.replaceEmojis(message);
        }

        // 3. Channel recipient filtering
        if (channelConfig.isEnabled()) {
            Set<Player> filtered = channelService.filterRecipients(player, event.getRecipients());
            event.getRecipients().clear();
            event.getRecipients().addAll(filtered);
        }

        // 4. Chat format
        if (chatConfig.isChatFormatEnabled()) {
            applyChatFormat(player, event);
        }

        // Color codes in message if player has permission
        if (player.hasPermission("ultichat.color")) {
            message = ChatColor.translateAlternateColorCodes('&', message);
        }

        // 5. @Mentions
        if (chatConfig.isMentionsEnabled()) {
            message = processMentions(player, message, event.getRecipients());
        }

        event.setMessage(message);
    }

    /**
     * Check anti-spam and cancel the event if the message is spam.
     * @return true if the event was cancelled (caller should return)
     */
    private boolean handleAntiSpam(Player player, String message, AsyncPlayerChatEvent event) {
        if (!chatConfig.isAntiSpamEnabled() || player.hasPermission("ultichat.spam.bypass")) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        // Captured once, at the start of this connection's own async chat processing, so every
        // state-changing step below can tell whether IT is still that same connection by the time it
        // runs (UltiKits/UltiChat#40 review, maintainer decision 2026-09-29). Two narrower checks were
        // tried here before this one and both eventually let a stale, superseded connection act on a
        // newer one's behalf: Player#isConnected() (UltiKits/UltiChat#35) belongs to this specific,
        // possibly-stale Player object and only knows whether THIS connection ended, not whether a
        // newer one has since taken its place; Bukkit.getPlayer(UUID) == null (the #40 review's own
        // narrowing) asks "is anyone online under this UUID" rather than "is this still the
        // connection that started this task" -- so a reconnect landing between this message's
        // recordMessage and that check still let the write through, attributed to the wrong
        // connection. A generation captured here and re-checked below answers the right question.
        long generation = connectionRegistry.currentGeneration(playerId);
        String spamReason = antiSpamService.checkSpam(player, message);
        if (spamReason != null) {
            event.setCancelled(true);
            // The refusal is the language file's text, which carries its own '&' colour code.
            player.sendMessage(ChatColor.RED + ChatColor.translateAlternateColorCodes('&', spamReason));
            return true;
        }
        // Skipped entirely, not written-then-undone, when this connection has since been superseded
        // or has ended: a skipped write can never re-create an entry a quit's cleanup already removed
        // (closing UltiKits/UltiChat#20's failure mode by construction, not by a reactive re-check),
        // and can never attribute a stale message to a newer connection's own state.
        if (connectionRegistry.isCurrent(playerId, generation)) {
            antiSpamService.recordMessage(playerId, message);
            // Re-checked, not reused: recordMessage above is not instantaneous, and a reconnect
            // landing between the write and this check must not let this task's cleanup run against
            // the new connection's now-live state -- the exact #40-review regression this generation
            // check exists to close. Bukkit.getPlayer(UUID) == null still asks the question this
            // cleanup actually needs answered (is anybody at all online for this UUID right now);
            // isCurrent additionally guards against acting on a UUID some newer, still-online
            // connection now owns.
            if (Bukkit.getPlayer(playerId) == null && connectionRegistry.isCurrent(playerId, generation)) {
                antiSpamService.cleanup(playerId);
            }
        }
        return false;
    }

    /**
     * Build and apply the chat format string to the event.
     */
    private void applyChatFormat(Player player, AsyncPlayerChatEvent event) {
        String format = chatConfig.getChatFormat();

        // With channels enabled: the channel's own format if it has one ({display} = its display
        // name), otherwise the global format with the display name in front (UltiKits/UltiChat#16).
        if (channelConfig.isEnabled()) {
            String channel = channelService.getPlayerChannel(player.getUniqueId());
            String channelDisplay = channelService.getChannelDisplayName(channel);
            String channelFormat = channelService.getChannelFormat(channel);
            if (channelFormat != null) {
                format = channelFormat.replace("{display}", channelDisplay);
            } else {
                format = channelDisplay + " " + format;
            }
        }

        // Replace placeholders. The display name is text the player may control (a nickname), so
        // it holds only a marker until every template step has run, and then goes in as literal
        // text: a "{message}" or a "%placeholder%" in it is shown as written (UltiKits/UltiChat#32).
        format = format.replace("{player}", "%1$s");
        format = format.replace("{displayname}", DISPLAY_NAME_MARKER);
        format = format.replace("{message}", "%2$s");

        // Translate color codes in format
        format = ChatColor.translateAlternateColorCodes('&', format);

        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        // Escape stray % chars that aren't format specifiers
        format = escapeFormatString(format);

        event.setFormat(format.replace(DISPLAY_NAME_MARKER, literalDisplayName(player)));
    }

    /**
     * Stands in for {@code {displayname}} while the format is filled. It holds no '%', '{', '}' or
     * '&', so no template step changes it, and no operator writes it.
     */
    private static final String DISPLAY_NAME_MARKER = "\u0000displayname\u0000";

    /**
     * The player's display name as it goes into the finished format: its '&' colour codes are
     * translated, as they were when the name was part of the template, and every '%' is doubled so
     * the chat line's own formatting shows it as a single '%' and treats nothing in it as a
     * specifier.
     */
    private static String literalDisplayName(Player player) {
        return ChatColor.translateAlternateColorCodes('&', player.getDisplayName()).replace("%", "%%");
    }

    /**
     * Process @mentions in the message. Highlights mentioned names and plays sounds.
     */
    String processMentions(Player sender, String message, Set<Player> recipients) {
        String result = message;
        for (Player online : Bukkit.getOnlinePlayers()) {
            String mention = "@" + online.getName();
            if (!result.contains(mention)) {
                continue;
            }

            // Self-mention check
            if (online.getUniqueId().equals(sender.getUniqueId()) && !chatConfig.isSelfMention()) {
                continue;
            }

            // Replace with formatted mention
            String mentionFormat = chatConfig.getMentionFormat();
            String formatted = mentionFormat.replace("{player}", online.getName());
            formatted = ChatColor.translateAlternateColorCodes('&', formatted);
            result = result.replace(mention, formatted);

            // Play sound to mentioned player
            playMentionSound(online);
        }
        return result;
    }

    /**
     * Play the configured mention sound to a player.
     */
    private void playMentionSound(Player player) {
        String soundName = chatConfig.getMentionSound();
        if (soundName == null || soundName.isEmpty()) {
            return;
        }
        // A name that is no sound plays the shipped sound instead; the operator is told at load
        // (UltiChat#warnAboutUnusableValues). An empty value, above, is silence by choice.
        XSound sound = XSound.matchXSound(soundName)
                .orElseGet(() -> XSound.matchXSound(ChatConfig.DEFAULT_MENTION_SOUND).orElse(null));
        if (sound != null) {
            player.playSound(player.getLocation(), sound.get(), 1.0f, 1.0f);
        }
    }

    /**
     * Escape % characters in a format string, preserving %1$s and %2$s specifiers.
     * 转义格式字符串中的 % 字符，保留 %1$s 和 %2$s 格式说明符。
     */
    static String escapeFormatString(String format) {
        if (format == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '%') {
                // Check if this is %1$s or %2$s
                if (i + 3 < format.length()
                        && (format.charAt(i + 1) == '1' || format.charAt(i + 1) == '2')
                        && format.charAt(i + 2) == '$'
                        && format.charAt(i + 3) == 's') {
                    sb.append(c); // keep as-is
                } else {
                    sb.append("%%");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
