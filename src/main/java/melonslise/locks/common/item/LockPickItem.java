package melonslise.locks.common.item;

import java.util.List;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LocksTagHelper;
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
	public static final int MAX_STACK_SIZE = 64;
	public static final Component TOO_COMPLEX_MESSAGE = Component.translatable(Locks.ID + ".status.too_complex");
	public static final ResourceLocation NETHERITE_LOCK_PICK_ID = new ResourceLocation(Locks.ID, "netherite_lock_pick");

	public LockPickItem(Properties props)
	{
		super(props);
	}

	public static final String KEY_STRENGTH = "Strength";

	// Read-only, unlike the 1.7.2 getOrSetStrength it replaces. That one wrote NBT on every read, so a
	// pick that had merely been looked at stopped stacking with a fresh one, and every caller had to
	// guarantee it never saw an empty stack (whose tag is a shared singleton). The registry is the source
	// of truth; a legacy Strength tag is still honoured so picks saved before 1.7.3 keep their value.
	public static float getStrength(ItemStack stack)
	{
		if(stack.isEmpty())
			return 0f;
		CompoundTag nbt = stack.getTag();
		if(nbt != null && nbt.contains(KEY_STRENGTH))
			return nbt.getFloat(KEY_STRENGTH);
		return LockTypeRegistry.getLockPickStats(stack.getItem()).strength();
	}

	public static boolean canPick(ItemStack stack, int cmp)
	{
		float strength = getStrength(stack);
		if(LocksServerConfig.ENABLE_ATTUNEMENT.get())
			strength += EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.ATTUNEMENT.get(), stack)
				* (float) (double) LocksServerConfig.ATTUNEMENT_STRENGTH_PER_LEVEL.get();
		return strength > cmp * 0.25f;
	}

	public static boolean canPick(ItemStack stack, Lockable lkb)
	{
		return canPick(stack, LocksServerConfig.ENABLE_COMPLEXITY.get()
			? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.COMPLEXITY.get(), lkb.stack) : 0);
	}

	// Every pick tier carries durability as of 1.7.3, so this is no longer netherite-only. A definition
	// that sets durability to 0 registers a pick with no pool at all, which simply never wears down.
	public static boolean usesDurability(ItemStack stack)
	{
		return stack.isDamageableItem();
	}

	/**
	 * Damages exactly one pick and reports whether that pick broke.
	 *
	 * Damage is stored on an ItemStack, not on an individual item within it. Keeping a damaged multi-item
	 * stack would therefore make every pick appear damaged and would duplicate that damage if the player
	 * split the stack. Work on a singleton copy instead. If it survives with new damage, it becomes the held
	 * pick and the untouched remainder goes back into the inventory. If it breaks, only one item is removed
	 * from the original stack and the next pristine pick remains ready in the hand.
	 */
	public static boolean damagePick(ItemStack stack, Player player, InteractionHand hand, int amount)
	{
		if(stack.getCount() <= 1)
		{
			int countBefore = stack.getCount();
			stack.hurtAndBreak(amount, player, broken -> broken.broadcastBreakEvent(hand));
			return stack.getCount() < countBefore;
		}

		ItemStack wornPick = stack.copy();
		wornPick.setCount(1);
		int damageBefore = wornPick.getDamageValue();
		wornPick.hurtAndBreak(amount, player, broken -> broken.broadcastBreakEvent(hand));

		if(wornPick.isEmpty())
		{
			stack.shrink(1);
			// A multi-item stack represents one active pick followed by pristine picks. Vanilla resets this
			// after a singleton breaks; do the same for the pristine remainder of our stack.
			if(!stack.isEmpty())
				stack.setDamageValue(0);
			return true;
		}

		// Creative mode and Unbreaking can prevent all requested damage. Keep the stack together unless
		// this particular pick actually acquired wear.
		if(wornPick.getDamageValue() == damageBefore)
			return false;

		ItemStack pristineRemainder = stack.copy();
		pristineRemainder.shrink(1);
		pristineRemainder.setDamageValue(0);
		player.setItemInHand(hand, wornPick);
		// This merges with another pristine stack when possible and drops the remainder beside the player
		// when every inventory slot is full, so splitting can never silently delete picks.
		player.getInventory().placeItemBackInInventory(pristineRemainder);
		return false;
	}

	/**
	 * Forge's stack-sensitive hook overrides the size forced to one by Properties#durability. A pristine
	 * damageable stack remains mergeable; vanilla already refuses to merge a worn singleton.
	 */
	@Override
	public int getMaxStackSize(ItemStack stack)
	{
		return MAX_STACK_SIZE;
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
	public boolean isEnchantable(ItemStack stack)
	{
		return stack.getCount() == 1 && LocksTagHelper.isLockPick(stack);
	}

	// The anvil never consults isEnchantable, and its result is a copy of the whole left input, count
	// included: one enchanted book laid against 64 pristine picks would enchant all 64 for a flat 40
	// levels. Forge calls this hook at the end of AnvilMenu#createResult, so refusing a stack here blanks
	// the result slot. A single pick is unaffected.
	@Override
	public boolean isBookEnchantable(ItemStack stack, ItemStack book)
	{
		return stack.getCount() == 1;
	}

	@Override
	public int getEnchantmentValue()
	{
		int value = LockTypeRegistry.getLockPickStats(this).enchantmentValue();
		return value > 0 ? value : super.getEnchantmentValue();
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
		lines.add(Component.translatable(Locks.ID + ".tooltip.strength", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(getStrength(stack))).withStyle(ChatFormatting.DARK_GREEN));
	}
}
