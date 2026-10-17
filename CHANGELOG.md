# Changelog

All notable changes to **NeroSecurity** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0-beta.1] - unreleased

The first build with gameplay. Requires Neroland Core 1.15.0. Scope cuts and known gaps are listed at the bottom.

### Added

- **Access levels and resolution.** A fixed four-rung ladder, PUBLIC < MEMBER < OFFICER < OWNER, plus a
  server-operator ADMIN override that no in-game action can revoke. Every access decision goes through one
  server-side resolver: it asks every access source, any deny wins, and otherwise the highest grant applies.
  Nothing is cached on the client.
- **Installations.** The unit of administration: owners, members with levels, linked device positions,
  lockdown state and a per-installation secret (kept server-side, never sent to clients or logs).
- **Security Hub** block. Placing one creates an installation you own; it is the only place membership is
  edited. Only an owner (sneaking) or an operator can break it; breaking it dissolves the installation, and
  any other removal (explosion, commands) leaves the installation orphaned for an operator to clean up.
- **Linking Tool** item. Sneak-use on a hub to select it, then use it on a security device to link it.
  The hub itself is never a linkable device.
- **Keycards.** Every encoded card is signed with its installation's server-side secret, so a card cannot be
  edited to raise its level or moved to another installation; a card from a different installation is a
  dead card. Each installation keeps a ledger of the cards it issued: an owner can revoke one card or all of
  them (`/nerosecurity keycards <installation> list|revoke <serial>|revokeall`). Cards can be bound to the
  person who wrote them. A card only ever grants access — it never overrides a denial or an operator, and during a
  lockdown only an OWNER card still works. Cards open doors, chests, force fields and sensors but do not
  administer: the hub, linking, members, lockdown, the log, alarms, settings and card management need a stored
  member at the right level. No player id is stored on the card itself.
- **Keycard Programmer.** Writes, copies and wipes cards. You can only write cards below your own level
  (owners can write owner cards); cards above OFFICER must be encrypted by default
  (`keycardBasicMaxLevel`). Encrypted cards can only be copied by an owner at a programmer fitted with an
  Encryption Module, and their tooltip hides level and serial. Writing your first card records the
  *first keycard* milestone.
- **Security Door** (1×2) and **Blast Door** (2×2). Set the minimum level, keycard-only mode and redstone
  behaviour per door. Sealed during lockdown except to owners and operators. They use Neroland Core power
  when it is available, but by default they work without it (`doorsRequirePower=false`); when power is
  required, an unpowered door still opens for its owner. Hardness and Open Speed modules; blast resistance is
  capped (`doorMaxBlastResistance`). Doors stay breakable, and breaking one you could not open is logged as a
  forced entry.
- **Secure Chest.** Checks access when opened. Automation (hoppers, pipes) only reaches it when the
  installation allows it (`/nerosecurity automation <installation> off|member|officer`, owner only) and never
  during lockdown or in keycard-only mode. Opening and changing a chest is recorded without its contents;
  it has no comparator output so its fill level stays private. Contents always drop when broken.
- **Modules:** Encryption, Hardness, Open Speed, Range, Strength.
- **Boss-proof blocks.** The hub, field blocks and every device (doors, chest, programmer, monitor, laser node,
  camera, alarm, emitter) are in `minecraft:wither_immune` and `minecraft:dragon_immune`, so a wither or the
  dragon cannot delete them or orphan an installation; mining and overloading still work.
- **Force Field Emitter.** Projects a plane or box of field blocks that permitted players walk through and
  everyone else (and every mob, projectile or vehicle) bumps into. During a lockdown only owners pass. Power
  draw grows with area and strength; Strength and Range modules raise them up to `maxFieldStrength` and
  `fieldMaxRange`. Fields can be overloaded by sustained attacks or explosions (`forceFieldBreachable`, on by
  default) and come back after a cooldown, and the emitter itself can always be broken, so a field slows a raid
  down rather than making a base unraidable. Owners always pass their own fields, keycard-only included, and
  only an owner can switch a field to keycard-only. Lockdown raises a field to full strength without resetting its
  overload, breaking and re-placing an emitter does not skip its cooldown, and field blocks can only be removed
  by their owners (or once their emitter is gone). Fields replace grass and snow but not real blocks or water;
  if something blocks part of the shape, the installation logs that the field is obstructed. Fields only fill air (and replaceable plants or snow, which are not restored), are never placed where an entity is standing,
  and never project into land another player's claim protects; note that a closed box can still enclose someone
  who was already inside it. The field blocks are immune to withers and the dragon.
