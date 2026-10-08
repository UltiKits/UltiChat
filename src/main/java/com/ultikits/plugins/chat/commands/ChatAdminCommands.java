package com.ultikits.plugins.chat.commands;

import com.ultikits.plugins.chat.UltiChat;
import com.ultikits.plugins.chat.service.AutoReplyService;
import com.ultikits.ultitools.abstracts.ReloadReport;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.annotations.command.CmdParam;
import com.ultikits.ultitools.annotations.command.CmdSender;
import com.ultikits.ultitools.annotations.command.CmdTarget;
import com.ultikits.ultitools.config.ConfigEntryPresenceException;
import com.ultikits.ultitools.config.ConfigWriteRefusedException;
import com.ultikits.ultitools.config.EntryPresence;
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
        ReloadReport report;
        try {
            report = plugin.reloadWithReport();
        } catch (Exception | Error e) {
            // The framework has logged the failure; the sender is told it and its cause (UltiKits/UltiChat#48).
            String cause = e.getMessage() == null || e.getMessage().isEmpty() ? e.getClass().getSimpleName() : e.getMessage();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    UltiChat.fillOnce(plugin.i18n("config_reload_failed"), "{0}", cause)));
            return;
        }
        if (report != null && report.isPartial()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', UltiChat.fillOnce(
                    plugin.i18n("config_reload_partial"), "{0}", String.join("; ", report.getPartialReasons()))));
            return;
        }
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

            // One pass: a rule name, keyword or response that itself contains {1}, {2} or {3} is
            // shown as written, never rewritten by a later placeholder (UltiKits/UltiChat#39)
            String line = UltiChat.fillOnce(plugin.i18n("autoreply_list_entry"),
                    "{0}", name, "{1}", keywordStr, "{2}", modeStr, "{3}", responseStr);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    /**
     * Add a new auto-reply rule. The response is the rest of the line, so it can be a sentence: a
     * single-word parameter made every rule created by command answer with one word, and the rules
     * the module ships could not have been created by it (UltiKits/UltiChat#26).
     * 添加新的自动回复规则；回复为该行剩余的全部内容，可以是一句话。
     */
    @CmdMapping(format = "autoreply add <name> <response...>")
    public void onAutoReplyAdd(@CmdSender CommandSender sender,
                               @CmdParam("name") String name,
                               @CmdParam("response") String[] response) {
        onAutoReplyAdd(sender, name, String.join(" ", response));
    }

    /**
     * Adds the rule {@code name} answering {@code response}: the body of {@code autoreply add}, with
     * the response already joined into one line.
     *
     * @param sender   the sender to answer
     * @param name     the rule name
     * @param response the whole response
     */
    void onAutoReplyAdd(CommandSender sender, String name, String response) {
        if (name.indexOf(AutoReplyService.UNUSABLE_NAME_CHARACTER) >= 0) {
            // The configuration file stores the rule under its name as a path, so a '.' splits it
            // into two keys and the rule is renamed and disabled on the next read (UltiKits/UltiChat#25)
            String msg = plugin.i18n("autoreply_invalid_name").replace("{0}", name);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
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
        } catch (AutoReplyService.RulesReplacedException e) {
            reportReplacedByPanel(sender, name);
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
    @CmdMapping(format = "autoreply setkeyword <name> <keyword...>")
    public void onAutoReplySetKeyword(@CmdSender CommandSender sender,
                                      @CmdParam("name") String name,
                                      @CmdParam("keyword") String[] keyword) {
        // The keyword is the rest of the line, so it can be a phrase such as the shipped rule's
        // "server IP" (UltiKits/UltiChat#26)
        onAutoReplySetKeyword(sender, name, String.join(" ", keyword));
    }

    /**
     * Sets rule {@code name}'s keyword: the body of {@code autoreply setkeyword}, with the keyword
     * already joined into one phrase.
     *
     * @param sender  the sender to answer
     * @param name    the rule name
     * @param keyword the whole keyword
     */
    void onAutoReplySetKeyword(CommandSender sender, String name, String keyword) {
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
        } catch (AutoReplyService.RulesReplacedException e) {
            reportReplacedByPanel(sender, name);
            return;
        }
        // One pass, so a rule name containing {1} is shown as written (UltiKits/UltiChat#39)
        String msg = UltiChat.fillOnce(plugin.i18n("autoreply_keyword_set"), "{0}", name, "{1}", keyword);
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
        } catch (AutoReplyService.RulesReplacedException e) {
            reportReplacedByPanel(sender, name);
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
        if (cause instanceof ConfigEntryPresenceException) {
            // The framework's presence precondition did not hold on the read it writes against (UltiKits/UltiChat#51):
            // add found the rule already in the file - an operator wrote it by hand since the last reload - and
            // setkeyword found it no longer there. Nothing was written and the rule set is rolled back; the sender is
            // told which, and nothing is logged here because the reply is the whole report.
            String msg = ((ConfigEntryPresenceException) cause).getRequired() == EntryPresence.MUST_BE_ABSENT
                    ? plugin.i18n("autoreply_exists") : plugin.i18n("autoreply_not_in_file");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("{0}", name)));
            return;
        }
        if (cause instanceof ConfigWriteRefusedException) {
            // The framework's write gate refused the rule write - the file would have changed outside this rule,
            // or uses YAML anchors - and logs a WARNING naming the file and why. Nothing was written and the rule
            // set is rolled back; the sender is told so and why, the reason holding no configuration value
            // (maintainer decision 2026-10-05, UltiKits/UltiChat#50).
            // One WARNING with the framework's message (file and reason, no value), so the reply's pointer to the
            // server log holds even for a refusal the framework itself does not log (#50 review, P3-3).
            plugin.getLogger().warn(UltiChat.fillOnce(plugin.i18n("log_autoreply_not_saved_refused"),
                    "{RULE}", name, "{REASON}", String.valueOf(cause.getMessage())));
            String msg = UltiChat.fillOnce(plugin.i18n("autoreply_not_saved_refused"),
                    "{0}", name, "{1}", ((ConfigWriteRefusedException) cause).getReason());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        plugin.getLogger().error(cause, plugin.i18n("log_autoreply_save_failed").replace("{RULE}", name));
        String msg = plugin.i18n("autoreply_save_failed").replace("{0}", name);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    /**
     * Tell the sender the rule change did not take effect: a panel configuration update replaced the
     * rule set while the change was being saved, so the panel's rules are what is now active and on
     * disk, and the command's change is in neither (UltiKits/UltiChat#29). Trying again applies the
     * change to the rules the panel wrote.
     *
     * @param sender the sender to report to
     * @param name   the rule name the command was changing
     */
    private void reportReplacedByPanel(CommandSender sender, String name) {
        String msg = plugin.i18n("autoreply_not_applied").replace("{0}", name);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + plugin.i18n("help_admin_header"));
        sender.sendMessage(ChatColor.AQUA + "/uchat reload" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_reload"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply list" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_list"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply add <name> <response...>" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_add"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply setkeyword <name> <keyword...>" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_setkeyword"));
        sender.sendMessage(ChatColor.AQUA + "/uchat autoreply remove <name>" + ChatColor.WHITE + " - " + plugin.i18n("help_admin_autoreply_remove"));
    }
}
