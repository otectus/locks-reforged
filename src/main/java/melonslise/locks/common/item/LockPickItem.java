package melonslise.locks.common.item;

import java.util.List;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LockTypeRegistry;
import melonslise.locks.common.util.Lockable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

public class LockPickItem extends Item
{
	public static final Component TOO_COMPLEX_MESSAGE = Component.translatable(Locks.ID + ".status.too_complex");
	public static final ResourceLocation NETHERITE_LOCK_PICK_ID = new ResourceLocation(Locks.ID, "netherite_lock_pick");
	public static final int NETHERITE_DURABILITY = 128;
	private static final int NETHERITE_ENCHANTABILITY = 15;

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

	public static boolean usesDurability(ItemStack stack)
	{
		return stack.isDamageableItem() && isNetheriteLockPick(stack);
	}

	public static void damagePick(ItemStack stack, Player player, InteractionHand hand)
	{
		stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
	}

	public static boolean isNetheriteLockPick(ItemStack stack)
	{
		return isNetheriteLockPick(stack.getItem());
	}

	private static boolean isNetheriteLockPick(Item item)
	{
		ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
		return NETHERITE_LOCK_PICK_ID.equals(itemId);
	}

	@Override
	public int getEnchantmentValue()
	{
		return isNetheriteLockPick(this) ? NETHERITE_ENCHANTABILITY : super.getEnchantmentValue();
	}

	@Override
	public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair)
	{
		return isNetheriteLockPick(toRepair) && repair.is(Items.NETHERITE_INGOT) || super.isValidRepairItem(toRepair, repair);
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
