package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player channel assignments and channel-based recipient filtering.
 * 管理玩家频道分配和基于频道的消息接收者过滤。
 */
@Service
public class ChannelService {

    @Autowired
    private ChannelConfig config;

    /** The module, whose language file gives the move notice its text (UltiKits/UltiChat#45). */
    @Autowired
    private UltiToolsPlugin plugin;

    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

    /**
     * Get the channel a player is currently in.
     * Returns the default channel if the player has no assignment.
     */
    public String getPlayerChannel(UUID playerId) {
        return playerChannels.getOrDefault(playerId, resolveDefaultChannel(config));
    }

    /**
     * The channel a player with no assignment, and a player who has just joined, is placed in
     * (UltiKits/UltiChat#44).
     * <p>
     * The configured {@code channels.default-channel} when a channel of that name is defined. When it
     * is not, {@code global} if that is defined and needs no permission, otherwise the first defined
     * channel in file order that needs none, so a new player is never placed in a channel that does
     * not exist, nor in one {@code /ch <name>} would refuse them. When no such channel is defined the
     * configured name is kept: new players are then all placed in that one undefined channel, whose
     * chat is not filtered by range or world ({@link #filterRecipients} treats an undefined channel as
     * having neither). Static so the load-time check can apply the same rule to the configuration
     * directly.
     *
     * @param config the channel configuration
     * @return the channel name new players are placed in
     */
    public static String resolveDefaultChannel(ChannelConfig config) {
        String configured = config.getDefaultChannel();
        Map<String, Map<String, Object>> defined = config.getChannels();
        if (defined == null || defined.isEmpty() || isDefined(defined, configured)) {
            return configured;
        }
        if (isOpen(defined.get("global"))) {
            return "global";
        }
        for (Map.Entry<String, Map<String, Object>> channel : defined.entrySet()) {
            if (isOpen(channel.getValue())) {
                return channel.getKey();
            }
        }
        return configured;
    }

