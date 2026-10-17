# Neroland Core APIs used by NeroSecurity

Every Neroland Core surface this mod actually consumes, checked against the `za.co.neroland.nerolandcore`
imports in `common/src` and the three loader source trees. NeroSecurity compiles against
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

## Summary

| Package | API | Used for |
| --- | --- | --- |
| `config` | `ConfigSchema`, `ConfigManager`, `ConfigValue` | The whole `config/nerosecurity.properties` schema |
| `registry` | `RegistrationProvider` (+ `RegistryEntry`), `CoreCreativeTab`, `BlockCodecs` | Registering blocks, items, block entities, menus and data components; the shared creative tab; block codecs |
| `machine` | `AbstractMachineBlockEntity` | Energy buffer and ticking for doors, laser nodes, cameras and emitters |
| `energy` | `NeroEnergyStorage` | The energy buffer type and the insert-only views handed to other mods |
| `sideconfig` | `SideConfig`, `SideConfigComponent`, `Channel`, `ChannelConfig`, `RelativeFace`, `SideGating`, `SideMode`, `SidePreset` | Fixed insert-only energy routing on every powered device |
| `platform` | `NeoForgeEnergyLookup`, `ForgeEnergyLookup`, `FabricEnergyLookup` | Exposing device energy through Core's shared capability on each loader |
| `data` | `PlayerDataErasure`, `PlayerDataEraser`, `SavedDataRecovery` | Erasure fan-out; corruption-safe loading and backups of every store |
| `progression` | `ProgressionGates` | The three player milestone gates for the quest pack |
| `event` | `ThresholdEvents` (+ `ThresholdCrossing`) | Publishing `nerosecurity:lockdown`; listening for faction tier crossings |
| `reputation` | `ReputationApi` | Reading a player's faction reputation in the NeroFactions bridge |
| `link` | `NeroLinkRegistry`, `LinkModuleInfo`, `LinkSnapshotProvider`, `LinkActionHandler`, `LinkActionResult`, `LinkEvent`, `LinkAlert`, `LinkAlerts` | The NeroLink companion-app module |

## Config — `za.co.neroland.nerolandcore.config`

**Used:** `ConfigSchema`, `ConfigManager`, `ConfigValue`.

`config/NeroSecurityConfig` declares one schema (file `config/nerosecurity.properties`) and
registers it with `ConfigManager` as step 1 of common init, because everything after it reads
config, including telemetry's opt-out. Core's schema has no sections, so keys are flat. Every gameplay
key is server-authoritative. `telemetryEnabled` and `renderAnimationsEnabled` are deliberately
**not**: crash reporting and animation are per-client choices a server must never force. The keys are
listed in the wiki's [Config](wiki/Config.md) page.

## Registries — `za.co.neroland.nerolandcore.registry`

**Used:** `RegistrationProvider` and `RegistrationProvider.RegistryEntry`, `CoreCreativeTab`, `BlockCodecs`.

- `registry/NeroSecurityBlocks`, `NeroSecurityItems`, `NeroSecurityBlockEntities`, `NeroSecurityMenus`
  and `NeroSecurityDataComponents` create one `RegistrationProvider` each; the per-feature
  `*Registrations` classes add their entries through them. The Forge and NeoForge entry points hand
  Core their mod bus with `RegistrationProvider.attach(...)`.
- Every block item and item is added to Core's shared creative tab with `CoreCreativeTab.add(...)`.
  NeroSecurity has no tab of its own.
- Every block's `MapCodec` is built with `BlockCodecs.simple(...)`.

## Machines, energy and side configuration

**Used:** `machine.AbstractMachineBlockEntity`, `energy.NeroEnergyStorage`, the `sideconfig` types above,
and the loader lookups `platform.NeoForgeEnergyLookup`, `platform.ForgeEnergyLookup`,
`platform.FabricEnergyLookup`.

- The door controller, laser grid node, security camera and force field emitter block entities extend
  `AbstractMachineBlockEntity`, which gives them an energy buffer, persistence and Core's ticker. The
  hub, programmer, monitor, chest and alarm are unpowered.
- `energy/InputOnlyEnergy` installs a `SideConfig` with the `ALL_INPUT` preset on the energy `Channel`,
  forces every face back to `SideMode.INPUT` on load and every tick, and returns `null` from
  `sideConfig()` so Core's configurator and intent packet find nothing to edit. Every external energy
  view is an insert-only `NeroEnergyStorage` wrapper, so nobody can drain or starve a security device.
- Each loader registers those views with Core's shared energy capability. `NeoForgeEnergyLookup` and
  `FabricEnergyLookup` register the block entities and, for doors, every door block, so any part of a
  door takes power. `ForgeEnergyLookup` attaches to block entities only, so a Forge door takes power
  through its controller block.
- Doors work without power unless `doorsRequirePower` is on.

## Data and erasure — `za.co.neroland.nerolandcore.data`

