# Sensors and Alarms

Sensors notice people. Alarms react. Both report to the installation they are linked to, and a sensor
that is not linked senses nothing.

**Detection is not permission.** A sensor only asks the same question a door asks: is this player
allowed here? Permitted people pass without anything being logged. Everyone else raises an event.

## Laser Grid Node

```text
I I I     I = iron ingot
I G I     G = glass pane
I T I     T = redstone torch
```

- Place two nodes **facing each other**, both linked to the same installation, with nothing solid
  between them. They pair and draw a beam. A node of another installation, or an unlinked one, blocks
  the beam instead of pairing.
- Pairing range is 8 blocks. Each Range Module adds 4, up to 32 (server settings `laserBaseRange`,
  `laserMaxRange`). A node has two module slots.
- When something that is not permitted crosses the beam, the installation logs "Laser grid tripped".
  The same entity trips the same node at most once every 5 seconds.
- Servers can make a trip hurt (`laserTripDamage`, 0 by default). It never hurts the installation's
  owners or members, or their pets.
- Nodes need power. An unpowered, unlinked or unpaired node is not armed. The lens lights up when it is
  armed.
- **Use** a node to open its settings (OFFICER or higher).

## Security Camera

```text
I I I     I = iron ingot
R G R     R = redstone, G = glass pane
. O .     O = observer, . = empty
```

- The camera watches a cone in front of it and pans slowly. It sees 16 blocks; each Range Module adds 4,
  up to 32. It has one module slot.
- When it sees something that is not permitted, the installation logs "Motion detected". The same
  entity is reported by the same camera at most once every 10 seconds.
- **It never records images.** It only notes that something came into view.
- Members are not logged. A server can change that with `privacyCameraLogsMembers`; the hub's Privacy
  tab then shows "Members ARE logged on this server" in amber.
- Cameras need power. A server can turn all cameras off with `camerasEnabled = false`.
- **Use** a camera to see whether it is armed. **Sneak and use** it to open its settings (OFFICER or
  higher).

## Sensor settings

Lasers and cameras share one screen, **"Who may cross this beam?"** or **"Who may pass this camera?"**.

- **Level ladder**: anyone below this level raises an event. New sensors start at MEMBER.
- **Also sense**: players only, players and hostile mobs (the default), or every living thing. Players
  are always checked. Tamed animals get their owner's access but are never logged by name. Items, experience orbs, projectiles, empty
  vehicles and spectators never trip anything.
- **Field of view** (cameras only): 60°, 90° or 120°.
- **Upgrades**: Range Modules.
- The status line says whether the sensor is armed and, if not, why.

Server operators are never logged by sensors. During a lockdown everyone below OWNER counts as an
intruder, so members can trip sensors too.

## Recording notice

The first time in a session that you walk into an area an installation is monitoring, you get one line:
`Security notice: this area is monitored by "<installation>". Presence and access events may be logged.`
The area covers everything the installation's devices can reach, plus a margin. If a sensor is about to
log you before you have seen the notice, you get the notice first. Owners and members get it too. It
cannot be switched off while anything is being logged.

## Alarm

```text
I R I     I = iron ingot
I N I     R = redstone
I I I     N = note block
```

The alarm needs no power, so it works on a server without a power mod. **Sneak and use** it to open
**"What should this alarm do?"** (OFFICER or higher).

**When this happens** (all ticked on a new alarm):

- Breaches: Laser grid tripped, Motion detected, Door forced, Chest forced, Device broken, Force field
  attacked, Force field overloaded.
- Denied access: Door access denied, Chest access denied, Keycard rejected.
- System: Power lost.

**Do this:**

- **Redstone pulse**: a 2-second redstone signal.
- **Sound**.
- **Light**: the alarm's beacon turns on for 10 seconds.
- **Message owner/officers**: one chat line to the installation's online owners, and to officers if an
  owner turned that on with `/nerosecurity notify-officers <installation> on`. The message names the
  installation and what happened, never who. Several alarms firing together send one message.
- **Trigger lockdown**: off by default. Only an owner can turn it on or off, and the server can forbid
  it. While it is on, only an owner can change the alarm's settings. See [Lockdown](Lockdown.md).

Events that arrive within 10 seconds of a firing are counted into it and reported as "(+N more)".

**Silence**: an officer can silence an alarm with the **Silence 10 min** button (at most 60 minutes,
`alarmSilenceMaxMinutes`). A lockdown overrides the silence: every alarm counts as armed while its
installation is locked down.

## Security Monitor

```text
I I I     I = iron ingot
I G I     G = glass pane
I R I     R = redstone
```

**Use** a linked monitor (MEMBER or higher) to see the installation's cameras and laser grids with
their status, and its recent events as sentences such as "Laser grid tripped — a player at 15, 65, -3,
2 min ago". The monitor never names who caused an event: it says "a player", whoever it was
and whether or not they chose not to be named. It never shows pictures. Press **Refresh** to update it.

## The audit log

Each installation keeps a capped log of security events: when, what kind, which device and, where a
player was involved, who.

- `/nerosecurity log <installation> [count]` shows the newest entries (OFFICER; 20 by default, at most
  100). Every read is itself logged.
- By default entries are kept for 7 days, at most 500 per installation, and presence entries (motion,
  members seen by a camera) for 60 minutes. Servers cannot keep anything longer than 90 days.
- A server can turn the log off with `auditLogEnabled = false`. Events still drive alarms.

## See also

- [Lockdown](Lockdown.md)
- [Blocks and Screens](Blocks-and-Screens.md)
- [Home](Home.md)