    /**
     * Moves every player whose tracked channel is no longer defined into the channel
     * {@link #resolveDefaultChannel} computes, and tells each online one in a single line
     * (UltiKits/UltiChat#45). Called after a reload, when a channel may have been removed from
     * {@code channels.channels}: an assignment is otherwise kept as written and points at a channel that
     * does not exist. A player in a defined channel, or already in the fallback, is left alone.
     */
    public void moveFromRemovedChannels() {
        Map<String, Map<String, Object>> defined = config.getChannels();
        String target = resolveDefaultChannel(config);
        for (Map.Entry<UUID, String> assignment : playerChannels.entrySet()) {
            String current = assignment.getValue();
            if (isDefined(defined, current) || target.equals(current)) {
                continue;
            }
            if (!playerChannels.replace(assignment.getKey(), current, target)) {
                continue;
            }
            Player player = Bukkit.getPlayer(assignment.getKey());
            if (player != null) {
                // One pass: a channel name is the operator's own text, shown as written.
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', UltiChat.fillOnce(
                        plugin.i18n("channel_removed_moved"),
                        "{0}", current, "{1}", getChannelDisplayName(target))));
            }
        }
    }

    /**
     * Whether a channel definition is open to every player: it exists and sets no {@code permission}
     * (the rule {@link #hasChannelPermission} applies: a missing or empty permission means everyone).
     */
    private static boolean isOpen(Map<String, Object> def) {
        if (def == null) {
            return false;
        }
        Object permission = def.get("permission");
        return permission == null || permission.toString().isEmpty();
    }

    /**
     * Whether {@code name} is a defined channel: present under {@code channels.channels} with a
     * definition, the rule {@link #getChannelDef} and {@code /ch <name>} already apply.
     */
    public static boolean isDefined(Map<String, Map<String, Object>> defined, String name) {
        return name != null && defined != null && defined.get(name) != null;
    }

    /**
     * Set a player's active channel.
     */
    public void setPlayerChannel(UUID playerId, String channel) {
        playerChannels.put(playerId, channel);
    }

    /**
     * Get the full channel definition map for the given channel name.
     * Returns null if the channel does not exist.
     */
    public Map<String, Object> getChannelDef(String name) {
        if (name == null || config.getChannels() == null) {
            return null;
        }
        return config.getChannels().get(name);
    }

    /**
     * Get the colorized display name for a channel.
     * Returns the channel name if no display-name is configured.
     */
    public String getChannelDisplayName(String channel) {
        Map<String, Object> def = getChannelDef(channel);
        if (def == null) {
            return channel;
        }
        Object displayName = def.get("display-name");
        if (displayName == null) {
            return channel;
        }
        return ChatColor.translateAlternateColorCodes('&', displayName.toString());
    }

    /**
     * Get a channel's own chat format, if it has one.
     * <p>
     * Returns {@code null} -- "use the global format with the display name in front" -- when the
     * channel does not exist or sets no {@code format:}. Otherwise returns the format as written;
     * {@code {display}} in it stands for the channel's display name. A format earlier versions shipped
     * is removed from the file when the module starts ({@code ChannelConfig#materializeText}), so the
     * file and the chat line agree (UltiKits/UltiChat#18).
     *
     * @param channel the channel name
     * @return the channel's own format, or {@code null}
     */
    public String getChannelFormat(String channel) {
        return ownFormat(getChannelDef(channel));
    }

    /**
     * A channel definition's own chat format, by the same rule as {@link #getChannelFormat}:
     * {@code null} for a missing definition or no {@code format:}. Static so the module's load-time
     * checks can apply the rule to the configuration directly.
     *
     * @param def a channel definition from {@code channels.channels}, or {@code null}
     * @return the channel's own format, or {@code null}
     */
    public static String ownFormat(Map<String, Object> def) {
        if (def == null) {
            return null;
        }
        Object format = def.get("format");
        if (format == null) {
            return null;
        }
        return format.toString();
    }

    /**
     * Filter recipients based on channel rules: same channel, cross-world, and range.
     *
     * @param sender     the message sender
     * @param recipients all potential recipients
     * @return the filtered set of recipients who should receive the message
     */
    public Set<Player> filterRecipients(Player sender, Set<Player> recipients) {
        String senderChannel = getPlayerChannel(sender.getUniqueId());
        Map<String, Object> def = getChannelDef(senderChannel);

        boolean crossWorld = true;
        int range = -1;

        if (def != null) {
            Object crossWorldObj = def.get("cross-world");
            if (crossWorldObj instanceof Boolean) {
                crossWorld = (Boolean) crossWorldObj;
            }
            Object rangeObj = def.get("range");
            if (rangeObj instanceof Number) {
                range = ((Number) rangeObj).intValue();
            }
        }

        Set<Player> filtered = new HashSet<>();
        Location senderLoc = sender.getLocation();

        for (Player recipient : recipients) {
            // Must be in the same channel
            String recipientChannel = getPlayerChannel(recipient.getUniqueId());
            if (!senderChannel.equals(recipientChannel)) {
                continue;
            }

            // Cross-world check
            if (!crossWorld) {
                if (!sender.getWorld().getName().equals(recipient.getWorld().getName())) {
                    continue;
                }
            }

            // Range check (only applies if range > 0 and same world)
            if (range > 0) {
                if (!sender.getWorld().getName().equals(recipient.getWorld().getName())) {
                    continue;
                }
                double distance = senderLoc.distance(recipient.getLocation());
                if (distance > range) {
                    continue;
                }
            }

            filtered.add(recipient);
        }

        return filtered;
    }

    /**
     * Check if a player has permission to join a channel.
     * Empty or null permission means everyone can access.
     */
    public boolean hasChannelPermission(Player player, String channel) {
        Map<String, Object> def = getChannelDef(channel);
        if (def == null) {
            return false;
        }
        Object permission = def.get("permission");
        if (permission == null || permission.toString().isEmpty()) {
            return true;
        }
        return player.hasPermission(permission.toString());
    }

    /**
     * Get the list of channels the player has permission to access.
     */
    public List<String> getAvailableChannels(Player player) {
        List<String> available = new ArrayList<>();
        if (config.getChannels() == null) {
            return available;
        }
        for (String channelName : config.getChannels().keySet()) {
            if (hasChannelPermission(player, channelName)) {
                available.add(channelName);
            }
        }
        return available;
    }

    /**
     * Remove a player's channel assignment (cleanup on quit).
     */
    public void removePlayer(UUID playerId) {
        playerChannels.remove(playerId);
    }
}
