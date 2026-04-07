# Locks Reforged Changelog

## 1.5.2

### Bug Fixes
- Fixed keys, master keys, key rings, Awareness enchantment, and curio key rings being unable to re-lock unlocked lockables. The toggle logic only ran when at least one lockable was locked; unlocking all lockables at a position made re-locking impossible in all game modes.
- Fixed `NullPointerException` crash in `StructureTemplateMixin` when the lockable handler capability is missing during structure copy or paste operations.
- Fixed `Lock.fromBuf` creating an all-zero pin combo on the client instead of generating a proper dummy combo from the lock's ID seed.
- Fixed `KeyRingInventory.extractItem` using `getMaxStackSize()` instead of `getCount()` as the extraction limit, violating the `IItemHandler` contract.
- Fixed `Transform.fromDirectionAndFace` returning null for unmapped direction/face combinations, which could cause `NullPointerException` in lock state calculations. Now falls back to `NORTH_MID`.
- Fixed `LockItem.isOpen()` calling `getOrCreateTag()` on read, which unnecessarily created empty NBT tags on items without existing data.

### Loot-Scaled Lock Generation
- Chests whose loot value falls below all configured tier thresholds no longer receive a lock when loot-scaled locks are enabled. Previously, these low-value chests always received a wood lock despite the config description stating otherwise.

### New Config
- Added **Loot Table Injection Patterns** server config option. Controls which loot tables receive lock pick and key loot injection. Default: `minecraft:chests/`. Add entries like `some_mod:chests/` to inject into modded dungeon chests.

### Security
- Added additional server-side validation to lock picking packets: the server now re-checks that the lock is still locked and the player still holds a valid pick before processing each pin attempt.

### Misc
- Invalid entries in the `Lockable Tags` config list now log a warning instead of being silently skipped.

## 1.5.0

### Generation Chance
- Re-enabled the `Generation Chance` config setting. In 1.4.4 this was disabled and all generated chests received a lock unconditionally. It is now a functional setting again (default 1.0). Lowering it allows a percentage of generated chests to skip lock placement.

### Bug Fixes
- Fixed lock picking GUI showing garbled textures when a lockpick breaks. The broken pick halves were being rendered with the lock body texture atlas (48x80) instead of the lockpick texture atlas (160x16), causing completely wrong UV sampling.
- Fixed the left broken pick piece's fade animation targeting the wrong sprite (right piece instead of left piece).
- Fixed adjacent Lootr chests not all receiving locks. When two Lootr single chests were next to each other, Minecraft auto-connected them as a double chest (`LEFT`/`RIGHT`), and the `RIGHT` half was filtered out before lock placement could run.

## 1.4.8

### Security
- Lock pin combinations are no longer sent to clients over the network. The server now only transmits the pin count; pin validation remains fully server-authoritative. Previously, the full combo was readable from packets, trivially bypassing the lock picking minigame.
- Network version predicates now enforce strict version matching. Clients and servers with mismatched mod versions are cleanly rejected instead of silently connecting with incompatible packet formats.
- Server-to-client pin attempt packets are now range-validated, rejecting out-of-bounds pin indices.

### Bug Fixes
- Fixed locks failing to render on multiplayer clients when the server's `Max Lockable Volume` config exceeded the default value. Client-side packet handling no longer re-runs volume/intersection validation that only the server should perform.
- Fixed potential client crash (`NullPointerException`) when lock packets arrive during dimension transitions or disconnect, before the client level is initialized.
- Fixed potential server crash (`NullPointerException`) when removing a lockable whose bounding box spans a chunk that has already been unloaded. Unloaded chunks are now skipped gracefully.
- Fixed lock-close sound playing to nearby players even when lock placement fails server-side validation (e.g. overlapping an existing lock). Sound now plays only after successful placement.
- Fixed the key crafting recipe consuming all key blanks in the grid but producing only one key. The recipe now accepts exactly one blank per craft.
- Fixed worldgen lock placement only registering in the first chunk when a double chest spans a chunk boundary. All intersecting proto-chunks now receive the lockable.
- Fixed potential `ArrayIndexOutOfBoundsException` when loading lockables with corrupted or out-of-range transform data from NBT. Invalid indices now fall back to the default transform.
- Fixed lock picking GUI springs not animating when pins move (regression from 1.4.2 rendering migration).

### Textures
- Changed the Netherite Lock Pick texture.
- Fixed the unlocked Netherite Lock texture.
- Darkened the Netherite lock mechanism texture.

### Cleanup
- Removed dead code: unused `LocksCapabilities.registerCaps()`, `LocksConfig.canGen()`, `WrittenBookItem` import, and commented-out code blocks across `Lock`, `Cuboid6i`, `LockPickingContainer`, and `LocksClientUtil`.

## 1.4.4

