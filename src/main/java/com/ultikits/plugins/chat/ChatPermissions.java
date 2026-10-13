package com.ultikits.plugins.chat;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers UltiChat's two command permission roots with Bukkit, with the defaults the maintainer chose on
 * 2026-10-14: {@code ultichat.channel} ({@code /ch}) is granted to every player, {@code ultichat.admin}
 * ({@code /uchat}) to operators.
 * <p>
 * Why the module does this itself: the framework sets each command's permission
 * ({@code CommandManager} calls {@code setPermission}) but registers no {@code Permission} object, and it
 * does not read a {@code permissions:} block from a module's {@code plugin.yml}. Bukkit treats a node that
 * is not registered as operator-only, so without this class every non-operator was told "Unknown command"
 * for {@code /ch}, whatever a channel's own permission said.
 * <p>
 * A node that is already registered when the module starts (by a permission plugin, another plugin, or an
 * operator's tooling) is left exactly as it is: this class neither replaces it nor removes it on unload.
 * Only the nodes it added itself are removed when the module is unloaded.
 * <p>
 * The one exception is a node an earlier UltiChat instance registered, recognised by this class's own
 * description text: on a hot swap the framework starts the new instance before it unloads the old one, so the
 * new instance takes the node over (re-registers it as its own object), and the old instance's unload, which
 * removes only the object it registered, then leaves it (Codex review of UltiKits/UltiChat#58). Keep the two
 * description texts unchanged in later versions: a hot swap from a version with other texts would not be
 * recognised, and the old instance's unload would remove the nodes until the next start.
 */
final class ChatPermissions {

    /** Permission of {@code /ch} ({@code ChannelCommands}); every player by default. */
    static final String CHANNEL = "ultichat.channel";

    /** Permission of {@code /uchat} ({@code ChatAdminCommands}); operators by default. */
    static final String ADMIN = "ultichat.admin";

    /** Description of the {@link #CHANNEL} node; also how a node an earlier UltiChat instance registered is recognised. */
    static final String CHANNEL_DESCRIPTION = "Use /ch to list, join and leave chat channels (registered by UltiChat)";

    /** Description of the {@link #ADMIN} node; also how a node an earlier UltiChat instance registered is recognised. */
    static final String ADMIN_DESCRIPTION = "Use /uchat to manage UltiChat (registered by UltiChat)";

    private final List<Permission> added = new ArrayList<>();

    /** Registers each root that is not registered yet, and remembers which ones it registered. */
    void register(PluginManager pluginManager) {
        add(pluginManager, new Permission(CHANNEL, CHANNEL_DESCRIPTION, PermissionDefault.TRUE));
        add(pluginManager, new Permission(ADMIN, ADMIN_DESCRIPTION, PermissionDefault.OP));
    }

    /** Removes the roots {@link #register} added, if they are still the registered ones. */
    void unregister(PluginManager pluginManager) {
        for (Permission permission : added) {
            if (pluginManager.getPermission(permission.getName()) == permission) {
                pluginManager.removePermission(permission);
            }
        }
        added.clear();
    }

    private void add(PluginManager pluginManager, Permission permission) {
        Permission existing = pluginManager.getPermission(permission.getName());
        if (existing != null) {
            if (!permission.getDescription().equals(existing.getDescription())) {
                return;
            }
            // An earlier UltiChat instance's node (hot swap): take it over, so that instance's unload leaves it.
            pluginManager.removePermission(existing);
        }
        pluginManager.addPermission(permission);
        added.add(permission);
    }
}
