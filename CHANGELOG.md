# Changelog

All notable changes to **NeroSecurity** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0-alpha.1] - 2026-10-11

Optional NeroEconomy seams. No blocks, items or recipes yet.

### Added

- **NeroEconomy seam for paid security tiers** (NeroEconomy stays optional; NeroSecurity builds, loads and
  runs without it): `economy/SecurityEconomy.chargeTier` charges reason `security_tier` into NeroEconomy's
  Reserve and returns an outcome with a player-facing message.
- The rules every security feature keeps around economy blocks (owners always reach their own blocks,
  never break blocks, no economy inspection for access, a failed renewal only downgrades), documented in
  the README and the new wiki page *NeroEconomy integration*.

### Changed

- NeroEconomy is declared as an optional dependency on every loader (Fabric `suggests`, NeoForge
  `optional`, Forge `mandatory = false`, floor `0.3.0-beta.1`) and compiled `compileOnly`.
- Neroland Core (floor 1.13.0) is now a required dependency, and NeroSecurity ships anonymous, opt-out
  crash reporting (see PRIVACY.md).

## [0.2.0-alpha.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** NeroSecurity adds no recipes of its own, so nothing needed a plugin. The build now
  compiles against the community EMI Unofficial Port (Unstable), the only EMI build for Minecraft 26.x,
  and dev clients load it with `-PwithEmi` (default runs stay JEI-only). EMI stays optional.

## [0.1.0-alpha.1] - 2026-09-20

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

