package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.enchantment.ComplexityEnchantment;
import melonslise.locks.common.enchantment.ShockingEnchantment;
import melonslise.locks.common.enchantment.SturdyEnchantment;
import melonslise.locks.common.item.LockItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksEnchantments
{
	public static final EnchantmentCategory LOCK_TYPE = EnchantmentCategory.create("LOCK", item -> item instanceof LockItem);

	public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Locks.ID);

	public static final RegistryObject<Enchantment>
		SHOCKING = ENCHANTMENTS.register("shocking", ShockingEnchantment::new),
		STURDY = ENCHANTMENTS.register("sturdy", SturdyEnchantment::new),
		COMPLEXITY = ENCHANTMENTS.register("complexity", ComplexityEnchantment::new);

	private LocksEnchantments() {}

	public static void register()
	{
		ENCHANTMENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
