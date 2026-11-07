package com.ultikits.plugins.placeholder.chat;

import com.ultikits.plugins.chat.config.ChannelConfig;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * Read-only channel placeholders. Deliberately outside the module's component-scan package:
 * the optional PlaceholderAPI superclass must not be linked by scans when PAPI is absent.
 */
public final class UltiChatExpansion extends PlaceholderExpansion implements Runnable {
    private final UltiToolsPlugin plugin;
    private final ChannelConfig config;
    private final ChannelService channels;

    public UltiChatExpansion(UltiToolsPlugin plugin, ChannelConfig config, ChannelService channels) {
        this.plugin = plugin;
        this.config = config;
        this.channels = channels;
    }

    /** Called only with PlaceholderAPI enabled; the callback releases this exact registration. */
    public static Runnable register(UltiToolsPlugin plugin, ChannelConfig config, ChannelService channels) {
        UltiChatExpansion expansion = new UltiChatExpansion(plugin, config, channels);
        if (!expansion.register()) {
            plugin.getLogger().warn(plugin.i18n("log_placeholder_registration_failed"));
            return null;
        }
        return expansion;
    }

    @Override
    public String getIdentifier() {
        return "ultichat";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /** Unknown parameters remain unresolved; known but unavailable values are empty. */
    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (!"channel".equals(params) && !"channel_display".equals(params)) {
            return null;
        }
        if (player == null || !config.isEnabled()) {
            return "";
        }
        String channel = channels.getPlayerChannel(player.getUniqueId());
        return "channel".equals(params) ? channel : channels.getChannelDisplayName(channel);
    }

    /**
     * PAPI removes by identifier, not instance. Its isRegistered() uses metadata equality, so it
     * cannot distinguish an equal replacement either. An old module must never remove a new copy.
     */
    @Override
    public void run() {
        if (getPlaceholderAPI().getLocalExpansionManager()
                .findExpansionByIdentifier(getIdentifier()).orElse(null) == this) {
            unregister();
        }
    }
}
