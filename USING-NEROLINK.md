# NeroSecurity link contract (NeroLink)

What NeroSecurity offers the NeroLink companion app through **Neroland Core's link API**, taken from
`common/src/main/java/za/co/neroland/nerosecurity/link/`. NeroSecurity opens no socket and talks to no
service: it registers with `NeroLinkRegistry` in Core, and the NeroLink mod serves that to the paired
app. Without the NeroLink mod the module is inert. The player-facing version of this page is
[NeroLink App and Notifications](wiki/Companion-App.md).

## Module

| Field | Value |
| --- | --- |
| Module id | `nerosecurity` |
| Schema version | `1` (every section and live row carries `schema_version`) |
| Sections | `installations`, `events`, `devices`, `log` (legacy), `wiki` |
| Actions | `lockdown`, `ack_event`, `silence_alarm` |
| Live topic | `nerosecurity.events` (Core topic `events` under module `nerosecurity`) |
| Module switch | `linkModuleEnabled` (default true; restart to apply). False registers nothing at all. |

Registration is the last step of common init and is guarded: a missing or older Core link API logs one
warning and leaves every installation working.

## Who sees what

Scope is re-resolved on every call, from the requester's **stored** relation to each installation:

| Role | From | Sees |
| --- | --- | --- |
| `owner` | the installation's owner list | everything below |
| `officer` | an OFFICER member row | everything below |
| `member` | a MEMBER member row | id, name, dimension, role and lockdown `{active, source}` only; `events` and `devices` answer `restricted` |
| none | anything else | nothing; an id they may not see answers like an unknown id |

- No operator elevation, no keycard, no faction, colony or station grant, no proximity. Those are
  in-game access, not phone access.
- A deny-wins resolver source (for example a land claim) hides the installation. Lockdown does not.
- **No coordinates anywhere.** Devices are a `kind` and an ordinal `label` ("Laser grid 3"); an
  installation row carries its `dimension` only.
- **No player UUID ever, and no rosters.** Who acted is an `actorKind`: `none`, `player`, `withheld`
  (opted out) or `you`.
- Installation parameters accept the exact canonical UUID only: no prefixes, no names.
- Any failure answers an empty section rather than an error from the bridge.

## Sections

Every response has `schema_version` and `asOf` (epoch ms).

### `installations`

No parameters. Top level: `lockdownEnabled`, `auditEnabled`, `actions` and `installations[]`.

`actions` advertises what the server allows: `lockdown` (`off`, `release` or `full`; `off` whenever
lockdown itself is disabled), `lockdownOffline` (boolean), `ack_event` (boolean), `silence_alarm`
(boolean).

An owner or officer row:

| Key | Meaning |
| --- | --- |
| `id`, `name`, `dimension`, `role` | the installation; `dimension` may be null |
| `orphaned` | true when the last real owner was erased |
| `lockdown` | `{active, since, source: manual\|alarm, autoReleaseAt, byOwner}`; inactive while lockdown is switched off on the server |
| `deviceCounts` | `{doors, fields, lasers, cameras, alarms, chests, other, total}` |
| `powered` | true if a loaded powered device has power, false if loaded ones exist and none has, null when unknown |
| `lastEventAt`, `lastEvent` | the newest audit row: `{type, severity, sentence}`, or null |
| `unackedAlerts` | the requester's unread alerts for this installation |
| `notifyOfficers` | whether officers are notified |

A member row has `id`, `name`, `dimension`, `role` and `lockdown: {active, source}` only.

### `events`

Read from the audit log, so it serves **every audit row type**, not only the types that are pushed.

| Param | Meaning |
| --- | --- |
| none | a merged feed of the newest 50 rows across every installation where the requester is owner or officer; `actorsIncluded` is always false |
| `installation=<uuid>` | one installation (owner or officer, else `restricted: true`) |
| `limit=<n>` | 1–100, default 50 |
| `actors=1` | with `installation`, adds display names; see below |

Top level: `auditEnabled`, `actorsIncluded`, `events[]`. Each row:

```json
{
  "id": "1760650000000-laser_tripped",
  "at": 1760650000000,
  "installationId": "…",
  "installationName": "Orbital Lab",
  "type": "laser_tripped",
  "severity": "critical",
  "sentence": "Laser grid tripped",
  "device": { "kind": "laser_grid_node", "label": "Laser grid 3" },
  "actorKind": "player",
  "alertId": "nerosecurity.<installationId>.laser_tripped"
}
```

`device` is present when the row's device is still linked; `alertId` when the requester has an alert
this row belongs to. `id` is for list keys only.

**`actors=1`** is a deliberate read. It is honoured only for an owner or officer whom the resolver
allows to read the log (no operator override), and only while the audit log is on. A `LOG_READ` row is
written **before** any row is read. Rows with `actorKind: player` then carry `actor: {name}` from the
server's local name cache. Never a UUID.

### `devices`