### Guaranteed Lock Generation
- All generated chests with loot tables now receive a lock. Chests that previously fell below the lowest loot value threshold (or failed the 85% generation chance roll) now get a wooden lock instead of no lock at all.
- The `Generation Chance` config setting is no longer used — all generated chests are locked unconditionally. The setting is kept in the config file for backwards compatibility.

## 1.4.3

### Bug Fixes
- Fixed locked chest generation being restricted to overworld biomes. Structure chests can now generate locks in the nether, the end, and modded dimensions as well.

## 1.4.2

### Netherite Lock Pick
- Netherite lock picks now use durability instead of being consumed outright when a failed pin attempt triggers a break.
- Added **128 durability** to the Netherite Lock Pick.
- Netherite lock picks can now be repaired with **Netherite Ingots** in an anvil.
- Netherite lock picks can now receive **Mending**.
- **Netherite Lockpick Unbreakable** now prevents durability loss entirely instead of only preventing the item from being deleted.

### Bug Fixes
- Fixed loot-scaled lock generation causing massive startup logspam from sampled loot functions such as invalid `SetItemDamageFunction` rolls on non-damageable items.
- Fixed loot-scaled lock generation crashing some modpacks by invoking loot-table behavior that performed structure lookups, world generation, or block-state access during startup/off-thread precomputation.
- Reworked loot value calculation to estimate chest value directly from loot table JSON data instead of executing live loot generation.
- When a chest loot table cannot be estimated safely, lock generation now falls back to the old random weighted system instead of failing or crashing world load.

## 1.4.1

### Bug Fixes
- Fixed locks not generating on chests during initial world creation. The async loot value pre-computation introduced in v1.4.0 left the cache empty during spawn area generation, causing all chests to receive no locks. Now falls back to random weighted lock selection while pre-computation runs in the background.

## 1.4.0

### Netherite Lock & Lock Pick
- Added **Netherite Lock** — the strongest lock tier with 14 pins, 200 explosion resistance, and 8 enchantability. Crafted at a smithing table from a Diamond Lock + Netherite Ingot + Netherite Upgrade Template.
- Added **Netherite Lock Pick** — strength 0.95, making it nearly unbreakable. Crafted at a smithing table from a Diamond Lock Pick + Netherite Ingot + Netherite Upgrade Template.
- Both netherite items are **fire-resistant** and survive in lava, like vanilla netherite gear.
- Added **Netherite Lockpick Unbreakable** server config option (off by default). When enabled, netherite lock picks never break during lock picking.
- Netherite lock picks are sold by level 5 toolsmith villagers (16 emeralds). Enchanted netherite locks are offered by wandering traders (40 emeralds).
- Added to loot-scaled lock generation with a value threshold of 60.0 (the highest tier).

### Awareness Enchantment
- Added **Awareness** enchantment (max level I, very rare). When an Awareness-enchanted lock is placed, it remembers who placed it. That player can open and re-lock it with a bare hand — no key needed.
- Works with overlapping locks: each lock independently tracks its owner, so multiple players' Awareness locks at the same position each work correctly.
- Configurable via **Enable Awareness** toggle in the server config (on by default).
- Shows "Aware (Owner-Bound)" tooltip on locks that have an owner.

### New Config
- Added **Netherite Lock** stat overrides (Length, Enchantment Value, Resistance) in the common config.
- Added **Netherite Lockpick Strength** override in the common config.
- Added **Netherite Lockpick Unbreakable** toggle in the server config.
- Added **Enable Awareness** enchantment toggle in the server config.

### Bug Fixes
- Fixed world loading hang in modpacks caused by synchronous loot table pre-computation blocking the server thread. Loot values are now computed asynchronously on a background thread. Chests generated before pre-computation finishes gracefully fall back to no lock.
- Fixed lockpicking GUI rendering corruption (textures smeared/repeated vertically) when using rendering optimization mods like **ImmediatelyFast** or **Embeddium**. Migrated all lockpicking screen rendering from raw Tesselator calls to `GuiGraphics.blit()`, which optimization mods handle correctly.
- Fixed potential `IndexOutOfBoundsException` crash in lock generation if the "Generated Lock Chances" config list was shorter than the "Generated Locks" list (e.g. from manual config editing). Now uses safe bounds checking with a warning log.
- Fixed potential crash in random lock generation if the weighted lock map was empty (e.g. all weights set to 0).

## 1.3.3

### New Config
- Added **Hide HUD Tooltip** server config option under Display. When enabled, the floating tooltip that appears in the world when looking at a lock while holding a lockpick is completely hidden (item name, enchantments, and all other info). Off by default.

### Bug Fixes
- Fixed lockpick controls remaining active after a lockpick breaks — holding left/right during a break would cause the new lockpick to slide on its own and break the break animation.

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
