# NeroSecurity

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** `0.5.0-beta.1`, the first build with gameplay. Beta: some textures are still generated
placeholders. Player documentation is in the [wiki](wiki/Home.md).

NeroSecurity protects bases. A **Security Hub** controls an **installation**: its owners, its members
and their levels, the devices linked to it, and its lockdown state. Every device asks one server-side
resolver whether a player may act, and any refusal wins. Bases stay raidable: devices can be broken,
force fields overload, and an owner is never locked out.

## What it does

- **Access levels.** PUBLIC < MEMBER < OFFICER < OWNER, plus a server-operator override. Every decision
  goes through one resolver (`access/AccessResolver`): the member list, keycards and optional
  integrations vote, any deny wins, the highest grant applies, and lockdown raises every requirement to
  OWNER. Nothing is cached client-side. See [Access Levels](wiki/Access-Levels.md).
- **Security Hub and Linking Tool.** Placing a hub registers an installation owned by the placer. The hub
  screen manages members, shows linked devices and recent events, and holds the lockdown button and a
  Privacy tab. The Linking Tool links and unlinks devices (same dimension, within `maxDeviceDistance`).
- **Keycards and the Keycard Programmer.** Cards are signed with a per-installation server-side secret
  and checked against a card ledger on every use. Write, copy and wipe at the programmer; encrypted cards
  need an owner and an Encryption Module; cards can be bound to their writer; owners revoke one card or
  all by command. See [Keycards](wiki/Keycards.md).
- **Security Door (1×2) and Blast Door (2×2).** Per-door level, keycard-only mode and redstone mode;
  Hardness and Open-Speed Modules; optional power through Neroland Core energy (`doorsRequirePower` is
  off by default). Always breakable; breaking one you could not open is logged as forced.
- **Secure Chest.** Access-checked storage; hoppers and pipes reach it only under the installation's
  automation policy, never in lockdown or keycard-only mode; no comparator output; contents never logged.
  See [Doors and Chests](wiki/Doors-and-Chests.md).
- **Laser Grid Node and Security Camera.** Powered presence sensors. Paired lasers trip on non-permitted
  entities; cameras watch a panning cone and never record images. Permitted players are not logged.
  Range Modules extend both.
- **Alarm.** Unpowered. Subscribes to breach, denial and power events and responds with a redstone
  pulse, sound, light, an identity-free chat line to online owners (and officers on request), and, as an
  owner-only opt-in, a time-boxed alarm lockdown. Coalesces storms into "+N more".
- **Security Monitor.** Read-only text view of an installation's cameras, lasers and recent events.
- **Audit log and recording notice.** A capped, short-retention log per installation, readable by
  officers (each read is logged); a one-line notice before anyone is logged in a monitored area. See
  [Sensors and Alarms](wiki/Sensors-and-Alarms.md).
- **Lockdown.** Officers start it, owners and operators clear it. Doors seal, chests close to all but
  OWNER-level access, fields run at maximum strength, alarms arm. Announced on Neroland Core's
  `nerosecurity:lockdown` threshold channel with an opaque installation id. See
  [Lockdown](wiki/Lockdown.md).
- **Force Field Emitter.** Projects a wall or shell of field blocks that permitted players walk through
  and everything else cannot. Draws power by size and strength; overloads under sustained attack and
  restarts after a cooldown. A raid-server preset is documented. See [Force Fields](wiki/Force-Fields.md).
- **NeroLink companion app module.** Read-only sections for your own installations (no coordinates, no
  player ids), coalesced phone notifications whose text is a fixed phrase plus the installation name,
  stored alerts, `/nerosecurity alerts` and a counts-only login line. Owners can acknowledge alerts and
  silence alarms from the app; remote lockdown is off by default. See [USING-NEROLINK.md](USING-NEROLINK.md)
  and [NeroLink App and Notifications](wiki/Companion-App.md).
- **Privacy controls.** Players can opt out of being named, export their data, and be erased through
  Neroland Core's shared erasure. Retention is short by default and capped at 90 days. See
  [Privacy](wiki/Privacy.md) and [PRIVACY.md](PRIVACY.md).
- **Quests (NeroQuests, optional).** The Access Granted chapter, completed by three player milestones
  (first keycard, installation online, lockdown drill) recorded as Neroland Core progression gates. The
  gates are never read to decide access. See [NeroQuests integration](wiki/NeroQuests-Integration.md).

## Requirements

- **Minecraft** 26.1.2, 26.2 or 26.3 on **NeoForge**, **MinecraftForge** or **Fabric**.
- **Neroland Core 1.15.0** or newer (below 2.0). Every loader manifest floors it at 1.15.0.
- **Java 25.**

Optional. NeroSecurity is complete without any of them. Each one is detected at start-up, and each
loader manifest floors it at the version NeroSecurity is compiled against:

