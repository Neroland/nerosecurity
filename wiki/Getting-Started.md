# Getting Started

This page takes you from nothing to a door that only your members can open, in five steps. You need
iron, redstone and a few vanilla parts. No other mod is required.

## 1. Place a Security Hub

Craft a **Security Hub**:

```text
I O I     I = iron ingot
I R I     O = observer
I I I     R = block of redstone
```

Place it. The hub registers a new **installation** that you own, and chat says
`Installation registered: Installation 1a2b3c4d`. The name is "Installation" plus the first eight
characters of its id. As the owner you can rename it on the hub screen with the pencil button next to
the title.

An installation is everything one hub controls: its owners, its members, its linked devices and its
lockdown state. A server allows each player a limited number of hubs (8 by default).

## 2. Point a Linking Tool at the hub

Craft a **Linking Tool**:

```text
. R       R = redstone
. I       I = iron ingot
S .       S = stick, . = empty
```

Sneak and use it on the hub. The action bar says `Linking tool set to <installation>`. The tool now
remembers that installation. You need to be an OFFICER or the OWNER of the installation to do this.

## 3. Place a Security Door

Craft a **Security Door** and place it. It is two blocks tall.

```text
I R I     I = iron ingot
I D I     R = redstone
I I I     D = iron door
```

A door that is not linked to any installation is a public door: anyone can open it.

## 4. Link the door

Use the Linking Tool (without sneaking) on the door. The action bar says `Linked to <installation>`.

- The device must be in the same dimension as the hub and within 128 blocks of it (counted as the sum
  of the X, Y and Z distances). Servers can change this limit.
- To unlink a device, sneak and use the Linking Tool on it.

## 5. Choose who may open it

Sneak and use the door. The screen **"Who can open this door?"** opens. Pick a level on the ladder:
PUBLIC, MEMBER, OFFICER or OWNER. A new door starts at MEMBER, so right now only you can open it.

To let a friend in, open the hub (use it), press **+ Add**, type their name, choose MEMBER or OFFICER
and press **Add**. They must be online when you add them. They can now open every members-only device
of this installation.

## What next

- [Access Levels](Access-Levels.md) explains what each level may do.
- [Keycards](Keycards.md) lets you hand out access without adding people to the member list.
- [Doors and Chests](Doors-and-Chests.md) covers power, redstone, modules and secure storage.
- [Sensors and Alarms](Sensors-and-Alarms.md) and [Lockdown](Lockdown.md) cover the rest of a defence.
- [Blocks and Screens](Blocks-and-Screens.md) lists every block, item, recipe and screen.

## See also

- [Home](Home.md)
