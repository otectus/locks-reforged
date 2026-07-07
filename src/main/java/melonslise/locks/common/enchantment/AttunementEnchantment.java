package melonslise.locks.common.enchantment;

import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;

public class AttunementEnchantment extends Enchantment
{
	public AttunementEnchantment()
	{
		super(Rarity.VERY_RARE, LocksEnchantments.LOCK_PICK_TYPE, new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
	}

	@Override
	public int getMinCost(int level)
	{
		return 8 + (level - 1) * 12;
	}

	@Override
	public int getMaxCost(int level)
	{
		return this.getMinCost(level) + 40;
	}

	@Override
	public int getMaxLevel()
	{
		return 2;
	}

	@Override
	public boolean isDiscoverable() { return LocksServerConfig.ENABLE_ATTUNEMENT.get(); }

	@Override
	public boolean isTradeable() { return LocksServerConfig.ENABLE_ATTUNEMENT.get(); }

	@Override
	public boolean isAllowedOnBooks() { return LocksServerConfig.ENABLE_ATTUNEMENT.get(); }
}
