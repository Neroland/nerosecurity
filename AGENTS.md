# Project context for AI coding agents — nerosecurity

> `AGENTS.md` and `CLAUDE.md` are kept byte-identical; update both together.

## The mod

- **NeroSecurity** — part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.
  **0.5.0-beta.1 is in progress.** No gameplay content yet. The foundation now has the network seam
  (own `nerosecurity:main` channel), the data-erasure registration, `MenuOpener`, JUnit tests and real
  (opt-out, anonymous) Sentry telemetry. Add shared content under `common/` and wire it through each
  loader entry point.
- Mod id: **`nerosecurity`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.nerosecurity`. Author: **Neroland**.
- Version: **0.5.0-beta.1** (in progress).
- Targets **MC 26.1.2, 26.2 AND 26.3** on **NeoForge, MinecraftForge/Forge, and Fabric** → the **"9 cells"**.
  **Java 25.** Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out.
- **Never commit or tag; leave changes for the developer**, who reviews and commits with native git (the
  source of truth).
- **Publish trigger — be careful with `gradle.properties`.** `publish.yml` runs on any push to `main` that
  touches `gradle.properties`, and publishes to CurseForge, Modrinth and GitHub Releases whenever no
  `v<mod_version>` tag exists yet. Bumping `mod_version` and pushing it to `main` IS a release.
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>`
  (`:fabric:26.1.2 :fabric:26.2 :fabric:26.3 :neoforge:26.1.2 :neoforge:26.2 :neoforge:26.3 :forge:26.1.2 :forge:26.2 :forge:26.3`). `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions=26.1.2,26.2,26.3`.
- **Version-specific code in `common/`.** Non-active nodes run `common/` through Stonecutter (`stonecutterProcessCommon`), so shared code uses the same `//? if >=26.3 {` blocks as the loader `src/` trees. Keep the files in the vcsVersion state, and never put a `*/` inside a disabled block. Datapack files whose format differs by version go in `common/src/main/resources-<mc>/`, which is merged over `common/src/main/resources` for every node at or above `<mc>` (`mergeCommonResources`).

## Progression and quests

- Personal milestones are player-scope Neroland Core gates under `common/src/main/resources/data/nerosecurity/neroland_gates/`,
  opened only through `progression/SecurityMilestones.record(...)`. They are records for the NeroQuests pack
  (`data/nerosecurity/neroquests/`, inert data, **no dependency on NeroQuests** of any kind) and are **never**
  read to decide access. Lockdown enter/exit fires Core ThresholdEvents channel `nerosecurity:lockdown` (scope is an opaque installation id, never a player, name or position); no other security event is broadcast. Never log,
  broadcast or send a player name, UUID or position; access logs and camera data stay out of anything quest-related.

## Build & verify

- Build the cells with the Gradle wrapper, e.g. `./gradlew :fabric:26.2:build` or all nine:
  `:neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build :forge:26.1.2:build :forge:26.2:build :forge:26.3:build
  :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build`.
- Unit tests (JUnit 5, plain JVM) live in `common/src/test/java` and are wired into the three `neoforge`
  nodes only; each `:neoforge:<mc>:build` runs them (`:neoforge:<mc>:test` alone). Every player-keyed store
  needs a probe in `data/SecurityErasureConformanceTest`.
- Static analysis: `./gradlew :fabric:26.2:ecjCheck` (the VS Code Problems panel, via `tools/ecj.prefs`).
  The task only FAILS on errors.
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.

## Conventions (cross-loader)

- **Resources are HAND-AUTHORED in `common/src/main/resources`** — the multiloader does not run datagen.
  Validate JSON after edits.
- **Platform seams via ServiceLoader (no Architectury).** Put loader-agnostic code in `common/`; ship one
  impl per loader plus a `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` /
  `net.fabricmc.*` / `net.minecraftforge.*` imports.
- Loader entry points: `NeroSecurityFabric` (+ `NeroSecurityFabricClient`), `NeroSecurityForge`,
  `NeroSecurityNeoForge` — each calls `NeroSecurityCommon.init()` during construction, THEN registers the
  network payloads (`{Fabric,NeoForge,Forge}SecurityNetwork`). Forge seals its channel at `build()`.
- `NeroSecurityCommon.init()` order is load-bearing: 0 Services → 1 Config → 2 Telemetry → 3 registries →
  4 `SecurityData.init()` (erasure, early) → 5 compat detection (`SecurityEconomy`) → 6 `SecurityNetwork.init()`
  → 7 link module (`link/SecurityLinkModule`) LAST.
- **Networking:** NeroSecurity owns `nerosecurity:main`. Declare payloads once in `network/SecurityNetwork`;
  send through `Services.NETWORK`. Never register on Neroland Core's `CoreNetwork` (Core drains its lists
  during its own bootstrap, so downstream registrations are silently dropped).
- **Menus:** every GUI opens through `menu/MenuOpener.open(player, provider)`, never `player.openMenu`.
- **Player data:** every player-keyed store joins `data/SecurityData` via `addStore(...)`, erases its rows
  and then calls `SavedDataRecovery.backupNow`; every `SavedData` is read through `SavedDataRecovery.get`.
- **Telemetry:** report handled failures with `NeroSecurityTelemetry.captureHandledException(t, source,
  operation)` using stable labels (subsystem + translation key or class name) — never player text, names,
  UUIDs or positions. Keep the placeholder-DSN guard.
- **Soft dependencies:** `compileOnly` + an `isModLoaded` guard + one bridge class. No reflection. Floor each
  optional dependency's manifest range at the compiled version.
- Neroland Core APIs in use are listed in `USING-CORE.md`.
- NeoForge/Forge debug tasks use `-PnerosecurityDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`nerosecurity.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `nerosecurity-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root: the player- and
  contributor-facing docs for NeroSecurity (features, blocks/items, machines, progression, recipes, FAQ).
- **Whenever you add, change, or remove a feature, update `wiki/` in the same change** — treat the
  wiki as part of "done"; code without a matching wiki update is incomplete.
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** — document only NeroSecurity here; cross-mod / ecosystem concepts belong in the
  relevant other mod's own wiki.

## DO NOT

- Commit, tag or push — leave changes for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.
