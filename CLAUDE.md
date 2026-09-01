# Locks Reforged — Forge 1.20.1 (Ported from 1.16.5)

## Quick Reference
- **Mod ID**: `locks`
- **Package**: `melonslise.locks` (original author's package, NOT com.otectus)
- **Version**: 1.7.4
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
  └── mixin/       — 16 mixins (chunk, level, door, piston, explosion, etc.)
src/main/resources/
  ├── META-INF/mods.toml
  ├── META-INF/accesstransformer.cfg  — ATs for Frustum, LevelRenderer, GameRenderer, VillagerTrades, PistonStructureResolver, Explosion, loot tables
  ├── locks.mixins.json               — 16 mixins (15 common + 1 client); the ONLY manifest the build packages
  └── assets/locks/, data/locks/
source-1.16.5/   — archived original source (porting reference)
```

## Key Dependencies
- **Curios** (5.14.1) — optional, keyring as curio slot (compileOnly API + runtimeOnly)
- **Carry On** (2.1.2.7) — optional, compiled only when vendored at `libs/`; adds `locks_carryon.mixins.json`

## Porting Context
This mod was ported from 1.16.5 to 1.20.1. Key references:
- `PORTING_NOTES.md` — detailed migration guide (class renames, API changes)
- `KNOWN_ISSUES.md` — runtime testing status and resolved issues
- `source-1.16.5/` — original source for comparison

## Conventions
- Registration: DeferredRegister on MOD bus
- Capabilities: AttachCapabilitiesEvent for lock storage on chunks/levels
- Mixins: `src/main/resources/locks.mixins.json` is the only manifest the build packages — add new mixins there.
  `locks_carryon.mixins.json` is a second, optional config present only when the Carry On jar is vendored.
  (1.7.2 deleted two dead root-level copies, `locks.mixins.json` and a stray `curios.mixins.json` that belonged
  to Curios itself; the project root is not a resource root, so neither was ever read.)
- Access Transformers: used for Frustum, renderers, villager trades, piston internals, explosion level, loot tables
- Official mappings (SRG names in AT config and mixin targets)
- Data generation: `./gradlew runData` with src/generated/resources/
- Tests: `./gradlew test` — plain JUnit 5, no FML/Bootstrap launch, so tests may only touch pure data classes
  (`BlockPos`, `CompoundTag`, `FriendlyByteBuf`, `Cuboid6i`, `Lock`) and pure policy classes. Anything needing
  `ItemStack`, config values, tags, `Level` or `Minecraft` must be manual QA — see KNOWN_ISSUES.md. No GameTest.
