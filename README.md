# NeroSecurity

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** `0.5.0-beta.1` in progress. No gameplay content yet; the foundation is in place (own network channel, data-erasure hook, menu safety wrapper, unit tests, anonymous crash reporting), along with the optional NeroEconomy seam and the NeroQuests quest pack.

## Build targets

- **Minecraft:** 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "9 cells")
- **Java:** 25
- Mod id: `nerosecurity` · package `za.co.neroland.nerosecurity`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

## Developer notes: NeroEconomy seam and rules

NeroSecurity has no content yet. `common/src/main/java/za/co/neroland/nerosecurity/economy/SecurityEconomy`
is the ready-to-use seam for paid security tiers:

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
- Dev clients load NeroEconomy only with `-PwithNeroEconomy` (NeoForge and Fabric), so the default run
  proves the mod works without it.

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

With NeroEconomy absent, tiers are free or configured another way.

## Quests (NeroQuests, optional)

NeroSecurity ships a NeroQuests quest pack: chapter `nerosecurity:access_granted` "Access Granted", five
player quests that teach a player to secure their own base (craft a keycard, encode it, bring a hub online,
run a lockdown drill, then the capstone). It is plain data in the jar and appears in the quest book only
when NeroQuests is installed; there is no dependency on NeroQuests in any form. The quests become
completable once the security features exist: future code calls
`progression/SecurityMilestones.record(player, milestone)` when a player encodes their first keycard,
brings an installation online or clears a lockdown drill, which opens the matching player-scope Neroland
Core gate (`nerosecurity:first_keycard`, `installation_online`, `lockdown_drill`). Those gates are
records for quests only and are never read to grant access. Once lockdowns exist, entering and leaving one fires the Neroland Core
threshold channel `nerosecurity:lockdown` (scope is an opaque installation id, never a player, name or
position); no other security event is broadcast, and no quest reward ever changes access state. See the wiki page
[NeroQuests integration](wiki/NeroQuests-Integration.md).

## Privacy & telemetry

> **Telemetry notice:** NeroSecurity sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/nerosecurity.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerosecurity/blob/main/PRIVACY.md).

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.
