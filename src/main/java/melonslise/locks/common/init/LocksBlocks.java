package melonslise.locks.common.init;

import melonslise.locks.Locks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Native steel ore blocks. These are ALWAYS registered under stable IDs regardless of the configured
 * {@link melonslise.locks.common.steel.SteelMaterialMode} or any installed steel mod — only their generation and
 * production recipes are gated by {@link melonslise.locks.common.steel.NativeSteelPolicy}. Registered from
 * {@link melonslise.locks.Locks} before {@code LocksItems} so the block items resolve.
 *
 * <p>Properties mirror vanilla iron / deepslate iron ore. The blocks drop themselves (standard Silk-Touch-safe
 * block loot) and are smelted/blasted into {@code locks:steel_ingot}.
 */
public final class LocksBlocks
{
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Locks.ID);
	public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Locks.ID);

	public static final RegistryObject<Block> STEEL_ORE = BLOCKS.register("steel_ore",
		() -> new Block(BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE)
			.requiresCorrectToolForDrops()
			.strength(3.0F, 3.0F)
			.sound(SoundType.STONE)));

	public static final RegistryObject<Block> DEEPSLATE_STEEL_ORE = BLOCKS.register("deepslate_steel_ore",
		() -> new Block(BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE)
			.requiresCorrectToolForDrops()
			.strength(4.5F, 3.0F)
			.sound(SoundType.DEEPSLATE)));

	public static final RegistryObject<Item> STEEL_ORE_ITEM = BLOCK_ITEMS.register("steel_ore",
		() -> new BlockItem(STEEL_ORE.get(), new Item.Properties()));

	public static final RegistryObject<Item> DEEPSLATE_STEEL_ORE_ITEM = BLOCK_ITEMS.register("deepslate_steel_ore",
		() -> new BlockItem(DEEPSLATE_STEEL_ORE.get(), new Item.Properties()));

	private LocksBlocks() {}

	public static void register()
	{
		BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
		BLOCK_ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
