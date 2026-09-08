# UltiChat — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultichat` here, `ultitools`, `ultiessentials`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `gui` rows (no GUI page class) and no `placeholder` rows (it consumes
  PlaceholderAPI variables via `PlaceholderAPI#setPlaceholders`, it does not register its own
  expansion) — both Kinds stay in the vocabulary for cross-repository consistency even though
  neither appears below.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for,
  not from whether it carries a permission string — most command executors in this repository
  carry one, so judging by the string alone would make nearly everything `admin`.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind (`config`, `event`, `gate`, `gui`, `persistence`, `scheduled`, `placeholder`) — the concept
  of "who this targets" does not apply to a config key or a background task the way it applies to
  a command.
- **Permission:** the literal node string, `none`, or `n/a`, each optionally suffixed with the
  literal text `(requireOp=true)` (preceded by one space) when the row's class-level
  `@CmdExecutor` carries that flag — the suffix augments whichever of the three base values
  applies; it is not a fourth value, and a row without it means its class's `requireOp` is
  `false` (or the row's Kind has no such class at all). `none` alone means no permission-node
  restriction at all, for anyone — `PermissionValidator` treats an empty `permission()` as no
  check to run, not as an implicit OP requirement. OP-only access, where it exists, comes from
  the separate `@CmdExecutor` class-level `requireOp` flag, a different mechanism entirely — the
  suffix exists so a reader does not have to cross-reference the class declaration to learn it.
  Neither of this module's two `@CmdExecutor` classes (`ChatAdminCommands`, `ChannelCommands`)
  sets `requireOp = true`, so no `command` row below carries the suffix; both rely on their
  literal permission node alone (`ultichat.admin`, `ultichat.channel`). `n/a` is for every Kind
  that is not `command` — a config key or a scheduled task has no permission node to declare in
  the first place, which is a different fact from a command that declares `none` deliberately.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 45 `config` rows below cite the reading
  member. Unlike the framework's own `config.yml` (read directly via Bukkit's
  `FileConfiguration`, with no bound entity at all), every one of this module's five
  configuration files is a real `@ConfigEntity`/`@ConfigEntry`-bound class, so a config row's
  Source cites whichever class and method actually calls the generated getter — not the config
  class's own field declaration, which merely binds the key, and does not by itself say what the
  running plugin does with the value.
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here. Where a
  feature's actual runtime behaviour genuinely diverges from what the config key or the public
  doc page describes it as doing (a dead key, an unreachable code path), that fact is itself part
  of "what the feature does" and is stated here as a plain, sourced observation — the same
  standard the framework's own `FEATURES.md` already applies to its documented `#432` defect —
  with the filed issue number, never as advice on how to fix it.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error:

1. **Multi-root repositories** — UltiBot's sources live under `ultibot-api/`, `ultibot-core/`
   and `ultibot-v1_21_R1/`, so a naive `<repo>/src/main/java` glob returns 0 for it, silently.
   This module is a single-root Maven project (`src/main/java` only), so this trap does not apply
   to it, but the robust `find` form is used regardless — the same command must work unmodified
   across all 18 repositories.
2. **Git worktrees and build output** — UltiEconomy carries
   `.worktrees/economy-v2/src/main/java`, so a `find` without the `-not -path` exclusions above
   reports 48 `@CmdMapping` sites where the real number is 24. This module carries no worktree
   directory.
3. **Javadoc and string literals** — requiring the annotation to start its own line (the
   `^[[:space:]]*@` anchor) is what defeats a javadoc mention or a warning-message string literal
   that merely contains the annotation's name as text; the framework's own `@CmdMapping` count
   drops from 46 to 15 under this anchor. This module's naive (unanchored) and line-start counts
   are identical for every annotation kind measured below — no javadoc or string-literal false
   positive exists in this module's source — but the anchored form is still the one used, so the
   same command is trustworthy unmodified against every repository in the fan-out.

