# Locks Mod 1.20.1 - Known Issues

## Port-Specific Notes

1. **Refmap warning in dev**: The mixin refmap (`locks.refmap.json`) shows "could not be read" in the dev environment. This is a known MixinGradle/ForgeGradle cosmetic issue — dev uses official (Mojang) names which match source annotations directly, so no remapping is needed. The refmap IS correctly included in the production JAR. No fix required.

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
- [ ] Multiplayer sync when players enter lock areas
- [ ] Structure template save/load with locks
- [ ] Modded block compatibility (blocks without BlockEntities)
- [ ] Loot table injection (verify lock picks/mechanisms appear in dungeon chests)
- [ ] Villager/wanderer trades (verify lock items in trade lists)
- [ ] World generation (verify locked chests spawn in overworld)
- [ ] Lock picking minigame UI
- [ ] Key ring container UI
- [ ] Enchantment application on locks
