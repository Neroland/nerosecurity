# NeroQuests integration

NeroSecurity works with or without **NeroQuests**. When NeroQuests is installed, the quest book gains the
chapter **Access Granted**, five quests that teach you to secure your own base one step at a time:

| Quest | Done when |
| --- | --- |
| Card Stock | You craft a blank keycard |
| First Keycard | You encode your first keycard |
| Installation Online | You register a security hub and link at least one device to it |
| Lockdown Drill | You trigger a lockdown on your own installation, then clear it |
| Head of Security | The three quests above are complete |

Rewards are experience and a few plain items; a quest never grants an access level, a key or a way
past a door. Only a lockdown that an owner starts by hand and an owner clears counts as the drill.

## Privacy

Your progress in these quests is kept by Neroland Core as three personal milestones (first keycard,
installation online, lockdown drill), covered by Core's own privacy page. NeroSecurity publishes nothing
about alarms or intrusions to other mods, and access logs or camera data never reach the quest system.
The one exception is lockdown: entering and leaving a lockdown is announced on Neroland Core's
`nerosecurity:lockdown` channel so quest packs can react. The announcement carries an opaque
installation id only, never a player, a name or a position. A server can turn it off with
`lockdownChannelEnabled = false`.

## See also

- [Home](Home.md)
- [NeroEconomy integration](NeroEconomy-Integration.md)
