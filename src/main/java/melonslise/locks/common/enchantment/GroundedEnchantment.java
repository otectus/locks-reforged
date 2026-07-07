package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class GroundedEnchantment extends Enchantment
{
	public GroundedEnchantment()
	{
		super(Rarity.RARE, LocksEnchantments.LOCK_PICK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 5 + (level - 1) * 10;
	}

	@Override
	public int getMaxCost(int level)
	{
		return this.getMinCost(level) + 40;
	}

	@Override
	public int getMaxLevel()
	{
		return 3;
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_GROUNDED.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_GROUNDED.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_GROUNDED.get(); }
}
