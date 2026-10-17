# Force Fields

A **Force Field Emitter** projects a wall or a hollow box of **force field** blocks. Permitted players
walk through them. Everyone and everything else is stopped.

```text
G G G     G = glass
I R I     I = iron ingot, R = block of redstone
I O I     O = observer
```

## Setting up

1. Place the emitter facing where you want the field, and link it to your hub with the Linking Tool.
2. **Use** the emitter to open **"Who can walk through this field?"** (OFFICER or higher).
3. Choose the level, the shape and the size, and give the emitter power.

The emitter projects once its energy buffer holds about 5 seconds of draw. A new emitter projects a
5 × 5 wall, 2 blocks in front of it, that MEMBERs and above may pass.

## Settings

- **Level ladder** and **Keycard only**: who may pass, exactly like a door. Only an OWNER can turn
  keycard-only on or off on a field, as with the OWNER level.
- **Field on**: switch the field off without unlinking the emitter.
- **Shape**: **wall** (flat, facing the same way as the emitter) or **shell** (a hollow box in front of
  the emitter).
- **Distance**: how far in front of the emitter the field starts.
- **Width**, **Height** and, for a shell, **Depth**: odd numbers, at least 1; a shell's depth is at
  least 3.
- **Upgrades**: four slots for Strength and Range Modules.

The screen refuses a shape that is too far away for the emitter's range (8 blocks, +4 per Range
Module, up to 24) or that has too many blocks (400 at most).

## Who gets through

- Players at or above the field's level pass, or with a keycard if the field is keycard-only.
- Owners always pass their own fields, keycard-only included, so a field can never seal its owner out.
- Mobs, projectiles, dropped items and vehicles never pass. A player riding something is stopped by
  what they are riding.
- During a lockdown only OWNER-level players pass.
- Field blocks cannot be mined. Only an owner of the installation (or an operator) can remove one by
  hand, and only in creative mode.
- Fields fill only air and replaceable blocks such as grass and snow (which do not come back). They are
  never placed where an entity is standing, and never inside land another player's claim protects. If
  something blocks part of the shape, the installation logs "Force field obstructed". A closed shell can
  still enclose someone who was already inside it.

## Power and strength

- An emitter's **strength** is 1 plus one per Strength Module, up to the server's `maxFieldStrength`
  (5 by default).
- It draws energy every tick: a base amount plus more for every block and every point of strength. More
  blocks and more strength cost more.
- If the energy runs out, the field holds for a second, then collapses ("Force field collapsed") and
  comes back once the buffer refills.

## Overload

A field is meant to slow a raid, not to stop one forever.

- Every hit from a player who may not pass, every projectile and every explosion adds **load** to the
  emitter and drains some of its energy. Load cools down over time.
- A stronger field holds more load.
- When the load is full, the whole field collapses ("Force field overloaded") and the emitter cannot
  project for 60 seconds. Breaking and re-placing the emitter does not cut that short.
- The emitter itself can always be broken, like any device.
- During a lockdown the field runs at the server's maximum strength, if the server allows it, for no
  extra power. A lockdown never resets the load.

A server can make fields unbreakable with `forceFieldBreachable = false`. That makes bases unraidable
and is not recommended. `forceFieldsEnabled = false` turns every emitter off; field blocks left in the
world then let everyone through and fade away.

## The raid-server preset

For PvP and raiding servers, these values make fields weaker and slower to recover. Every other setting
keeps its default. Put them in `config/nerosecurity.properties`.

| Key | Default | raid-server |
| --- | --- | --- |
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

## See also

- [Lockdown](Lockdown.md)
- [Blocks and Screens](Blocks-and-Screens.md)
- [Home](Home.md)
