package com.ultikits.plugins.chat.commands;

import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.plugins.chat.service.AutoReplyService;
import com.ultikits.plugins.chat.service.ChannelService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The command help, the player-only refusal and the auto-reply save-failure console line follow the
 * server's {@code language}: under {@code language: zh} each is the Chinese catalogue text.
 */
@DisplayName("/ch and /uchat text follows the language setting")
class CommandTextLanguageTest {

    private static UltiToolsPlugin pluginIn(String code) {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(code));
        return plugin;
    }

    private static List<String> sent(CommandSender sender) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("/ch help is the Chinese catalogue text")
    void channelHelp() {
        List<String> expected = Arrays.asList(
                ChatColor.GOLD + CatalogueText.text("zh", "help_channel_header"),
                ChatColor.AQUA + "/ch list" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_channel_list"),
                ChatColor.AQUA + "/ch <name>" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_channel_switch"));
        CommandSender sender = mock(CommandSender.class);

        new ChannelCommands(pluginIn("zh"), mock(ChannelService.class)).handleHelp(sender);

        assertThat(sent(sender)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("/ch list and /ch <name> from the console are refused with the Chinese catalogue text")
    void playerOnlyRefusal() {
        String expected = ChatColor.RED + CatalogueText.text("zh", "player_only");
        CommandSender console = mock(CommandSender.class);
        ChannelCommands commands = new ChannelCommands(pluginIn("zh"), mock(ChannelService.class));

        commands.onList(console);
        commands.onSwitch(console, "global");

        assertThat(sent(console)).containsExactly(expected, expected);
    }

    @Test
    @DisplayName("/uchat help is the Chinese catalogue text")
    void adminHelp() {
        List<String> expected = Arrays.asList(
                ChatColor.GOLD + CatalogueText.text("zh", "help_admin_header"),
                ChatColor.AQUA + "/uchat reload" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_admin_reload"),
                ChatColor.AQUA + "/uchat autoreply list" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_admin_autoreply_list"),
                ChatColor.AQUA + "/uchat autoreply add <name> <response>" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_admin_autoreply_add"),
                ChatColor.AQUA + "/uchat autoreply setkeyword <name> <keyword>" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_admin_autoreply_setkeyword"),
                ChatColor.AQUA + "/uchat autoreply remove <name>" + ChatColor.WHITE + " - " + CatalogueText.text("zh", "help_admin_autoreply_remove"));
        CommandSender sender = mock(CommandSender.class);

        new ChatAdminCommands(pluginIn("zh"), mock(AutoReplyService.class)).handleHelp(sender);

        assertThat(sent(sender)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("A failed auto-reply save is logged with the Chinese catalogue text")
    void saveFailureLine() throws Exception {
        String expected = CatalogueText.text("zh", "log_autoreply_save_failed").replace("{RULE}", "greet");
        UltiToolsPlugin plugin = pluginIn("zh");
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        AutoReplyService service = mock(AutoReplyService.class);
        doThrow(new IOException("simulated write failure")).when(service).addRule("greet", "greet", "Hello there!");

        new ChatAdminCommands(plugin, service).onAutoReplyAdd(mock(CommandSender.class), "greet", "Hello there!");

        verify(logger).error(any(IOException.class), org.mockito.ArgumentMatchers.eq(expected));
    }
}
