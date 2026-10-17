# NeroLink App and Notifications

NeroSecurity can show your installations in the **NeroLink companion app** and send security
notifications to your phone. This needs the NeroLink mod on the server and the app paired to your
account. NeroSecurity itself opens no network connection; it only hands data to Neroland Core's link
system, which the NeroLink mod serves.

A server can switch the whole module off with `linkModuleEnabled = false` (takes effect on restart).

## What the app shows

You only ever see **your own** installations: the ones you own or have a member row on.

- **Owners and officers** see the installation in full: its lockdown state, how many devices of each
  kind it has, whether it has power, its latest event, unread alerts, and its device list and event
  feed.
- **Members** see the installation's name and whether it is locked down. Nothing else.
- Nobody else sees anything. An installation you may not see looks exactly like one that does not
  exist.

What the app never gets:

- **No coordinates.** Devices are named by kind and number, for example "Laser grid 3". An installation
  shows its dimension only.
- **No player ids and no member lists.** An event says whether a player was involved ("a player"
  or "you"), not who.
- An owner or officer can deliberately tap **show who** on one installation's events. The app then gets
  display names for that one read, and the read is logged in the audit log like reading it in game.

Your role in the app comes only from the installation's owner and member lists. A keycard, a bound
faction or colony, or being a server operator does not give you anything in the app.

## Notifications

Notifications only reach you once you have switched the **Security** notification category on in the
app. It is off until you do.

When something happens at an installation, its **owners** are notified. **Officers** are notified too
when an owner turned that on with `/nerosecurity notify-officers <installation> on`. The player who
caused the event is never notified about it.

| Notified | Live feed only, no notification | Never sent live or notified |
| --- | --- | --- |
| Laser grid tripped, Door forced, Chest forced, Device broken, Force field overloaded | Single denied attempts (see below) | Members seen by a camera |
| Motion detected, Power lost, Force field collapsed | Lockdown confirmed by owner | Doors and chests opened, chest changes |
| Lockdown entered, cleared, released automatically, or cleared by an operator | Alarm triggered, alarm silenced, alarm lockdown suppressed | Keycard bookkeeping, member and device changes, log reads, purges |

The app's **event list** for an installation (owners and officers only) is read from the audit log, so
it shows every kind of logged event, including the ones in the last column. The live feed and the
notifications use only the first two columns.

- **Denied attempts.** One denied door, chest or keycard does not buzz your phone. Three of them at one
  installation within 60 seconds send one "Repeated access denied" notification
  (`pushDeniedThreshold`, `pushDeniedWindowSeconds`).
- **No storms.** You get at most one notification per installation per 60 seconds
  (`pushCooldownSeconds`). Anything else in that window is counted, and when the window closes you get
  one "Security events" notification with "(+N more)". Lockdown changes always come through at once.
- **What the text says.** The title is a fixed phrase such as "Laser grid tripped". The body is the
  installation's name, plus "(+N more)" when events were grouped. **A notification never names a
  player and never gives a position.** Tapping it opens that installation in the app.

Notification text is shown on a locked phone and passes through the NeroLink relay and your phone's
push service. That is why it carries nothing but the phrase and your installation's name.

## Alerts

Each notification is also stored as an **alert**, so you see it even if you were offline. There is one
alert per kind of event per installation; a new event refreshes it instead of piling up. Alerts older
than the audit log's retention (7 days by default) are removed.

- In the app, owners can acknowledge an alert.
- In game, `/nerosecurity alerts` lists your unread alerts, `/nerosecurity alerts ack <id>`
  acknowledges one (the short id shown in the list), and `/nerosecurity alerts ack all` acknowledges
  them all.
- Shortly after you join, you get one private line when you have unread alerts:
  `[NeroSecurity] 3 unread security alerts (2 installations). /nerosecurity alerts to review.` It gives
  counts only. A server can turn this off with `loginAlertSummaryEnabled = false`.

`pushNotificationsEnabled = false` turns off notifications, alerts and the login line together. The
app can still show your installations.

## Actions from the app

The app can do three things, and only for an **owner** of the installation:

| Action | Default | What it does |
| --- | --- | --- |
| Acknowledge an alert | on (`linkActionAckEnabled`) | Marks one of your own alerts as read. |
| Silence alarms | on (`linkActionSilenceEnabled`) | Silences the installation's alarms for 1 to 60 minutes. A lockdown still overrides it. |
| Lockdown | **off** (`linkLockdownMode`) | See below. |

The app cannot change members, levels, keycards, names, device settings or automation. Requests are
rate limited, and a repeated request is not applied twice.

### Remote lockdown

Remote lockdown is **off** unless the server owner turns it on:

- `linkLockdownMode = release`: an owner can **clear** a lockdown from the app.
- `linkLockdownMode = full`: an owner can also **start** one, or confirm an alarm lockdown.
- `linkLockdownAllowOffline = true`: the app's lockdown action also works while the owner is not
  logged in to the game. It is off by default.

The trade-off: with `release` or `full`, anyone holding the owner's unlocked phone can unseal the
owner's bases, and with `full` also seal them. A careful choice is `release` with
`linkLockdownAllowOffline = true`: your phone can stand your base down, but never seal it.

## See also

- [Lockdown](Lockdown.md)
- [Home](Home.md)
