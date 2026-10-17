# Privacy

This page explains, in plain words, what NeroSecurity lets a server know about you, how long it keeps
it, and what you can do about it. The full notice is
[PRIVACY.md](https://github.com/Neroland/nerosecurity/blob/main/PRIVACY.md).

## What a server keeps

NeroSecurity records **who did what, at which security device, and when**, for each installation. It
exists so an owner can protect their base and find out when someone breaks in.

It keeps:

- **Owners and members** of each installation, as player ids with a level. No names, no join dates.
- **Device positions** of the owner's own blocks. Never your position.
- **Lockdown state**, as on/off flags and times, not who started it.
- **The keycard list.** A bound card's row holds the one player allowed to use it. The card item itself
  never holds a player's name or id.
- **The audit log**: one row per security event, with the event type, the time, the device and, where
  a player acted, that player's id.
- **Your opt-out choice**, only if you opted out.
- **Your export file**, only if you asked for one.

It never keeps:

- **Images or video.** A camera is a presence sensor. There are no camera feeds.
- **Chat, inventories or chest contents.** A chest event says that someone opened or changed it, never
  what was inside.
- **Where you walk.** Only the position of the device that saw you is recorded.

## Who can see it

- **Officers and owners** of an installation can read its audit log with `/nerosecurity log`. Every
  read is itself logged, with the reader's name.
- **Alarm messages, monitors and phone notifications never name anyone.** They say what happened and
  where (which installation), not who.
- **Members are not logged by cameras or lasers**, unless the server sets `privacyCameraLogsMembers`,
  or the installation is in lockdown. During a lockdown everyone below OWNER is logged like an intruder.
- **Server operators** are never logged by sensors.
- **Pets are checked, never named.** A tamed animal gets its owner's access, but if it trips a sensor the
  event records no player at all.

## The recording notice

The first time in a session that you enter an area an installation is monitoring, you see:
`Security notice: this area is monitored by "<installation>". Presence and access events may be logged.`
If a sensor is about to log you before you have seen it, you see it first. It cannot be switched off
while anything is logged.

## How long it is kept

| Data | Kept by default | Setting |
| --- | --- | --- |
| Audit log entries | 7 days | `privacyAuditRetentionDays` |
| Presence entries (motion detected, member seen by camera) | 60 minutes | `privacyCameraEventRetentionMinutes` |
| Entries per installation | 500, oldest out first | `privacyAuditMaxEntriesPerInstallation` |
| Anything logged, whatever the settings | never more than 90 days | `privacyRetentionHardCapDays` (can only be lowered) |
| Your export file | 7 days | fixed |
| Installations, members, keycards, your opt-out | while they exist, until you ask to be erased | — |

Old entries are deleted when the server starts, every hour by default, and before every log read.

## Your choices

You can do all of this yourself, in game, without asking anyone. The same buttons are on the
**Privacy** tab of any security hub you can open.

- **Stop being named**: `/nerosecurity privacy optout` or **Stop naming me**. From then on the log
  records you as "anonymous" in new rows. Rows written before you opted out keep your name until they
  expire (7 days by default) or until you ask for erasure. The event itself is still recorded: a tripped
  laser is still a tripped laser. You stay on the member lists you belong to, and your access does not
  change. One exception: if you are an officer and read an installation's log, that log read always
  names you, because it is how owners see who looked.
- **Be named again**: `/nerosecurity privacy optin` or **Name me again**. This applies to new rows;
  rows recorded as anonymous stay anonymous.
- **Check**: `/nerosecurity privacy status` (or just `/nerosecurity privacy`) says which applies.
- **Get a copy**: `/nerosecurity privacy export` or **Export my data**. The server writes one file with
  everything NeroSecurity keeps about you and tells you its path in chat; ask the server operator for
  it. It never contains another player's id or name. You can export once a minute by default
  (`privacyExportCooldownSeconds`).
- **Be erased**: `/neroland data eraseme`. This is Neroland Core's command and covers every Neroland mod
  on the server. The hub's **Erase my data…** button asks you to confirm and then fills the command in
  for you.

When you are erased, NeroSecurity removes your member rows, your ownership, keycard rows bound to you
(those cards stop working), every log row that names you, your opt-out row, your export file, your
stored alerts and its record of which notices you have seen. A base you owned alone is not deleted:
it passes to an anonymous placeholder owner that grants nothing, and is marked orphaned so an operator
can remove it. If the base had other owners, they keep it. The backup copy is refreshed straight away,
so erased rows cannot come back from it.

## Phone notifications

If the server runs the NeroLink mod and you have paired the NeroLink companion app and switched its
**Security** category on, you can get notifications about installations you own (or are an officer of,
if the owner chose that). A notification has two lines, what happened and which installation, for
example "Laser grid tripped" / "Orbital Lab (+3 more)". It never contains a player's name or id or a
position. It passes through the NeroLink relay and your phone's push service, which see those two lines
while delivering it. See [NeroLink App and Notifications](Companion-App.md).

## What server owners can switch off

| Setting | Effect when `false` |
| --- | --- |
| `camerasEnabled` | Every camera is dark and records nothing. |
| `auditLogEnabled` | Nothing is written to the audit log. |
| `forceFieldsEnabled` | No force field projects. |
| `lockdownEnabled` | Lockdown cannot start; stored lockdowns are ignored. |
| `pushNotificationsEnabled` | No phone notifications, stored alerts or login summaries. |

## Crash reports

NeroSecurity sends anonymous crash reports (the error, version numbers and the list of loaded mods)
to its developers through Sentry, on EU servers. They never contain your IP address, username, player
id or world data. To opt out, set `telemetryEnabled=false` in `config/nerosecurity.properties`; it takes
effect on restart.

## Questions

Email [info@neroland.co.za](mailto:info@neroland.co.za). For data on a particular server, that server's
operator is responsible.

## See also

- [Commands](Commands.md)
- [Config](Config.md)
- [Home](Home.md)
