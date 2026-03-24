package melonslise.locks.common.item;

import java.util.List;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LockTypeRegistry;
import melonslise.locks.common.util.Lockable;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class LockPickItem extends Item
{
	public static final Component TOO_COMPLEX_MESSAGE = Component.translatable(Locks.ID + ".status.too_complex");

	public LockPickItem(Properties props)
	{
		super(props);
	}

	public static final String KEY_STRENGTH = "Strength";

	// WARNING: EXPECTS LOCKPICKITEM STACK
	public static float getOrSetStrength(ItemStack stack)
	{
		CompoundTag nbt = stack.getOrCreateTag();
		if(!nbt.contains(KEY_STRENGTH))
			nbt.putFloat(KEY_STRENGTH, LockTypeRegistry.getLockPickStats(stack.getItem()).strength());
		return nbt.getFloat(KEY_STRENGTH);
	}

	public static boolean canPick(ItemStack stack, int cmp)
	{
		return getOrSetStrength(stack) > cmp * 0.25f;
	}

	public static boolean canPick(ItemStack stack, Lockable lkb)
	{
		return canPick(stack, LocksServerConfig.ENABLE_COMPLEXITY.get()
			? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.COMPLEXITY.get(), lkb.stack) : 0);
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> lines, TooltipFlag flag)
	{
		super.appendHoverText(stack, world, lines, flag);
		float displayStrength;
		if (stack.hasTag() && stack.getTag().contains(KEY_STRENGTH))
			displayStrength = stack.getTag().getFloat(KEY_STRENGTH);
		else
			displayStrength = LockTypeRegistry.getLockPickStats(this).strength();
		lines.add(Component.translatable(Locks.ID + ".tooltip.strength", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(displayStrength)).withStyle(ChatFormatting.DARK_GREEN));
	}
}
