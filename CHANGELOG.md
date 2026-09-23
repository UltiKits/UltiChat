# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

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

### Fixed

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
