# Locks Mod Porting Notes: 1.16.5 → 1.20.1

## Build System
- ForgeGradle 4.1 → 6.0, Gradle 8.1.1
- Java 8 → Java 17
- Forge 1.16.5-36.1.4 → 1.20.1-47.2.0
- Mixin setup via `mixingradle 0.7-SNAPSHOT` + `annotationProcessor` (same plugins, updated versions)
- pack_format 6 → 15
- Must set `JAVA_HOME` to JDK 17 if other JDKs are on PATH

## Class Renames (Major)
| 1.16.5 | 1.20.1 |
|--------|--------|
| World | Level |
| ServerWorld | ServerLevel |
| TileEntity | BlockEntity |
| ChestTileEntity | ChestBlockEntity |
| LockableTileEntity | BaseContainerBlockEntity |
| HopperTileEntity | HopperBlockEntity |
| AbstractFurnaceTileEntity | AbstractFurnaceBlockEntity |
| PlayerEntity | Player |
| ServerPlayerEntity | ServerPlayer |
| Container | AbstractContainerMenu |
| ContainerType | MenuType |
| ItemGroup | CreativeModeTab (event-based) |
| Chunk | LevelChunk |
| ChunkPrimer | ProtoChunk |
| ChunkManager | ChunkMap |
| Template | StructureTemplate |
| PistonBlockStructureHelper | PistonStructureResolver |
| CompoundNBT | CompoundTag |
| PacketBuffer | FriendlyByteBuf |
| MatrixStack | PoseStack |
| ClippingHelper | Frustum |
| ScreenManager | MenuScreens |

## Capability System
- `@CapabilityInject` removed → use `CapabilityManager.get(new CapabilityToken<>(){})`
- `Capability.IStorage` removed → serialization handled directly by `ICapabilitySerializable`
- `CapabilityManager.INSTANCE.register()` removed → use `RegisterCapabilitiesEvent`
- `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY` → `ForgeCapabilities.ITEM_HANDLER`
- `CapabilityStorage.java` and `EmptyCapabilityStorage.java` deleted

## Creative Tabs
- `ItemGroup` anonymous class removed
- New `LocksCreativeTabs.java` using `DeferredRegister<CreativeModeTab>` with builder pattern
- Items added via `displayItems()` callback, not `Item.Properties.tab()`

## Damage Types
- `DamageSource` constructors removed → data-driven via `data/locks/damage_type/shock.json`
- `bypassArmor()` → add to `data/minecraft/tags/damage_type/bypasses_armor.json`
- Runtime: `LocksDamageSources.shock(level)` creates source from registry

## Loot Table Injection
- Original: Mixin on `LootTableManager` + manual GSON deserialization + pool merging
- New: `LootTableLoadEvent` handler parses inject JSON pools via `Deserializers.createLootTableSerializer()` GSON
- Pools added via `e.getTable().addPool(pool)` from Forge's mutable LootTable
- Deleted mixins: `LootTableManagerMixin`, `LootTableAccessor`, `LootPoolAccessor`, `ForgeHooksAccessor`
- Deleted utilities: `LocksUtil.resourceManager`, `LocksUtil.lootTableFrom()`, `LocksUtil.mergeEntries()`

## World Generation
- `BiomeLoadingEvent` removed → JSON biome modifier at `data/locks/forge/biome_modifier/`
- `LocksConfiguredFeatures.java` and `LocksConfiguredPlacements.java` deleted → JSON data files
- `Placement<NoPlacementConfig>` → `PlacementModifier` with Codec
- `Feature.place(ISeedReader, ChunkGenerator, Random, BlockPos, Config)` → `Feature.place(FeaturePlaceContext<Config>)`
- Feature/placement registered via JSON `configured_feature` and `placed_feature`
- `java.util.Random` → `net.minecraft.util.RandomSource` in config/utility methods

## Rendering (Client)
- `GuiGraphics` replaces direct `PoseStack`/`AbstractGui` rendering in screens
- `render(PoseStack, ...)` → `render(GuiGraphics, ...)`
- Mojang math library → JOML (`Matrix4f`, `Vector3f`, `Vector4f`)
- `IRenderTypeBuffer` → `MultiBufferSource`
- `ActiveRenderInfo` → `Camera`
- `DefaultVertexFormats` → `DefaultVertexFormat`
- `LineState` → `RenderStateShard.LineStateShard`
- `RenderType.create()` 5-param (package-private) → use 7-param overload (AT'd by Forge)
- `Options.renderDistance` field → `Options.renderDistance().get()` (`OptionInstance<Integer>`)
- `AbstractContainerScreen.tick()` now final → override `containerTick()` instead

## Menu/Container System
- `LocksContainerTypes` → `LocksMenuTypes`
- `IForgeMenuType.regular()` → `IForgeMenuType.create()`
- `AbstractContainerMenu.quickMoveStack()` now abstract, must be overridden
- `SpecialRecipeSerializer` → `SimpleCraftingRecipeSerializer`
- `Item.is(TagKey)` removed → use `ItemStack.is(TagKey)` instead

## Config Events
- `ModConfig.ModConfigEvent` (inner class) → `net.minecraftforge.fml.event.config.ModConfigEvent` (top-level)

## Networking
- `net.minecraftforge.fml.network.*` → `net.minecraftforge.network.*`
- `PacketBuffer` → `FriendlyByteBuf`
- `ServerChunkCache.chunkMap.getPlayers()` now returns `List<ServerPlayer>` (was `Stream`)
- SimpleChannel API unchanged

## Entity Level Access
- `entity.level` (field) → `entity.level()` (method) everywhere

## Mixin Targets
- 4 loot-related mixins deleted (replaced by event)
- 14 mixins renamed to match 1.20.1 class names
- Method descriptors simplified to bare names for better SRG mapping compatibility
- `StructureTemplate.fillFromWorld` 3rd param changed from `BlockPos` to `Vec3i`
- `StructureTemplate.load` gained `HolderGetter<Block>` parameter
- `LevelChunk` constructor gained `PostLoadProcessor` parameter
- `ChunkMap.playerLoadedChunk` signature changed (MutableObject wrapper)
- `PistonStructureResolver` moved to `net.minecraft.world.level.block.piston` package

## Access Transformers
- AT entries MUST use SRG (intermediary) names (e.g., `m_113006_`, `f_109442_`), NOT official mapped names
- Removed `LootTableManager.GSON` and `WorldDecoratingHelper.level` entries
- Added `PistonStructureResolver` (f_60409_, f_60412_), `Explosion` (f_46012_), `Frustum` (m_113006_), `LevelRenderer` (f_109442_), `GameRenderer` (m_109141_) entries
- `RenderType.create` 7-param version is already AT'd by Forge's parent AT
