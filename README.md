# UltiChat

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.0-blue)](https://github.com/UltiKits/UltiTools-Reborn)

智能聊天管理模块 / Smart Chat Management Module

需要 UltiTools-API 6.3.0 或更高版本；更早的框架会拒绝加载本模块（本模块声明 `api-version: 630`）。

Requires UltiTools-API 6.3.0 or later; an older framework refuses to load this module (it declares `api-version: 630`).

从 UltiEssentials 提取的独立聊天功能模块，提供自动回复、聊天格式、公告广播、频道系统等 8 大功能。

Standalone chat module extracted from UltiEssentials with 8 features: auto-reply, chat format, announcements, channels, mentions, anti-spam, join/quit messages, and custom emojis.

## 功能 / Features

| 功能 | Feature | 说明 |
|------|---------|------|
| 智能自动回复 | Auto-Reply | 关键词匹配（精确/包含/正则），多行回复，冷却时间 |
| 聊天格式 | Chat Format | 自定义前缀/后缀，颜色代码，PlaceholderAPI |
| 入退消息 | Join/Quit | 自定义入服/退服消息，首次入服奖励，欢迎标题 |
| 定时广播 | Broadcasts | 聊天/Boss栏/标题三种广播，轮播消息 |
| @提及 | Mentions | @玩家名高亮提示+音效 |
| 聊天频道 | Channels | 全局/本地/自定义频道，范围限制，权限控制 |
| 防刷屏 | Anti-Spam | 冷却时间/重复检测/大写限制，按权限豁免 |
| 自定义表情 | Emojis | `:shortcode:` 替换为自定义文本/颜色 |

## 命令 / Commands

### 管理命令 / Admin Commands

| 命令 | 权限 | 说明 |
|------|------|------|
| `/uchat reload` | `ultichat.admin` | 重载配置 / Reload configs |
| `/uchat autoreply list` | `ultichat.admin` | 列出自动回复规则 / List auto-reply rules |
| `/uchat autoreply add <name> <response...>` | `ultichat.admin` | 添加规则（回复可为一句话）/ Add rule (the response may be a sentence) |
| `/uchat autoreply setkeyword <name> <keyword...>` | `ultichat.admin` | 修改规则关键词（可为词组）/ Change a rule's keyword (may be a phrase) |
| `/uchat autoreply remove <name>` | `ultichat.admin` | 移除规则 / Remove rule |

### 频道命令 / Channel Commands

| 命令 | 权限 | 说明 |
|------|------|------|
| `/ch list` | `ultichat.channel` | 列出可用频道 / List channels |
| `/ch <name>` | `ultichat.channel` | 切换频道 / Switch channel |

## 配置 / Configuration

配置文件位于插件数据目录 `config/` 下：

| 文件 | 说明 |
|------|------|
| `config/chat.yml` | 聊天格式、@提及、入退消息、反刷屏 |
| `config/autoreply.yml` | 自动回复规则（关键词、匹配模式、回复内容、冷却） |
| `config/channels.yml` | 频道定义（显示名、范围、权限、跨世界、可选的频道格式） |
| `config/announcements.yml` | 定时广播（聊天/Boss栏/标题，消息列表，间隔秒数，默认 300 / 60 / 600；`/ul reload` 即生效） |
| `config/emojis.yml` | 表情开关与自定义表情映射 |

### 自动回复示例 / Auto-Reply Example

```yaml
autoreply:
  enabled: true
  cooldown: 5             # 全局冷却（秒）/ global cooldown (seconds)
  rules:
    greeting:
      keyword: "你好"
      mode: contains      # exact / contains / regex
      case-sensitive: false
      response:
        - "&a欢迎来到服务器！"
        - "&7输入 /help 查看帮助"
      permission: ""
```

### 频道示例 / Channel Example

```yaml
channels:
  enabled: true
  default-channel: global
  channels:
    global:
      display-name: "&f[全局]"
      permission: ""
      range: -1
      cross-world: true
      # 不设 format 时：chat.yml 的 chat.format 前加频道显示名
      # Without format: chat.yml's chat.format with the display name in front
    local:
      display-name: "&a[本地]"
      # 可选：频道自己的格式，{display} = 频道显示名（需 chat.format-enabled: true）
      # Optional: the channel's own format, {display} = display name (needs chat.format-enabled: true)
      format: "{display} &f{player}&7: {message}"
      permission: ""
      range: 100
      cross-world: false
```

## 权限 / Permissions

| 权限 | 说明 |
|------|------|
| `ultichat.admin` | 管理命令；默认仅 OP / Admin commands; operators by default |
| `ultichat.channel` | 频道命令；默认所有玩家 / Channel commands; every player by default |
| `ultichat.color` | 聊天中使用颜色代码 / Colour codes in chat |
| `ultichat.emoji` | 使用自定义表情 / Emoji shortcodes |
| `ultichat.spam.bypass` | 豁免防刷屏 / Bypass anti-spam |
| `ultichat.autoreply.bypass` | 不触发自动回复 / Bypass auto-reply |
| 频道的 `permission` 值 / a channel's `permission` value | 加入该频道（如出厂 staff 频道的 `ultichat.channel.staff`）/ Join that channel (e.g. `ultichat.channel.staff` for the shipped staff channel) |

@提及没有权限节点，由 `config/chat.yml` 的 `mentions.enabled` 与 `mentions.self-mention` 控制。
Mentions have no permission node; `mentions.enabled` and `mentions.self-mention` in `config/chat.yml` control them.

## Optional temporary anti-spam mutes / 可选的反刷屏临时禁言

Automatic muting is **off by default**. To opt in, add the following under the existing `anti-spam` section in `config/chat.yml` (do not replace your other settings), then run `/uchat reload`:

```yaml
  auto-mute:
    enabled: true
    threshold: 3
    window: 60
    duration: 30
```

Cooldown, duplicate and caps refusals each count once. The third refusal in a rolling 60 seconds mutes that UUID for 30 seconds; the boundary is included. Accepted messages do not reset the count. Muted attempts are refused without extending the deadline. Threshold accepts 2–20, window 1–600 seconds and duration 5–600 seconds. The existing `anti-spam.enabled` must also be true.

Strikes and mutes survive reconnects and ordinary config reloads, **not a server restart or module recreation**. Changing the numeric settings resets pending strikes but keeps active deadlines; reloading identical settings changes nothing. Disabling either enable switch and reloading clears auto-mute state, so re-enabling starts empty. Existing cooldown/duplicate history is unaffected.

`ultichat.spam.bypass` exempts both triggering and enforcement. There is no new unmute command: staff can temporarily grant that existing permission for one player, or disable automatic muting and reload to clear all automatic mutes. Revoking bypass before a mute expires restores its enforcement. Only chat handled by UltiChat is muted, not commands or another plugin's private messages. There is no escalation or restart persistence.

An older `anti-spam.mute-duration` key stays ignored and never opts a server in. Existing configuration and customized language files are not replaced; missing notice keys fall back to the jar's catalogue. Muted players see an onset notice and then the remaining seconds rounded up.

自动禁言**默认关闭**。要启用，在 `config/chat.yml` 已有的 `anti-spam` 段内添加上述 `auto-mute` 小节（不要替换其他设置），再执行 `/uchat reload`。冷却、重复和大写限制每拦截一条消息计一次；滚动 60 秒内第 3 次触发时禁言 30 秒，恰好位于窗口边界的记录仍计入。合规消息不清空计数，禁言中的尝试不会延长时间。次数范围 2–20，窗口 1–600 秒，禁言 5–600 秒；原有 `anti-spam.enabled` 也必须开启。

记录跨重连和普通配置重载保留，但**不跨服务器重启或模块重新创建**。更改数字配置会清空尚未触发禁言的计数，不改变已有禁言的到期时间；相同配置重载不变。关闭任一启用开关并重载会清空自动禁言记录，重新开启从零开始，原有冷却与重复消息历史不变。`ultichat.spam.bypass` 同时豁免触发和执行；没有新增解除禁言命令，可临时给单个玩家该权限，或关闭自动禁言并重载以清除全部记录。禁言到期前撤回豁免权限会恢复拦截。不影响命令和其他插件的私聊，不递增惩罚，也不持久化到磁盘。旧 `anti-spam.mute-duration` 仍无效，不会作为启用依据；服主配置与语言文件不会被替换，缺少的新提示从 jar 补全。
## PlaceholderAPI expansion / 占位符扩展

When PlaceholderAPI is installed and enabled before UltiChat loads, other plugins can use:

| Placeholder | Value / 值 |
|-------------|------------|
| `%ultichat_channel%` | Current channel ID, e.g. `global` or `local` / 当前频道 ID |
| `%ultichat_channel_display%` | Current channel's display name, with color codes applied, e.g. `[Local]` / 当前频道显示名（含颜色） |

For example, run `/papi parse me %ultichat_channel%` as an online player, switch with
`/ch local`, and parse again: the value changes to `local`. Display names follow the
current `config/channels.yml`, including changes loaded by `/uchat reload` or `/ul reload`.
A player without a tracked assignment gets the same default/fallback channel as chat.
These placeholders are read-only and add no permission nodes or configuration keys.

Known placeholders return empty text without an online player or while `channels.enabled`
is false. Unknown parameters stay unresolved; use the exact lowercase spellings above.
The expansion survives `/papi reload` and is removed when UltiChat unloads. Install or remove
PlaceholderAPI with a server restart, not a runtime plugin-manager reload. UltiChat still
works without PlaceholderAPI. Existing welcome tokens such as `%online_players%` and
`%max_players%` keep their existing behaviour; they are not renamed to expansion tokens.

在 UltiChat 加载前安装并启用 PlaceholderAPI，即可在其他插件中使用上表的占位符。
在线玩家可执行 `/papi parse me %ultichat_channel%`，再用 `/ch local` 切换频道并重新解析，
结果会变为 `local`。显示名随配置重载更新；无在线玩家或关闭频道功能时返回空文本。
占位符只读，不新增权限或配置；`/papi reload` 后仍保留，UltiChat 卸载时移除。
安装或移除 PlaceholderAPI 后请重启服务器；原有欢迎消息中的占位符保持不变。

## 已知限制 / Known Limitations

The auto-reply commands preserve hand-written configuration rather than repairing incomplete or
invalid entries. For these unusual file shapes, edit `config/autoreply.yml` yourself, then run
`/uchat reload` before using the commands again (see [#53](https://github.com/UltiKits/UltiChat/issues/53)):

- **A rule without a `keyword:` line:** it cannot trigger, and `setkeyword` refuses to add its
  first keyword because the command changes only an existing keyword entry. Add the `keyword:`
  line inside that rule by hand; its response and other settings are not rewritten by the command.
- **`autoreply.rules` is not a YAML map:** for example, `rules: not a map`. If a rule is still
  loaded from before that edit, `setkeyword` replies that it has no keyword line, although the
  actual problem is the parent `rules` value. `add` also refuses to write. Restore `rules` to a
  map of rule names and their settings; neither command changes the malformed file.
- **A null placeholder such as `foo: ~`:** it does not define a usable rule, but `add foo` replies
  `Rule 'foo' already exists.` because the name is present in the file. Replace the placeholder
  with a complete rule, or delete that placeholder entry, reload, then use `add foo` to create it.
  The refused `add` leaves the placeholder and its comments unchanged.

手写的不完整或结构异常配置不会被命令自动修复：规则缺少 `keyword:` 行时请手动补上；
`autoreply.rules` 必须是规则名到规则设置的 YAML 映射（不能写成字符串）；`foo: ~` 占位条目
仍算文件中已存在的名称，请手动补成完整规则，或删除该占位条目后再用 `add` 创建。
编辑后先运行 `/uchat reload`，再继续使用命令；拒绝写入不会覆盖服主的配置。

- **删除 `config/autoreply.yml` 之后 / After deleting `config/autoreply.yml`:** 请先执行 `/uchat reload`，再执行 `/uchat autoreply add`。否则文件会被重新创建且只包含新规则，出厂示例规则 `server-ip` 与 `rules-info` 不会恢复。
  Run `/uchat reload` before `/uchat autoreply add`; otherwise the file is recreated holding only the new rule and the shipped `server-ip` and `rules-info` example rules do not come back.

## 构建 / Build

```bash
# 在仓库根目录执行 / run from the repository root
mvn clean package
```

JAR 文件: `target/UltiChat-1.0.0.jar`

## 测试 / Testing

```bash
# 在仓库根目录执行 / run from the repository root
mvn test
```

737 tests; 97.53% line coverage and 90.77% branch coverage (JaCoCo, `mvn -B -T 1 verify`).

## License / 许可证

Licensed under the [MIT License](LICENSE).
