# Locks Mod 1.20.1 - Known Issues

## Port-Specific Notes

1. **Refmap warning in dev**: The mixin refmap (`locks.refmap.json`) shows "could not be read" in the dev environment. This is a known MixinGradle/ForgeGradle cosmetic issue — dev uses official (Mojang) names which match source annotations directly, so no remapping is needed. The refmap IS correctly included in the production JAR. No fix required.

## Native Steel Fallback (new in 1.7.1)

Locks ships its own steel material (`steel_ingot`, `steel_nugget`, `steel_ore`, `deepslate_steel_ore`) and merges it into the standard `forge:ingots/steel` / `forge:nuggets/steel` / `forge:ores/steel` tags, but **prefers a modpack's own steel** when one exists.

**How it works:** one detection service — `common/steel/NativeSteelPolicy` — inspects the *members* of the Forge steel tags, filters out Locks' own IDs (so its ingot never masks a real external provider), and decides per material form whether the native version is active. A custom recipe condition (`locks:steel`, registered via `CraftingHelper` in `FMLCommonSetupEvent`) gates the native-producing recipes; a custom placement modifier (`locks:steel_ore`) gates ore generation from a cached, thread-safe policy snapshot refreshed on `TagsUpdatedEvent`. The native blocks/items are **always registered** (existing worlds/stacks stay valid) — only acquisition is toggled. Server config `Steel Material Mode` (`AUTO` / `FORCE_NATIVE` / `EXTERNAL_ONLY`) overrides the automatic behavior.

