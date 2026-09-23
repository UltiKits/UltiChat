# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- `anti-spam.duplicate-window` in `config/chat.yml` now takes effect. It was documented as the
  duplicate-detection time window but never read: a message counted as a duplicate when the
  player's last `anti-spam.max-duplicate` accepted messages were all the same text, however long
  ago they had been sent. It now means: `0` -- the new declared default -- is no time limit, exactly
  that old rule; a positive value (up to 600) is a window in seconds, and a retained copy older than
  it stops counting. The allowed range is now 0-600 (it was 10-600). **Upgrade consequence:** a
  server that ran an earlier version already has `duplicate-window: 60` in its own file (the
  framework wrote the old default there), and that 60 now applies, so duplicate detection on such a
  server is more permissive than before the upgrade: with the default `max-duplicate: 3`, the same
  message sent three times is refused on the fourth only if the first of those three is at most 60
  seconds old. At module start and on every reload, any window above 0 is announced with one
  warning naming the file, the key and the value; set it to `0` and run `/uchat reload` to restore
  the previous behaviour exactly (UltiKits/UltiChat#14).
- `config/chat.yml` 中的 `anti-spam.duplicate-window` 现在会生效。它此前被文档描述为重复检测的时间窗口，
  但从未被读取：只要玩家最近 `anti-spam.max-duplicate` 条被接受的消息都是同一内容，无论发送于多久以前，
  新消息都会被判为重复。现在它的含义是：`0`（新的声明默认值）表示不限时，与上述旧规则完全相同；
  正值（最大 600）表示以秒为单位的时间窗口，早于该窗口的保留消息不再计数。允许范围改为 0-600（原为 10-600）。
  **升级后果：**运行过早期版本的服务器，其自己的文件中已有 `duplicate-window: 60`（框架曾把旧默认值写入该文件），
  这个 60 现在会生效，因此该服务器上的重复检测会比升级前更宽松：在默认 `max-duplicate: 3` 下，
  同一消息发送三次后，只有当这三次中的第一次发送于 60 秒以内时，第四次才会被拦截。
  模块启动时与每次重载时，只要时间窗口大于 0，就会记录一条指明文件、键名与当前值的警告；
  将其设为 `0` 并执行 `/uchat reload`，即可完全恢复旧行为（UltiKits/UltiChat#14）。

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

- Removed the three announcement interval settings `announcements.chat.interval`,
  `announcements.bossbar.interval` and `announcements.title.interval` from
  `config/announcements.yml`. None of them was ever read: the announcements have always run on
  fixed periods -- chat every 300 seconds, boss bar every 60 seconds, title every 600 seconds --
  whatever the file said, and they still do, so nothing about when announcements appear changes.
  An upgraded server keeps the keys in its own file (the framework never removes a key), so at
  module start and on every reload this module now logs one warning per leftover key, naming the
  file and the key; delete the key to silence it. Removing the settings is not a rejection of
  configurable periods: that capability belongs in the framework and is requested in
  UltiKits/UltiTools-Reborn#531 (UltiKits/UltiChat#13).
- 从 `config/announcements.yml` 中移除了三个公告间隔设置 `announcements.chat.interval`、
  `announcements.bossbar.interval` 与 `announcements.title.interval`。它们从未被读取：
  公告一直按固定周期播报——聊天公告每 300 秒、Boss 栏公告每 60 秒、标题公告每 600 秒——
  与文件中的值无关，现在也仍是如此，因此公告出现的时间没有任何变化。
  升级后的服务器自己的文件中仍保留这些键（框架从不删除键），因此本模块现在会在模块启动与每次重载时
  为每个残留键记录一条警告，指明文件与键名；删除该键即可消除警告。移除这些设置并不是否决「可配置周期」：
  这一能力属于框架，已在 UltiKits/UltiTools-Reborn#531 中提出请求（UltiKits/UltiChat#13）。
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
  exactly equal to one of those three strings is treated as not set, so an upgraded server's chat
  looks exactly as before. The cost: none of those three exact strings can be chosen on purpose --
  change any single character (for example, add a space) and the format counts as your own and
  takes effect (UltiKits/UltiChat#16).
- `config/channels.yml` 中频道自己的 `format:` 现在会作用于该频道成员的聊天行，前提是 `channels.enabled`
  与（`config/chat.yml` 中的）`chat.format-enabled` 均为 true（默认如此）；任一关闭时不使用任何频道格式，与之前相同。
  它此前有文档说明却从未被使用：
  每个频道的聊天行都是全局 `chat.format` 前加频道显示名，未设置自有格式的频道现在仍是如此。
  在频道格式中，`{display}` 会被替换为频道显示名，与 `{player}`、`{displayname}`、`{message}` 并用。
  本模块出厂的频道不再设置格式。**升级的服务器：**早期版本曾把三条格式写入每台服务器的 `channels.yml`
  （global 为 `{display}&f: {message}`，local 为 `{display}&7: {message}`，
  staff 为 `&c[Staff] &f{player}&7: {message}`），其中两条不含玩家名。与这三条字符串之一完全相同的格式
  会被视为未设置，因此升级后服务器的聊天显示与之前完全相同。代价是：无法刻意选用这三条字符串本身——
  只要改动任意一个字符（例如加一个空格），该格式就会被视为您自己的格式并生效（UltiKits/UltiChat#16）。
- A player who leaves the server now has their anti-spam history cleared -- the time of their last
  message and the recent messages kept for duplicate detection. Previously this was never done,
  so the module kept an entry for every player who had chatted since the server started, for as
  long as the server ran. What a player notices: after leaving and rejoining, their message
  history starts empty, so a message refused as a duplicate before they left is accepted again
  after they rejoin (UltiKits/UltiChat#20).
- 玩家离开服务器时，其反刷屏记录（上一条消息的时间，以及为重复检测保留的最近消息）现在会被清除。
  此前从未清除，因此只要服务器在运行，本模块就会为自启动以来每一位发过言的玩家保留一条记录。
  玩家可察觉的变化：离开并重新进入后，其消息记录从空开始，因此离开前被判为重复而拦截的消息，
  重新进入后会被接受（UltiKits/UltiChat#20）。
- Auto-reply rules changed with `/uchat autoreply add`, `/uchat autoreply setkeyword` and
  `/uchat autoreply remove` are now written to `config/autoreply.yml` as the command runs, so they
  survive `/uchat reload` and `/ul reload`; previously only a clean server stop saved them, and
  either reload silently discarded every rule change made since that stop. If the file cannot be
  written, the rule set is left exactly as it was and the sender is told the rule could not be
  saved instead of being told it was added, changed or removed. If the file was changed on disk
  while the server was running, the save still wins -- that is the same contract the shutdown
  save has always had -- but a warning in the server log now names the file whose edits were
  overwritten, instead of the overwrite being silent (UltiKits/UltiChat#17).
- 使用 `/uchat autoreply add`、`/uchat autoreply setkeyword` 与 `/uchat autoreply remove`
  更改的自动回复规则，现在会在命令执行时立即写入 `config/autoreply.yml`，
  因此能在 `/uchat reload` 与 `/ul reload` 后保留；此前只有干净地停止服务器才会保存，
  任一 reload 都会静默丢弃自上次停止以来的所有规则更改。若文件无法写入，
  规则集会保持原样，且发送者会收到保存失败的提示，而不是被告知已添加、已更改或已移除。
  若服务器运行期间文件在磁盘上被修改，保存仍会覆盖它——这与关机保存一直以来的行为相同——
  但现在服务器日志会以警告指明被覆盖编辑的文件，而不再静默覆盖（UltiKits/UltiChat#17）。
- Uninstalling this module (`/upm uninstall UltiTools-Chat`) now really removes its commands
  (`/uchat`, and `/ch`/`/channel` when channels are enabled) and stops its chat, join/quit, channel and
  auto-reply listeners from firing; this module has no unload work of its own. Previously this
  module's empty unload method replaced the framework's, so after `/upm uninstall` its commands and
  listeners stayed active until the server restarted (UltiKits/UltiChat#23).
- 卸载本模块（`/upm uninstall UltiTools-Chat`）现在会真正移除其命令（`/uchat`，以及启用频道时的 `/ch`/`/channel`），
  并使其聊天、进出服、频道与自动回复监听器停止触发；本模块自身没有卸载工作。此前本模块的空卸载方法替换了
  框架的卸载方法，因此执行 `/upm uninstall` 后其命令与监听器会一直生效，直到服务器重启
  （UltiKits/UltiChat#23）。
