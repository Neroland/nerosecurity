# Keycards

A keycard lets you hand out access without adding someone to the member list. The card carries a
level for one installation. Hold it in either hand and use a door, chest, force field or sensor of
that installation, and you count as having that level there.

A card opens doors, chests, fields and sensors. It never manages the installation.

## Making cards

Craft blank cards: two paper and one iron nugget make **two blank keycards** (shapeless). A blank card
opens nothing.

Cards are written at a **Keycard Programmer**:

```text
I C I     I = iron ingot
I R I     C = comparator
I I I     R = redstone
```

Place the programmer and link it to your hub with the Linking Tool, like any device. Use it to open
the **"Write a keycard"** screen. Any MEMBER on the member list can look at it (a card does not
count here); each button checks your level again.

The programmer has three slots:

- **Card**: the card to write, wipe or copy from.
- **Blank**: the blank card a copy is written onto.
- **Module**: one Encryption Module.

## Writing, copying and wiping

- **Write.** Put a blank card in the Card slot, pick a level on the ladder and press **Write**. You need
  OFFICER or higher, and you can only write cards below your own level. An OWNER can also write OWNER
  cards.
- **Encrypted.** Tick it to write an encrypted card. This needs an OWNER and an Encryption Module in the
  Module slot. By default any card above OFFICER must be encrypted (the server setting
  `keycardBasicMaxLevel`).
- **Bind to me.** Only you can use a bound card. Who it is bound to is kept on the server, never on the
  card.
- **Copy.** Put a written card in the Card slot and a blank in the Blank slot, then press **Copy**. You
  can copy cards of your own level or of any level you could write. A copy gets its own serial number
  and is never bound. Encrypted cards can only be copied by an OWNER at a programmer with an Encryption
  Module. A card from another installation cannot be copied here.
- **Wipe.** Turns the card in the Card slot back into a blank. If the card belongs to this installation
  it is also removed from the card list, so it stops working at once.

The screen shows **Cards issued: n / 512**. Each installation can have a limited number of live cards
(512 by default). When the list is full, nothing more can be written or copied until cards are
revoked; nothing is removed for you. Once the list is more than three quarters full, only officers and
owners can copy cards.

## Using a card

- Hold the card in your main hand or off hand and use the device.
- The server checks the card every time: its signature, its serial, whether it was revoked, and, for a
  bound card, who is holding it. A card of this installation that fails is ignored and the attempt is
  logged as "Keycard rejected".
- A card only ever **adds** access. It never overrides a refusal from another source, and a card for a
  different installation does nothing.
- A device set to **Keycard only** ignores the member list and bound organisations: only a card at its
  level or higher opens it. Refusals and lockdown still apply. The device's settings screen never needs
  a card, so the owner can always turn keycard-only off again.
- A card counts only for getting through: opening doors and chests, passing force fields and passing
  sensors. An **OWNER card opens OWNER doors**, including during a lockdown.
- A card never counts for anything else. Opening the hub or the monitor, linking and unlinking devices,
  members, lockdown, reading the log or the card list, silencing alarms, settings screens, writing,
  copying, wiping or revoking cards, automation, renaming, dissolving and binding organisations all
  need someone on the member list at the right level (or a bound organisation the owner chose).

## The card list

Every installation keeps a list of the cards it issued.

- `/nerosecurity keycards <installation> list` shows serials, levels, flags and ages (OFFICER; each
  read is logged like reading the audit log).
- `/nerosecurity keycards <installation> revoke <serial>` kills one card (OWNER).
- `/nerosecurity keycards <installation> revokeall` kills every card issued so far (OWNER).

## What the tooltip shows

A blank card says "Blank — encode it at a keycard programmer." A written card shows its level and what
that level means, its serial number, and "Bound to its holder" when bound. An encrypted card shows only
"ENCRYPTED — cannot be copied without the owner". The tooltip never names a player. The encrypted
tooltip only hides the level from view; it is not a secret, and the server checks every card anyway.

The card also shows the installation's name as it was when the card was written. It is a label only.

## See also

- [Access Levels](Access-Levels.md)
- [Doors and Chests](Doors-and-Chests.md)
- [Home](Home.md)