**Positive control:** the line-start form returns `@CmdExecutor` = 2, `@CmdMapping` = 7,
`@EventListener` = 4 (classes), `@EventHandler` = 6 (handler methods), `@Scheduled` = 3,
`@ConditionalOnConfig` = 1, `@ConfigEntity` = 5 (classes), `@ConfigEntry` = 45 — confirmed by
reading `ChatAdminCommands.java` (5 `@CmdMapping` sites: `reload`, `autoreply list`,
`autoreply add <name> <response>`, `autoreply setkeyword <name> <keyword>` at line 110, and
`autoreply remove <name>`) and `ChannelCommands.java` (2 sites: `list`, `<name>`) directly, not by
trusting the count alone. `autoreply setkeyword <name> <keyword>`
(`ChatAdminCommands#onAutoReplySetKeyword`) is this module's standing positive control — the row a
prior mechanical approach in this exact codebase silently dropped; its presence below is checked
by name, not merely by count. This document's command-row count matches the `@CmdMapping`
annotation-site count exactly (7 against 7) — unlike the framework's own `/upm`/`/ulticloud`
sections, this module has no bare-`help` `@CmdMapping`-free dispatch path documented as an extra
row (see the `## Administration` section's own note on why `/uchat help` and `/ch help` are
deliberately not separate rows here).

## Administration

`ChatAdminCommands` — class-level `@CmdExecutor(permission = "ultichat.admin", description =
"UltiChat admin commands", alias = {"uchat"})`, `@CmdTarget(BOTH)`. This section covers the one
`@CmdMapping` site (`reload`) that is not part of the Auto-Reply sub-namespace; the other four
sites on this same class are catalogued under `## Auto-Reply` below, all sourced to this same
class.

**Note on `/uchat help` and bare `/uchat`:** both are reachable (`BaseCommandExecutor#onCommand`
short-circuits a literal `help` argument, and a zero-argument invocation, to
`ChatAdminCommands#handleHelp` before format-matching runs — the same mechanism the framework's
own `FEATURES.md` documents for `/upm help`/`/ulticloud help`), but this document's command-row
count is fixed at exactly 7 (matching the 7 real `@CmdMapping` sites 1:1, with no added
short-circuit-only row) — a deliberate scope decision for this module's first pass, not an
oversight. `/ch help` and bare `/ch` reach `ChannelCommands#handleHelp` the identical way.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.uchat.reload | Reload every UltiChat configuration file from disk | command | `/uchat reload` | ultichat.admin | both | admin | brief | ChatAdminCommands#onReload |

## Auto-Reply

