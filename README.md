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

375 tests, 97.9% instruction coverage (JaCoCo).