- **`raid-server` preset** (documented values for PvP servers; every other key keeps its default):

  | Key | Default | raid-server |
  |---|---|---|
  | `forceFieldBreachable` | true | true |
  | `maxFieldStrength` | 5 | 3 |
  | `maxFieldArea` | 400 | 256 |
  | `fieldLoadCapacityPerStrength` | 40 | 25 |
  | `fieldLoadDecayPerSecond` | 2.0 | 1.0 |
  | `fieldExplosionLoadPerRadius` | 10 | 15 |
  | `fieldMeleeLoad` | 1.0 | 1.5 |
  | `forceFieldOverloadSeconds` | 60 | 180 |
  | `fieldPowerPerBlockStrength` | 0.25 | 0.5 |
  | `fieldLockdownRaisesToMax` | true | true |
  | `doorMaxBlastResistance` | 3600 | 1200 |
- **Optional integrations** (each only when that mod is installed; NeroSecurity works fully without them):
  - **NeroFactions:** an owner can bind an installation to a faction they belong to; faction members then get
    access by tier (Member → MEMBER, Trusted / Inner Circle → OFFICER), capped by the binding's maximum (MEMBER
    unless the owner chooses OFFICER), never OWNER. Only a stored owner can bind or unbind — not a keycard.
  - **NeroColonies:** bind to a colony you belong to; colony members get access by role (capped, never OWNER).
  - **Nerospace:** bind to a station you own; the station's current owner gets OFFICER.
  - **Open Parties and Claims:** NeroSecurity never lets you do what a claim forbids — a claim can only deny.
  - Commands: `/nerosecurity bind <installation>` shows bindings; `bind faction|colony|station …` and
    `unbind …` are owner-only. A binding to a mod that is later removed stays inactive until you unbind it.
- **Laser Grid Node.** Place two nodes facing each other, both linked to the same installation; a
  non-permitted entity crossing the beam trips it. A node from another installation never extends your beam.
  Optional damage on trip (`laserTripDamage`, default 0) never hurts the installation's own owners or members
  (or their pets), even during a lockdown. Needs power; an unpowered pair goes dark.
- **Security Camera.** A powered presence sensor with a field of view and a slow pan. It never captures
  imagery: it only records that something it was not told to ignore came into view. Members are not logged
  unless the server owner turns on `privacyCameraLogsMembers`.
- **Alarm.** Reacts to the event types you choose with a redstone pulse, a sound, a light, and a chat line to
  online owners (and officers, if an owner turns it on with `/nerosecurity notify-officers <installation>
  on|off`). Alarm storms are coalesced into one message with a "+N more"
  count. Chat names the installation and what happened, never who. Officers can silence an alarm.
- **Alarm-triggered lockdown** (off by default; only an owner can enable it per alarm, and the server can
  disable it with `alarmLockdownEnabled`). It only fires on critical events, releases itself after 5 minutes
  unless an owner confirms it, and cannot fire again for 10 minutes. An owner is never locked out. Each alarm
  only reacts to the events it is subscribed to, and while its lockdown response is on only an owner can change
  its settings (officers can still silence it).
- **Security Monitor.** Shows linked cameras and laser grids with their status, and recent events as
  sentences ("Laser grid tripped — 3 minutes ago"). It never shows who caused an event.
- **Audit log.** Each installation keeps a capped log of security events: when, what kind, which device, and
  who (where the event has a player). Officers and owners can read it with `/nerosecurity log`; every read is
  itself logged.
- **Recording notice.** Armed cameras and laser grids show a lit lens, and you get a one-line message the first
  time each session you enter an area where an installation is recording. The area covers everything its
  sensors can reach, and if a sensor is about to log you before you have seen the notice, you get the notice
  first.
- **Lockdown.** OFFICER or above can enter lockdown at the hub or with `/nerosecurity lockdown`; only an
  owner or operator can clear it. During lockdown effective access drops to OWNER, and an owner can always
  reach their own hub.
- **Lockdown threshold channel.** Entering and leaving lockdown fires Neroland Core's threshold channel
  `nerosecurity:lockdown` so quest packs can react. The scope is an opaque random installation id, never a
  player, name or position. Residual risk: a listener learns that *some* base entered lockdown. Turn it off
  with `lockdownChannelEnabled=false`. This reverses 0.4.0's "no threshold channels" rule for lockdown only;
  no other security event is broadcast.
- **Commands.** `/nerosecurity installations` lists your own installations; `/nerosecurity lockdown
  <installation> on|off`; `/nerosecurity admin unlock|list|dissolve` for operators. Suggestions show short
  installation ids only, scoped to your own installations.
- **Config keys** (all server-authoritative): `lockdownEnabled`, `lockdownChannelEnabled`,
  `maxInstallationsPerPlayer`, `maxMembersPerInstallation`, `maxDeviceDistance`, `maxDevicesPerInstallation`.
