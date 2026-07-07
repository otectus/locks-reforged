package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.enchantment.AttunementEnchantment;
import melonslise.locks.common.enchantment.AutoPickEnchantment;
import melonslise.locks.common.enchantment.AwarenessEnchantment;
import melonslise.locks.common.enchantment.ComplexityEnchantment;
import melonslise.locks.common.enchantment.FinesseEnchantment;
import melonslise.locks.common.enchantment.GroundedEnchantment;
import melonslise.locks.common.enchantment.LastCatchEnchantment;
import melonslise.locks.common.enchantment.QuietHandEnchantment;
import melonslise.locks.common.enchantment.ReinforcedEnchantment;
import melonslise.locks.common.enchantment.ShockingEnchantment;
import melonslise.locks.common.enchantment.SilentEnchantment;
import melonslise.locks.common.enchantment.SturdyEnchantment;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockPickItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksEnchantments
{
	public static final EnchantmentCategory LOCK_TYPE = EnchantmentCategory.create("LOCK", item -> item instanceof LockItem);
	public static final EnchantmentCategory LOCK_PICK_TYPE = EnchantmentCategory.create("LOCK_PICK", item -> item instanceof LockPickItem);

	public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Locks.ID);

	public static final RegistryObject<Enchantment>
		SHOCKING = ENCHANTMENTS.register("shocking", ShockingEnchantment::new),
		STURDY = ENCHANTMENTS.register("sturdy", SturdyEnchantment::new),
		COMPLEXITY = ENCHANTMENTS.register("complexity", ComplexityEnchantment::new),
		SILENT = ENCHANTMENTS.register("silent", SilentEnchantment::new),
		AUTO_PICK = ENCHANTMENTS.register("auto_pick", AutoPickEnchantment::new),
		REINFORCED = ENCHANTMENTS.register("reinforced", ReinforcedEnchantment::new),
		AWARENESS = ENCHANTMENTS.register("awareness", AwarenessEnchantment::new),
		FINESSE = ENCHANTMENTS.register("finesse", FinesseEnchantment::new),
		ATTUNEMENT = ENCHANTMENTS.register("attunement", AttunementEnchantment::new),
		GROUNDED = ENCHANTMENTS.register("grounded", GroundedEnchantment::new),
		QUIET_HAND = ENCHANTMENTS.register("quiet_hand", QuietHandEnchantment::new),
		LAST_CATCH = ENCHANTMENTS.register("last_catch", LastCatchEnchantment::new);

	private LocksEnchantments() {}

	public static void register()
	{
		ENCHANTMENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
