package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class SilentEnchantment extends Enchantment
{
	public SilentEnchantment()
	{
		super(Rarity.UNCOMMON, LocksEnchantments.LOCK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 10;
	}

	@Override
	public int getMaxCost(int level)
	{
		return 40;
	}

	@Override
	public int getMaxLevel()
	{
		return 1;
	}

	@Override
	protected boolean checkCompatibility(Enchantment other)
	{
		return super.checkCompatibility(other) && other != LocksEnchantments.SHOCKING.get();
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_SILENT.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_SILENT.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_SILENT.get(); }
}
