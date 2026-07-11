package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.steel.NativeSteelState;
import melonslise.locks.common.steel.NativeSteelPolicy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class LocksCreativeTabs
{
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Locks.ID);

	public static final RegistryObject<CreativeModeTab> LOCKS_TAB = TABS.register("locks",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.locks"))
			.icon(() -> new ItemStack(LocksItems.IRON_LOCK.get()))
			.displayItems((params, output) -> {
				// Best-effort omission of redundant native steel material when an external provider supplies it.
				// This is NOT the source of truth for compatibility (recipes/worldgen are); the default snapshot
				// is all-native, so nothing is hidden before tags load. Functional steel items stay visible.
				NativeSteelState steel = NativeSteelPolicy.get();

				output.accept(LocksItems.SPRING.get());
				output.accept(LocksItems.WOOD_LOCK_MECHANISM.get());
				output.accept(LocksItems.COPPER_LOCK_MECHANISM.get());
				output.accept(LocksItems.IRON_LOCK_MECHANISM.get());
				output.accept(LocksItems.STEEL_LOCK_MECHANISM.get());
				if (steel.nativeIngotActive())
					output.accept(LocksItems.STEEL_INGOT.get());
				if (steel.nativeNuggetActive())
					output.accept(LocksItems.STEEL_NUGGET.get());
				if (steel.nativeOreActive())
				{
					output.accept(LocksBlocks.STEEL_ORE_ITEM.get());
					output.accept(LocksBlocks.DEEPSLATE_STEEL_ORE_ITEM.get());
				}
				output.accept(LocksItems.KEY_BLANK.get());

				output.accept(LocksItems.WOOD_LOCK.get());
				output.accept(LocksItems.COPPER_LOCK.get());
				output.accept(LocksItems.IRON_LOCK.get());
				output.accept(LocksItems.STEEL_LOCK.get());
				output.accept(LocksItems.GOLD_LOCK.get());
				output.accept(LocksItems.DIAMOND_LOCK.get());
				output.accept(LocksItems.NETHERITE_LOCK.get());

				output.accept(LocksItems.KEY.get());
				output.accept(LocksItems.MASTER_KEY.get());
				output.accept(LocksItems.KEY_RING.get());

				output.accept(LocksItems.WOOD_LOCK_PICK.get());
				output.accept(LocksItems.COPPER_LOCK_PICK.get());
				output.accept(LocksItems.IRON_LOCK_PICK.get());
				output.accept(LocksItems.STEEL_LOCK_PICK.get());
				output.accept(LocksItems.GOLD_LOCK_PICK.get());
				output.accept(LocksItems.DIAMOND_LOCK_PICK.get());
				output.accept(LocksItems.NETHERITE_LOCK_PICK.get());
			})
			.build());

	private LocksCreativeTabs() {}

	public static void register()
	{
		TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
