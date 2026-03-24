package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class AutoPickEnchantment extends Enchantment
{
	public AutoPickEnchantment()
	{
		super(Rarity.VERY_RARE, LocksEnchantments.LOCK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 15 + (level - 1) * 12;
	}

	@Override
	public int getMaxCost(int level)
	{
		return this.getMinCost(level) + 20;
	}

	@Override
	public int getMaxLevel()
	{
		return 3;
	}

	@Override
	protected boolean checkCompatibility(Enchantment other)
	{
		return super.checkCompatibility(other) && other != LocksEnchantments.COMPLEXITY.get();
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_AUTO_PICK.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_AUTO_PICK.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_AUTO_PICK.get(); }
}
