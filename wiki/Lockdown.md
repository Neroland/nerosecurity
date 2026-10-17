# Lockdown

Lockdown hardens a whole installation with one action. While it is on, **every access check needs
OWNER**. Owners are never locked out, and an owner can always reach their own hub.

## Starting and clearing a lockdown

- **Start**: an OFFICER or OWNER presses **LOCKDOWN IS OFF — TURN ON** on the hub and confirms, or runs
  `/nerosecurity lockdown <installation> on`.
- **Clear**: an OWNER presses **LOCKDOWN IS ON — TURN OFF** and confirms, or runs
  `/nerosecurity lockdown <installation> off`.
- **Operators** can clear any lockdown with `/nerosecurity admin unlock <installation>`.

When an owner starts a lockdown by hand and an owner clears it again, the clearing owner records the
*lockdown drill* milestone for the optional quest pack. Alarm lockdowns and the companion app never
count.

## What changes during a lockdown

- **Doors** seal. Redstone no longer opens them. Only OWNER-level access opens them.
- **Secure chests** open only for OWNER-level access, and automation (hoppers, pipes) is blocked.
- **Force fields** let only OWNER-level players through. If the server allows it
  (`fieldLockdownRaisesToMax`, on by default), fields also run at the server's maximum strength without
  using more power. A lockdown never resets a field's overload meter.
- **Alarms** are all armed, even silenced ones.
- **Sensors** treat everyone below OWNER as an intruder, so members trip lasers and cameras too.
- **Settings screens and the hub** follow the same rule: only OWNER-level access changes anything.

"OWNER-level access" means the installation's owners and server operators. An OWNER keycard for this
installation still opens doors, chests and fields during a lockdown, but it never opens the hub or
changes a setting. See [Keycards](Keycards.md).

## Alarm lockdowns

An owner can tick **Trigger lockdown** on an alarm. That alarm can then start a lockdown by itself.

- Only an owner can turn this on or off, and the server can forbid it (`alarmLockdownEnabled`).
- It only reacts to critical events: Laser grid tripped, Door forced, Chest forced, Device broken and
  Force field overloaded (a server can lower this with `alarmLockdownMinSeverity = warn`).
- An alarm lockdown **clears itself after 5 minutes** unless an owner confirms it. The hub shows
  **LOCKDOWN (ALARM) — CONFIRM**; confirming keeps the lockdown on until an owner clears it.
- After an alarm lockdown ends, alarms cannot start another one on that installation for 10 minutes.
  A blocked attempt is logged as "Alarm lockdown suppressed (cooldown)".
- A running alarm lockdown is never extended.

## From the companion app

Changing lockdown from the NeroLink companion app is **off by default**. A server can allow it with
`linkLockdownMode`: `release` lets an owner clear a lockdown from the app, `full` also lets them start
one. See [NeroLink App and Notifications](Companion-App.md) for the trade-off.

## Server switches

- `lockdownEnabled = false` turns lockdown off on the server. No lockdown can start, and any stored
  lockdown is ignored but kept, so turning the switch back on restores it.
- Entering and leaving a lockdown is announced on Neroland Core's threshold channel
  `nerosecurity:lockdown`, so quest packs can react. The announcement carries an opaque installation id
  only, never a player, a name or a position. Turn it off with `lockdownChannelEnabled = false`.

## See also

- [Access Levels](Access-Levels.md)
- [Sensors and Alarms](Sensors-and-Alarms.md)
- [Home](Home.md)
