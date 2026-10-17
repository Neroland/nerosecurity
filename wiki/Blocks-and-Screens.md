# Blocks and Screens

Every NeroSecurity block and item, what its tooltip says, how it is made, and what each screen shows.
All recipes use vanilla materials.

## Blocks

| Block | Tooltip | Use | Sneak-use |
| --- | --- | --- | --- |
| Security Hub | Controls one installation: who is a member, what is linked, lockdown. | Opens the hub screen (OFFICER) | — (an owner breaks it while sneaking to dissolve the installation) |
| Keycard Programmer | Writes, copies and wipes keycards for the installation it is linked to. | Opens the programmer (MEMBER) | — |
| Security Door | A powered 1×2 door that opens only for the access level you set. | Opens or closes | Opens its settings (OFFICER) |
| Blast Door | A powered 2×2 door with heavier plating. Same rules as the security door. | Opens or closes | Opens its settings (OFFICER) |
| Secure Chest | A chest that checks access on open and only lets automation in when the installation allows it. | Opens the chest | Opens its settings (OFFICER) |
| Laser Grid Node | Pairs with a facing node and raises an alarm event when someone not permitted crosses the beam. Needs power. | Opens its settings (OFFICER) | — |
| Security Camera | Watches a cone in front of it and raises an alarm event for anyone not permitted. Senses presence only; it never records images. | Says whether it is armed | Opens its settings (OFFICER) |
| Security Monitor | Shows the installation's cameras, lasers and recent events as text. | Opens the monitor (MEMBER) | — |
| Alarm | Reacts to security events: redstone, sound, light, a chat message — and, if the owner allows it, lockdown. | — | Opens its settings (OFFICER) |
| Force Field Emitter | Projects a wall or shell of field blocks that permitted players walk through and everyone else cannot. Needs power; overloads under fire. | Opens its settings (OFFICER) | — |
| Force Field | Solid to anyone not permitted, to mobs, projectiles and vehicles — mounted players included. | Placed only by an emitter | — |

The levels in brackets are the lowest level that may do it, at the device's own installation. A device
that is not linked to an installation has no owner: doors and chests are public, sensors and emitters do
nothing.

Every device except the hub and the force field can be broken by anyone. Breaking a device you could
not use is logged as forced.

## Items

| Item | Tooltip |
| --- | --- |
| Linking Tool | Sneak-use on a hub to select it, then use on a device to link it. |
| Keycard | Blank — encode it at a keycard programmer. (A written card shows its level, serial and whether it is bound; an encrypted one only says it is encrypted.) |
| Encryption Module | Fit to a keycard programmer to write and copy encrypted keycards (owner only). |
| Hardness Module | Fit to a door: harder to break and more blast resistant. |
| Open-Speed Module | Fit to a door: opens and closes faster. |
| Range Module | Fit to a laser node or camera: +4 blocks of range. It also adds 4 blocks of range to a force field emitter. |
| Strength Module | Fit to an emitter: +1 strength (more load before overload, more power). |

## Recipes

All are shaped crafting recipes except the keycard. `.` is an empty slot.

| Result | Pattern | Key |
| --- | --- | --- |
| Security Hub | `IOI` `IRI` `III` | I iron ingot, O observer, R block of redstone |
| Linking Tool | `.R` `.I` `S.` | R redstone, I iron ingot, S stick |
| Keycard ×2 | shapeless | 2 paper, 1 iron nugget |
| Keycard Programmer | `ICI` `IRI` `III` | I iron ingot, C comparator, R redstone |
| Security Door | `IRI` `IDI` `III` | I iron ingot, R redstone, D iron door |
| Blast Door | `BOB` `SOS` `BOB` | B block of iron, O obsidian, S Security Door |
| Secure Chest | `III` `ICI` `III` | I iron ingot, C chest |
| Laser Grid Node | `III` `IGI` `ITI` | I iron ingot, G glass pane, T redstone torch |
| Security Camera | `III` `RGR` `.O.` | I iron ingot, R redstone, G glass pane, O observer |
| Security Monitor | `III` `IGI` `IRI` | I iron ingot, G glass pane, R redstone |
| Alarm | `IRI` `INI` `III` | I iron ingot, R redstone, N note block |
| Force Field Emitter | `GGG` `IRI` `IOI` | G glass, I iron ingot, R block of redstone, O observer |
| Encryption Module | `NXN` `XCX` `NXN` | N iron nugget, X redstone, C ender pearl |
| Hardness Module | `NXN` `XCX` `NXN` | N iron nugget, X iron ingot, C obsidian |
| Open-Speed Module | `NXN` `XCX` `NXN` | N iron nugget, X redstone, C piston |
| Range Module | `NRN` `RCR` `NRN` | N iron nugget, R redstone, C spyglass |
| Strength Module | `NON` `ODO` `NON` | N iron nugget, O obsidian, D diamond |

## Screens

Every screen has a one-line title and a subtitle that says what it is for. Buttons you cannot use are
greyed out, and hovering over them says why. The bottom line of each screen shows what just happened,
or why the server refused.

### Security Hub: Overview

