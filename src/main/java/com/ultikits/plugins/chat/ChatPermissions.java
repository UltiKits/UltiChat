package com.ultikits.plugins.chat;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

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
 * A node is registered only if no one registered it first, and it is never removed. A node already there
 * (from a permission plugin, another plugin, or an earlier UltiChat instance on a hot swap, where the framework
 * starts the new instance before it unloads the old one) is left exactly as it is. Nothing is removed on unload
 * because Bukkit's {@code removePermission} leaves a node's default grant in place: removing would keep
 * granting {@code ultichat.channel} after an unload and add one more stale grant per hot swap (Codex review of
 * UltiKits/UltiChat#58). After {@code /upm uninstall} the two nodes therefore stay registered until the next
 * start; nothing but this module's own two commands checks them.
 */
final class ChatPermissions {

    /** Permission of {@code /ch} ({@code ChannelCommands}); every player by default. */
    static final String CHANNEL = "ultichat.channel";

    /** Permission of {@code /uchat} ({@code ChatAdminCommands}); operators by default. */
    static final String ADMIN = "ultichat.admin";

    private ChatPermissions() {
    }

    /** Registers each root that is not registered yet; a registered one is left as it is. */
    static void register(PluginManager pluginManager) {
        addIfAbsent(pluginManager, new Permission(CHANNEL, "Use /ch to list, join and leave chat channels",
                PermissionDefault.TRUE));
        addIfAbsent(pluginManager, new Permission(ADMIN, "Use /uchat to manage UltiChat", PermissionDefault.OP));
    }

    private static void addIfAbsent(PluginManager pluginManager, Permission permission) {
        if (pluginManager.getPermission(permission.getName()) == null) {
            pluginManager.addPermission(permission);
        }
    }
}
