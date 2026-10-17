# Doors and Chests

## Security Door and Blast Door

The **Security Door** is a powered door two blocks tall and one wide. The **Blast Door** is two by two,
with heavier plating, and follows the same rules. Both open only for the access level you set.

Recipes:

```text
Security Door         Blast Door
I R I                 B O B
I D I                 S O S
I I I                 B O B

I = iron ingot, R = redstone, D = iron door,
B = block of iron, O = obsidian, S = Security Door
```

### Using a door

- **Use** the door to open it, and use it again to close it. Anyone allowed to open it may close it.
- An open door closes itself after 3 seconds by default (`doorAutoCloseTicks`, 0 keeps it open).
- The door is only passable once it is fully open.
- An unlinked door is public: anyone can open and close it.
- When you are refused, the door flashes red, plays a short sound and the action bar says why.

### Settings

**Sneak and use** the door to open **"Who can open this door?"** (OFFICER or higher).

- **Level ladder**: the lowest level that opens the door. New doors start at MEMBER.
- **Keycard only**: ignore membership; only a keycard at this level or higher opens it.
- **Opens while powered by redstone**: shown only when the server allows door redstone. A powered door
  opens for anyone who can power it, so the screen warns you. Redstone never opens a door during a
  lockdown. Doors never emit redstone.
- **Power** bar and status lines: linked or not, sealed by lockdown, unpowered.
- **Upgrades**: two slots for Hardness and Open-Speed Modules.

### Power

Doors store energy from Neroland Core's power system (or any compatible power mod). On NeoForge and
Fabric every part of the door takes power; on Forge only the
controller block does (the bottom block, or the bottom-left block of a blast door). A linked, closed door draws a little energy each tick, and each opening costs a bit more.

By default doors **work without power** (`doorsRequirePower = false`), which suits servers with no
power mod. When the server requires power, an unpowered door opens only for its owners and operators.

### Modules

- **Hardness Module**: the door is harder to break and more blast resistant. Each module makes breaking
  slower. Blast resistance is capped by the server (`doorMaxBlastResistance`); nothing makes a door
  unbreakable.
- **Open-Speed Module**: the door opens and closes faster.

### Breaking

Anyone can break a door. That keeps bases raidable. If the person breaking it could not have opened it,
the installation logs "Door forced", which alarms and the companion app can react to.

A wither or the ender dragon cannot destroy a door, chest, hub or any other NeroSecurity block.
Raiding means mining or overloading, not summoning a boss.

### During lockdown

A door of an installation in lockdown is sealed: only OWNER-level access opens it. See
[Lockdown](Lockdown.md).

## Secure Chest

The **Secure Chest** has 27 slots and checks access when you open it.

```text
I I I     I = iron ingot
I C I     C = chest
I I I
```

- **Use** to open it. You need the chest's level (MEMBER by default), or a keycard when it is
  keycard-only. An unlinked secure chest behaves like a plain chest.
- Access is checked again every tick while the chest is open, so a lockdown or a revoked card closes
  the screen straight away.
- **Sneak and use** to open **"Who can open this chest?"** (OFFICER or higher): the level ladder, the
  keycard-only switch, and the automation line.
- The chest has no comparator output, so its fill level stays private.
- Anyone can break it. The contents always drop. A break by someone who could not open it is logged as
  "Chest forced".
- Opening a chest and changing its contents are logged, without the items.

### Automation

Hoppers, droppers and pipes count as **automation**. Each installation has one automation policy, set
by an owner:

`/nerosecurity automation <installation> off|member|officer`

Automation acts as a player of that level. It can reach a chest only when:

- the policy is not `off`,
- the policy is at least the chest's level,
- the chest is not keycard-only, and
- the installation is not in lockdown.

New installations start with the server's default policy (`chestAutomationDefault`, `off` unless the
server changed it). The chest screen shows whether automation is allowed and, if not, why.

## See also

- [Keycards](Keycards.md)
- [Blocks and Screens](Blocks-and-Screens.md)
- [Home](Home.md)
