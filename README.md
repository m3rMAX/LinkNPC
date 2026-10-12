# LinkNPC 1.1.0 — Custom Animated Mascots

Paper 1.21.11 plugin, Java 21. No resource pack required.

## Commands
- `/NPCHERO discord <link>` — creates a custom blue Discord-themed mascot.
- `/NPCHERO youtube <link>` — creates a custom red YouTube-themed mascot.
- `/NPCHERO remove` — removes the nearest mascot within 5 blocks.
- `/NPCHERO list` — lists saved mascots.
- `/NPCHERO help` — command help.
- `/npc` is an alias for `/NPCHERO`.

## Animation and appearance
The mascots are built from multiple `BlockDisplay` entities, not player skins. They bob gently up and down and have their own block-built Discord/YouTube-themed shapes. Right-clicking the invisible interaction anchor sends the clickable link. Tab completion suggests subcommands and example URL prefixes.

## Build
Run `mvn package` with Java 21 and internet access. The jar is written to `target/LinkNPC-1.1.0.jar`.