The title is the installation's name; an owner can rename it with the pencil button. The subtitle
reads "Who can get in, what is linked, and what happened." **STATUS** shows whether the installation
is normal or in lockdown, how many devices are linked and how many are working, and your own level
with what it means. **LOCKDOWN** holds one large button: "LOCKDOWN IS OFF — TURN ON" or "LOCKDOWN IS
ON — TURN OFF", with "Seals doors, fields to max, only the OWNER gets in." beneath it. Every press asks
you to confirm. After an alarm lockdown an owner sees "LOCKDOWN (ALARM) — CONFIRM" and a smaller
"Clear lockdown" button. **MEMBERS** lists owners first, then members with their level; **+ Add**,
**▲ Promote**, **▼ Demote** and **– Remove** manage them. **DEVICES** lists every linked device with
its position and state (unpowered, unpaired, idle, not loaded, sealed); to unlink one, sneak-use the
Linking Tool on it. **LAST 5 EVENTS** shows the newest events as sentences, in the same words as the
monitor. Reading the hub is not logged as a log read.

### Security Hub: Privacy

Titled "Privacy — what *installation* records", with the subtitle "Who did what, where and when. Never
items, chat or pictures." **WHAT IS KEPT** shows how long the audit log keeps entries on this server,
whether cameras are on and whether they log members (in amber when they do), who can read the log
("OFFICER and above. Every read is itself logged.") and the area notice. **YOUR DATA ON THIS SERVER**
has three buttons: **Export my data**, **Stop naming me** (or **Name me again**) and **Erase my data…**.
Erase asks you to confirm, then opens chat with `/neroland data eraseme` filled in; you press Enter to
run it. See [Privacy](Privacy.md).

### Keycard Programmer

Titled "Write a keycard", with the subtitle "Pick a level, press Write. The card opens whatever allows
that level." On the left are the **Card**, **Blank** and **Module** slots. On the right is the level
ladder with the meaning of the chosen level, the **Encrypted** and **Bind to me** switches, and a
coloured band that previews the card you are about to write. Below are **Write**, **Copy** and
**Wipe**, and the count "Cards issued: n / 512". See [Keycards](Keycards.md).

### Security Door and Blast Door

Titled "Who can open this door?", with the subtitle "Anyone at this level or higher. Lockdown always
needs the OWNER." It has the level ladder and its meaning, **Keycard only** ("Ignore membership: only a
keycard at this level opens it. You can always sneak-use it to turn this off."), **Opens while powered
by redstone** when the server allows it, the power bar, and status lines: which installation it is
linked to (or "Not linked to any installation — anyone can use it."), and whether it is sealed by
lockdown or unpowered. The **UPGRADES** box holds the Hardness and Open-Speed Modules. Changes save at
once.

### Secure Chest

Titled "Who can open this chest?", with the subtitle "Hoppers and pipes count as 'automation' and
follow the installation's rule." It has the level ladder, **Keycard only**, and the line "Automation:
off / MEMBER / OFFICER" with a dot that shows whether automation can reach this chest and why not:
"off for this installation", "this chest's level is above the automation level", "keycard-only chests
never allow automation" or "blocked during lockdown". It also shows the command an owner uses to change
the policy.

### Sensor settings (Laser Grid Node and Security Camera)

Titled "Who may cross this beam?" for a laser and "Who may pass this camera?" for a camera, with the
subtitle "Anyone below this level raises an alarm event. Permitted people are not logged." It has the
level ladder, **Also sense** (players only, players and hostile mobs, every living thing), **Field of
view** (60°, 90°, 120°, cameras only), the power bar, the **UPGRADES** box for Range Modules, and a
status line: "Armed — recording active", "Not armed — no power", "No facing node within range" or "Not
linked — senses nothing". When the server has switched cameras off, an amber line says so.

### Alarm

Titled "What should this alarm do?", with the subtitle "Tick what it listens for on the left and what
it does on the right." The left column, **WHEN THIS HAPPENS**, lists the events in three groups:
Breaches, Denied access and System. The right column, **DO THIS**, lists Redstone pulse, Sound, Light,
Message owner/officers and Trigger lockdown. Trigger lockdown is greyed out unless you are the owner,
and a warning explains that an alarm lockdown locks out everyone but the owner for a few minutes, then
clears itself. At the bottom are **Silence 10 min**, the time left on a silence, and whether the alarm
is armed. Ticks save at once.

### Security Monitor

The title is the installation's name and its status. The subtitle reads "Sensor status and recent
events, as text. No pictures are ever kept." The left half lists the installation's cameras and laser
grids with their state; the right half lists recent events as sentences such as "Laser grid tripped — a
player at 15, 65, -3, 2 min ago". It never names who. **Refresh** updates it. Amber lines appear when
the server has switched cameras or the audit log off.

### Force Field Emitter

Titled "Who can walk through this field?", with the subtitle "Permitted people pass. Everyone and
everything else is stopped." It has the level ladder, **Field on**, **Keycard only**, **Shape** (wall or
shell), and steppers for **Distance**, **Width**, **Height** and, for a shell, **Depth**. Below are the
power bar, the **Load** bar (how close the field is to overloading), the **UPGRADES** box for Strength
and Range Modules, and a status line such as "Projecting — strength 2/5, 25/25 blocks, 12/t, load 18%",
"No power", "Overloaded (42 s)" or "Idle". During a lockdown it reads "Projecting at maximum —
lockdown". If the shape does not fit, an amber line says why, for example "Too large for this emitter's
range (12)."

## See also

- [Getting Started](Getting-Started.md)
- [Home](Home.md)
