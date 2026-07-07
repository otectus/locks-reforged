package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class LastCatchEnchantment extends Enchantment
{
	public LastCatchEnchantment()
	{
		super(Rarity.VERY_RARE, LocksEnchantments.LOCK_PICK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 12;
	}

	@Override
	public int getMaxCost(int level)
	{
		return this.getMinCost(level) + 50;
	}

	@Override
	public int getMaxLevel()
	{
		return 1;
	}

	@Override
	protected boolean checkCompatibility(Enchantment other)
	{
		return super.checkCompatibility(other) && other != LocksEnchantments.FINESSE.get();
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_LAST_CATCH.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_LAST_CATCH.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_LAST_CATCH.get(); }
}
