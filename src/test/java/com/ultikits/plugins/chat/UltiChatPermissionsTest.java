package com.ultikits.plugins.chat;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UltiChat's two command permission roots reach Bukkit with their defaults (maintainer decision 2026-10-14).
 * <p>
 * {@code /ch} ({@code ChannelCommands}) is gated by {@code ultichat.channel} and {@code /uchat}
 * ({@code ChatAdminCommands}) by {@code ultichat.admin}. The framework registers neither a module's command
 * permission nor any {@code permissions:} block of a module's {@code plugin.yml}, and Bukkit treats an
 * unregistered node as operator-only, so before this change every non-operator got "Unknown command" for
 * {@code /ch}. The module now registers {@code ultichat.channel} as granted to everyone and
 * {@code ultichat.admin} as operator-only when it starts, and removes what it registered when it is unloaded.
 */
@DisplayName("UltiChat command permissions")
class UltiChatPermissionsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private UltiChat startedModule(File dir) throws IOException {
        File file = new File(dir, "config/chat.yml");
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "anti-spam:\n  enabled: true\n".getBytes(StandardCharsets.UTF_8));
        UltiChat plugin = mock(UltiChat.class);
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.chat.i18n.CatalogueText.answer("en"));
        when(plugin.operatorConfigFile(anyString())).thenAnswer(inv -> new File(dir, inv.<String>getArgument(0)));
        when(plugin.registerSelf()).thenCallRealMethod();
        doCallRealMethod().when(plugin).registerCommandPermissions();
        doCallRealMethod().when(plugin).onUnregister();
        doCallRealMethod().when(plugin).unregisterCommandPermissions();
        assertThat(plugin.registerSelf()).isTrue();
        return plugin;
    }

    @Test
    @DisplayName("starting the module registers ultichat.channel for everyone and ultichat.admin for operators")
    void startRegistersBothRoots(@TempDir File dir) throws IOException {
        startedModule(dir);

        Permission channel = server.getPluginManager().getPermission("ultichat.channel");
        Permission admin = server.getPluginManager().getPermission("ultichat.admin");
        assertThat(channel).isNotNull();
        assertThat(channel.getDefault()).isEqualTo(PermissionDefault.TRUE);
        assertThat(admin).isNotNull();
        assertThat(admin.getDefault()).isEqualTo(PermissionDefault.OP);

        PlayerMock player = server.addPlayer();
        PlayerMock operator = server.addPlayer();
        operator.setOp(true);
        assertThat(player.hasPermission("ultichat.channel")).as("a player without any grant can use /ch").isTrue();
        assertThat(player.hasPermission("ultichat.admin")).as("a player without any grant cannot use /uchat").isFalse();
        assertThat(operator.hasPermission("ultichat.admin")).isTrue();
    }

    @Test
    @DisplayName("unloading the module removes the two roots it registered")
    void unloadRemovesWhatStartRegistered(@TempDir File dir) throws IOException {
        UltiChat plugin = startedModule(dir);

        plugin.onUnregister();

        assertThat(server.getPluginManager().getPermission("ultichat.channel")).isNull();
        assertThat(server.getPluginManager().getPermission("ultichat.admin")).isNull();
    }

    @Test
    @DisplayName("a root something else registered first is neither replaced nor removed")
    void anExistingRegistrationIsLeftAlone(@TempDir File dir) throws IOException {
        Permission theirs = new Permission("ultichat.channel", PermissionDefault.FALSE);
        server.getPluginManager().addPermission(theirs);

        UltiChat plugin = startedModule(dir);
        assertThat(server.getPluginManager().getPermission("ultichat.channel")).isSameAs(theirs);
        assertThat(server.getPluginManager().getPermission("ultichat.admin").getDefault()).isEqualTo(PermissionDefault.OP);

        plugin.onUnregister();
        assertThat(server.getPluginManager().getPermission("ultichat.channel")).isSameAs(theirs);
        assertThat(server.getPluginManager().getPermission("ultichat.admin")).isNull();
    }
}