- Own network channel `nerosecurity:main`, a menu-open guard, and early registration with Neroland Core's
  player-data erasure hook.
- Unit tests for the resolver, the installation store, erasure conformance, the event bus, the lockdown
  channel and the shipped quest/gate data (run in the NeoForge build).
- **NeroLink companion app module.** Installations, recent events, device status and the legacy summary are
  readable from the NeroLink companion app (your own installations only, no coordinates, no player ids);
  breaches and lockdown changes become grouped phone notifications and stored alerts whose text is only a
  fixed phrase and the installation name; owners can acknowledge alerts and silence alarms from the app, and
  remote lockdown is off unless the server enables it (`linkLockdownMode`). New `/nerosecurity alerts` and a
  counts-only login summary. Config: `linkModuleEnabled`, `pushNotificationsEnabled`, `pushCooldownSeconds`,
  `pushDeniedThreshold`, `pushDeniedWindowSeconds`, `linkLockdownMode`, `linkLockdownAllowOffline`,
  `linkActionAckEnabled`, `linkActionSilenceEnabled`, `loginAlertSummaryEnabled`. See PRIVACY.md.
- **Models and animation.** Every block has a multi-part model. Doors and blast doors slide open and clamp shut
  in lockdown; the secure chest lid opens; laser beams are drawn between paired nodes and flash when tripped;
  cameras pan and turn toward what they saw; alarms spin a beacon; force-field emitters spin their core faster
  as they draw more power; the hub and monitor screens animate. Turn the motion off with
  `renderAnimationsEnabled=false` (client setting): every device then draws as a still frame.
- **All block and item textures in this release are procedurally generated by `tools/gen_security_textures.py`;
  none are hand-painted. They are readable and consistent but not final art.** CI checks every texture (32 px
  wide, at least six colours, animated strips with their `.mcmeta`) and fails on any texture the generator does
  not own.
- **Screens.** New hub screen with an Overview tab (status, lockdown, members, devices, the last five events)
  and a Privacy tab (what the installation records, plus Export / Stop naming me / Erase my data). Keycard
  programmer, door, chest, sensor, alarm, monitor and force-field screens are rebuilt on one toolkit: every
  disabled control says why, lockdown and removals ask for confirmation, and the footer shows the server's
  answer ("Saved." or the reason it refused).
- **Deny feedback.** When a device refuses you, you hear a short buzz, the device flashes and the action bar
  says why. Only you get this feedback.
- **Sounds** use vanilla sound events; there are no custom sounds and no `sounds.json` in this release.
- Keycards show a coloured band for their level in the hotbar; encrypted cards show a purple band and no level.
- Renderers are culled by distance and view (never drawn off-screen). `/nsber` (client command) prints the
  renderer statistics used for the performance budget in `docs/BER-BUDGET.md`.

### Changed

- Crash reporting is now active in official builds (still opt-out with `telemetryEnabled=false`, which takes
  effect on restart). Reports now mask your OS account name and home folder, and are grouped by source.
- The optional NeroEconomy floor is now 0.4.0-beta.1 (the version NeroSecurity is compiled against); the
  runtime version probe is gone.
- Dependabot now targets `main`; the wiki sync refuses to publish pages that reference private planning
  material.

### Privacy

- **Retention defaults (server-authoritative):** audit entries are kept 7 days (`privacyAuditRetentionDays`),
  camera presence entries 60 minutes (`privacyCameraEventRetentionMinutes`), at most 500 entries per
  installation (`privacyAuditMaxEntriesPerInstallation`). Server owners can lengthen retention only up to a
  hard cap of 90 days (`privacyRetentionHardCapDays`). Old entries are purged at start-up, hourly and on read.
- An erasure request also removes the player from every audit entry.
- **Your privacy controls:** `/nerosecurity privacy optout|optin|status|export` and the hub's Privacy tab buttons now work: opting out records you as "anonymous" in every new log row from that moment on (the event itself still fires; rows from before you opted out keep your id until they expire or you ask for erasure, and a record of you reading a security log always names you), export writes a JSON copy of your NeroSecurity data, including the security alerts NeroSecurity raised for you, to `nerosecurity/exports/` in the world folder (one per minute, `privacyExportCooldownSeconds`; deleted after 7 days), and erasure now also clears your opt-out, export file and recording-notice memory and renames an orphaned installation to a neutral name.
- A tamed animal is judged by its owner's access but logged as nobody (its owner is never named for it). Server operators are never logged by sensors.
- Doors, chests, force fields, alarms, monitors and keycard checks now show the recording notice before they log a player who has not had it yet. Monitors, hubs, the app's event list and notifications no longer tell an opted-out player apart from any other player.
- Caught errors sent to error reporting carry the error's type only, never its message, and UUID-shaped text is masked in every report.
- Cameras only share a coarse look direction with nearby clients (rounded, at most every two seconds), and
  sensor settings are no longer sent to clients at all.

