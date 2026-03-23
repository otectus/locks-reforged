package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.worldgen.LockChestsFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksFeatures
{
	public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Locks.ID);

	public static final RegistryObject<Feature<NoneFeatureConfiguration>>
		LOCK_CHESTS = FEATURES.register("lock_chests", () -> new LockChestsFeature(NoneFeatureConfiguration.CODEC));

	private LocksFeatures() {}

	public static void register()
	{
		FEATURES.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