`installation=<uuid>` (owner or officer, else `restricted: true`). Each row: `kind`, `label`, `loaded`,
and, for a loaded device, `powered` (when it uses power), `state`, `tripped` (laser) and
`silencedUntil` (alarm).

| Kind | States |
| --- | --- |
| `security_door`, `blast_door` | `open`, `sealed`, `unpowered`, `closed` |
| `secure_chest` | `open`, `locked` |
| `laser_grid_node` | `unpaired`, `armed`, `unpowered`, `disarmed` |
| `security_camera` | `armed`, `unpowered`, `disarmed` |
| `alarm` | `active`, `armed`, `silenced` |
| `force_field_emitter` | `projecting`, `cooldown`, `collapsed`, `off`, `unpowered` |

Kinds are `security_door`, `blast_door`, `laser_grid_node`, `security_camera`, `alarm`, `secure_chest`,
`force_field_emitter`, `keycard_programmer`, `security_monitor`, or `other`. The number of devices read
is bounded by `maxDevicesPerInstallation`.

### `log` (legacy)

The generic summary shape older app builds render: a `headline` ("2 installations · 0 in lockdown · 3
events in the last hour") and `sections[]` with an "Installations" list (`label`, `value`, `status`,
`severity`) and a "Recent events" list (the newest 10 rows from installations where the requester is
owner or officer). Kept through 0.5.x.

### `wiki`

Public and the same for every player. With no `page` parameter: `{mod, title, pages[{slug, title}],
asOf}`. With `page=<slug>`: `{mod, slug, title, format: "markdown", content, asOf}`, or
`{error: "unknown page"}`. The pages are this repository's `wiki/*.md`, bundled into the jar at build
time with a generated index; only slugs in the index are served.

## Actions

Every action runs this pipeline, in order:

1. a known action id, the module switch and a running server;
2. the action's own config switch, else `ACTION_DISABLED`;
3. a replayed `requestId` returns the first result with no second change (per player, newest 64 ids,
   10 minutes);
4. the per-player rate limit, else `VALIDATION` "Too many requests; try again in N s.";
5. for `lockdown` and `silence_alarm`, the installation is re-read and the requester must be a
   **stored owner** whom the resolver allows at OWNER with **no operator override**; missing, unknown
   and not-yours are one answer, `NOT_OWNER`;
6. the action itself.

| Action | Params | Switch (default) | Rate limit | Offline | Result `state` |
| --- | --- | --- | --- | --- | --- |
| `lockdown` | `installationId`, `enabled` (boolean), `requestId` | `linkLockdownMode` (`off`) | 1 per 10 s, 6 per minute | only with `linkLockdownAllowOffline` (false) | `{installationId, lockdown}` |
| `ack_event` | `eventId` (an alert id), `requestId` | `linkActionAckEnabled` (true) | 30 per minute | yes | `{eventId, acked: true}` |
| `silence_alarm` | `installationId`, `minutes` (1 to `alarmSilenceMaxMinutes`), `requestId` | `linkActionSilenceEnabled` (true) | 6 per minute | yes | `{installationId, silencedUntil}` |

- **`lockdown`.** With `linkLockdownMode = off` (the default) or `lockdownEnabled = false`, every call
  answers `ACTION_DISABLED`. `release` allows `enabled: false` only, which clears any active lockdown.
  `full` also allows `enabled: true`, which starts a lockdown, or confirms an active alarm lockdown
  (it stops auto-releasing). Asking for the state it is already in returns that state. A remote
  lockdown never records the lockdown-drill milestone. With `release` or `full`, a lost or unlocked
  phone can unseal its owner's bases (and with `full` also seal them); `release` plus
  `linkLockdownAllowOffline = true` is the "my phone can stand my base down, never seal it" setting.
- **`ack_event`** acknowledges one of the requester's own NeroSecurity alerts (the `alertId` of an event
  row). Anything else is `VALIDATION` "Unknown alert.".
- **`silence_alarm`** silences every alarm of the installation until `now + minutes`, including alarms
  in unloaded chunks, and logs "Alarm silenced". A lockdown still overrides it.

No action touches membership, levels, keycards, names, device settings or automation. Messages are
fixed text; logs carry the action id and exception class only.

## Live events: `nerosecurity.events`

`LinkEvent`s are player-scoped: one per recipient, never a broadcast.

**Recipients** are the installation's stored owners, plus its officers when the owner turned on
`/nerosecurity notify-officers`, **minus the player who caused the event**. Never anyone because they
are nearby.

**Which events go live:**

| Mapping | Event types |
| --- | --- |
| Row and notification | `laser_tripped`, `door_forced`, `chest_forced`, `device_forced`, `field_overloaded` (critical); `motion_detected`, `power_lost`, `field_collapsed` (warn); `lockdown_entered` (critical), `lockdown_exited`, `lockdown_auto_released` (info), `admin_unlock` (warn) |
| Row; notification only as `repeated_denied` | `door_denied`, `chest_denied`, `keycard_rejected` |
| Row only | `lockdown_confirmed`, `alarm_triggered`, `alarm_silenced`, `alarm_lockdown_suppressed` |
| Never live | everything else, for example `camera_member_seen`, `door_opened`, `chest_opened`, `chest_changed`, `log_read`, `audit_purged` and keycard bookkeeping |

