# Changelog

## 1.4.0 - Live control, presets, combos, and session tools

- Added global `ACTIVE/PAUSED` action state and an `F9` emergency stop that clears the queue, cancels sequences, removes temporary entities, and restores tracked effects, weather, and reversible blocks without disconnecting the LIVE.
- Fixed queue shrinking so it discards the lowest-priority work instead of urgent gifts.
- Added versioned configuration migration, atomic writes, and automatic retention of the ten latest backups.
- Added built-in and local presets with preview, replace/merge modes, deterministic duplicate IDs, registry validation, export, and privacy-safe documents that never include connection data.
- Added a detailed simulator for username, gift ID/name, unit coins, amount, likes, and comments; previews do not consume cooldowns, counters, statistics, or queue capacity.
- Added `PER_UNIT`, `ONCE`, `TIERED`, and `SCALED` gift execution while preserving the existing three-roses-equals-three-actions behavior for migrated rules.
- Added declarative scaling, deterministic weighted roulette, bounded timelines, and safe cinematic actions including sounds, launch, freeze, particles, centered messages, visual item rain, Gift Cannon, and Like Fountain.
- Added in-memory-only goals, rankings, viewer-mob defeat counts, optional chat widgets, name hiding, and automatic session reset that survives reconnect attempts.
- Added optional temporary avatars with HTTPS allowlists, byte/dimension limits, redirect blocking, public-address checks, and session cleanup.
- Added bounded viewer bosses and an opt-in reversible block box with double confirmation, block-entity protection, change limits, and rollback.
- Added a read-only OBS browser overlay bound only to `127.0.0.1`, with a random session token, CSP, and no LAN listener.
- Added adaptive action throttling during slow ticks and expanded tests across the complete six-build Forge/NeoForge matrix.

## 1.3.0 - Multi-version Forge ports

- Added Forge builds for Minecraft 1.16.5, 1.18.2, 1.19.2, and 1.20.1 while keeping Forge and NeoForge 1.21.1.
- Added version-specific Minecraft/Forge adapters while sharing the LIVE connector, configuration, rule engine, safety limits, and gift-combo handling.
- Added a Java 8-compatible 1.16.5 artifact and Java 17-compatible artifacts for 1.18.2 through 1.20.1.
- Added CI and CurseForge automation that builds, tests, verifies, and publishes all six loader/version artifacts.
- Added cross-version compatibility for effect lookup and mouse-wheel callbacks used by the full visual editor.
- Kept the corrected gift amount behavior across every supported version: three roses execute the configured action set three times, subject to the configured safety cap.

## 1.2.1 - Forge support and gift combos

- Added a dedicated Minecraft Forge 1.21.1 build alongside the existing NeoForge 1.21.1 build.
- Split loader-specific startup, event, HUD, and config-screen integration from the shared gameplay code.
- Fixed gift combos so each gift repeats the configured action set, capped by `maxTriggersPerEvent`.
- Applied cooldowns once per incoming combo so a combo does not suppress its own repetitions.
- Updated CI and CurseForge publishing to build, test, and upload both loader artifacts.

## 1.2.0 - Random modpack targets

- Added an `Aleatório (todos os mods)` card to the mob, item, and effect galleries.
- Random targets are selected again on every rule activation from all compatible registered modpack content.
- Added lazy immutable registry pools so high-frequency LIVE events do not repeatedly scan every registry.
- Kept mob caps, action-rate limits, quantities, effect levels, and effect durations active for random targets.

## 1.1.0 - Visual action catalog

- Added searchable, scrollable icon galleries for mobs, items, and effects, including compatible modpack content.
- Added localized names and registry IDs so users no longer need to memorize `minecraft:id` values.
- Added contextual controls for mob/item amount, effect level and duration, teleport radius, and weather duration.
- Added editable custom messages and kept manual registry-ID entry available for advanced users.
- Expanded effect execution to every registered effect and mob spawning to safe spawn-egg-backed entity types.
- Virtualized the gallery so only visible cards and icons are rendered each frame.

## 1.0.3 - FancyMenu foreground fix

- Rendered custom labels after the vanilla widget layer so FancyMenu's injected blur cannot blur them.
- Kept buttons, edit fields, labels, and status text on a readable foreground layer.

## 1.0.2 - UI readability fix

- Replaced the native screen background effect with a local dim overlay.
- Added text shadows and increased the contrast of secondary labels.

## 1.0.1 - Launcher compatibility fix

- Replaced the provider-discovered default random generator with the universally available Java random implementation.
- Fixed startup failure on modular Minecraft Java 21 runtimes reporting that `L32X64MixRandom` was unavailable.

## 1.0.0 - Initial release

- Added direct public TikTok LIVE connection by username.
- Added likes, gifts, comments, follows, shares, subscriptions, joins, room statistics, LIVE start, and LIVE end events.
- Added configurable rules, like thresholds, gift tiers, exact gift-ID overrides, cooldowns, and multiple actions.
- Added the `F8` connection, rules, history, safety, and simulator screens.
- Added compact HUD, bounded priority queue, reconnect handling, and duplicate-event protection.
- Added safe entity/item allowlists, temporary spawned mobs, visual-only lightning, and action-rate limits.
- Added Portuguese and English translations.
