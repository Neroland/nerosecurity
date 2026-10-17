# Config

NeroSecurity's settings live in `config/nerosecurity.properties`, created on first launch. Keys are
flat (no sections). Operators can reload the file with `/neroland config reload`; a few keys say they
need a restart.

Every key is decided by the server, except the two **client** keys at the top, which each player sets
for their own game. Numbers must stay inside the range shown. A word setting with an unknown value
falls back to its safest choice (for example `linkLockdownMode` reads anything unknown as `off`).

## Client

| Key | Default | What it does |
| --- | --- | --- |
| `telemetryEnabled` | true | Anonymous crash reports. Set `false` to opt out; takes effect on restart. See [Privacy](Privacy.md). |
| `renderAnimationsEnabled` | true | `false` makes devices draw a still frame instead of animating. |

## Switches

| Key | Default | What it does when `false` |
| --- | --- | --- |
| `lockdownEnabled` | true | No lockdown can start; stored lockdowns are kept but ignored. |
| `lockdownChannelEnabled` | true | Lockdown changes are not announced on Neroland Core's `nerosecurity:lockdown` channel. |
| `forceFieldsEnabled` | true | Emitters stay idle; leftover field blocks let everyone through and fade. |
| `camerasEnabled` | true | Every camera is dark and raises nothing. |
| `auditLogEnabled` | true | Nothing is written to the audit log; events still drive alarms. |
| `alarmChatEnabled` | true | Alarms send no chat messages. |
| `alarmLockdownEnabled` | true | No alarm may trigger a lockdown. |
| `factionsIntegrationEnabled` | true | NeroFactions bindings grant nothing. |
| `coloniesIntegrationEnabled` | true | NeroColonies bindings grant nothing. |
| `nerospaceIntegrationEnabled` | true | Nerospace station bindings grant nothing. |
| `claimsIntegrationEnabled` | true | Land claims are not consulted. |

## Installations

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `maxInstallationsPerPlayer` | 8 | 1–64 | Hubs one player may own. |
| `maxMembersPerInstallation` | 64 | 1–1000 | Member rows per installation (owners not counted). |
| `maxDeviceDistance` | 128 | 16–1024 | How far from its hub a device may be linked (X + Y + Z distance, same dimension). |
| `maxDevicesPerInstallation` | 256 | 16–4096 | Devices one installation may link. |

## Keycards

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `keycardsPerInstallation` | 512 | 16–4096 | Live cards per installation. When full, nothing more is written until cards are revoked. |
| `keycardBasicMaxLevel` | officer | member, officer, owner | Highest level a non-encrypted card may carry. |

## Doors and chests

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `doorsRequirePower` | false | | When true, an unpowered door opens only for its owners and operators. |
| `doorPowerPerTick` | 2 | 0–100 | Energy a linked, closed door draws each tick. |
| `doorOpenCost` | 200 | 0–100000 | Energy per opening. |
| `doorEnergyCapacity` | 20000 | 1000–1000000 | Energy a door stores. |
| `doorOpenTicks` | 20 | 5–100 | Open and close time. Each Open-Speed Module divides it by 1 + 0.5 × modules. |
| `doorAutoCloseTicks` | 60 | 0–1200 | How long an open door waits before closing. 0 = stays open. |
| `doorMaxBlastResistance` | 3600 | 100–100000 | Hardness Modules never raise a door's blast resistance past this. |
| `doorRedstoneAllowed` | true | | Whether a door may be set to open on redstone. |
| `doorDeniedEventCooldownTicks` | 40 | 0–1200 | Least time between two "denied" events from one player at one door or chest. |
| `chestAutomationDefault` | off | off, member, officer | Automation policy for new installations. |
| `chestAutomationEventIntervalTicks` | 1200 | 20–72000 | Least time between automation "chest changed" events per chest. |

## Lasers and cameras

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `laserBaseRange` | 8 | 2–32 | Pairing range; each Range Module adds 4. |
| `laserMaxRange` | 32 | 2–64 | Range cap after modules. |
| `laserScanIntervalTicks` | 2 | 1–20 | How often a beam is checked. |
| `laserTripCooldownTicks` | 100 | 1–1200 | One trip event per entity per node in this time. |
| `laserTripDamage` | 0.0 | 0–10 | Damage to a non-permitted entity that trips a beam. Never hurts owners, members or their pets. |
| `laserPowerPerTick` | 1 | 0–100 | Energy an armed node draws each tick. |
| `laserEnergyCapacity` | 4000 | 500–1000000 | Energy a node stores. |
| `cameraBaseRange` | 16 | 4–48 | View distance; each Range Module adds 4. |
| `cameraMaxRange` | 32 | 4–64 | Range cap after modules. |
| `cameraScanIntervalTicks` | 10 | 2–40 | How often a camera looks. |
| `cameraMaxRaycastsPerScan` | 8 | 1–32 | Line-of-sight checks per look. |
| `cameraEventCooldownTicks` | 200 | 20–6000 | One "motion detected" event per entity per camera in this time. |
| `cameraSweepTicks` | 200 | 40–1200 | Length of one full pan. |
| `cameraPowerPerTick` | 2 | 0–100 | Energy an armed camera draws each tick. |
| `cameraEnergyCapacity` | 4000 | 500–1000000 | Energy a camera stores. |