These rows still appear in the `events` section, which reads the audit log.

A live row has the same shape as an `events` row plus `schema_version`. When the row is also a
notification it carries the reserved `_notify` object:

```json
"_notify": {
  "title": "Laser grid tripped",
  "body": "Orbital Lab (+3 more)",
  "deepLink": "nerolink://server/{server}/security/<installationId>"
}
```

The bridge substitutes its relay id for `{server}`. Caps: title 120, body 240, deep link 256
characters.

**Summary rows.** When a notification window closes with further events counted into it, each
recipient gets one row `{schema_version, asOf, type: "summary", at, installationId, installationName,
extra, _notify}` whose title is "Security events" and whose body is the installation name plus
"(+N more)". Only players who are still recipients when the window closes get it.

## Push behaviour

- **Cooldown and coalescing.** At most one notification per (recipient, installation) per
  `pushCooldownSeconds` (default 60), leading edge first. Later events in the window are counted, keeping
  the highest severity, and become one summary when the window closes (checked every second). Lockdown
  kinds (`lockdown_entered`, `lockdown_exited`, `lockdown_auto_released`, `admin_unlock`) bypass the
  window and leave it untouched.
- **Repeated denials.** `pushDeniedThreshold` (3) door, chest or keycard denials at one installation
  within `pushDeniedWindowSeconds` (60) produce one `repeated_denied` notification, once per window.
  Single denials are rows only.
- **No identity, no position.** `link/PushCopy` is the only builder of notification and alert text. Its
  inputs are an installation name, a push kind, a count and an installation id; it cannot receive a
  player, a player id, a device position or a dimension. The title is a fixed English phrase from the
  bundled language file; the body is the installation's name (cleaned again, 32 characters at most)
  plus "(+N more)". **Push text never names a player or a position.**
- **Order.** The live row with `_notify` is published first, then the alert is raised, so a NeroLink
  bridge that reads `_notify` sends one rich notification. An older bridge sends its generic text.
- Delivery (pairing, the player's notification category, whether the app is connected) is the NeroLink
  bridge's business, not NeroSecurity's.

## Alerts for offline owners

Every notification is also stored as a Core `LinkAlert` for the recipient, so an owner who was offline
sees it later.

- Alert id `nerosecurity.<installationId>.<kind>`: one alert per (installation, kind). A new emit
  replaces the old one, so its text refreshes "(+N more)" instead of piling up.
- Text: `"<phrase> — <installation name>"` plus "(+N more)". Same rules as the notification.
- Severity: from the kind (critical, warn or info, as in the table above).
- Alerts older than `privacyAuditRetentionDays` are dismissed at server start and on the purge cadence.
- `pushNotificationsEnabled = false` stops every live row, notification, alert and login summary; the
  sections and actions keep working.

**Login summary.** About two seconds after joining, a player with unread NeroSecurity alerts gets one
private line: `[NeroSecurity] 3 unread security alerts (2 installations). /nerosecurity alerts to
review.` Counts only. Gated by `loginAlertSummaryEnabled`, `pushNotificationsEnabled` and the module
switch.

**`/nerosecurity alerts`** lists the player's own unread alerts, newest first, with a short handle
(`<first 8 of the installation id>.<kind>`). `/nerosecurity alerts ack <handle>` acknowledges matching
alerts; `/nerosecurity alerts ack all` acknowledges every one. Nobody can see or touch another player's
alerts.

## Erasure

The module persists nothing of its own. Its in-memory state (notification windows, request ids, rate
limits, pending login summaries) is keyed by player and joins NeroSecurity's composite eraser as store
`link`. Alerts live in Core's alert store, which Core erases and backs up.

## Config summary

| Key | Default | Effect |
| --- | --- | --- |
| `linkModuleEnabled` | true | Register the module at all (restart). |
| `pushNotificationsEnabled` | true | Live rows, notifications, alerts, login summary. |
| `pushCooldownSeconds` | 60 | Notification window per recipient and installation (10–3600). |
| `pushDeniedThreshold` | 3 | Denials before one `repeated_denied` (1–50). |
| `pushDeniedWindowSeconds` | 60 | Denial counting window (10–3600). |
| `linkLockdownMode` | off | `off`, `release` or `full`; anything else reads as `off`. |
| `linkLockdownAllowOffline` | false | Remote lockdown while the owner is not in game. |
| `linkActionAckEnabled` | true | The `ack_event` action. |
| `linkActionSilenceEnabled` | true | The `silence_alarm` action. |
| `loginAlertSummaryEnabled` | true | The login line. |
