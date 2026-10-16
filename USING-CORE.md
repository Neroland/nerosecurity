# Neroland Core APIs used by NeroSecurity

Every Neroland Core surface this mod actually consumes today, checked against the
`za.co.neroland.nerolandcore` imports in `common/src`. NeroSecurity compiles against
**Neroland Core 1.15.0** and floors every loader manifest at that compiled version
(`[1.15.0,2.0)` on NeoForge/Forge, `>=1.15.0 <2.0.0` on Fabric), so an older Core is refused by
the loader with a clear message instead of failing later with a missing-method error.

## Getting Core at build time

Core is a published Maven artifact, never bundled into the NeroSecurity jar:
`za.co.neroland.nerolandcore:nerolandcore-<loader>-<mc>:<nerolandcore_version>`, with the version set
once in `gradle.properties` (`nerolandcore_version`). `stonecutter.gradle` resolves it from:

1. `mavenLocal()` first — run Core's own `publishToMavenLocal` to build against a local Core.
2. GitHub Packages (`https://maven.pkg.github.com/Neroland/neroland-core`), for CI and fresh clones.
   Reading a package needs credentials even when it is public: set the Gradle properties
   `gpr.user` / `gpr.key` (a token with `read:packages`), or the environment variables
   `GITHUB_ACTOR` / `GITHUB_TOKEN`. GitHub Actions provides the latter automatically; the
   package must be readable by this repository (or use an organisation token with
   `read:packages` in place of `GITHUB_TOKEN`).

## Config — `za.co.neroland.nerolandcore.config`

**Used:** `ConfigSchema`, `ConfigManager`, `ConfigValue` (available at the 1.15.0 pin).

`config/NeroSecurityConfig` declares one schema (file `config/nerosecurity.properties`) and
registers it with `ConfigManager` as step 1 of common init, because everything after it reads
config — including telemetry's opt-out. Core's schema has no sections, so keys are flat. The only
key today is `telemetryEnabled`, deliberately **not** server-authoritative: anonymous crash
reporting is a per-client choice a server must never force on or off. It is read once at startup.

## Progression — `za.co.neroland.nerolandcore.progression`

**Used:** `ProgressionGates`; in tests additionally `Gate` and `GateScope`.

- `progression/SecurityMilestones` records three player-scope milestone gates
  (`nerosecurity:first_keycard`, `installation_online`, `lockdown_drill`) with
  `ProgressionGates.open(ServerPlayer, Identifier)` and, for a player who may be offline,
  `ProgressionGates.open(MinecraftServer, UUID, Identifier)` (offline-capable since **1.15.0**).
  The gates are datapack files under `data/nerosecurity/neroland_gates/`. They are records for the
  optional quest pack only and are **never** read to decide access.
- `ShippedGateContentTest` decodes every shipped gate file with Core's own `Gate.DATA_CODEC`
  and checks it is player-scoped (`GateScope.PLAYER`) with no `requires`.

## Data and erasure — `za.co.neroland.nerolandcore.data`

**Used:** `PlayerDataErasure`, `PlayerDataEraser`; in tests additionally `ErasureConformance`.
**Planned:** `SavedDataRecovery`.

- **`PlayerDataErasure`** — `data/SecurityData.init()` registers one composite eraser with the
  shared fan-out as step 4 of common init, before any NeroSecurity store exists. One erasure
  request, or Core's inactivity retention sweep, therefore reaches NeroSecurity from the first
  build that stores anything. Every player-keyed store joins the composite with
  `SecurityData.addStore(...)`.
- **`ErasureConformance`** (test suite, in Core's main source set since **1.11.0**) —
  `SecurityErasureConformanceTest` runs Core's harness against `SecurityData`'s registered eraser
  on every NeoForge build: the request must reach every store in the composite, a bystander's
  rows must survive, a failing store must not stop the next one, and the report must carry no
  player identity.
- **`SavedDataRecovery`** (since **1.11.0**; registry-aware backups since **1.15.0**) — planned,
  one per store. Every NeroSecurity `SavedData` will load through `SavedDataRecovery.get(...)` so
  a corrupt save file recovers from a backup instead of crash-looping, and every store's erasure
  step will call `SavedDataRecovery.backupNow(...)` straight after erasing, so erased rows cannot
  come back from a stale backup.

## What is *not* used

- **Networking:** NeroSecurity does **not** use Core's `CoreNetwork`. Core drains its payload
  lists during its own bootstrap, so a downstream registration would be silently dropped.
  NeroSecurity runs its own `nerosecurity:main` channel (`network/SecurityNetwork` plus one
  registrar per loader) behind its own `NetworkPlatform` seam.
- **Link API:** `link/SecurityLinkModule` is an empty slot at the end of common init; nothing is
  registered with `NeroLinkRegistry` yet.
- **Threshold events:** not fired yet. The planned lockdown channel is `nerosecurity:lockdown`,
  whose scope will be an opaque installation id, never a player, name or position.
- No Core currency, reputation, energy, side-config, upgrade, material or highlight APIs. The
  optional NeroEconomy bridge talks to NeroEconomy's own API, not to Core's currency seam.