`ChatAdminCommands` (see `## Administration` above for the class-level annotation) and
`AutoReplyListener`/`AutoReplyService`. Keyword-triggered automatic chat replies with three match
modes (contains/exact/regex), per-rule case sensitivity and permission gating, a global per-player
cooldown, multi-line responses, and console command execution on trigger.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.autoreply.add | Add a new contains-mode auto-reply rule; refuses rather than overwrites if the name already exists | command | `/uchat autoreply add <name> <response>` | ultichat.admin | both | admin | brief | ChatAdminCommands#onAutoReplyAdd |
| ultichat.autoreply.list | List every configured auto-reply rule with its keyword, match mode, and response | command | `/uchat autoreply list` | ultichat.admin | both | admin | brief | ChatAdminCommands#onAutoReplyList |
| ultichat.autoreply.remove | Remove an existing auto-reply rule by name | command | `/uchat autoreply remove <name>` | ultichat.admin | both | admin | brief | ChatAdminCommands#onAutoReplyRemove |
| ultichat.autoreply.setkeyword | Change an existing rule's keyword, leaving its response, mode, and case-sensitivity untouched | command | `/uchat autoreply setkeyword <name> <keyword>` | ultichat.admin | both | admin | brief | ChatAdminCommands#onAutoReplySetKeyword |
| ultichat.autoreply.trigger | Match an incoming chat message against every configured rule (first match wins); on match, send the rule's response (string or multi-line list) and dispatch any configured console commands, subject to a per-rule permission check, a per-player global cooldown, and a bypass permission | event | send a chat message containing a configured keyword | n/a | n/a | player | none | AutoReplyListener#onPlayerChat |
| ultichat.autoreply.persistence | A rule added, keyword-changed, or removed via the `/uchat autoreply` commands is never explicitly saved to disk (`AbstractConfigEntity#save()` is never called anywhere in this module's source); it survives only across a *clean* server stop, because `UltiTools#onDisable` calls `ConfigManager#saveAll()` unconditionally for every loaded module's config state. Both `/uchat reload` and `/ul reload` re-`init()` `AutoReplyConfig` straight from disk with no save first, silently reverting any command-made rule change made since the last clean stop. A known product defect (UltiKits/UltiChat#17), not fixed here per this plan's zero-new-code rule | persistence | add/change/remove a rule via the `/uchat autoreply` commands, then either stop the server cleanly or run `/uchat reload` | n/a | n/a | admin | detailed | AutoReplyService#addRule, UltiTools#onDisable, ConfigManager#reloadConfigs |

## Channels

`ChannelCommands` — class-level `@CmdExecutor(permission = "ultichat.channel", description =
"Channel commands", alias = {"ch", "channel"})`, `@CmdTarget(BOTH)`, gated by
`@ConditionalOnConfig(value = "config/channels.yml", path = "channels.enabled")`. Both mapped
methods carry `@CmdTarget(BOTH)` at the class level but each manually refuses a non-player sender
with a red "players only" chat message before doing any work — the class's own code comment
states this is deliberate: a `PLAYER`-only `@CmdTarget` on the wildcard `<name>` mapping would
intercept the literal `list` sub-command before an exact-match check could run, under this
framework's scored format matching (`BaseCommandExecutor.calculateMatchScore`), so both mappings
accept `BOTH` and check `instanceof Player` by hand instead. Named channel-membership assignment
(join/quit bookkeeping) is catalogued here too, sourced to a different class
(`PlayerChannelListener`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.channel.assign-on-join | Assign a newly-joined player to the configured default channel | event | join the server as any player | n/a | n/a | internal | none | PlayerChannelListener#onPlayerJoin |
| ultichat.channel.cleanup-on-quit | Remove a quitting player's tracked channel assignment from the in-memory map (the assignment is never persisted to disk in the first place — see `## Data persistence`) | event | quit the server as any player | n/a | n/a | internal | none | PlayerChannelListener#onPlayerQuit |
| ultichat.channel.gate | Register the `ChannelCommands` bean (and therefore the entire `/ch` command and both rows above/below that depend on it) only if `channels.enabled` is `true` at component-scan time; a change to this key takes effect only on a full server restart, not on `/ul reload`/`/uchat reload` — `@ConditionalOnConfig` is evaluated once, at boot | gate | `channels.enabled` in `plugins/UltiTools/UltiChat/config/channels.yml`, applied only on a full server restart | n/a | n/a | admin | brief | ChannelCommands#ChannelCommands |
| ultichat.channel.list | List every channel the sender has permission to join, with the sender's current channel highlighted | command | `/ch list` | ultichat.channel | both | player | brief | ChannelCommands#onList |
| ultichat.channel.switch | Switch the sender's active channel, subject to the channel existing and the sender holding that channel's own `permission` key (if any) | command | `/ch <name>` | ultichat.channel | both | player | brief | ChannelCommands#onSwitch |

## Chat message pipeline

`ChatListener#onChat` (`@EventHandler(priority = LOWEST)` on `AsyncPlayerChatEvent`) is the single
handler backing chat format, `@mention` highlighting, channel-scoped message delivery, anti-spam
enforcement, and emoji-shortcode substitution — five independently config-gated behaviours dispatched
from one method, in this fixed order: anti-spam check (may cancel the event outright) → emoji
substitution (permission-gated) → channel-scoped recipient filtering (if channels are enabled) →
chat format (if enabled) → color-code translation (permission-gated) → `@mention` highlighting and
sound (if enabled). Each sub-behaviour's own config toggle is catalogued at key granularity under
`## Configuration`; this single event row is the "one row per handler" unit per this phase's own
convention, and its own config-per-file checklist row (`ultichat.config.chat-yml`) is what actually
exercises each toggle independently.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.chat.pipeline | Process an outgoing chat message through anti-spam, emoji substitution, channel-scoped recipient filtering, chat formatting, and `@mention` highlighting, in that fixed order (see section note above). A sender holding `ultichat.spam.bypass` skips the anti-spam slice entirely — `ChatListener#handleAntiSpam` returns before `AntiSpamService#checkSpam` or `#recordMessage` run at all, so cooldown, duplicate-detection, and caps-limit enforcement are all bypassed together, not individually | event | send any chat message as a player | n/a | n/a | player | brief | ChatListener#onChat |

## Join/Quit Messages

`JoinQuitListener` — custom join/quit chat lines, a multi-line welcome message and title shown
only to the joining player, and a server-wide first-join broadcast. All four sub-behaviours inside
`onPlayerJoin` are independently config-gated (`join-quit.join-message-enabled`,
`join-quit.welcome-enabled`, `join-quit.title.enabled`, and the always-on
`hasPlayedBefore()`-gated first-join broadcast) and are catalogued at key granularity under
`## Configuration`; this event row is the single "one row per handler" unit for `onPlayerJoin`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.joinquit.on-join | Override the join broadcast line, send a multi-line welcome message and a welcome title to the joining player only, and broadcast a server-wide first-join message the first time a never-before-seen player joins (`Player#hasPlayedBefore()`) | event | join the server as any player | n/a | n/a | player | brief | JoinQuitListener#onPlayerJoin |
| ultichat.joinquit.on-quit | Override the quit broadcast line | event | quit the server as any player | n/a | n/a | player | none | JoinQuitListener#onPlayerQuit |

## Scheduled Broadcasts

`AnnouncementService` — three independently-toggled, message-rotating broadcasts. Each
`@Scheduled` method's `period` argument is a hardcoded tick count that happens to numerically
match its corresponding `announcements.*.interval` config key's shipped default (300s/60s/600s
respectively), but none of the three methods actually reads that key — see the row notes below
and `UltiKits/UltiChat#13` (a known product defect, not fixed here per this plan's zero-new-code
rule).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.announce.bossbar-broadcast | Show a rotating boss-bar message to every online player, in the configured color, removed automatically after the configured duration; `announcements.bossbar.interval` is declared but never read — the real cadence is a hardcoded 1200-tick (60s) period regardless of this key's value (UltiKits/UltiChat#13) | scheduled | runs automatically every 1200 ticks (60s, hardcoded) while `announcements.bossbar.enabled` is true and at least one player is online | n/a | n/a | player | brief | AnnouncementService#broadcastBossBar |
| ultichat.announce.chat-broadcast | Broadcast a rotating chat message (with configured prefix) to every online player; `announcements.chat.interval` is declared but never read — the real cadence is a hardcoded 6000-tick (300s) period regardless of this key's value (UltiKits/UltiChat#13) | scheduled | runs automatically every 6000 ticks (300s, hardcoded) while `announcements.chat.enabled` is true and at least one player is online | n/a | n/a | player | brief | AnnouncementService#broadcastChat |
| ultichat.announce.title-broadcast | Show a rotating title/subtitle to every online player, splitting each configured message on its first double-vertical-bar occurrence into title and subtitle; `announcements.title.interval` is declared but never read — the real cadence is a hardcoded 12000-tick (600s) period regardless of this key's value (UltiKits/UltiChat#13) | scheduled | runs automatically every 12000 ticks (600s, hardcoded) while `announcements.title.enabled` is true and at least one player is online | n/a | n/a | player | brief | AnnouncementService#broadcastTitle |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.channel.membership-not-persisted | A player's active channel assignment lives only in an in-memory map (`ChannelService#playerChannels`) — it is never written to disk anywhere in this module (no `@Table`/`DataOperator` usage exists in this module's source at all). Every player's channel reverts to `channels.default-channel` on their next join after any server restart, regardless of what they had switched to before | persistence | switch channel with `/ch <name>`, then restart the server and rejoin | n/a | n/a | internal | brief | ChannelService#playerChannels, PlayerChannelListener#onPlayerJoin |

(See `## Auto-Reply`'s `ultichat.autoreply.persistence` row for the auto-reply rule set's own,
partial-persistence behaviour — it is catalogued in that section, not here, because it is a
consequence of the `/uchat autoreply` commands specifically, not a module-wide storage guarantee.)

## Configuration

Every `@ConfigEntry`-annotated field across this module's five `@ConfigEntity` classes (45 keys
total: `AnnouncementConfig` 15, `AutoReplyConfig` 3, `ChannelConfig` 3, `ChatConfig` 22,
`EmojiConfig` 2 — matching the reconciliation table's own `@ConfigEntry` count of 45 exactly).
Several of these keys already have a behavioural row above (auto-reply rules, the channel gate,
join/quit messages, the chat pipeline, scheduled broadcasts) — that row documents the *feature*
the key drives, this row documents the *key* itself, at file-and-key granularity, so the
reconciliation table can prove every key is accounted for without also making every behavioural
row carry a `config` Kind.

**Five keys are declared and validated but never read by any production code, or are read only
inside a method with no caller — both count as "no observable effect" from an operator's
perspective.** Each is called out in its own row below with the filed issue number
(`UltiKits/UltiChat#13`, `#14`, `#15`) rather than a claim that flipping it changes anything.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultichat.config.announcements.announcements.bossbar.color | Boss-bar announcement color; invalid `BarColor` enum values silently fall back to `BLUE` | config | `config/announcements.yml: announcements.bossbar.color (default: BLUE)` | n/a | n/a | admin | brief | AnnouncementService#broadcastBossBar |
| ultichat.config.announcements.announcements.bossbar.duration | Boss-bar display duration before automatic removal | config | `config/announcements.yml: announcements.bossbar.duration (default: 10)` | n/a | n/a | admin | brief | AnnouncementService#broadcastBossBar |
| ultichat.config.announcements.announcements.bossbar.enabled | Enable the rotating boss-bar announcement | config | `config/announcements.yml: announcements.bossbar.enabled (default: false)` | n/a | n/a | admin | brief | AnnouncementService#broadcastBossBar |
| ultichat.config.announcements.announcements.bossbar.interval | Declared as the boss-bar broadcast interval; never read — the real cadence is a hardcoded 1200-tick (60s) period regardless of this key's value. Known product defect, UltiKits/UltiChat#13 | config | `config/announcements.yml: announcements.bossbar.interval (default: 60, has no effect, see UltiKits/UltiChat#13)` | n/a | n/a | admin | brief | AnnouncementConfig#bossBarInterval (declared, never read outside this class) |
| ultichat.config.announcements.announcements.bossbar.messages | Boss-bar message pool, rotated in order on each firing | config | `config/announcements.yml: announcements.bossbar.messages (default: 1 entry)` | n/a | n/a | admin | none | AnnouncementService#broadcastBossBar |
| ultichat.config.announcements.announcements.chat.enabled | Enable the rotating chat-line announcement | config | `config/announcements.yml: announcements.chat.enabled (default: true)` | n/a | n/a | admin | brief | AnnouncementService#broadcastChat |
| ultichat.config.announcements.announcements.chat.interval | Declared as the chat announcement interval; never read — the real cadence is a hardcoded 6000-tick (300s) period regardless of this key's value. Known product defect, UltiKits/UltiChat#13 | config | `config/announcements.yml: announcements.chat.interval (default: 300, has no effect, see UltiKits/UltiChat#13)` | n/a | n/a | admin | brief | AnnouncementConfig#chatInterval (declared, never read outside this class) |
| ultichat.config.announcements.announcements.chat.messages | Chat announcement message pool, rotated in order on each firing | config | `config/announcements.yml: announcements.chat.messages (default: 2 entries)` | n/a | n/a | admin | none | AnnouncementService#broadcastChat |
| ultichat.config.announcements.announcements.chat.prefix | Prefix prepended to every chat announcement line | config | `config/announcements.yml: announcements.chat.prefix (default: "&6[Announcement] &f")` | n/a | n/a | admin | none | AnnouncementService#broadcastChat |
| ultichat.config.announcements.announcements.title.enabled | Enable the rotating title/subtitle announcement | config | `config/announcements.yml: announcements.title.enabled (default: false)` | n/a | n/a | admin | brief | AnnouncementService#broadcastTitle |
| ultichat.config.announcements.announcements.title.fade-in | Title fade-in duration, in ticks | config | `config/announcements.yml: announcements.title.fade-in (default: 10)` | n/a | n/a | admin | none | AnnouncementService#broadcastTitle |
| ultichat.config.announcements.announcements.title.fade-out | Title fade-out duration, in ticks | config | `config/announcements.yml: announcements.title.fade-out (default: 20)` | n/a | n/a | admin | none | AnnouncementService#broadcastTitle |
| ultichat.config.announcements.announcements.title.interval | Declared as the title announcement interval; never read — the real cadence is a hardcoded 12000-tick (600s) period regardless of this key's value. Known product defect, UltiKits/UltiChat#13 | config | `config/announcements.yml: announcements.title.interval (default: 600, has no effect, see UltiKits/UltiChat#13)` | n/a | n/a | admin | brief | AnnouncementConfig#titleInterval (declared, never read outside this class) |
| ultichat.config.announcements.announcements.title.messages | Title/subtitle message pool, double-vertical-bar-separated, rotated in order on each firing | config | `config/announcements.yml: announcements.title.messages (default: 1 entry)` | n/a | n/a | admin | none | AnnouncementService#broadcastTitle |
| ultichat.config.announcements.announcements.title.stay | Title stay duration, in ticks | config | `config/announcements.yml: announcements.title.stay (default: 70)` | n/a | n/a | admin | none | AnnouncementService#broadcastTitle |
| ultichat.config.autoreply.autoreply.cooldown | Global per-player cooldown between auto-reply triggers | config | `config/autoreply.yml: autoreply.cooldown (default: 10)` | n/a | n/a | admin | brief | AutoReplyListener#isOnCooldown |
| ultichat.config.autoreply.autoreply.enabled | Enable the auto-reply system entirely | config | `config/autoreply.yml: autoreply.enabled (default: true)` | n/a | n/a | admin | brief | AutoReplyListener#onPlayerChat |
| ultichat.config.autoreply.autoreply.rules | The rule set itself — a map of rule name to `{keyword, response, mode, case-sensitive, permission, commands}`; also mutated in-memory by the `/uchat autoreply` commands (see `ultichat.autoreply.persistence`) | config | `config/autoreply.yml: autoreply.rules (default: 2 entries)` | n/a | n/a | admin | detailed | AutoReplyService#getRules |
| ultichat.config.channels.channels.channels | The channel definitions map — one entry per channel, each `{display-name, format, permission, range, cross-world}`. The per-channel `format` sub-field is declared and documented but never applied — `ChannelService#getChannelFormat` exists and reads it correctly but has no caller anywhere; `ChatListener#applyChatFormat` always uses the single global `chat.yml` `chat.format` string instead, only prepending the channel's `display-name`. Known product defect, UltiKits/UltiChat#16 | config | `config/channels.yml: channels.channels (default: 3 entries — global, local, staff)` | n/a | n/a | admin | detailed | ChannelService#getChannelDef |
| ultichat.config.channels.channels.default-channel | The channel a player is assigned on join, and the fallback returned for a player with no tracked assignment | config | `config/channels.yml: channels.default-channel (default: global)` | n/a | n/a | admin | brief | ChannelService#getPlayerChannel |
| ultichat.config.channels.channels.enabled | Two distinct effects: gates whether `ChannelCommands` (`/ch`) is registered at boot (see `ultichat.channel.gate`, restart required either way), AND is read live on every chat message by `ChatListener#onChat` to decide whether channel-scoped recipient filtering and the channel-display-name format prefix apply — the second effect DOES take immediate effect on `/uchat reload`/`/ul reload`, unlike the first | config | `config/channels.yml: channels.enabled (default: true)` | n/a | n/a | admin | detailed | ChatListener#onChat, ChannelCommands#ChannelCommands |
| ultichat.config.chat.anti-spam.caps-limit | Maximum uppercase-letter percentage before a message is refused as excessive caps; a value of `0` or `100`+ disables this specific check | config | `config/chat.yml: anti-spam.caps-limit (default: 70)` | n/a | n/a | admin | brief | AntiSpamService#isExcessiveCaps |
| ultichat.config.chat.anti-spam.cooldown | Minimum seconds between a player's messages before the next one is refused | config | `config/chat.yml: anti-spam.cooldown (default: 2)` | n/a | n/a | admin | brief | AntiSpamService#checkCooldown |
| ultichat.config.chat.anti-spam.duplicate-window | Declared and documented as a duplicate-detection time window (seconds); never read anywhere — `AntiSpamService#isDuplicate` compares only the last `anti-spam.max-duplicate` retained messages by count, with no time-based expiry at all. Known product defect, UltiKits/UltiChat#14 | config | `config/chat.yml: anti-spam.duplicate-window (default: 60, has no effect, see UltiKits/UltiChat#14)` | n/a | n/a | admin | brief | ChatConfig#antiSpamDuplicateWindow (declared, never read outside this class) |
| ultichat.config.chat.anti-spam.enabled | Enable the anti-spam system entirely (cooldown, duplicate detection, caps limiting) | config | `config/chat.yml: anti-spam.enabled (default: true)` | n/a | n/a | admin | brief | AntiSpamService#checkSpam |
| ultichat.config.chat.anti-spam.max-duplicate | Number of consecutive identical messages that trigger a duplicate refusal | config | `config/chat.yml: anti-spam.max-duplicate (default: 3)` | n/a | n/a | admin | brief | AntiSpamService#isDuplicate |
| ultichat.config.chat.anti-spam.mute-duration | Read by `AntiSpamService#mutePlayer`, which itself is never called from anywhere in this module — the automatic-mute mechanism this key is meant to drive is unreachable; no player is ever actually muted regardless of this key's value. Known product defect, UltiKits/UltiChat#15 | config | `config/chat.yml: anti-spam.mute-duration (default: 30, has no effect, see UltiKits/UltiChat#15)` | n/a | n/a | admin | detailed | AntiSpamService#mutePlayer (read here, but this method has no caller) |
| ultichat.config.chat.chat.format | The chat message format string, `{player}`/`{message}`/`{displayname}` placeholders plus PlaceholderAPI variables, applied when `chat.format-enabled` is true | config | `config/chat.yml: chat.format (default: "&7[&f%player_world%&7] &f{player}&7: &f{message}")` | n/a | n/a | admin | brief | ChatListener#applyChatFormat |
| ultichat.config.chat.chat.format-enabled | Enable custom chat formatting via `chat.format` | config | `config/chat.yml: chat.format-enabled (default: true)` | n/a | n/a | admin | brief | ChatListener#onChat |
| ultichat.config.chat.join-quit.first-join-message | Server-wide broadcast the first time a never-before-seen player joins; empty string disables it (checked, not merely a falsy default) | config | `config/chat.yml: join-quit.first-join-message (default: "&6Welcome new player &e%player_name%&6!")` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerJoin |
| ultichat.config.chat.join-quit.join-message-enabled | Enable overriding the join broadcast line | config | `config/chat.yml: join-quit.join-message-enabled (default: true)` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerJoin |
| ultichat.config.chat.join-quit.join-message-format | The join broadcast line's format string | config | `config/chat.yml: join-quit.join-message-format (default: "&a[+] &e%player_name% &7joined the server")` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerJoin |
| ultichat.config.chat.join-quit.quit-message-enabled | Enable overriding the quit broadcast line | config | `config/chat.yml: join-quit.quit-message-enabled (default: true)` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerQuit |
| ultichat.config.chat.join-quit.quit-message-format | The quit broadcast line's format string | config | `config/chat.yml: join-quit.quit-message-format (default: "&c[-] &e%player_name% &7left the server")` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerQuit |
| ultichat.config.chat.join-quit.title.enabled | Enable a welcome title/subtitle shown only to the joining player. Distinct field from `AnnouncementConfig#titleEnabled` — same generated `isTitleEnabled()` getter name, different class, different config file | config | `config/chat.yml: join-quit.title.enabled (default: true)` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerJoin |
| ultichat.config.chat.join-quit.title.main | Welcome title's main line | config | `config/chat.yml: join-quit.title.main (default: "&6Welcome Back")` | n/a | n/a | admin | none | JoinQuitListener#sendWelcomeTitle |
| ultichat.config.chat.join-quit.title.sub | Welcome title's subtitle line | config | `config/chat.yml: join-quit.title.sub (default: "&7%player_name%")` | n/a | n/a | admin | none | JoinQuitListener#sendWelcomeTitle |
| ultichat.config.chat.join-quit.welcome-enabled | Enable a multi-line welcome message sent only to the joining player | config | `config/chat.yml: join-quit.welcome-enabled (default: true)` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerJoin |
| ultichat.config.chat.join-quit.welcome-lines | The welcome message's lines, sent in order | config | `config/chat.yml: join-quit.welcome-lines (default: 4 lines)` | n/a | n/a | admin | none | JoinQuitListener#sendWelcomeMessage |
| ultichat.config.chat.mentions.enabled | Enable `@player`-mention highlighting and sound | config | `config/chat.yml: mentions.enabled (default: true)` | n/a | n/a | admin | brief | ChatListener#onChat |
| ultichat.config.chat.mentions.format | The highlight format applied to a matched `@player` mention | config | `config/chat.yml: mentions.format (default: "&e@{player}&r")` | n/a | n/a | admin | none | ChatListener#processMentions |
| ultichat.config.chat.mentions.self-mention | Whether a player mentioning their own name counts as a mention (highlight + sound) | config | `config/chat.yml: mentions.self-mention (default: false)` | n/a | n/a | admin | none | ChatListener#processMentions |
| ultichat.config.chat.mentions.sound | The sound played to a mentioned player; an unrecognized `XSound` name is silently ignored (no sound plays, no error) | config | `config/chat.yml: mentions.sound (default: ENTITY_EXPERIENCE_ORB_PICKUP)` | n/a | n/a | admin | brief | ChatListener#playMentionSound |
| ultichat.config.emojis.emojis.enabled | Enable emoji-shortcode substitution in chat messages (permission-gated by `ultichat.emoji`) | config | `config/emojis.yml: emojis.enabled (default: true)` | n/a | n/a | admin | brief | EmojiService#replaceEmojis |
| ultichat.config.emojis.emojis.mappings | Shortcode-to-Unicode mapping table (default 4 entries: `:heart:`, `:star:`, `:smile:`, `:sword:`); additional mappings may be added by hand | config | `config/emojis.yml: emojis.mappings (default: 4 entries)` | n/a | n/a | admin | none | EmojiService#replaceEmojis |
