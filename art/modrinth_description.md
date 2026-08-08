# NeroSecurity

**Lock it down — keycards, blast doors, force fields, cameras and turret-grade defence for tech bases, colony outposts and orbital stations.**

NeroSecurity is the **locks, access control & base defence** mod of the Neroland ecosystem. It layers keycards, powered security doors, projected force fields, surveillance cameras, alarms, laser grids, tiered player access levels, base lockdown and secure containers into a coherent defence stack, so a NeroColonies outpost or a Nerospace station can be genuinely defensible rather than relying on raw block hardness or vanilla doors. Security is about *who* can get in — not how much obsidian you stacked.

Built on **Neroland Core**, so its power framework, upgrade modules, progression gates, `c:` material tags, and — crucially — its data-erasure hook for access and camera logs are shared with the rest of the lineup. *(Planned — in design; not yet released.)*

---

## 1. Keycards & the access-resolution service

Every gated block asks Core a single question — *may player P perform action A here at access level L?* — resolved **server-side, every time**, so keycards and camera access can't be spoofed by a client.

- 💳 **Keycards** — physical item tokens carrying an encoded access level and (optionally) a bound owner UUID; readers validate them through the service rather than trusting the card blindly.
- 🖊️ **Programmer block** — writes and clones cards, but itself demands a sufficient access level, so you can't mint an admin card without admin rights. Encryption is an upgrade module, so high-tier cards can't be copied on a basic reader.
- 🪪 **Tiered access levels** — a small ordered scale (Public → Member → Officer → Owner/Admin) per installation, the backbone every other feature depends on.

## 2. Doors, force fields & laser grids

- 🚪 **Security doors** — powered, access-gated blast doors with an optional keycard-only mode; upgrade slots for hardness and open speed.
- 🛡️ **Force fields** — an emitter projects a controllable energy barrier across a configurable area, passable only to permitted players; cost scales with size and strength, and it's config-capped for PvP/raid servers.
- 🔦 **Laser grids** — paired nodes project detection beams; a break raises an event on the installation bus (and can deal damage if configured) — the energy-tier successor to vanilla tripwire.

## 3. Cameras, alarms & the security-event bus

Detection blocks *produce* events; alarms, lockdown and logging *consume* them — so adding a sensor or response is a matter of the shared bus, never rewiring redstone by hand.

- 📹 **Cameras** — viewable through a monitor or handheld viewer with an access level required to open the feed; may optionally log motion events (**personal-data sensitive** — see below).
- 🚨 **Alarm blocks** — subscribe to the bus and fire on forced doors, tripped grids, camera motion or unauthorised access, responding with redstone, sound, a beacon highlight, or a message to online owners.

## 4. Lockdown & secure storage

- 🔒 **Base lockdown mode** — a hub block runs a Normal → Lockdown → released state machine: force-seals doors, raises fields to full, arms alarms and drops access for all but top-tier cards. Trigger it by hand, by alarm event, or by server command.
- 🗄️ **Secure chests** — access-gated containers that refuse unauthorised players server-side, with an optional audit trail (**personal-data sensitive**), and standard item capabilities so automation can be allowed or denied per access policy.

## 5. Claim integration

- 🧭 **Claim-aware** — reads claim/ownership state from mods like **FTB Chunks** and **Open Parties and Claims** through Core compat tags and feeds it into access resolution. NeroSecurity only ever *adds* restrictions on top of a claim — it never overrides one to grant access.

## Privacy (POPIA / GDPR)

NeroSecurity is the most privacy-sensitive mod in the ecosystem, because cameras, access logs and secure-chest audit trails record **personal data** — player identities, timestamps and locations. It is compliant **by default**, and the defaults always err toward less data and shorter retention:

- 🔑 **Keyed by UUID, never names** — logs record who, what and when on *that* installation, and nothing more. No chat, no unrelated player movement, no inventory beyond the action performed.
- ⏳ **Short, configurable retention** — camera footage lives in a buffer measured in minutes; access and audit logs purge after a small, configurable number of days. Old data is auto-deleted, never kept indefinitely.
- 🧹 **One erase clears everything** — logging routes through Core's shared **data-erasure hook**, so a single erasure request purges a player's access and camera records across NeroSecurity *and* every other Neroland mod at once, alongside export for the right of access.
- 🙈 **Opt-out & per-feature toggles** — players and servers can opt out of being logged where feasible, and cameras, logging, force fields and lockdown are each independently disableable in a clearly labelled `privacy` config section.
- 👁️ **No silent recording** — an unmistakable "recording active" indicator surfaces whenever surveillance is in effect, and reading the audit log is itself privileged and itself audited.
- 📡 **Anonymous crash telemetry, opt-out** — carries only version strings (mod / MC / loader / OS / Java), never IPs, names, UUIDs or world data, and is disableable in config.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one power type, one upgrade-module system, one progression arc, shared `c:` tags, and Core's permission/reputation/faction APIs as the access backbone. NeroSecurity ships in its own creative tab.
- 🤝 **Interoperates, never hard-depends** — **NeroFactions** rank, **NeroColonies** membership and **Nerospace** station ownership feed straight into access levels, so a faction can lock a station deck to officers with no per-player setup. All optional: absent, NeroSecurity falls back to per-player ownership.
- 🌌 **Progression-gated** — cheap "locked workshop" tiers craft on Earth, while force fields and laser grids need space-tier power budgets, naturally gating advanced defence behind reaching orbit.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroSecurity (it loads first).
- Conventional `c:` tags and loader-native power/item capabilities mean **Create**, **AE2**, **Mekanism**, **Ad Astra** and **Energized Power** interoperate — powering fields and doors, or exposing secure chests via item handlers — as the 26.x ecosystem fills in, with no hard dependency on any of them.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroSecurity by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/nerosecurity) and the [GitHub repository](https://github.com/Neroland/nerosecurity). Full terms: [LICENSE](https://github.com/Neroland/nerosecurity/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerosecurity/wiki)** — every block, device and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerosecurity/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerosecurity/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/nerosecurity)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