**Detection & fallback rules (AUTO):** native ingot ⇔ no foreign ingot; native nugget ⇔ no foreign nugget; native ore gen ⇔ no foreign ore **and** native ingot active. Each form is independent (e.g. a mod with ingots but no nuggets still gets Locks' nugget).

**Verified:** `./gradlew test` (9 new pure-logic policy tests + 16 existing, all pass), `compileJava` / `build` / `runData` clean, JAR contains all blockstates/models/textures/loot/tags/recipes/features/biome-modifier/lang. Dedicated-server boot **alone** logs `ingot=true, nugget=true, oreGeneration=true` with no foreign steel; **with a datapack adding vanilla iron to the three steel tags** it correctly reports the foreign IDs and flips all native fallbacks off — no recipe/worldgen errors either way. Remaining in-game checks (visual ore render/gen, live mode-switch in JEI) are in the Still Needs Testing list below.

## Lock Pick Enchantments (new in 1.7.0)

Five lock-pick-side enchantments in a new `LOCK_PICK` enchantment category, complementing the seven lock-side enchantments and providing counterplay ("locks define resistance, lock picks define technique"). Each is config-gated (enable toggle + tunable values under the `Enchantments` → `Lock Pick` section) and, when disabled, restores the exact prior behavior.

- **Finesse** (RARE, max III) — reduces break chance by boosting effective pick strength in the break roll only (+15%/level default). Never affects Complexity/`canPick`; keeps ≥5% break chance so a pick can't become unbreakable via Finesse. Counters Sturdy. Incompatible with Last Catch.
- **Attunement** (VERY_RARE, max II) — increases effective pick strength against Complex locks inside `canPick` only (+0.10/level). Counters Complexity.
- **Grounded** (RARE, max III) — reduces Shocking-lock damage while a pick is held in either hand (−20%/level, max level across both hands), floored at 0. Counters Shocking.
- **Quiet Hand** (UNCOMMON, max I) — lowers the wrong-pin sound volume (1.0 → 0.25 default); correct-pin sound unchanged.
- **Last Catch** (VERY_RARE, max I) — ~20% chance to cancel a break that would otherwise occur. Incompatible with Finesse.

**Data-driven enchantability:** lock picks are now enchantable at the enchanting table; per-pick enchantability comes from an optional `enchantment_value` field in `data/locks/lockpick_types/*.json` (wood 5, copper 7, iron 10, steel 12, gold 22, diamond 10, netherite 15). Picks with no entry default to 0 (not table-enchantable) and are unaffected.

Verified in the headless build: `./gradlew compileJava`/`build` clean, `runData` boots (registries + config spec build) without error, all JSON valid. In-game behavior is in the Still Needs Testing list below.

## Carry On Compatibility (new in 1.6.4)

Locks are moved together with the block when it is picked up and placed with [Carry On](https://www.curseforge.com/minecraft/mc-mods/carry-on). Server-authoritative; optional (only active when `carryon` is installed) and gated by an `IMixinConfigPlugin`.

**How it works:** three thin mixins into Carry On (`PickupHandler`, `CarryOnData`, `PlacementHandler`) delegate to `common/compat/CarryOnCompat` + `CarriedLockTransfer`. On pickup the intersecting `Lockable`s are serialized into the block entity's Forge persistent data (which rides inside Carry On's own carried `"tile"` NBT) and removed from the world; on placement they are offset to the new position and re-registered via `LockableHandler.add`, preserving lock id, `Lock` combo, locked state, enchantments, Awareness owner, and key/keyring/masterkey compatibility. No new storage — the canonical store stays the per-chunk `LockableStorage` + per-level `LockableHandler`.

**Config:** `[Compatibility.CarryOn]` in the server config — master toggle, allow/deny carrying locked blocks, optional authorization requirement (default off: anyone can carry, the lock just moves), deny partial multi-block pickup (default on), and transfer logging.

**Building:** requires `libs/carryon-forge-1.20.1-2.1.2.7.jar` (gitignored, like `respawningstructures`) on the compile classpath for the mixins. If the jar is absent the build automatically skips the compat (sources/config/mixin wiring excluded) and the mod builds normally.

Manual QA (needs a world with Locks + Carry On; not runnable in the headless build env):

- [ ] Lock a single chest → Carry On it → place elsewhere → lock is visible, locked, pickable, key-compatible; no ghost lock at old pos; no duplicate at new pos
- [ ] Unlocked locked-chest carried and placed, then re-locked with the same key
- [ ] With `Require Authorization` on: no matching key/ring/masterkey/owner in inventory → pickup denied, lock intact
- [ ] Awareness lock → only the owner can carry (auth on)
- [ ] Key ring and Curios key ring authorize (auth on)
- [ ] Carry across chunk borders / dimensions → all affected chunks save
- [ ] Server restart after moving → lock persists
- [ ] Death/drop while carrying (`placeCarried`) → lock restored at drop pos
- [ ] Locked double chest → pickup denied safely (partial multi-block), original lock intact
- [ ] After move: hopper extraction, redstone/open interaction, explosion resistance, break protection all honor the restored lock
- [ ] With Carry On **not** installed → existing Locks behavior unchanged (mixins inert)
- [ ] With C2ME installed → no blocking chunk fetch regressions during carry/place

## Resolved Compatibility Issues

- **C2ME hang — world generation freezes (re-entrant blocking chunk fetch)** *(fixed in 1.6.4)*: A silent hang (no crash report, no exception) during initial world load. C2ME's `beforeAwaitChunk` redirect makes the spawn-chunk-blocked Server thread drain the chunk executor (`managedBlock`), which fires `ChunkEvent.Load` re-entrantly on that same thread; `onChunkLoad` ran inline (already "on the server thread") and called a **blocking** `Level#getChunk(x, z)` that re-parked on the chunk future the thread was mid-completing — a permanent self-deadlock. Proven with live `jstack` dumps on the minimal C2ME + locks repro. Fixed by using the live `LevelChunk` the event already supplies (the re-fetch was redundant) with only a non-blocking `hasChunk` guard; hardening `LocksThreadUtil` (no-blocking-fetch contract + safe null-server degrade); and replacing every `hasChunk`-guarded blocking `getChunk`/`getChunkAt` in `LockableHandler` with the non-blocking `getChunkNow`. Also: border-spanning generated locks on already-finished `ImposterProtoChunk` neighbours are now written through to the wrapped chunk's storage instead of an orphaned (never-drained) list, and per-chunk `LockableStorage` map access is now synchronized (`snapshot()`).

- **C2ME crash — `ArrayIndexOutOfBoundsException` reading the handler map off-thread** *(fixed in 1.6.3)*: The 1.6.2 fix made all *writes* to `LockableHandler.lockables` main-thread-only, but `StructureTemplateMixin#fillFromWorld` still *read* the map from a C2ME worker thread via `new ArrayList<>(handler.getLoaded().values())`. Copying a fastutil open-addressing map while the main thread rehashes it reads a torn backing array — the snapshot itself throws `Index -1`. Fixed by guarding every structural mutation of the map with an internal monitor and adding `LockableHandler.snapshotLoaded()` (a synchronized copy) for the cross-thread readers (`fillFromWorld`, Respawning Structures compat). Also synchronized the neighbour-`ProtoChunk` lock-list writes from `LockChestsFeature` (border-spanning generated locks on worldgen worker threads), drained under the list monitor in `LevelChunkMixin`.

- **Ghost-locked door — blocked with no visible/pickable lock** *(fixed in 1.6.3)*: Door-blocking reads the per-chunk `LockableStorage` (`getInChunk`), while rendering and the pick minigame read the world-global `LockableHandler` index / client mirror. A divergence (e.g. a missed sync under async chunk loading) left a door blocked authoritatively while its lock was invisible and unpickable; force-loading didn't help because the chunk was already loaded. Fixed with a server-side self-healing reconcile at interaction time: the chunk's storage is re-registered into the world index (idempotent, makes the lock canonical and observed so its state persists/syncs), and a blocking lock is pushed to the interacting player so a desynced client re-renders it. The open-screen packet now carries the full lockable so the pick minigame can't dead-end with "Lockable not found" on a sync race; pin order remains server-authoritative.

- **Resurrected / duplicate locks after chunk reload or structure respawn** *(fixed in 1.6.3)*: `LockableHandler.remove` skipped a multi-chunk lockable's chunks that were unloaded, leaving a stale on-disk copy that `registerChunkStorage` resurrected as canonical on reload. On the server `remove` now force-loads every chunk the lockable spans (the chunks already exist on disk) and clears the lock from all of them; the client path stays best-effort and never force-loads.

- **C2ME crash — `ArrayIndexOutOfBoundsException` in `LockableStorage.deserializeNBT`** *(fixed in 1.6.2)*: The 1.6.0 fix funneled the `LevelChunk` constructor and structure-template mixins onto the main server thread, but **missed the chunk capability deserialization path**. Forge reads each chunk's `LockableStorage` NBT inside `ChunkSerializer.read`, which C2ME runs on parallel worker threads, and `deserializeNBT` was mutating the world-global non-thread-safe `LockableHandler.lockables` map (and registering observers) directly off-thread — so concurrent chunk loads corrupted the shared map's backing array (`Index -1 out of bounds for length 33`, surfacing as `Couldn't load chunk [x, z]`). Fixed by making `LockableStorage.deserializeNBT` pure chunk-local NBT parsing (no handler, observer, packet, or level access) and moving world-handler registration to the main thread via `LockableHandler.registerChunkStorage`, driven by `ChunkEvent.Load` (and routed through `LocksThreadUtil` like the other paths). Chunk-unload bookkeeping is likewise deferred to the main thread. Defensive parsing now skips a malformed lockable entry (with a warning) instead of failing the whole chunk load.

- **Locks disappear after chunk reload / server restart** *(fixed in 1.6.2)*: Two causes. (1) The lockable id counter lives on the `LockableHandler`, a **Level capability** that Forge does not persist, so it reset to `0` every restart and newly placed locks reused ids of already-saved (unloaded) locks; on reload the by-id merge made the old lock adopt the new lock's object/position and vanish. Now persisted per-level via `LocksSavedData` and advanced to the max id seen on chunk registration, with an early main-thread bootstrap on `LevelEvent.Load`. (2) Lock state changes only synced to clients without marking all occupied chunks unsaved; state now dirties every loaded chunk the lockable occupies. Chunk-border lockables also now resolve to a single canonical instance regardless of chunk load/unload order, and the client `AddLockableToChunkPacket` handler no longer force-loads chunks.

- **C2ME crash — `ArrayIndexOutOfBoundsException` in `LevelChunk`** *(fixed in 1.6.0, issue #10)*: C2ME (Concurrent Chunk Management Engine) runs chunk loading/generation on parallel worker threads. `LevelChunkMixin` was mutating the world-global `LockableHandler.lockables` map (a non-thread-safe `Int2ObjectLinkedOpenHashMap`), registering observers, and sending packets directly from the `LevelChunk` constructor — so several chunks loading at once corrupted the shared map's backing array, surfacing as an `ArrayIndexOutOfBoundsException`. Fixed by funneling all handler mutation and packet sync onto the main server thread via `LocksThreadUtil.runOnServerThread` (inline when already on the server thread, deferred to the next tick otherwise). The same deferral was applied to `StructureTemplateMixin#placeInWorld`; `ChunkMapMixin` and `StructureTemplateMixin#fillFromWorld` now snapshot lockable collections before iterating; and `LootValueCalculator`'s cache is now a `ConcurrentHashMap`. Behavior without C2ME is unchanged. A one-time INFO log notes when the off-thread deferral path first activates.

## Resolved (Inherited from 1.16.5)

- **Lock reshuffling doesn't persist** *(fixed)*: The original mod regenerated lock combinations from the lock ID seed on every load. If a combination was ever reshuffled at runtime, the change would be lost. Fixed by storing the `combo` byte array directly in NBT (`Lock.toNbt`/`fromNbt`) and syncing it over the network (`Lock.toBuf`/`fromBuf`). Backward compatible — locks saved without a `Combo` tag fall back to seed-based generation.

- **Performance: new quaternion every frame** *(fixed)*: The original code allocated new `Quaternionf` objects on every render frame in 3 locations (`Sprite.draw`, `LocksClientForgeEvents.renderLocks`, `LocksClientUtil.worldToScreen`). Fixed by caching static `Quaternionf` fields and using JOML's in-place mutation methods (`rotationX`/`rotationY`/`rotationZ`).

- **Loot injection behavior change** *(non-issue)*: The original mod merged loot pool entries directly into vanilla loot tables via mixin + GSON deserialization. The 1.20.1 port uses `LootTableLoadEvent` to add pools from inject JSON files. Investigation confirmed this produces identical gameplay results — pool names don't conflict and probability distributions are the same.

- **JOML math migration** *(verified correct)*: All active quaternion/matrix operations in `LocksClientUtil.worldToScreen`, `LocksClientForgeEvents.renderLocks`, and `Sprite.draw` correctly use 1.20.1 JOML patterns. The `vec.transform(quat)` → `quat.transform(vec)` change was applied correctly throughout.

## Resolved During Build

- **Access Transformer SRG mapping**: AT entries updated to use SRG (intermediary) names. Fixed `PistonStructureResolver` package path.
- **CapabilityItemHandler removed**: Replaced with `ForgeCapabilities.ITEM_HANDLER` across 6 files.
- **Item.is(TagKey) API change**: Tag checking moved from `Item` to `ItemStack` — all `.getItem().is(tag)` calls updated to `stack.is(tag)`.
- **IForgeMenuType.regular() removed**: Replaced with `IForgeMenuType.create()`.
- **AbstractContainerScreen.tick() now final**: `LockPickingScreen.tick()` renamed to `containerTick()`.
- **LockPickingContainer missing quickMoveStack**: Added required override returning `ItemStack.EMPTY`.
- **Options.renderDistance type change**: Now `OptionInstance<Integer>`, accessed via `.renderDistance().get()`.
- **RenderType.create() access**: Used 7-parameter overload (already AT'd by Forge) instead of 5-parameter package-private version.
- **Random vs RandomSource**: `LocksConfig` methods updated to accept `RandomSource` instead of `java.util.Random`.
- **getPlayers() returns List**: Added `.stream()` call in `LocksPacketDistributors`.
- **ModConfigEvent promoted**: Changed from inner class `ModConfig.ModConfigEvent` to top-level `ModConfigEvent`.

## Resolved During Runtime Testing

- **LevelMixin `hasNeighborSignal` failure**: In 1.20.1, `hasNeighborSignal` moved from `Level` to the `SignalGetter` interface as a default method. Mixin cannot `@Inject` or `@Overwrite` interface default methods on the implementing class. Fixed by having `LevelMixin implements SignalGetter` and providing an `@Override` method that checks the lock state before delegating to the original signal-checking logic.
- **Registry already frozen**: Items, enchantments, menu types, features, and recipe serializers were being eagerly instantiated during class loading (via static field initializers like `new Item(...)`). In 1.20.1, `Item.<init>()` calls `createIntrusiveHolder()` which requires the registry to be unfrozen. Fixed by wrapping all instantiation in lambda suppliers: `ITEMS.register("name", () -> new Item(...))`.
- **build.gradle mixin config incomplete**: Added `config "${project.mod_id}.mixins.json"` to the `mixin {}` block and `mixin.env.remapRefMap`/`mixin.env.refMapRemappingFile` properties to run configs.

## Testing Status

### Verified
- [x] All 15 mixins apply successfully at runtime (no MixinApplyError)
- [x] Mod loads to title screen without errors
- [x] Config files created with correct defaults
- [x] Creative tab registered

### Still Needs Testing
- [ ] Lock persistence across chunk unload/reload
- [ ] Multiplayer sync when players enter lock areas (ChunkMapMixin packet fix applied in 1.3.0)
- [ ] Structure template save/load with locks
- [ ] Modded block compatibility (blocks without BlockEntities)
- [ ] Loot table injection (verify lock picks/mechanisms appear in dungeon chests)
- [ ] Villager/wanderer trades (verify lock items in trade lists)
- [ ] World generation (verify locked chests spawn in overworld)
- [ ] Lock picking minigame UI
- [ ] Key ring container UI
- [ ] Enchantment application on locks (7 lock-side: Shocking, Sturdy, Complexity, Silent, Auto-Pick, Reinforced, Awareness)
- [ ] Silent enchantment suppresses rattle sound
- [ ] Auto-Pick enchantment bypasses minigame at correct rates (10%/20%/30%)
- [ ] Reinforced enchantment scales explosion resistance
- [ ] Silent/Shocking incompatibility enforced by enchanting table
- [ ] Auto-Pick/Complexity incompatibility enforced by enchanting table
- [ ] Awareness enchantment stores owner UUID on lock placement
- [ ] Awareness lock auto-unlocks for owner (toggle behavior)
- [ ] Awareness lock rattles for non-owner players
- [ ] Overlapping Awareness locks from different owners work independently
- [ ] Awareness config toggle disables the enchantment
- [ ] Lock picks enchantable at the enchanting table; `enchantment_value` read from `lockpick_types/*.json` (netherite still 15)
- [ ] Finesse lowers observed break chance (I/II/III); never lets a pick bypass Complexity (`canPick` unaffected); break chance never drops to 0
- [ ] Attunement lets a weak pick cross a Complexity threshold (I >0.25, II >0.50); disabling it restores the failure
- [ ] Grounded reduces Shocking damage per level (−20/−40/−60%), from either hand, taking the max level; disabling restores full damage
- [ ] Quiet Hand reduces wrong-pin volume (1.0 → 0.25) only when present; correct-pin sound unchanged
- [ ] Last Catch occasionally saves a pick (~20%); anvil/table rejects combining it with Finesse
- [ ] Netherite unbreakable config still wins over Finesse/Last Catch; Grounded/Quiet Hand still function
- [ ] Each lock-pick enchantment's config toggle disables it (hidden from table/trades/loot) and its tunable values take effect
- [ ] Netherite lock has 14 pins in lock picking screen
- [ ] Netherite lock pick has 0.95 strength in tooltip
- [ ] Netherite lock pick rarely breaks (high strength)
- [ ] Netherite Lockpick Unbreakable config prevents breaking entirely
- [ ] Netherite lock and lock pick survive in lava (fire-resistant)
- [ ] Netherite smithing recipes work at smithing table
- [ ] Netherite items appear in creative tab after diamond
- [ ] Netherite lock pick sold by level 5 toolsmith
- [ ] Netherite lock sold by wandering trader (rare, enchanted)
- [ ] Async loot precompute doesn't block world loading
- [ ] Lockpicking GUI renders correctly with ImmediatelyFast installed
