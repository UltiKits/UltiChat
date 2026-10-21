# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [2.0.0] - 2026-10-21

### Changed

- Requires UltiTools-API 6.3.0 or later. The module's `api-version` rises from `620` in the
  released `v1.0.0` to `630`; an older framework refuses to load it.
- 需要 UltiTools-API 6.3.0 或更高版本。模块的 `api-version` 从已发布的 `v1.0.0` 中的 `620`
  提升至 `630`；更早的框架会拒绝加载本模块。

- `plugin.yml` now declares `identify-string: ultichat`, the key of this module's entry in the
  UltiCloud catalogue. The framework's update check and `/upm update` skip a module that does not
  declare it, so this module now takes part in both: a later published version carrying the same key
  is reported at startup and can be installed with `/upm update` (UltiKits/UltiTools-Reborn#474).
  An older framework refuses the module with a warning that the UltiTools version is outdated (the
  refusal names the module by its `plugin.yml` `name:`, `UltiTools-Chat`).
- `plugin.yml` 现在声明 `identify-string: ultichat`，即本模块在 UltiCloud 模块目录中的条目键。框架的更新检查和
  `/upm update` 会跳过未声明该键的模块，因此本模块现在会参与两者：带有同一键的更高发布版本会在启动时提示，
  并可用 `/upm update` 安装（UltiKits/UltiTools-Reborn#474）。更早的框架会拒绝加载本模块，并给出 UltiTools 版本过旧的警告
  （拒绝信息以 `plugin.yml` 的 `name:` 即 `UltiTools-Chat` 指称本模块）。

- `/uchat autoreply add <name> <response...>` takes the rest of the line as the response, and
  `/uchat autoreply setkeyword <name> <keyword...>` the rest of the line as the keyword, so a rule
  created by command can answer with a sentence and match a phrase. Both took a single word, and a
  longer line was refused with the usage text (UltiKits/UltiChat#26).
- `/uchat autoreply add <名称> <回复...>` 现在把该行剩余内容作为回复，`/uchat autoreply setkeyword <名称> <关键词...>`
  把该行剩余内容作为关键词，因此通过命令创建的规则可以用一句话回复、匹配词组。此前两者都只接受一个词，更长的内容会被当作用法错误拒绝
  （UltiKits/UltiChat#26）。

- Message and title settings in `config/announcements.yml`, `config/chat.yml`,
  `config/channels.yml` and `config/autoreply.yml` -- the announcements, the join/quit messages,
  the shipped channels' display names and the two example auto-reply rules -- are written in the
  server's language when the module starts, and the file is what the module shows; previously they
  were fixed English text, so `language: zh` had no effect on them. A setting that is still
  built-in text -- in any language, or a default an earlier version shipped -- follows
  `language`: it is rewritten when the module starts or after `/ul reload`. A setting you edited is
  kept. To keep a built-in text but stop it following `language`, change at least one character. An
  example auto-reply rule counts as built-in only while its keyword and response together are one
  language's built-in pair; a rule where either was edited is yours. Edit these settings in the
  config files: an edit of the extracted language file does not change them, and released versions
  never read them from it. A changed `language` is picked up at a restart or a full `/ul reload`,
  not by a single-module reload such as `/uchat reload` or `/ul reload UltiChat`
  (UltiKits/UltiChat#18).
- The three channel formats earlier versions shipped (`{display}&f: {message}`,
  `{display}&7: {message}`, `&c[Staff] &f{player}&7: {message}`) are now removed from
  `config/channels.yml` when the module starts, so the file matches what the chat shows; the chat
  line is unchanged (UltiKits/UltiChat#16, #18).
- `config/announcements.yml`、`config/chat.yml`、`config/channels.yml` 与 `config/autoreply.yml`
  中的消息与标题设置（公告、进出服消息、出厂频道的显示名与两条示例自动回复规则）在模块启动时按服务器语言写入，
  文件内容即模块显示的内容；此前它们是写死的英文，`language: zh` 对它们不起作用。
  仍为内置文本（任一语言的内置文本，或旧版本的出厂默认值）的设置会跟随 `language`：模块启动或执行 `/ul reload` 后改写为当前语言的文本。你改过的设置保持不变。若想保留内置文本又不让它跟随语言，
  请至少改动一个字符。示例自动回复规则只有在关键词与回复合起来仍是同一语言的内置文本时才算内置；关键词或回复任一被改过，
  该规则即归你所有。请在配置文件中修改这些设置：修改解压出的语言文件不会改变它们，已发布的版本也从不从语言文件读取它们。
  修改后的 `language` 在重启或完整的 `/ul reload` 后生效，单个模块的重载（如 `/uchat reload` 或 `/ul reload UltiChat`）不会读取它（UltiKits/UltiChat#18）。
- 早期版本出厂的三条频道格式（`{display}&f: {message}`、`{display}&7: {message}`、
  `&c[Staff] &f{player}&7: {message}`）现在会在模块启动时从 `config/channels.yml` 中移除，
  使文件与聊天显示一致；聊天行保持不变（UltiKits/UltiChat#16、#18）。

- `anti-spam.duplicate-window` in `config/chat.yml` now takes effect. It was documented as the
  duplicate-detection time window but never read: a message counted as a duplicate when the
  player's last `anti-spam.max-duplicate` accepted messages were all the same text, however long
  ago they had been sent. It now means: `0` -- the new declared default -- is no window-based expiry,
  that old counting rule, but a player's duplicate history is cleared after 24 hours without
  messages (a memory bound; see the UltiKits/UltiChat#40 entry below); a positive value (up to
  600) is a window in seconds, and a retained copy older than it stops counting. The allowed range is now 0-600 (it was 10-600). **Upgrade consequence:** a
  server that ran an earlier version already has `duplicate-window: 60` in its own file (the
  framework wrote the old default there), and that 60 now applies, so duplicate detection on such a
  server is more permissive than before the upgrade: with the default `max-duplicate: 3`, the same
  message sent three times is refused on the fourth only if the first of those three is at most 60
  seconds old. At module start and on every reload, any window above 0 is announced with one
  warning naming the file, the key and the value; set it to `0` and run `/uchat reload` to restore
  the previous duplicate-counting rule (UltiKits/UltiChat#14).
- `config/chat.yml` 中的 `anti-spam.duplicate-window` 现在会生效。它此前被文档描述为重复检测的时间窗口，
  但从未被读取：只要玩家最近 `anti-spam.max-duplicate` 条被接受的消息都是同一内容，无论发送于多久以前，
  新消息都会被判为重复。现在它的含义是：`0`（新的声明默认值）表示不按窗口过期，即上述旧的计数规则，
  但玩家 24 小时未发消息后其重复记录会被清除（内存上限，见下文 UltiKits/UltiChat#40 的条目）；
  正值（最大 600）表示以秒为单位的时间窗口，早于该窗口的保留消息不再计数。允许范围改为 0-600（原为 10-600）。
  **升级后果：**运行过早期版本的服务器，其自己的文件中已有 `duplicate-window: 60`（框架曾把旧默认值写入该文件），
  这个 60 现在会生效，因此该服务器上的重复检测会比升级前更宽松：在默认 `max-duplicate: 3` 下，
  同一消息发送三次后，只有当这三次中的第一次发送于 60 秒以内时，第四次才会被拦截。
  模块启动时与每次重载时，只要时间窗口大于 0，就会记录一条指明文件、键名与当前值的警告；
  将其设为 `0` 并执行 `/uchat reload`，即可恢复之前的重复计数规则（UltiKits/UltiChat#14）。

- A channel format you edited before this version now takes effect. Channel formats were never
  applied before, so an edit -- for example recolouring the shipped `{display}&f: {message}` to
  `{display}&e: {message}` -- showed nothing; it is no longer one of the three formerly shipped
  strings, so it now applies as written, and a format without `{player}` or `{displayname}` shows
  no sender name, one without `{message}` no message text. At module start and on every reload,
  each channel whose format is in effect and lacks one of those gets one warning naming the file,
  the channel, and what to add (UltiKits/UltiChat#16).
- 您在本版本之前编辑过的频道格式现在会生效。频道格式此前从未被应用，因此编辑它（例如把出厂的
  `{display}&f: {message}` 改色为 `{display}&e: {message}`）不会有任何显示；改动后它已不再是三条旧出厂字符串之一，
  因此现在会按原样生效：不含 `{player}` 或 `{displayname}` 的格式不会显示发送者名字，不含 `{message}` 的格式不会显示消息内容。
  模块启动时与每次重载时，对每个格式生效且缺少上述内容的频道，会记录一条指明文件、频道与需要添加什么的警告（UltiKits/UltiChat#16）。

### Removed

- The language-file entries `spam_muted` and `no_permission` from `lang/en.json` and
  `lang/zh.json`: no code ever displayed them. Players are never muted automatically (automatic
  muting is requested as a feature in UltiKits/UltiChat#30), and a permission refusal comes from
  UltiTools' own translated message.
- 从 `lang/en.json` 与 `lang/zh.json` 中移除语言文件条目 `spam_muted` 与 `no_permission`：从未有任何代码显示它们。
  玩家从不会被自动禁言（自动禁言已作为功能请求记录在 UltiKits/UltiChat#30），权限拒绝消息来自 UltiTools 自身已翻译的消息。

- Removed the automatic-mute setting `anti-spam.mute-duration` from `config/chat.yml`. Automatic
  muting was documented but never happened: the code that would have muted a player was never
  called, so no player was ever muted, whatever the setting said. Anti-spam behaves exactly as
  before -- a message that trips the cooldown, duplicate or capital-letter check is refused, and
  the player can send the next acceptable message as usual. An upgraded server that still has the
  key in its own file gets one warning at module start and on every reload, naming the file and the
  key; delete the key to silence it. Removing the setting is not a rejection of automatic muting:
  it is requested as a feature in UltiKits/UltiChat#30 (UltiKits/UltiChat#15).
- 从 `config/chat.yml` 中移除了自动禁言设置 `anti-spam.mute-duration`。自动禁言此前有文档说明，
  但从未真正发生：本应禁言玩家的代码从未被调用，因此无论该设置为何值，都没有任何玩家被禁言过。
  反刷屏的行为与之前完全相同——触发冷却、重复或大写字母检测的消息会被拦截，玩家之后仍可照常发送合规消息。
  自己的文件中仍保留该键的升级服务器，会在模块启动与每次重载时收到一条指明文件与键名的警告；
  删除该键即可消除警告。移除该设置并不是否决自动禁言：它已作为功能请求记录在 UltiKits/UltiChat#30 中
  （UltiKits/UltiChat#15）。

### Fixed

- `/uchat autoreply add`, `setkeyword` and `remove` now write exactly the rule they name into
  `config/autoreply.yml` - `remove` removes it - and nothing else: every other rule, one an operator added
  or edited by hand while the server ran included, and every other line of the file stay byte for byte. `add`
  refuses a name the file already holds (`Rule '<name>' already exists.`), one an operator added by hand since the
  last reload included; `setkeyword` writes only that rule's keyword, so a hand edit of its response stays, and
  writes nothing when the operator deleted the rule by hand (the replies are described under
  UltiKits/UltiChat#51 below). A rule name is read whole, dots included. Before, the
  commands saved the whole configuration: against the framework's 6.3.0 write rules a rule edited by hand was
  then not changed or removed while the command reported success, and a write the framework refused was
  reported as success. Now a refused write rolls the change back and is answered
  `Rule '<name>' was not saved: <reason>. The change was rolled back; fix config/autoreply.yml as the server
  log says, then run the command again.` The module's own line about an overwritten operator edit, and the
  framework's overwrite warning that replaced it earlier in this release (UltiTools-Reborn#527), are gone:
  nothing the operator wrote is overwritten any more (UltiKits/UltiChat#50).
- `/uchat autoreply add`、`setkeyword` 和 `remove` 现在只把被点名的那条规则写入 `config/autoreply.yml`（`remove` 则删除它），
  不写其他内容：其他每条规则（包括服务器运行期间服主手动添加或修改的规则）以及文件的其他每一行都逐字节保持不变。`add` 会拒绝文件中
  已有的规则名（包括上次重载后服主手动添加的规则），回复「规则 '<名称>' 已存在」；`setkeyword` 只写该规则的关键词，因此该规则
  响应内容的手动修改会保留；若服主已手动删除该规则，则不写入任何内容（回复见下文 UltiKits/UltiChat#51）。
  规则名按整体读取（包括其中的点）。此前命令保存整个配置：在框架 6.3.0 的写入规则下，手动改过的规则不会被修改或删除，
  命令却报告成功；框架拒绝的写入也被报告为成功。现在被拒绝的写入会回滚，并回复「规则 '<名称>' 未保存：<原因>。更改已回滚；
  请按服务器日志的提示修正 config/autoreply.yml，然后重新执行该命令。」模块自己关于覆盖服主修改的那一行，以及本版本早先用来
  取代它的框架覆盖警告（UltiTools-Reborn#527）都已不存在：服主写下的内容不再被覆盖（UltiKits/UltiChat#50）。

- `/uchat autoreply add` and `setkeyword` now ask the framework whether `config/autoreply.yml` holds the
  rule, on the same read of the file as the write (the framework's `MUST_BE_ABSENT` and `MUST_BE_PRESENT`
  write conditions), instead of reading the file a second time inside the module. A valid file is no longer
  refused: `add` writes the rule when the file has no `autoreply` section, no `rules` key, `rules:` left empty,
  `rules: {}`, only comments, or the settings in the flat `"autoreply.rules":` form. Before, the command
  answered `config/autoreply.yml cannot be read as it is now. Run /uchat reload`, and the advice did not help,
  because the file was the same after a reload. A rule an operator wrote by hand since the last reload is still
  never replaced (`Rule '<name>' already exists.`; an entry holding only `~` counts as a rule of that name).
  `setkeyword` on a rule the operator deleted, or whose entry has no `keyword:` line, writes nothing and
  answers `Rule '<name>' has no keyword line in config/autoreply.yml (...), so nothing was saved`; give such a
  rule its first keyword by editing the file. A file the framework cannot read or parse is answered with the
  framework's reason (`Rule '<name>' was not saved: <reason>. The change was rolled back; ...`). A refused or
  failed write now rolls the in-memory rule set back whatever the failure was; before, only an `IOException`
  did. **Changed:** a file the operator deleted while the server runs holds no rule, so `add` now creates it
  holding only the new rule (nothing the operator wrote is overwritten); `setkeyword` still writes nothing.
  After deleting `config/autoreply.yml`, run `/uchat reload` before `/uchat autoreply add`: otherwise the file
  holds only the new rule and the shipped `server-ip` and `rules-info` example rules do not come back
  (UltiKits/UltiChat#51).
- `/uchat autoreply add` 和 `setkeyword` 现在向框架询问 `config/autoreply.yml` 是否已有该规则，询问与写入基于对文件的同一次读取
  （框架的 `MUST_BE_ABSENT` 与 `MUST_BE_PRESENT` 写入条件），不再在模块内部第二次读取该文件。有效的文件不再被拒绝：
  文件没有 `autoreply` 段、没有 `rules` 键、`rules:` 留空、`rules: {}`、只有注释，或设置写成扁平的 `"autoreply.rules":` 形式时，
  `add` 都会写入规则。此前命令回复「config/autoreply.yml 无法按当前内容读取，请执行 /uchat reload」，而该建议并无帮助，
  因为重载后文件依然如此。服主在上次重载后手动写入的规则仍不会被替换（「规则 '<名称>' 已存在」；只写了 `~` 的条目也算同名规则）。
  `setkeyword` 针对服主已删除的规则、或条目中没有 `keyword:` 行的规则，不写入任何内容，并回复「规则 '<名称>' 在
  config/autoreply.yml 中没有 keyword 行（……），因此未保存任何内容」；要给这样的规则设置第一个关键词，请直接编辑文件。
  框架无法读取或解析的文件，回复框架给出的原因（「规则 '<名称>' 未保存：<原因>。更改已回滚；……」）。被拒绝或失败的写入现在
  无论出于什么原因都会回滚内存中的规则集；此前只在 `IOException` 时回滚。**变更：** 服务器运行期间被服主删除的文件不含任何规则，
  因此 `add` 现在会重新创建该文件并只写入新规则（服主写下的内容不会被覆盖）；`setkeyword` 仍不写入任何内容。
  删除 `config/autoreply.yml` 后，请先执行 `/uchat reload` 再执行 `/uchat autoreply add`，否则文件中只有新规则，出厂示例规则
  `server-ip` 与 `rules-info` 不会恢复（UltiKits/UltiChat#51）。

- `/uchat reload` now reports what the framework's reload reported. A reload the framework finished
  partially is answered with the parts that did not reload, and one that failed with its cause; the
  reply `UltiChat configuration reloaded.` is only sent when every part reloaded. Before, the reply was
  always success, and a failing reload surfaced as an unhandled error (UltiKits/UltiChat#48).
- `/uchat reload` 现在如实反映框架的重载结果。框架只完成部分重载时，回复未重载的部分；重载失败时回复失败原因；
  只有全部重载成功才回复「UltiChat 配置已重新加载」。此前回复总是成功，重载失败时还会表现为未处理的错误
  （UltiKits/UltiChat#48）。

- After `/uchat reload` or `/ul reload`, a player who had switched into a channel that was removed
  from `channels.channels` is moved to the channel new players land in (see the
  `channels.default-channel` entry below: one that needs no permission) and told in one line. Before,
  the player kept the removed channel: their chat had no range or world limit among only the players
  holding that name, it no longer reached anyone else, and nothing was logged (UltiKits/UltiChat#45).
- `/uchat reload` 或 `/ul reload` 之后，已切换到某个已从 `channels.channels` 删除的频道的玩家，会被移到新玩家所进入的频道
  （见下方 `channels.default-channel` 条目：不需要权限的频道），并收到一行提示。此前该玩家仍留在被删除的频道里：
  聊天只在持有同一名称的玩家之间、不受范围或世界限制，不再送达其他人，也没有任何日志（UltiKits/UltiChat#45）。

- A `channels.default-channel` that names no channel defined under `channels.channels` is now named in
  the console when the module starts and on every reload (while channels are enabled), together with
  the channel new players are placed in instead: `global` if it is defined and needs no permission,
  otherwise the first channel in the file that needs none (a channel `/ch` would refuse them is never
  chosen). Before, the setting was accepted without a word and new players were placed in a channel
  that does not exist. With no such channel, new players are all placed in the one undefined channel
  and the warning says so (UltiKits/UltiChat#44).
- 当 `channels.default-channel` 指向 `channels.channels` 下没有定义的频道时，模块启动和每次重载时（频道启用的情况下）
  会在控制台点名，并说明新玩家改为进入哪个频道：已定义且不需要权限的 `global` 时用 `global`，否则用文件中第一个不需要权限的频道
  （不会选中 `/ch` 会拒绝玩家加入的频道）。此前该设置被无声接受，新玩家被放进一个不存在的频道。没有这样的频道时，
  新玩家都被放进那一个未定义的频道，警告中会如实说明（UltiKits/UltiChat#44）。

- With PlaceholderAPI installed, the join, quit and welcome texts now show the module's own
  `%online_players%` and `%max_players%` as numbers. The shipped welcome line showed them as the
  literal tokens, because the text went to PlaceholderAPI first and no expansion provides them. The
  module's own placeholders are now filled first, in one pass, and PlaceholderAPI runs on the result;
  a nickname that contains a `%token%` is still shown as written. The module's `{player}` and
  `{displayname}` are filled in these texts too; with PlaceholderAPI installed they stayed literal
  (UltiKits/UltiChat#42).
- 装有 PlaceholderAPI 时，进入、退出和欢迎文本现在会把模块自己的 `%online_players%` 与 `%max_players%` 显示为数字。
  出厂欢迎语此前把它们原样显示为占位符，因为文本先交给了 PlaceholderAPI，而没有任何扩展提供这两个占位符。
  现在先一次性替换模块自己的占位符，再把结果交给 PlaceholderAPI；昵称里含有的 `%token%` 仍按原样显示。
  这些文本里模块自己的 `{player}` 与 `{displayname}` 现在也会被替换；装有 PlaceholderAPI 时它们此前同样原样显示
  （UltiKits/UltiChat#42）。

- At `anti-spam.duplicate-window: 600` a repeat sent exactly 600 seconds after the newest of a
  player's retained copies is now refused as a duplicate, as the window says; the history was dropped
  one millisecond too early, so that repeat was accepted. The cooldown comparison was checked in the
  same pass and already agrees with its own cleanup (UltiKits/UltiChat#41).
- 在 `anti-spam.duplicate-window: 600` 时，与玩家保留的最新副本恰好相隔 600 秒的重复消息，现在按窗口设定被当作重复消息拦截；
  此前历史记录被提早一毫秒清除，该重复消息会被放行。冷却时间的比较已在同一轮核对，与它自己的清理一致（UltiKits/UltiChat#41）。

- A configuration value UltiChat cannot use is now named in the console when the module starts and
  on every reload, with what it does instead: an `announcements.bossbar.color` that is no boss-bar
  colour (blue is used), a `mentions.sound` that is no sound (the default sound now plays; it played
  nothing), an auto-reply rule `mode` other than `contains`, `exact` or `regex` (the rule matches as
  `contains`), and an auto-reply regular expression that does not compile (the rule never fires).
  Each of these used to happen silently.
- UltiChat 无法使用的配置值现在会在模块启动和每次重载时于控制台点名，并说明实际做法：不是 Boss 栏颜色的
  `announcements.bossbar.color`（使用蓝色）、不是音效的 `mentions.sound`（现在播放默认音效，此前不播放）、
  不是 `contains`、`exact` 或 `regex` 的自动回复规则 `mode`（按 `contains` 匹配），以及无法编译的自动回复正则表达式（该规则永远不会触发）。
  此前这些情况都不会有任何提示。

- Anti-spam and auto-reply cooldown history now expires by elapsed time rather than by leaving
  the server. Reconnecting does not reset either history, so it cannot be used to bypass these
  checks. Cooldown checks discard history once it is 60 seconds old; duplicate history is cleared after
  more than 600 seconds without an accepted message while `anti-spam.duplicate-window` is positive,
  or after more than 24 hours while it is `0`. Auto-reply cooldown history expires after 300
  seconds. Expired records are also swept once a minute, so players who stop chatting do not
  leave records for the lifetime of the server. Concurrent chat processing no longer loses a
  freshly recorded duplicate-history entry during expiry, and each chat message or matching
  auto-reply no longer scans every tracked player (UltiKits/UltiChat#20, #34, #40).
- 反刷屏与自动回复冷却记录现在按经过时间清理，而不是在玩家离开服务器时清理。重连不会重置这两类记录，
  因此不能通过重连绕过检测。消息冷却检测会丢弃已满 60 秒的记录；`anti-spam.duplicate-window` 为正数时，
  超过 600 秒未发送被接受的消息后清除重复记录；为 `0` 时则在超过 24 小时后清除。自动回复冷却记录
  在 300 秒后过期。每分钟也会清理一次过期记录，因此停止聊天的玩家不会在服务器整个运行期间一直留下记录。
  并发聊天处理在清理过期记录时不再丢失刚记录的重复消息；每条聊天消息或匹配的自动回复也不再扫描所有
  被追踪的玩家（UltiKits/UltiChat#20、#34、#40）。

- A chat format using `{displayname}` inserts the player's display name as written. The name was put
  into the format before `{message}` and PlaceholderAPI were filled, so a nickname containing
  `{message}` repeated the message, and one containing a PlaceholderAPI placeholder such as
  `%server_name%` was expanded as if the operator had written it. Colour codes in the name still show
  as colour. The shipped `chat.format` uses `{player}` and was not affected (UltiKits/UltiChat#32).
- 使用 `{displayname}` 的聊天格式现在按原样插入玩家显示名。此前显示名在填入 `{message}` 与 PlaceholderAPI 之前就放进了格式，
  因此含有 `{message}` 的昵称会让消息重复出现，含有 PlaceholderAPI 占位符（如 `%server_name%`）的昵称会像管理员写入的一样被展开。
  显示名中的颜色代码仍显示为颜色。出厂的 `chat.format` 使用 `{player}`，不受影响（UltiKits/UltiChat#32）。

- Messages show an apostrophe once: `Auto-reply rule 'greet' added.`, `You don't have permission for
  channel staff.` Language entries wrote every apostrophe doubled, and players saw both. The jar's
  text is corrected, and a doubled apostrophe in a language file an earlier version extracted onto
  the server is shown as one too (UltiKits/UltiChat#37).
- 消息中的引号现在只显示一个，例如 `Auto-reply rule 'greet' added.`。此前语言条目把每个引号都写成两个，玩家会看到两个。
  jar 中的文字已更正，服务器上由旧版本解压出的语言文件中的双引号也会显示为一个（UltiKits/UltiChat#37）。

- The `/ch` channel list shows a channel's display name exactly as configured, and the join, quit and
  welcome lines (without PlaceholderAPI) insert a player's display name exactly as it is. A value
  containing a later placeholder, such as `{1}` or `%online_players%`, was rewritten by it.
- `/ch` 频道列表现在按配置原样显示频道显示名；未安装 PlaceholderAPI 时，加入、退出与欢迎消息按原样插入玩家显示名。此前值中若含有
  随后的占位符（如 `{1}`、`%online_players%`），会被其改写。

- `/uchat autoreply list` and `/uchat autoreply setkeyword` show a rule name, keyword or response
  exactly as written. A value containing `{1}`, `{2}` or `{3}` was rewritten by the placeholders
  filled after it, so the line showed text the rule does not have (UltiKits/UltiChat#39).
- `/uchat autoreply list` 与 `/uchat autoreply setkeyword` 现在按原样显示规则名、关键词和回复。此前值中若含有 `{1}`、`{2}`
  或 `{3}`，会被随后填入的占位符改写，显示出规则并不具有的内容（UltiKits/UltiChat#39）。

- A `/uchat autoreply add`, `setkeyword` or `remove` whose save coincides with a panel update of the
  auto-reply rules now says the change did not take effect ("...the panel replaced the auto-reply rules
  while it was being saved. Run the command again."). The panel's update replaces the whole rule set,
  so the command's change was neither active nor saved, yet the command reported success. The panel's
  rules are kept as the panel wrote them (UltiKits/UltiChat#29).
- `/uchat autoreply add`、`setkeyword` 或 `remove` 的保存恰好与面板更新自动回复规则同时发生时，命令现在会说明更改未生效（保存时面板替换了规则，
  请重新执行）。面板更新会整体替换规则集，因此命令的更改既未生效也未保存，而命令此前却报告成功。面板写入的规则保持不变
  （UltiKits/UltiChat#29）。

- `/uchat autoreply add` refuses a rule name containing `.` and says why. The configuration file
  stores a rule under its name as a path, so `my.rule` was split into two keys when the file was
  written: the rule was renamed to `my` and never fired, while the command reported it added
  (UltiKits/UltiChat#25).
- `/uchat autoreply add` 现在拒绝包含 `.` 的规则名并说明原因。配置文件以规则名作为路径保存规则，`my.rule` 写入文件时会被拆成两个键：
  规则被改名为 `my` 且永远不会触发，而命令却报告已添加（UltiKits/UltiChat#25）。

- The anti-spam refusals now follow the `language` setting. The cooldown, duplicate and
  too-many-capitals refusals were fixed Chinese text in every language although the language files
  already carried them; they now come from `spam_cooldown`, `spam_duplicate` and `spam_caps`
  (`Please wait before sending another message.`, `Stop sending duplicate messages!`,
  `Please reduce the amount of uppercase letters.` under `language: en`), shown in red. Under
  `language: zh` the wording is the language file's, which differs slightly from the old fixed text
  (UltiKits/UltiChat#18).
- `language: zh` now applies to what was fixed English text: the `/ch` and `/uchat` help, the
  players-only refusal of `/ch` from the console, the two commands' descriptions (shown by `/help`),
  and the console lines about a channel format missing a sender or message token, an applied
  `anti-spam.duplicate-window`, a key this version no longer reads and a failed `config/autoreply.yml`
  save. Their English wording is unchanged.
- 反垃圾消息的拒绝提示现在跟随 `language` 设置。冷却、重复与大写字母过多三种拒绝提示原先在任何语言下都是写死的中文，
  而语言文件里其实已有它们；现在分别来自 `spam_cooldown`、`spam_duplicate` 与 `spam_caps`（`language: en` 下为
  `Please wait before sending another message.`、`Stop sending duplicate messages!`、
  `Please reduce the amount of uppercase letters.`），以红色显示。`language: zh` 下的措辞取自语言文件，与原先写死的文本
  略有不同（UltiKits/UltiChat#18）。
- `language: zh` 现在也对原先写死为英文的内容生效：`/ch` 与 `/uchat` 的帮助、从控制台执行 `/ch` 时的「仅限玩家」提示、
  两个命令的描述（由 `/help` 显示），以及关于频道格式缺少发送者或消息占位符、已应用的 `anti-spam.duplicate-window`、
  本版本不再读取的配置键与 `config/autoreply.yml` 保存失败的控制台日志。它们的英文措辞不变。

- The announcement interval settings `announcements.chat.interval`, `announcements.bossbar.interval`
  and `announcements.title.interval` in `config/announcements.yml` now take effect. They were
  documented but never read: the announcements always ran every 300, 60 and 600 seconds
  respectively, whatever the file said. The values are in seconds, with the same defaults (300 / 60
  / 600), so a server that never changed them sees no difference; a server that did now gets the
  interval it configured -- **including a value edited before this upgrade, which had no effect
  until now and starts applying the first time this version loads**. The old accepted range of 10 to
  3600 seconds is gone: the range is now the framework's, 1 second to about 3.4 years, so an
  interval of 1 to 9 seconds (which the old range rejected) and one above an hour are both
  accepted and take effect -- `interval: 1` on the chat announcement sends a line every second.
  A changed value takes effect at `/ul reload` (or `/uchat reload`) without
  a restart, keeping each announcement's place in its cycle -- the next one comes one new interval
  after the last, or on the next tick if that moment has already passed. A value below 1 second (or
  above about 3.4 years) stops the module from loading at startup with a message naming the key;
  on a reload it is ignored with a warning and the running interval is kept. This uses the
  framework's config-bound timers (UltiKits/UltiTools-Reborn#531), so this version needs UltiTools
  6.3.0 or later and declares `api-version: 630` (UltiKits/UltiChat#13).
- `config/announcements.yml` 中的公告间隔设置 `announcements.chat.interval`、
  `announcements.bossbar.interval` 与 `announcements.title.interval` 现在会生效。它们此前有文档说明却从未被读取：
  无论文件中写什么，公告一直分别每 300、60、600 秒播报一次。数值单位为秒，默认值不变（300 / 60 / 600），
  因此从未改过的服务器不会看到任何变化；改过的服务器现在会按其配置的间隔播报——**包括升级前编辑过的值：
  它此前不起作用，本版本首次加载时开始生效**。原先 10 到 3600 秒的允许范围已取消，范围改为框架的 1 秒到约 3.4 年，
  因此 1 到 9 秒（原范围不接受）以及超过一小时的间隔都会被接受并生效——聊天公告设 `interval: 1` 即每秒发送一条。
  修改后的值在执行
  `/ul reload`（或 `/uchat reload`）时生效，无需重启，并保持每条公告在周期中的位置——下一次播报在上一次之后
  一个新间隔，若该时刻已过则在下一个 tick。小于 1 秒（或大于约 3.4 年）的值会在启动时使模块拒绝加载，
  并提示键名；重载时则忽略该值并警告，继续使用正在运行的间隔。此功能使用框架的配置绑定定时器
  （UltiKits/UltiTools-Reborn#531），因此本版本需要 UltiTools 6.3.0 或更高版本，并声明 `api-version: 630`
  （UltiKits/UltiChat#13）。

- A channel's own `format:` in `config/channels.yml` now applies to its members' chat lines, while
  both `channels.enabled` and `chat.format-enabled` (in `config/chat.yml`) are true -- the defaults;
  with either off, no channel format is used, as before. It was
  documented but never used: every channel's line was the global `chat.format` with the channel's
  display name in front, and that is still what a channel without its own format gets. In a
  channel format, `{display}` is replaced by the channel's display name, alongside `{player}`,
  `{displayname}` and `{message}`. The channels shipped with this module no longer set a format.
  **Upgraded servers:** earlier versions wrote three formats into every server's `channels.yml`
  (`{display}&f: {message}` for global, `{display}&7: {message}` for local,
  `&c[Staff] &f{player}&7: {message}` for staff); two of them contain no player name. A format
  exactly equal to one of those three strings is removed from `channels.yml` when the module
  starts, so an upgraded server's chat looks exactly as before and its file says so. The cost: none
  of those three exact strings can be chosen on purpose -- change any single character (for
  example, add a space) and the format counts as your own and takes effect (UltiKits/UltiChat#16,
  #18).
- `config/channels.yml` 中频道自己的 `format:` 现在会作用于该频道成员的聊天行，前提是 `channels.enabled`
  与（`config/chat.yml` 中的）`chat.format-enabled` 均为 true（默认如此）；任一关闭时不使用任何频道格式，与之前相同。
  它此前有文档说明却从未被使用：
  每个频道的聊天行都是全局 `chat.format` 前加频道显示名，未设置自有格式的频道现在仍是如此。
  在频道格式中，`{display}` 会被替换为频道显示名，与 `{player}`、`{displayname}`、`{message}` 并用。
  本模块出厂的频道不再设置格式。**升级的服务器：**早期版本曾把三条格式写入每台服务器的 `channels.yml`
  （global 为 `{display}&f: {message}`，local 为 `{display}&7: {message}`，
  staff 为 `&c[Staff] &f{player}&7: {message}`），其中两条不含玩家名。与这三条字符串之一完全相同的格式
  会在模块启动时从 `channels.yml` 中移除，因此升级后服务器的聊天显示与之前完全相同，文件也如实反映这一点。
  代价是：无法刻意选用这三条字符串本身——只要改动任意一个字符（例如加一个空格），该格式就会被视为您自己的格式并生效
  （UltiKits/UltiChat#16、#18）。
- Auto-reply rules changed with `/uchat autoreply add`, `/uchat autoreply setkeyword` and
  `/uchat autoreply remove` are now written to `config/autoreply.yml` as the command runs, so they
  survive `/uchat reload` and `/ul reload`; previously only a clean server stop saved them, and
  either reload silently discarded every rule change made since that stop. If the file cannot be
  written, the rule set is left exactly as it was and the sender is told the rule could not be
  saved instead of being told it was added, changed or removed. Only the rule the command names is
  written; see UltiKits/UltiChat#50 above for what happens to the rest of the file
  (UltiKits/UltiChat#17).
- 使用 `/uchat autoreply add`、`/uchat autoreply setkeyword` 与 `/uchat autoreply remove`
  更改的自动回复规则，现在会在命令执行时立即写入 `config/autoreply.yml`，
  因此能在 `/uchat reload` 与 `/ul reload` 后保留；此前只有干净地停止服务器才会保存，
  任一 reload 都会静默丢弃自上次停止以来的所有规则更改。若文件无法写入，
  规则集会保持原样，且发送者会收到保存失败的提示，而不是被告知已添加、已更改或已移除。
  只写入命令点名的规则；文件其余部分的处理见上文 UltiKits/UltiChat#50（UltiKits/UltiChat#17）。
- Uninstalling this module (`/upm uninstall UltiTools-Chat`) now really removes its commands
  (`/uchat`, and `/ch`/`/channel` when channels are enabled) and stops its chat, join/quit, channel and
  auto-reply listeners from firing. Previously this
  module's empty unload method replaced the framework's, so after `/upm uninstall` its commands and
  listeners stayed active until the server restarted (UltiKits/UltiChat#23).
- 卸载本模块（`/upm uninstall UltiTools-Chat`）现在会真正移除其命令（`/uchat`，以及启用频道时的 `/ch`/`/channel`），
  并使其聊天、进出服、频道与自动回复监听器停止触发。此前本模块的空卸载方法替换了
  框架的卸载方法，因此执行 `/upm uninstall` 后其命令与监听器会一直生效，直到服务器重启
  （UltiKits/UltiChat#23）。