**Used:** `PlayerDataErasure`, `PlayerDataEraser`, `SavedDataRecovery`; in tests additionally
`ErasureConformance`.

- **`PlayerDataErasure`**: `data/SecurityData.init()` registers one composite `PlayerDataEraser` with
  the shared fan-out as step 4 of common init, before any NeroSecurity store exists. One erasure request
  (`/neroland data eraseme`, `/neroland data erase <uuid>`) or Core's inactivity retention sweep
  therefore reaches every NeroSecurity store. Each player-keyed store joins the composite with
  `SecurityData.addStore(...)`; the link module joins as store `link` for its in-memory state.
- **`SavedDataRecovery`**: every NeroSecurity `SavedData` loads through `SavedDataRecovery.get(...)`, so
  a corrupt save recovers from the last-known-good backup instead of crash-looping, and every store's
  erasure step calls `SavedDataRecovery.backupNow(...)` straight after erasing, so erased rows cannot
  come back from a stale backup. The stores are `installation/SecurityInstallations`,
  `audit/SecurityAuditLog` and `privacy/PrivacyOptOuts`.
- **`ErasureConformance`** (test suite): `SecurityErasureConformanceTest` runs Core's harness against
  the registered eraser on every NeoForge build.

## Progression — `za.co.neroland.nerolandcore.progression`

**Used:** `ProgressionGates`; in tests additionally `Gate` and `GateScope`.

`progression/SecurityMilestones` opens three player-scope gates (`nerosecurity:first_keycard`,
`installation_online`, `lockdown_drill`) with `ProgressionGates.open(ServerPlayer, Identifier)` and,
for an owner who may be offline when a device is first linked, `ProgressionGates.open(MinecraftServer,
UUID, Identifier)`. They are recorded when a player writes their first keycard, when an installation
first has a linked device, and when an owner clears a lockdown an owner started by hand. The gates are
datapack files under `data/nerosecurity/neroland_gates/` and are **never** read to decide access.
`ShippedGateContentTest` decodes every shipped gate file with `Gate.DATA_CODEC`.

## Threshold events — `za.co.neroland.nerolandcore.event`

**Used:** `ThresholdEvents`, `ThresholdEvents.ThresholdCrossing`.

- `lockdown/LockdownChannel` fires one crossing on channel `nerosecurity:lockdown` per lockdown
  transition (value 1 rising on entry, 0 falling on exit, operator unlock or alarm auto-release). The
  scope is the opaque `inst:<id>` key: never a player, name or position. It fires only while
  `lockdownChannelEnabled` and `lockdownEnabled` are both on. No other security event is broadcast.
- `compat/factions/FactionsCompat` listens with `ThresholdEvents.onSourcedCrossing` for NeroFactions
  reputation-tier crossings. A crossing carries a faction id and no player; it only tells force field
  emitters to re-check passability this tick.

## Reputation — `za.co.neroland.nerolandcore.reputation`

**Used:** `ReputationApi`, in `compat/factions/NeroFactionsBridge` only (compiled only when the
NeroFactions integration is on). It reads the acting player's reputation with a bound faction to find
their faction tier, which `FactionTierMapping` turns into an access level capped at OFFICER.

## Link API — `za.co.neroland.nerolandcore.link`

**Used:** `NeroLinkRegistry`, `LinkModuleInfo`, `LinkSnapshotProvider`, `LinkActionHandler`,
`LinkActionResult`, `LinkEvent`, `LinkAlert`, `LinkAlerts`.

`link/SecurityLinkModule` is the last step of common init. It registers a `LinkSnapshotProvider` and a
`LinkActionHandler` with `NeroLinkRegistry` under one `LinkModuleInfo`, publishes per-player
`LinkEvent`s on `NeroLinkRegistry.eventBus()`, and raises, lists, acknowledges and dismisses
per-player `LinkAlert`s through `LinkAlerts`. `privacy/SecurityPrivacyActions` reads `LinkAlerts` to
count a player's unread alerts for their data export. The whole contract is in
[USING-NEROLINK.md](USING-NEROLINK.md). A missing or older Core link API logs one warning and leaves
every installation working.

## What is *not* used

- **Networking:** NeroSecurity does **not** use Core's `CoreNetwork`. Core drains its payload lists
  during its own bootstrap, so a downstream registration would be silently dropped. NeroSecurity runs
  its own `nerosecurity:main` channel (`network/SecurityNetwork` plus one registrar per loader) behind
  its own `platform/NetworkPlatform` seam.
- **Upgrades:** NeroSecurity's five modules are its own (`upgrade/SecurityModule`,
  `upgrade/SecurityModuleSlots`), shaped like Core's upgrade container, because their effects have no
  counterpart in Core's shared upgrade types.
- No Core currency, material or highlight APIs. The optional NeroEconomy bridge talks to NeroEconomy's
  own API, not to Core's currency seam.