## Alarms, notice and monitor

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `alarmCoalesceSeconds` | 10 | 1–120 | Events this soon after a firing become one "+N more" firing. |
| `alarmRedstonePulseTicks` | 40 | 1–1200 | Redstone pulse length. |
| `alarmActiveTicks` | 200 | 20–1200 | How long an alarm stays lit. |
| `alarmSilenceMaxMinutes` | 60 | 1–1440 | Longest silence. |
| `alarmLockdownMinSeverity` | critical | warn, critical | Lowest event severity that may start an alarm lockdown. |
| `alarmLockdownAutoReleaseSeconds` | 300 | 30–3600 | An alarm lockdown ends after this unless an owner confirms it. |
| `alarmLockdownCooldownSeconds` | 600 | 0–86400 | Wait after an alarm lockdown before alarms may start another. |
| `noticeMarginBlocks` | 16 | 4–64 | How far beyond the devices the recording notice reaches. |
| `noticeCheckIntervalTicks` | 40 | 10–200 | How often players are checked against monitored areas. |
| `monitorEventCount` | 10 | 1–50 | Events a monitor shows. |

## Privacy

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `privacyAuditRetentionDays` | 7 | 1–90 | Audit entries older than this are deleted. Never more than `privacyRetentionHardCapDays`. |
| `privacyCameraEventRetentionMinutes` | 60 | 1–1440 | Presence entries (motion, members seen) are deleted after this. |
| `privacyAuditMaxEntriesPerInstallation` | 500 | 10–5000 | Entries kept per installation; the oldest go first. |
| `privacyRetentionHardCapDays` | 90 | 1–90 | The ceiling for every retention setting. It can only be lowered. |
| `privacyPurgeIntervalMinutes` | 60 | 5–1440 | How often old entries are deleted (also at start-up and before every read). |
| `privacyCameraLogsMembers` | false | | When true, cameras also log permitted players. |
| `privacyExportCooldownSeconds` | 60 | 10–86400 | Least time between two data exports by one player. |

## Force fields

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `forceFieldBreachable` | true | | Whether hits, projectiles and explosions can overload a field. `false` makes fields unraidable. |
| `maxFieldStrength` | 5 | 1–10 | Strength cap; lockdown raises fields to this. |
| `maxFieldArea` | 400 | 16–4096 | Most field blocks per emitter. |
| `fieldBaseRange` | 8 | 2–32 | How far from the emitter a field may reach; each Range Module adds 4. |
| `fieldMaxRange` | 24 | 2–64 | Range cap after modules. |
| `fieldBasePowerPerTick` | 5 | 0–1000 | Energy a projecting emitter draws each tick before blocks are counted. |
| `fieldPowerPerBlockStrength` | 0.25 | 0–10 | Draw per tick = base + blocks × strength × this (rounded up). |
| `fieldEnergyCapacity` | 100000 | 1000–10000000 | Energy an emitter stores. |
| `fieldRestartTicks` | 100 | 20–1200 | Ticks of draw the buffer must hold before a field forms. |
| `fieldBrownoutGraceTicks` | 20 | 0–200 | Ticks without enough energy before a field collapses. |
| `forceFieldOverloadSeconds` | 60 | 5–3600 | Cooldown after an overload. |
| `fieldLoadCapacityPerStrength` | 40 | 1–1000 | Load a field holds per point of strength. |
| `fieldLoadDecayPerSecond` | 2.0 | 0–100 | How fast load cools. |
| `fieldMeleeLoad` | 1.0 | 0–100 | Load per hit from a player who may not pass. |
| `fieldProjectileLoad` | 2.0 | 0–100 | Load per projectile hit. |
| `fieldExplosionLoadPerRadius` | 10 | 0–1000 | Load per block of explosion radius (TNT is 4). |
| `fieldEnergyPerLoad` | 50 | 0–10000 | Energy drained per point of load. |
| `fieldBlastResistance` | 6.0 | 0–1200 | Explosion resistance of a field block while fields are breachable. |
| `fieldLockdownRaisesToMax` | true | | Whether lockdown runs fields at `maxFieldStrength` for free. |
| `fieldReprojectIntervalTicks` | 40 | 10–200 | How often an emitter re-checks its blocks. |
| `fieldSyncIntervalTicks` | 10 | 1–40 | How often nearby players are told whether they may pass. |
| `fieldBreachEventCooldownTicks` | 200 | 20–6000 | One "force field attacked" event per attacker per emitter in this time. |

For a PvP server, see the raid-server preset on the [Force Fields](Force-Fields.md) page.

## NeroLink companion app

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `linkModuleEnabled` | true | | Whether NeroSecurity offers anything to the companion app. Takes effect on restart. |
| `pushNotificationsEnabled` | true | | Phone notifications, stored alerts and the login line. |
| `pushCooldownSeconds` | 60 | 10–3600 | At most one notification per installation in this time; later events are counted in. Lockdown changes skip it. |
| `pushDeniedThreshold` | 3 | 1–50 | Denied attempts within the window before one "Repeated access denied" notification. |
| `pushDeniedWindowSeconds` | 60 | 10–3600 | The window for the threshold above. |
| `linkLockdownMode` | off | off, release, full | What the app's lockdown action may do. |
| `linkLockdownAllowOffline` | false | | Whether the app's lockdown action works while the owner is not in the game. |
| `linkActionAckEnabled` | true | | Whether owners may acknowledge alerts from the app. |
| `linkActionSilenceEnabled` | true | | Whether owners may silence alarms from the app. |
| `loginAlertSummaryEnabled` | true | | The one-line unread-alert count after joining. |

See [NeroLink App and Notifications](Companion-App.md).

## See also

- [Commands](Commands.md)
- [Home](Home.md)
