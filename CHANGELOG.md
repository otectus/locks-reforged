# Locks Reforged Changelog

## 1.2.2

### Bug Fixes
- Fixed pervasive null-pointer crashes caused by unsafe `.orElse(null)` capability chains across 12 files — affects lock placement, lock picking, key ring usage, chunk loading, packet handling, and client rendering.
- Fixed chunk iteration bug in `Cuboid6i.getContainedChunks()` where X/Z axes were swapped, causing incorrect chunk lookups for locks spanning non-square chunk areas.
- Fixed hopper lock bypass at negative coordinates — `(int)` truncation replaced with `Mth.floor()` for correct block position calculation.
- Fixed `BreakSpeed` event defaulting to `BlockPos.ZERO` when position is absent, which could falsely block mining at world origin.

### Resources
- Added missing `shock2.ogg` to the shock sound event in `sounds.json`.
- Removed empty `forge:ingots/steel` tag file.

## 1.2.1

### Bug Fixes
- Fixed startup crash (`UnsupportedOperationException`) caused by `Files.newDirectoryStream()` on Forge's `UnionFileSystem` when loading lock/lockpick type definitions from the mod JAR.

### Curios API Integration
- Added optional integration with [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) (v5.14.0+).
- The **Key Ring** can now be equipped in the **Charm** curio slot.
- Locks automatically check curio-equipped key rings when no matching key or key ring is found in hand.
- Right-clicking with an empty main hand opens the curio key ring's inventory GUI.
- Hand-held key rings are still checked first, preserving existing priority.
- Fully optional — the mod works normally without Curios installed.

## 1.2.0

### Tag-Based Lockable Blocks
- Blocks can now be marked as lockable using **block tags** in addition to the existing regex system.
- Added `locks:lockable` block tag, which includes `forge:chests`, `forge:barrels`, `c:chests`, and `c:barrels` out of the box.
- Modded chests and barrels that use community convention tags are now automatically lockable without needing custom regex patterns.
- Added `Lockable Tags` server config option to specify additional block tags whose members can be locked.
- The tag check runs before the regex fallback for better performance.

### Data-Driven Item Recognition
- Custom lock, lock pick, and key items registered via JSON configs are now **automatically recognized** without manually editing item tag JSONs.
- Items extending `LockItem`, `LockPickItem`, or `KeyItem` are detected via `instanceof` first, with the existing tag system preserved as a fallback for third-party mods.

### Misc
- Removed resolved FIXME on the lock enchantment category.