| Mod | Minimum | What it adds |
| --- | --- | --- |
| NeroEconomy | 0.4.0-beta.1 | A ready seam for paid security tiers. Nothing costs credits yet. |
| NeroFactions | 0.5.0-beta.1 | Bind an installation to a faction; members get access by reputation tier, capped at OFFICER. |
| NeroColonies | 0.5.0-beta.1 | Bind to a colony; members get access by role, capped at OFFICER. |
| Nerospace | 1.5.0 | Bind to a station; its current owner gets OFFICER. |
| Open Parties and Claims | 0.32.7 (0.32.8 on 26.3) | Claims can refuse access on claimed land. They never grant. |

The companion-app features need the NeroLink mod on the server; without it the link module is inert.

## Supported cells

Nine cells, each a Stonecutter node `:<loader>:<mc>`:

| | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- |
| NeoForge | `:neoforge:26.1.2` | `:neoforge:26.2` | `:neoforge:26.3` |
| Forge | `:forge:26.1.2` | `:forge:26.2` | `:forge:26.3` |
| Fabric | `:fabric:26.1.2` | `:fabric:26.2` | `:fabric:26.3` |

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert
- `wiki/` — the player wiki; it is published to the GitHub wiki and also bundled into the jar for the
  companion app

## Building

Build the per-node tasks. A plain `./gradlew build` is not the supported entry point.

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

- Unit tests (JUnit 5, plain JVM) run in the three NeoForge nodes: `./gradlew :neoforge:26.2:test`.
- Static analysis: `./gradlew :fabric:26.2:ecjCheck`.
- Neroland Core resolves from `mavenLocal()` first, then GitHub Packages; see
  [USING-CORE.md](USING-CORE.md).
- Optional sibling integrations compile in by default; `nerofactions_compat`, `nerocolonies_compat`,
  `nerospace_compat` and `opac_compat` in `gradle.properties` turn each one off for a build.
- Dev clients load NeroEconomy only with `-PwithNeroEconomy` (NeoForge and Fabric).

## Developer notes: NeroEconomy seam and rules

`common/src/main/java/za/co/neroland/nerosecurity/economy/SecurityEconomy` is the ready-to-use seam for
paid security tiers. Nothing calls it yet.

- `SecurityEconomy.chargeTier(player, price, key)` charges reason `security_tier` into NeroEconomy's
  Reserve. Upgrade only when `outcome.ok()`. NeroSecurity never pays out for guarding (that would be a
  faucet).
- NeroEconomy is **optional**. It is compiled `compileOnly` (never bundled) and declared optional in
  every loader manifest. One bridge class, `economy/NeroEconomyBridge`, is the only code that imports
  `za.co.neroland.neroeconomy`, and it is loaded only after `isModLoaded("neroeconomy")`. There is no
  runtime version probe and no reflection: every loader manifest floors the optional dependency at the
  compiled NeroEconomy version (`0.4.0-beta.1`), so the loader refuses an older one, and the bridge call
  is wrapped in `catch (RuntimeException | LinkageError)`. The handle is claimed once from common init.
- Every call returns an `EconomyOutcome`: `APPLIED` (grant), `REFUSED` (nothing moved; show
  `outcome.message()`), or `ABSENT` (NeroEconomy is not installed; use the fallback).
- Request keys name the thing being paid for, never a player. A key holding a UUID or 32 hex digits is
  refused before it is sent. Nothing logs who paid.

### Rules for guarding economy blocks (non-negotiable)

Security is the feature most able to lock players out of their own money, so every lock, guard, zone or
tier must keep these rules:

1. **Owners always reach their own economy blocks.** The owner of a Player Shop, Auto-Trader or Market
   Board listing, and any player at a Bank, Trading or Exchange Terminal, can always open it. Credits and
   goods are never held hostage by a lapsed security subscription.
2. **Never break a block to enforce a restriction.** Breaking an Auto-Trader settles its rule and drops its
   slots like a chest; doing that automatically would scatter a player's goods.
3. **Never inspect economy state to decide access.** No balance or holding of any player is read for an
   access decision; NeroEconomy exposes none to other mods anyway.
4. **Failed renewal downgrades only.** Charge when the player chooses or renews a tier, never silently in
   the background. A refused renewal lowers the protection tier; it never removes the owner's access to
   their own blocks.

## Wiki

- [Home](wiki/Home.md)
- [Getting Started](wiki/Getting-Started.md)
- [Access Levels](wiki/Access-Levels.md), [Keycards](wiki/Keycards.md), [Doors and Chests](wiki/Doors-and-Chests.md)
- [Sensors and Alarms](wiki/Sensors-and-Alarms.md), [Lockdown](wiki/Lockdown.md), [Force Fields](wiki/Force-Fields.md)
- [NeroLink App and Notifications](wiki/Companion-App.md), [Blocks and Screens](wiki/Blocks-and-Screens.md)
- [Commands](wiki/Commands.md), [Config](wiki/Config.md), [Privacy](wiki/Privacy.md)

## Privacy & telemetry

> **Telemetry notice:** NeroSecurity sends anonymous error reports (stack trace, mod/game versions and
> the list of loaded mod ids — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/nerosecurity.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerosecurity/blob/main/PRIVACY.md).

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.
