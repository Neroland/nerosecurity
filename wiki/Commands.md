# Commands

All NeroSecurity commands start with `/nerosecurity`. Players need no operator permission for them;
each command checks your level at the installation, exactly as the hub does. A keycard in your hand
never counts for a command. Replies go to you only.

`<installation>` is an installation's id, the first eight characters of its id, or its exact name (in
quotes if it contains spaces). Tab-completion suggests the short ids of your own installations only,
never names or owners.

## Your installations

| Command | Who | What it does |
| --- | --- | --- |
| `/nerosecurity installations` | anyone | Lists the installations you own or belong to, with your level and whether each is locked down. |
| `/nerosecurity lockdown <installation> on` | OFFICER | Starts a lockdown. |
| `/nerosecurity lockdown <installation> off` | OWNER | Clears a lockdown. |
| `/nerosecurity log <installation> [count]` | OFFICER | Shows the newest audit log entries (20 by default, 1 to 100). The read is itself logged. |
| `/nerosecurity keycards <installation> list` | OFFICER | Lists issued cards: serial, level, flags and age. Logged like a log read. |
| `/nerosecurity keycards <installation> revoke <serial>` | OWNER | Kills one card. |
| `/nerosecurity keycards <installation> revokeall` | OWNER | Kills every card issued so far. |
| `/nerosecurity automation <installation> off\|member\|officer` | OWNER | Sets how far hoppers and pipes may reach into secure chests. |
| `/nerosecurity notify-officers <installation> on\|off` | OWNER | Whether officers also get alarm chat messages and phone notifications. |

## Bindings (optional mods)

| Command | Who | What it does |
| --- | --- | --- |
| `/nerosecurity bind <installation>` | MEMBER | Shows the faction, colony and station bindings. |
| `/nerosecurity bind <installation> faction <faction> [member\|officer]` | OWNER | Binds a NeroFactions faction you belong to. |
| `/nerosecurity bind <installation> colony here\|<colony> [member\|officer]` | OWNER | Binds a NeroColonies colony you belong to; `here` is the one you stand in. |
| `/nerosecurity bind <installation> station <station>` | OWNER | Binds a Nerospace station you own. |
| `/nerosecurity unbind <installation> faction\|colony\|station` | OWNER | Removes a binding. |

The optional `member|officer` is the most the binding may give (MEMBER when left out). Only a real
owner can bind or unbind; an OWNER keycard does not count. See [Access Levels](Access-Levels.md).

## Alerts

| Command | Who | What it does |
| --- | --- | --- |
| `/nerosecurity alerts` | anyone | Lists your unread security alerts. |
| `/nerosecurity alerts ack <id>` | anyone | Acknowledges one of your alerts, by the short id the list shows. |
| `/nerosecurity alerts ack all` | anyone | Acknowledges all of your alerts. |

See [NeroLink App and Notifications](Companion-App.md).

## Privacy

| Command | Who | What it does |
| --- | --- | --- |
| `/nerosecurity privacy` or `/nerosecurity privacy status` | anyone | Says whether security logs name you. |
| `/nerosecurity privacy optout` | anyone | Security logs record you as anonymous from now on. |
| `/nerosecurity privacy optin` | anyone | Security logs name you again. |
| `/nerosecurity privacy export` | anyone | Writes a copy of your NeroSecurity data to a file on the server and tells you where. |
| `/neroland data eraseme` | anyone | Neroland Core's erasure command: removes you from every Neroland mod on the server. |

These act only on you. See [Privacy](Privacy.md).

## Operators

These need operator permission (level 2).

| Command | What it does |
| --- | --- |
| `/nerosecurity admin list` | Lists every installation: short id, name, counts of owners, members and devices, and whether it is orphaned or locked down. No player names. |
| `/nerosecurity admin unlock <installation>` | Clears a lockdown on any installation. The owners are notified. |
| `/nerosecurity admin dissolve <installation>` | Removes an **orphaned** installation. Any other installation is dissolved by breaking its hub. |

An installation becomes orphaned when its hub is removed by something other than its owner (an
explosion, a command, another mod), or when its last owner's data is erased.

## Client command

`/nsber` prints how much work NeroSecurity's block renderers did over the last 200 frames, and
`/nsber reset` clears the count. It runs on your own game only and is meant for testing performance.

## See also

- [Home](Home.md)
