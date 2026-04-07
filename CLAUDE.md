# Locks Reforged — Forge 1.20.1 (Ported from 1.16.5)

## Quick Reference
- **Mod ID**: `locks`
- **Package**: `melonslise.locks` (original author's package, NOT com.otectus)
- **Version**: 1.5.2
- **MC**: 1.20.1 | **Forge**: 47.2.0 | **Java**: 17
- **Mappings**: Official
- **License**: CC BY-NC 3.0 (Attribution-NonCommercial)
- **Original Author**: Melonslise

## Purpose
Universal lock system for Minecraft. Locks can be dynamically attached to multiple blocks (including modded blocks). Features lock picking mechanic, keyring, master keys, and villager trades.

## Build
- `./gradlew build` — full build (output: build/libs/)
- `./gradlew compileJava` — compile-only
- `./gradlew runClient` — launch dev client
- `./gradlew runData` — run data generation

## Project Structure
```
src/main/java/melonslise/locks/
  ├── client/      — rendering, GUI, key bindings
  ├── common/      — items, blocks, capabilities, network, events
  └── mixin/       — 15 mixins (chunk, level, piston, explosion, etc.)
src/main/resources/
  ├── META-INF/mods.toml
  ├── META-INF/accesstransformer.cfg  — ATs for Frustum, LevelRenderer, GameRenderer, VillagerTrades, PistonStructureResolver, Explosion, loot tables
  ├── locks.mixins.json               — 15 mixins (14 common + 1 client)
  └── assets/locks/, data/locks/
source-1.16.5/   — archived original source (porting reference)
```

## Key Dependencies
- **Curios** (5.14.1) — optional, keyring as curio slot
  - Has its own `curios.mixins.json` (13 mixins) at project root

## Porting Context
This mod was ported from 1.16.5 to 1.20.1. Key references:
- `PORTING_NOTES.md` — detailed migration guide (class renames, API changes)
- `KNOWN_ISSUES.md` — runtime testing status and resolved issues
- `source-1.16.5/` — original source for comparison

## Conventions
- Registration: DeferredRegister on MOD bus
- Capabilities: AttachCapabilitiesEvent for lock storage on chunks/levels
- Mixins: 2 mixin configs (locks.mixins.json + curios.mixins.json)
- Access Transformers: used for Frustum, renderers, villager trades, piston internals, explosion level, loot tables
- Official mappings (SRG names in AT config and mixin targets)
- Data generation: `./gradlew runData` with src/generated/resources/
