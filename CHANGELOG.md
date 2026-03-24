# Locks Reforged Changelog

## 1.3.2

### Loot-Scaled Lock Generation
- Lock tier is now determined by the **value of a chest's loot table contents** instead of random weighted selection. Village chests get wood/copper locks, while end city chests get gold/diamond locks. Chests with loot below a configurable minimum threshold get no lock at all.
- **Multi-sample averaging**: each loot table is sampled 32 times (configurable) and averaged, producing consistent tier assignments across server restarts instead of relying on a single random roll.
- **Sub-linear stack count scaling**: item value now scales with `sqrt(count)` instead of linearly, so 64 cobblestone no longer outranks a diamond sword.
- **Item value overrides**: configurable per-item base values for materials that are valuable but have COMMON rarity (diamonds, emeralds, netherite, etc.). 15 vanilla items have sensible defaults out of the box.
- Fully configurable: item base value, rarity multipliers (Common/Uncommon/Rare/Epic), enchantment value bonus, per-tier loot value thresholds, sample count, and item overrides.
- Enabled by default. Set `Enable Loot-Scaled Locks = false` in `locks-common.toml` to revert to the old random weighted system.

### Per-Enchantment Config Toggles
- Each of the 6 enchantments can now be individually enabled or disabled in `locks-server.toml` under the new **Enchantments** section.
- Disabled enchantments won't appear in enchanting tables, villager trades, or loot, and their effects are ignored on existing items.
- Disabled enchantments are also stripped from locks generated during world generation.

### Item Renames & Textures
- Renamed **Copper Lock Pick** → **Bobby Pin Lock Pick** to better reflect its bent-wire design.
- Renamed **Bobby Pin Lock Pick** (wood) → **Wood Lock Pick**.
- Redesigned the Wood Lock Pick texture as a whittled wooden stick with a flat chisel tip, visually distinct from the wire-shaped bobby pin picks.

### Bug Fixes
- Fixed lockpicking GUI rendering corruption (textures smeared/repeated vertically) caused by unflushed `GuiGraphics` buffers conflicting with raw Tesselator draw calls, and blend state leaking into other mods' rendering.
- Fixed world generation deadlock (freeze at ~50% "Preparing spawn area") caused by loot-scaled lock generation calling `getRandomItems()` during worldgen feature placement. Loot table values are now pre-computed at server start.

## 1.3.0

### New Enchantments
- **Silent**: Suppresses the lock rattle sound when access is denied. Useful for hidden locks on secret bases. Incompatible with Shocking.
- **Auto-Pick**: Gives lock picks a 10%/20%/30% chance (per level) to instantly open the lock, bypassing the minigame entirely. Represents a faulty lock mechanism. Incompatible with Complexity.
- **Reinforced**: Increases the lock's explosion resistance by 50%/100%/150% per level. Protects against TNT and creeper griefing.

### Bug Fixes
- Fixed multiplayer packet duplication in `ChunkMapMixin` — lock data was being sent to ALL players tracking a chunk instead of only the player who just loaded it. Reduces unnecessary network traffic in multiplayer.

### Misc
- Resolved FIXME comment on offhand interaction handling with a clear explanation of the correct behavior.
- Added GitHub Actions CI workflow for automated builds.

## 1.2.4

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
