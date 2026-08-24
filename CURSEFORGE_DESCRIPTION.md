# TikTok Chaos

**Suggested CurseForge summary:** Turn TikTok LIVE likes, gifts, comments, and follows into safe, configurable Forge or NeoForge singleplayer events.

TikTok Chaos connects a public TikTok LIVE broadcast directly to Minecraft Java. Viewer interactions can spawn temporary mobs, give items, apply effects, or trigger other configurable actions in a Forge or NeoForge singleplayer world.

No companion desktop application, paid service, TikTok password, API key, or additional Minecraft dependency is required. Open the in-game menu with `F8`, enter the LIVE creator's username, and connect.

## Features

- Likes, gifts, comments, follows, shares, subscriptions, joins, room statistics, LIVE start, and LIVE end events
- Accumulated-like thresholds and gift value tiers
- Exact gift-ID overrides
- Whitelisted comment commands; arbitrary chat text is never executed
- In-game rule editor with cooldowns, thresholds, targets, amounts, and multiple actions
- Searchable, scrollable icon galleries for mobs, items, and effects, including compatible modpack content
- Random mob, item, and effect targets selected again on every activation from vanilla and modpack registries
- Contextual controls for quantities, effect strength/duration, teleport radius, weather duration, and messages
- Compact HUD and 100-event session history
- Built-in event simulator for offline setup and testing
- Detailed gift/combo simulator with preview that does not consume cooldowns or session statistics
- Built-in and local presets with preview, replace/merge, export, validation, and automatic backups
- Per-unit, once, tiered, and scaled combo execution plus deterministic weighted roulette
- Timed cinematic sequences, viewer bosses, Gift Cannon, Like Fountain, sounds, particles, and temporary visual items
- Global pause and `F9` emergency cleanup without disconnecting the LIVE
- Session-only goals, ranking, optional chat, hidden names, and opt-in temporary avatars
- Optional read-only OBS overlay bound only to `127.0.0.1` with a random session token
- Automatic reconnect with progressive delay
- Persistent JSON configuration
- No required companion app or additional mod

## Quick start

1. Install the JAR matching your exact Minecraft version and loader.
2. Enter a singleplayer world.
3. Press `F8`.
4. Enter the LIVE creator's username without `@`.
5. Select **Connect**.

The creator must currently be LIVE and the broadcast must be public.

## Safety

TikTok Chaos never executes LIVE comments as Minecraft or operating-system commands. The mob catalog is restricted to registered spawn-egg-backed entities, action throughput is limited, and spawned mobs expire automatically. World editing is disabled by default; its reversible box requires double confirmation, skips block entities, has a hard block cap, and rolls itself back.

## Requirements

- Minecraft Java `1.16.5`, `1.18.2`, `1.19.2`, `1.20.1`, or `1.21.1`
- Forge on every listed version, or NeoForge on `1.21.1`
- Java 8 for 1.16.5, Java 17 for 1.18.2–1.20.1, and Java 21 for 1.21.1
- Client-side installation for singleplayer

## Privacy and limitations

The mod does not request or store TikTok passwords, login cookies, or API keys. It only receives events from a public LIVE room. Event history, ranking, comments, and downloaded avatars remain in memory/session cache only and are cleared when the session ends.

The bundled connection library uses an unofficial community implementation of TikTok's Webcast interface. TikTok may change or restrict this interface at any time. Private, age-restricted, region-restricted, or guest-blocked broadcasts may not work, and large broadcasts may aggregate individual like events.

## Credits and license

TikTok Chaos is licensed under MIT. It bundles the MIT-licensed [TikTokLiveJava](https://github.com/jwdeveloper/TikTok-Live-Java) and DepenDance libraries, MIT-licensed Java-WebSocket, and BSD-3-Clause Protocol Buffers runtime. Full third-party notices are included in the JAR.

TikTok Chaos is an independent community project and is not affiliated with, endorsed by, or sponsored by TikTok, ByteDance, Mojang, Microsoft, Forge, or NeoForge. TikTok is a trademark of its respective owner.

**Coded by [Pexe171](https://github.com/Pexe171).**
