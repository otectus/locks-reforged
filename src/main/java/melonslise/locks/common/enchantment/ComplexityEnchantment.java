package melonslise.locks.common.enchantment;

import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class ComplexityEnchantment extends Enchantment
{
	public ComplexityEnchantment()
	{
		super(Rarity.VERY_RARE, LocksEnchantments.LOCK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 7 + level * 10;
	}

	@Override
	public int getMaxCost(int level)
	{
		return this.getMinCost(level) + 15;
	}

	@Override
	public int getMaxLevel()
	{
		return 3;
	}
}
