package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.worldgen.ChestPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class LocksPlacements
{
	public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Locks.ID);

	public static final RegistryObject<PlacementModifierType<ChestPlacement>>
		CHEST = PLACEMENT_MODIFIERS.register("chest", () -> () -> ChestPlacement.CODEC);

	private LocksPlacements() {}

	public static void register()
	{
		PLACEMENT_MODIFIERS.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
