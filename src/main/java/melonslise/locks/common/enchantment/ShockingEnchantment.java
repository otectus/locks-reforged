package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class ShockingEnchantment extends Enchantment
{
	public ShockingEnchantment()
	{
		super(Rarity.UNCOMMON, LocksEnchantments.LOCK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 2 + (level - 1) * 9;
	}

	@Override
	public int getMaxCost(int level)
	{
		return this.getMinCost(level) + 30;
	}

	@Override
	public int getMaxLevel()
	{
		return 5;
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_SHOCKING.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_SHOCKING.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_SHOCKING.get(); }
}
