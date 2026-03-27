package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class AwarenessEnchantment extends Enchantment
{
	public AwarenessEnchantment()
	{
		super(Rarity.VERY_RARE, LocksEnchantments.LOCK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 25;
	}

	@Override
	public int getMaxCost(int level)
	{
		return 50;
	}

	@Override
	public int getMaxLevel()
	{
		return 1;
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_AWARENESS.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_AWARENESS.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_AWARENESS.get(); }
}
