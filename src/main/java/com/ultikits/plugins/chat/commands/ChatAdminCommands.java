package com.ultikits.plugins.chat.commands;

import com.ultikits.plugins.chat.service.AutoReplyService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.annotations.command.CmdParam;
import com.ultikits.ultitools.annotations.command.CmdSender;
import com.ultikits.ultitools.annotations.command.CmdTarget;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.Map;

/**
 * Admin commands for UltiChat management.
 * UltiChat 管理命令。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(permission = "ultichat.admin", description = "command_description_admin", alias = {"uchat"})
public class ChatAdminCommands extends BaseCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final AutoReplyService autoReplyService;

    public ChatAdminCommands(UltiToolsPlugin plugin, AutoReplyService autoReplyService) {
        this.plugin = plugin;
        this.autoReplyService = autoReplyService;
    }

    /**
     * Reload all UltiChat configurations.
     * 重新加载所有 UltiChat 配置。
     */
    @CmdMapping(format = "reload")
    public void onReload(@CmdSender CommandSender sender) {
        plugin.reloadSelf();
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.i18n("config_reloaded")));
    }

    /**
     * List all auto-reply rules.
     * 列出所有自动回复规则。
     */
    @CmdMapping(format = "autoreply list")
    public void onAutoReplyList(@CmdSender CommandSender sender) {
        Map<String, Map<String, Object>> rules = autoReplyService.getRules();

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.i18n("autoreply_list_header")));

        if (rules.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.i18n("autoreply_list_empty")));
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : rules.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> rule = entry.getValue();
            Object keyword = rule.get("keyword");
            Object mode = rule.get("mode");
            Object response = rule.get("response");
            String keywordStr = keyword != null ? keyword.toString() : "";
            String modeStr = mode != null ? mode.toString() : "contains";
            String responseStr = response != null ? response.toString() : "";

            String line = plugin.i18n("autoreply_list_entry");
            line = line.replace("{0}", name);
            line = line.replace("{1}", keywordStr);
            line = line.replace("{2}", modeStr);
            line = line.replace("{3}", responseStr);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    /**
     * Add a new auto-reply rule.
     * 添加新的自动回复规则。
     */
    @CmdMapping(format = "autoreply add <name> <response>")
    public void onAutoReplyAdd(@CmdSender CommandSender sender,
                               @CmdParam("name") String name,
                               @CmdParam("response") String response) {
        Map<String, Map<String, Object>> rules = autoReplyService.getRules();
        if (rules.get(name) != null) {
            String msg = plugin.i18n("autoreply_exists").replace("{0}", name);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        try {
            autoReplyService.addRule(name, name, response);
        } catch (IOException e) {
            reportSaveFailure(sender, name, e);
            return;
        }
        String msg = plugin.i18n("autoreply_added").replace("{0}", name);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    /**
     * Set an existing auto-reply rule's keyword to a value distinct from its name.
     * 为已存在的自动回复规则设置与名称不同的关键词。
     * <p>
     * A separate verb rather than a third {@code add} parameter: the existing
     * {@code autoreply add <name> <response>} format cannot grow a keyword parameter
     * without becoming ambiguous against itself under the framework's scored format
     * matching, since a shorter actual arg list partially matches a longer format
     * (see {@code BaseCommandExecutor.calculateMatchScore}). A distinct literal
     * ("setkeyword") at the same position "add" occupies is unambiguous instead.
     */
    @CmdMapping(format = "autoreply setkeyword <name> <keyword>")
    public void onAutoReplySetKeyword(@CmdSender CommandSender sender,
                                      @CmdParam("name") String name,
                                      @CmdParam("keyword") String keyword) {
        Map<String, Map<String, Object>> rules = autoReplyService.getRules();
        if (rules.get(name) == null) {
            String msg = plugin.i18n("autoreply_not_found").replace("{0}", name);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        try {
            autoReplyService.setKeyword(name, keyword);
        } catch (IOException e) {
            reportSaveFailure(sender, name, e);
            return;
        }
        String msg = plugin.i18n("autoreply_keyword_set").replace("{0}", name).replace("{1}", keyword);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    /**
     * Remove an auto-reply rule.
     * 移除自动回复规则。
     */
    @CmdMapping(format = "autoreply remove <name>")
    public void onAutoReplyRemove(@CmdSender CommandSender sender,
                                  @CmdParam("name") String name) {
        Map<String, Map<String, Object>> rules = autoReplyService.getRules();
        if (!rules.containsKey(name)) {
            String msg = plugin.i18n("autoreply_not_found").replace("{0}", name);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        try {
            autoReplyService.removeRule(name);
        } catch (IOException e) {
            reportSaveFailure(sender, name, e);
            return;
        }
        String msg = plugin.i18n("autoreply_removed").replace("{0}", name);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    /**
     * Tell the sender the rule change was not saved, and put the reason in the server log.
     * 向发送者报告规则更改未保存，并将原因记录到服务器日志。
     * <p>
     * {@link AutoReplyService} has already rolled the change back, so the rule set is exactly what
     * it was before the command ran. The sender is told that and nothing else: the underlying
     * {@link IOException} names a path on the server's own filesystem, which belongs in the log
     * rather than in a chat line (UltiKits/UltiChat#17).
     * <p>
     * The log call passes the exception itself rather than its {@code toString()}, so the stack
     * trace survives -- it is what tells a read-only file apart from a full disk -- and so the
     * {@code SEVERE} record carries a {@code Throwable}, which is what {@code SystemLogHandler}
     * requires before it will report the failure to a connected panel.
     *
     * @param sender the sender to report to
     * @param name   the rule name the command was changing
     * @param cause  the write failure
     */
    private void reportSaveFailure(CommandSender sender, String name, IOException cause) {
        plugin.getLogger().error(cause, plugin.i18n("log_autoreply_save_failed").replace("{RULE}", name));
        String msg = plugin.i18n("autoreply_save_failed").replace("{0}", name);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + plugin.i18n("help_admin_header"));
        sender.sendMessage(ChatColor.AQUA + "/uchat reload" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_reload"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply list" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_list"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply add <name> <response>" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_add"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply setkeyword <name> <keyword>" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_setkeyword"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply remove <name>" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_remove"));
    }
}
