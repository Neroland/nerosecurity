# Access Levels

Every NeroSecurity device asks one question: *is this player's level high enough?* This page explains
the levels, who has which level, and how the answer is worked out.

## The four levels

| Level | Meaning |
| --- | --- |
| PUBLIC | anyone |
| MEMBER | can open members-only devices |
| OFFICER | can manage members, link devices and read the log |
| OWNER | full control, including lockdown release |

Server operators also get **ADMIN**, shown as a white pill: a server operator override. It is not a
level you can give anyone. No member row, keycard or command can carry it.

## Who has which level

- **Owners** are set by placing the hub. The player who placed it is the owner. Owners are never added,
  promoted or removed on the hub screen.
- **Members and officers** are added at the hub: **+ Add**, type the name, choose MEMBER or OFFICER.
  The player must be online.
- **Keycards** give their holder the level written on the card, at that card's installation only. See
  [Keycards](Keycards.md).
- **Bound organisations** (optional mods, see below) can give their members MEMBER or OFFICER.
- Everybody else is PUBLIC.

Your level is the highest one any of these gives you.

## Who may manage whom

You can only manage players below your own level, and only give levels below your own. So an OFFICER
can add, promote and remove MEMBERs, and only an OWNER (or an operator) manages OFFICERs. Nobody can
make someone an OWNER at the hub, and nobody can change their own level.

**Remove** takes a member off the list. They lose access to every members-only device here.

## What each level may do

| Action | Lowest level |
| --- | --- |
| Open a door or chest set to MEMBER, read a Security Monitor, look at a Keycard Programmer | MEMBER |
| Copy and wipe keycards | MEMBER |
| Open the hub screen, add or change members below you | OFFICER |
| Link and unlink devices with the Linking Tool | OFFICER |
| Change a device's settings | OFFICER |
| Write keycards | OFFICER |
| Read the audit log and the keycard list | OFFICER |
| Start a lockdown | OFFICER |
| Silence an alarm | OFFICER |
| Rename or dissolve the installation | OWNER |
| Clear a lockdown | OWNER |
| Revoke keycards, set the chest automation policy | OWNER |
| Turn an alarm's lockdown response on or off | OWNER |
| Switch a force field to keycard-only (or back) | OWNER |
| Let officers receive alarm messages | OWNER |
| Bind a faction, colony or station | OWNER |

Each device also has its own minimum level, which you set on its screen. Setting a device to OWNER,
or changing a device that is already at OWNER, needs an OWNER.

## How a decision is made

The server decides every time, in this order. Nothing is remembered on your client.

1. A server operator is always allowed.
2. Every source of access is asked: the member list, any keycard in your hands, and any bound
   organisation. A keycard only counts for getting through doors, chests, fields and sensors. A land
   claim can also refuse.
3. **Any refusal wins.** If one source refuses you, you are refused, whatever the others say.
4. Otherwise your level is the highest one any source gave you.
5. While the installation is in **lockdown**, every action needs OWNER. See [Lockdown](Lockdown.md).
6. You are allowed when your level reaches what the device or action needs.

When you are refused you always get three things: a short sound, a red flash on the device, and a
line on the action bar that says what was needed and what you have, for example
`Needs OFFICER — you are MEMBER`.

## Optional mods

Each of these works only when that mod is installed and the server has not switched the integration
off. NeroSecurity works fully without any of them.

- **NeroFactions.** An owner can bind the installation to a faction they belong to. Faction members
  then get a level from their reputation tier: Associate gives PUBLIC, Member gives MEMBER, Trusted and
  Inner Circle give OFFICER.
- **NeroColonies.** An owner can bind to a colony they belong to. Allies get MEMBER; Chiefs and the
  colony's owner get OFFICER. Strangers and enemies get nothing.
- **Nerospace.** An owner can bind to a station they own. Whoever owns that station at the time gets
  OFFICER.
- **Open Parties and Claims.** A claim can refuse access on claimed land. It never grants access. A
  claim only refuses using a block on claimed land: hub-level actions such as lockdown, members,
  renaming and the card list are never blocked by a claim over the hub's chunk.

A faction or colony binding has a maximum level: MEMBER unless the owner asks for OFFICER. No binding
can ever give OWNER. A faction or colony binding is suspended while none of the installation's owners
belongs to that faction or colony any more.

Binding commands (owners only, and you must belong to the faction or colony, or own the station):

- `/nerosecurity bind <installation>` shows the current bindings.
- `/nerosecurity bind <installation> faction <faction> [member|officer]`
- `/nerosecurity bind <installation> colony here|<colony> [member|officer]` (`here` is the colony you
  are standing in)
- `/nerosecurity bind <installation> station <station>`
- `/nerosecurity unbind <installation> faction|colony|station`

`<installation>` is the installation's id (the first eight characters are enough) or its exact name,
in quotes if it contains spaces. `/nerosecurity installations` lists yours.

## See also

- [Getting Started](Getting-Started.md)
- [Home](Home.md)
