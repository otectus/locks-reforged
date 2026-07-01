# Locks Reforged Changelog

## 1.6.5

### Carry On compatibility — locks travel with carried blocks
- Added optional, server-authoritative compatibility with [Carry On](https://www.curseforge.com/minecraft/mc-mods/carry-on). Because a Locks Reforged lock is a spatial `Lockable` (bounding box + `Lock` + transform + lock item + id) held in per-chunk `LockableStorage`, **not** data on the chest block entity, Carry On — which only copies block state + block-entity NBT — was blind to it. Two concrete failures resulted: with `Protect Lockables` **off**, Carry On removed the block but left the `Lockable` behind (a floating "ghost" lock at the old spot and an unlocked chest at the new one); with it **on**, Carry On gates pickup on a `BlockEvent.BreakEvent` that Locks already cancels for protected locked blocks, so *no* locked container could be carried, even by its owner.
- Now the existing lock is **moved** with the block (never re-created): id, `Lock` combo, locked/open state, enchantments, Awareness owner, and key / key ring / master key compatibility are all preserved, and the restored lock renders, blocks interaction, supports lockpicking, and persists across chunk reload and server restart. Transfer rides inside the block entity's Forge persistent data — which Carry On already serializes into its carried `"tile"` NBT and restores on placement — so no second storage system is introduced; the canonical store stays `LockableStorage` + `LockableHandler`, updated through the existing `remove(id)` / `add(lockable)` fan-out (all touched chunks marked unsaved, add/remove packets sent).
- Implemented as three thin mixins into Carry On (`PickupHandler`, `CarryOnData`, `PlacementHandler`) delegating to a new `common/compat/CarryOnCompat` + `CarriedLockTransfer`. Both normal placement (`tryPlaceBlock`) and forced placement on death/drop (`placeCarried`) restore the lock. Locks' `onBlockBreak` now yields to an authorized carry via a thread-scoped marker instead of vetoing it.
- **Authorization:** carrying an *unlocked* lockable is always allowed (it simply moves so it can't ghost). For *locked* blocks, the new `Require Authorization To Carry Locked Blocks` option (default **off** — anyone can carry, the lock just travels) can be enabled to require a matching key, key ring, master key (anywhere in the inventory, since Carry On needs empty hands), Awareness ownership, or creative — so Carry On can't be used to bypass a lock.
- **Safety:** a lock spanning more than the single carried block (e.g. a locked double chest) is denied pickup rather than corrupted (`Deny Partial Multi Block Lock Pickup`, default on). A lockable on a block with no block entity (only reachable via Carry On's `pickupAllBlocks`) is denied, since there is nowhere durable to carry the lock.
- **Config:** new `[Compatibility.CarryOn]` server-config section — `Enable Carry On Compatibility`, `Allow Carrying Locked Blocks`, `Require Authorization To Carry Locked Blocks`, `Deny Partial Multi Block Lock Pickup`, `Log Carry On Lock Transfers`.
- **Async-chunk safe:** restore validates target chunks are loaded and reuses the main-thread `handler.add`; no new blocking chunk fetches (consistent with the 1.6.x C2ME work).

### Build
- The Carry On compat is compiled only when `libs/carryon-forge-1.20.1-2.1.2.7.jar` is present (vendored + gitignored, like the Respawning Structures jar). Its mixins need the target classes on the compile classpath, so when the jar is absent the build automatically excludes the compat sources, its mixin config, and all extra mixin wiring — the mod still builds normally, just without Carry On support. A second, plugin-gated mixin config (`locks_carryon.mixins.json`) applies these mixins only when `carryon` is actually installed at runtime.

## 1.6.4

### C2ME / Async Chunk — the world-load HANG (re-entrant blocking chunk fetch)
- Fixed a hard hang (no crash, no exception — the game freezes during initial world generation) under C2ME. The Server thread, while blocked awaiting a spawn chunk, is made by C2ME's `beforeAwaitChunk` redirect to **drain the chunk executor** (`managedBlock`); a chunk-load completion fires `ChunkEvent.Load` **re-entrantly on that same thread**. Our `onChunkLoad` handler ran inline (it was already "on the server thread") and called a **blocking** `Level#getChunk(x, z)` to re-fetch the chunk — which re-parks on the very chunk future the thread is mid-completing. Permanent self-deadlock.
- `onChunkLoad` now uses the live `LevelChunk` the event already provides (the blocking re-fetch was redundant) and keeps only a **non-blocking** `hasChunk` staleness guard. `registerChunkStorage` is a leaf-`mutex` map put with no chunk fetch, so running it inline during the drain is safe.
- Hardened `LocksThreadUtil.runOnServerThread` with a hard contract that deferred work must never perform a blocking chunk fetch, and made the `server == null` branch degrade safely (run inline only on the owning thread, otherwise drop + warn-once) instead of mutating the global handler off-thread.
- Replaced every `hasChunk`-guarded **blocking** `getChunk`/`getChunkAt` in `LockableHandler` (`add`, `addDirect`, `getInChunk`, `markDirty`, `update`) with the non-blocking `getChunkSource().getChunkNow(x, z)`. Behaviour-preserving on the main thread; removes the same latent re-entrant-hang surface (including the path reached by `StructureTemplate#placeInWorld`'s deferred `add`).

### C2ME correctness hardening
- **Border-spanning generated locks no longer silently dropped.** When `LockChestsFeature` adds a lock to a neighbour the worldgen region returns as an already-finished `ImposterProtoChunk`, the lock previously landed in an inherited `ProtoChunk` list that nothing drains. It is now written through to the wrapped `LevelChunk`'s storage and registered with the world handler on the main thread.
- **Per-chunk `LockableStorage` is now thread-safe.** Its fastutil map is written on C2ME worker threads (`deserializeNBT`, the `LevelChunk` drain) and read on the main thread; all mutations and a new `snapshot()` copy are guarded by the storage monitor, giving an explicit happens-before edge (used by `registerChunkStorage` and `ChunkMapMixin`).
- Added a one-time diagnostic warning if `nextId()` allocates off-thread before the persisted counter is bootstrapped (`initIds`), so any future ordering regression is observable; the existing `advanceLastId` backstop still reconciles ids on chunk load.

### Build
- Fixed the recurring **mislabeled jar** bug: `processResources` did not track `mod_version` as an input, so it stayed UP-TO-DATE and baked a stale version into `mods.toml` even as the jar filename updated (e.g. a "1.6.3" jar internally reporting 1.6.2). Added `inputs.property 'mod_version'` so the embedded version always matches the release.

## 1.6.3

### C2ME / Async Chunk — the *remaining* crash (off-thread read of the handler map)
- Fixed an `ArrayIndexOutOfBoundsException` (same `Index -1` signature as the 1.6.2 crash, now on the **read** side) that could still occur under C2ME. `StructureTemplate#fillFromWorld` (our mixin) runs on a C2ME worker thread and was copying the world-global `LockableHandler.lockables` map (`new ArrayList<>(handler.getLoaded().values())`). The 1.6.x fixes serialized all *writes* to that map onto the main thread, but **building a snapshot of a fastutil open-addressing map while the main thread rehashes it still reads a torn backing array** — the copy itself crashes. A plain snapshot does not make a concurrent read safe.
- `LockableHandler` now guards every structural mutation of its map with an internal monitor and exposes `snapshotLoaded()`, a synchronized copy used by `fillFromWorld` (and the Respawning Structures compat). No behavior change on the main thread or without C2ME; we only serialize the rare worker-thread read against main-thread writes.
- Also closed a lower-probability worldgen race: a border-spanning generated lock is appended to **neighbouring** `ProtoChunk` lock lists from `LockChestsFeature` on worldgen worker threads. That list is now synchronized and is drained under its monitor when the chunk converts to a `LevelChunk`.

### Ghost-locked doors — a door could stay blocked with no visible, pickable lock
- Root cause: two stores must mirror each other — the per-chunk `LockableStorage` (read by **door-blocking** via `getInChunk`) and the world-global `LockableHandler` index (read by **rendering** and the **lock-picking minigame**, including the client). When they diverged (e.g. a missed sync under async chunk loading), a door blocked authoritatively while its lock was invisible and could not be picked — and force-loading the chunk didn't help because the chunk was already loaded.
- **Self-healing reconcile at interaction time (server-authoritative).** When a player interacts with a block that has lockables, the server now re-registers that chunk's storage into the world index (idempotent, no extra packets) so every lock that can block a door is the canonical, observed instance — guaranteeing its lock/unlock changes persist and sync. When a lock actually blocks the interaction, the server also pushes it to the interacting player so a desynced or missing client copy re-renders and becomes pickable. A door can no longer be permanently blocked by a lock the player cannot see or pick.
- **Lock-picking no longer dead-ends on a sync race.** The open-screen packet now carries the full lockable instead of just its id, so the client reconstructs it directly instead of failing with "Lockable not found" when its loaded map hasn't caught up. The pin order stays server-authoritative (the network form is lossy on it). The client still prefers its already-loaded instance when present.

### No more resurrected / duplicate locks across chunk reloads
- `LockableHandler.remove` previously skipped chunks of a multi-chunk lockable that were unloaded, leaving a stale copy on disk that `registerChunkStorage` resurrected as canonical on reload (a removed lock reappearing, or a duplicate after a structure respawn). On the server it now force-loads each chunk the lockable spans (they already exist on disk — this loads, it does not generate) and clears the lock from all of them. The client path stays best-effort and never force-loads.

### Respawning Structures — preserved and hardened
- No behavioral change to the respawn re-lock flow; it still runs on the server thread and reuses the exact world-generation rules (`LocksUtil.createChestLockable`). It now benefits directly from the fixes above: the stale-lock clear uses the thread-safe `snapshotLoaded()`, and `remove` clearing every chunk copy means repeated respawns can't accumulate duplicate locks.

### Tests
- Added a JUnit source set (`src/test`) covering the bootstrap-free pure logic: `Cuboid6i` geometry and chunk-span math (including negative coordinates and border spanning), `Lock` combo/locked NBT round-trips plus the seed-regeneration backward-compat path, and `LockableHandler`'s id-counter monotonicity (the guarantee that prevents id-reuse resurrection). Run with `./gradlew test`.

## 1.6.2

### C2ME / Async Chunk — the remaining crash (chunk capability deserialization)
- Fixed a crash (`ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 33` in `LockableStorage.deserializeNBT`, e.g. `Couldn't load chunk [97, -71]`) still present in 1.6.1 under C2ME / async chunk loading. The 1.6.0 fix funneled the `LevelChunk` constructor and structure mixins onto the main thread but **missed the chunk capability deserialization path**: Forge reads each chunk's `LockableStorage` NBT on C2ME worker threads, and that code was mutating the world-global, non-thread-safe `LockableHandler` map (and registering observers) directly off-thread.
- `LockableStorage.deserializeNBT` is now **pure chunk-local NBT parsing**: it only hydrates that chunk's own storage, never touches the world handler, observers, packets, or the chunk's level, and makes no thread assumptions. Registration of a chunk's lockables into the world handler now happens on the main server thread via a single new path, `LockableHandler.registerChunkStorage`, driven by `ChunkEvent.Load` (inline without async chunk mods, deferred to the next tick under C2ME). Chunk unload bookkeeping is likewise routed onto the main thread.

### Locks no longer disappear after chunk unload/reload or server restart
- **ID collision across restarts (primary cause).** The lockable id counter (`lastId`) lives on the `LockableHandler`, which is a **Level capability** — and Forge does not persist Level capabilities, so the counter reset to `0` on every server restart. Newly placed locks then reused ids belonging to already-saved locks in unloaded chunks; when an old chunk reloaded, the by-id merge made the old lock adopt the new lock's object and position, so it rendered in the wrong place or vanished. The counter is now persisted per-level via `SavedData` (`LocksSavedData`) and is also advanced to the maximum id seen when chunks register, keeping lock ids globally unique across restarts. It is resolved early on the main thread (`LevelEvent.Load`) so ids stay correct even when structure generation allocates them off-thread under C2ME.
- **Chunk-border identity.** When a lockable spans multiple chunks, whichever chunk loads first now provides the single canonical runtime instance, and later-loading chunks are pointed at it — so a stale chunk copy can never overwrite live lock state, regardless of the order adjacent chunks load or unload.
- **Lock-state persistence.** Changing a lock's state (lock/unlock, combo reshuffle, etc.) now marks **every** loaded chunk the lockable occupies as unsaved, not just the one that triggered the change. Previously the state update only synced to clients and could fail to persist across a save/restart.
- **Client force-load.** The client handler for `AddLockableToChunkPacket` no longer calls `mc.level.getChunk(x, z)`, which force-created a client chunk if it wasn't loaded yet. It now registers the lockable into the client's world handler (the render source) and only touches chunk storage when the chunk is already loaded, via a non-forcing lookup.
- Chunk NBT loading is now defensive: a malformed lockable entry (missing/invalid bounding box, empty lock stack, or any parse error) is skipped with a warning naming the chunk and id, instead of aborting the whole chunk load.

## 1.6.1

### Respawning Structures Compatibility
- Added compatibility with the **Respawning Structures** mod (`someaddons/respawningstructures`). When that mod regenerates a structure, it re-places the structure's chests and loot by replaying its structure pieces, which does not re-run biome decoration — so our `LockChestsFeature` never fired again and respawned chests came back unlocked. Locks Reforged now re-locks the chests of a respawned structure automatically.
- Implemented as a soft dependency via Respawning Structures' public `StructureRespawnEvents.AFTER_RESPAWN_EVENT` callback (wired by reflection, so there is no hard dependency and no effect when the mod is absent). After a structure respawns, stale lockables overlapping the structure footprint are cleared and the freshly placed chests are re-locked using the **same rules as world generation** (generation chance, loot-scaled tiers, and enchant chance), so the lock distribution matches a newly generated structure. Double chests receive a single lock spanning both halves.
- Extracted the per-chest lock selection logic into a shared `LocksUtil.createChestLockable(...)` helper so world generation and the respawn path stay identical; normal world-generation behavior is unchanged.

## 1.6.0

### C2ME / Async Chunk Compatibility
- Fixed a crash (`ArrayIndexOutOfBoundsException` in `LevelChunk`) when running alongside C2ME (Concurrent Chunk Management Engine) or other mods that load/generate chunks on parallel threads (issue #10). The `LevelChunk` constructor mixin was mutating the world-global lockable handler map, registering observers, and sending packets directly from the (now off-thread) constructor, corrupting the non-thread-safe handler map.
- Lockable handler mutation and packet sync from chunk and structure code paths are now funneled onto the main server thread. Without an async chunk mod this work still runs inline on the server thread, so behavior is unchanged; under C2ME it is deferred to the next server tick. A one-time log line notes when compatibility deferral first activates.
- Hardened `LootValueCalculator`'s value cache (now a `ConcurrentHashMap`) against concurrent writes from world-generation worker threads, and snapshotted lockable collections before iterating in the chunk-watch and structure-copy paths to avoid `ConcurrentModificationException`.

### Shocking — Configurable Damage & Theft Punishments
- The Shocking enchantment's damage is no longer hardcoded. New server config (under **Enchantments > Shocking**): **Shocking Damage Base** (default 0.0), **Shocking Damage Per Level** (default 1.5), **Shocking Max Damage** (default 1024.0), **Shocking Requires Enchantment** (default true), and **Shocking Cooldown Ticks** (default 0). The default formula is unchanged: **1.5 damage per enchantment level when a lock pick breaks**.
- New opt-in theft punishments (all default **off**, preserving existing behavior): **Shocking Triggers On Wrong Pin**, **Shocking Triggers On Unauthorized Interaction** (interacting with a locked block without a key), and **Shocking Triggers On Block Break Attempt** (trying to break a protected locked block). The original pick-break shock remains **on** by default. Creative players remain exempt; raising the cooldown is recommended when enabling the interaction triggers.

### Configurable Trades
- Villager and wandering trader lock trades are now configurable (under **Trades**). Lock picks, locks, and lock mechanisms can each be toggled independently for villagers and the wandering trader, so you can disable easy lock picks while still selling locks (or vice versa).
- The lock villager profession is configurable via **Villager Profession** (default `minecraft:toolsmith`).
- Added optional, default-**off** early-game villager lock sales (wood/copper/iron locks at trade levels 1-3) via **Enable Villager Lock Trades**, so `Enable Villager Lockpick Trades = false` + `Enable Villager Lock Trades = true` removes lock picks while letting players buy locks for chest protection. All existing default trades are unchanged when the config is left at defaults.

### Loot-Scaled Locks (hardening + docs)
- Removed the unused **Loot Value Samples** config option (the loot-value estimator is deterministic, so it was never read).
- Expanded the **Loot Value Tiers** documentation with a worked example for making diamond/netherite locks exclusive to high-value chests. The existing system already supports this: chests below the lowest threshold get no lock, and higher loot values map to higher lock tiers.

### Curios Key Ring Behavior
- A key ring worn in a Curios slot now only unlocks/relocks a lock when the player is actually aiming at the lock model, instead of toggling on any right-click of the host block. This stops a worn key ring from instantly re-locking a door or chest the moment you try to open it. Held keys, held key rings, master keys, lock picks, the Awareness enchantment, and the shift-empty-hand Curios key ring screen are unaffected.

### Bug Fixes & Hardening
- Fixed a potential client `NullPointerException` when a lock-state update packet (`UpdateLockablePacket`) arrived while the client level was still null (world load/unload/disconnect); it now guards the level like the other lockable packets.
- Fixed a lock that straddles a chunk boundary disappearing from rendering and stopping its lock/unlock sync when only one of its chunks unloaded. On chunk unload, a lockable is now kept in the handler until none of the chunks it occupies remain loaded.
- Capability providers now invalidate their `LazyOptional` when detached (chunk/level unload, player logout), following the standard Forge pattern.
- Minor cleanup in `Cuboid6i.containedChunksTo` (append instead of index-insert) and an aligned fastutil default-value check in `UpdateLockablePacket`.

## 1.5.4

### Bug Fixes
- Fixed a ghost lockpick briefly appearing at the insertion depth when a pick broke at the far end of a long lock (netherite and other 13+ pin locks). `LockPickingScreen.updatePickParts()` did not clamp the right fragment's texture width, so on long locks the UV overflowed the pick atlas and the edge-clamped sample rendered as a full pick silhouette behind the breaking pieces. The right fragment now clips to the atlas and follows the pick's actual insertion position.

## 1.5.3

### Bug Fixes
- Fixed inability to place locks on containers in Adventure gamemode. Vanilla's `ItemStack.useOn()` short-circuits when `mayBuild` is false, which prevented `LockItem.useOn()` from ever firing. Lock placement now falls back to the `RightClickBlock` event handler when sneak-clicking with a lock item in Adventure mode, matching how picking, relocking, and pickup already worked.

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
