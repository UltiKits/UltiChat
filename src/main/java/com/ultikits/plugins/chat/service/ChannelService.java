package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
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

    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

    /**
     * Get the channel a player is currently in.
     * Returns the default channel if the player has no assignment.
     */
    public String getPlayerChannel(UUID playerId) {
        return playerChannels.getOrDefault(playerId, config.getDefaultChannel());
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
