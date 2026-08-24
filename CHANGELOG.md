# Changelog

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