- NeroSecurity now stores player data: installation owners and members (as player UUIDs with levels),
  installation names, linked device positions and lockdown state. See PRIVACY.md. A Neroland Core erasure
  request removes a player's member rows; installations they solely own are handed to a tombstone owner and
  flagged orphaned so another player's base is not deleted.

### Known gaps in this build

- The renderer performance budget in `docs/BER-BUDGET.md` has not been measured on a live client yet.
- On Forge, door power can only be fed into the door's controller (bottom) block; NeoForge and Fabric
  accept power through any part. On Fabric, the Hardness module slows breaking but does not raise
  explosion resistance.
- Each installation can issue up to `keycardsPerInstallation` cards (default 512); when full, writing
  refuses until cards are revoked — nothing is silently un-revoked.
- During a lockdown everyone below OWNER counts as an intruder to sensors, so members can trip lasers and
  cameras while the base is locked down.
- FTB Chunks and Flan claims are not supported yet (no stable build for every Minecraft version this mod
  targets); only Open Parties and Claims is.
- The hub screen is for officers and owners; members get the deny message and use the
  `/nerosecurity privacy` commands instead of the Privacy tab.

### Not in this release

Custom sounds; hand-painted art; a read-only member view of the hub; camera imagery of any kind (cameras are
presence sensors only); a lockdown HUD overlay; resource-pack texture variants beyond the generator; nameable
device labels.

## [0.4.0-alpha.1] - 2026-10-11

Quest-pack data and milestone seams. Requires Neroland Core 1.15.0. Still no blocks, items or recipes.

### Added

- **NeroQuests quest pack**: chapter `nerosecurity:access_granted` "Access Granted", 5 player quests (Card
  Stock, First Keycard, Installation Online, Lockdown Drill, Head of Security) that appears in the quest book
  when NeroQuests is installed; inert otherwise, no dependency. The quests are data only today: they become
  completable once the keycard item, security hub and lockdown exist and the code records the milestones
  below. Until `nerosecurity:keycard` is registered NeroQuests drops *Card Stock* at load (its validator
  treats an unknown item from a loaded mod as a typo); the four gate and completion quests load and wait.
- **Milestone gates** (player scope, no `requires`, records only): `nerosecurity:first_keycard`,
  `nerosecurity:installation_online`, `nerosecurity:lockdown_drill`.
- `progression/SecurityMilestones`: one `record(...)` per gate for future security code to call, online or
  offline (Core 1.15.0's `ProgressionGates.open(server, uuid, gate)`). A milestone gate is never read for
  an access decision; access comes from keycards, owners, factions and claims only.

### Changed

- Neroland Core floor raised from 1.13.0 to 1.15.0 (offline-capable progression gates).
- No threshold channels, by design: a crossing about alarms, intrusions or lockdowns would announce that a
  base is under attack right now, so the security event bus stays internal. Quest rewards never change
  access state (no hook gates, no free access level, no door bypass).

## [0.3.0-alpha.1] - 2026-10-11

Optional NeroEconomy seams. No blocks, items or recipes yet.

### Added

- **NeroEconomy seam for paid security tiers** (NeroEconomy stays optional; NeroSecurity builds, loads and
  runs without it): `economy/SecurityEconomy.chargeTier` charges reason `security_tier` into NeroEconomy's
  Reserve and returns an outcome with a player-facing message.
- The rules every security feature keeps around economy blocks (owners always reach their own blocks,
  never break blocks, no economy inspection for access, a failed renewal only downgrades), documented in
  the README and the new wiki page *NeroEconomy integration*.

### Changed

- NeroEconomy is declared as an optional dependency on every loader (Fabric `suggests`, NeoForge
  `optional`, Forge `mandatory = false`, floor `0.3.0-beta.1`) and compiled `compileOnly`.
- Neroland Core (floor 1.13.0) is now a required dependency, and NeroSecurity ships anonymous, opt-out
  crash reporting (see PRIVACY.md).

## [0.2.0-alpha.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** NeroSecurity adds no recipes of its own, so nothing needed a plugin. The build now
  compiles against the community EMI Unofficial Port (Unstable), the only EMI build for Minecraft 26.x,
  and dev clients load it with `-PwithEmi` (default runs stay JEI-only). EMI stays optional.

## [0.1.0-alpha.1] - 2026-09-20

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

