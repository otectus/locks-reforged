package melonslise.locks.common.item;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class LockingItem extends Item
{
	public LockingItem(Properties props)
	{
		super(props.stacksTo(1));
	}

	public static final String KEY_ID = "Id";

	public static ItemStack copyId(ItemStack from, ItemStack to)
	{
		to.getOrCreateTag().putInt(KEY_ID, getOrSetId(from));
		return to;
	}

	public static int getOrSetId(ItemStack stack)
	{
		CompoundTag nbt = stack.getOrCreateTag();
		if(!nbt.contains(KEY_ID, Tag.TAG_INT))
			nbt.putInt(KEY_ID, ThreadLocalRandom.current().nextInt());
		return nbt.getInt(KEY_ID);
	}

	// Type-checked, not merely key-present: an Id written as a string, a list or a compound by another mod or a
	// hand-edited save must be refused outright. CompoundTag#getInt would quietly return 0 for those, collapsing
	// every mistyped stack onto the single credential 0 — one key that opens every such lock.
	public static boolean hasId(ItemStack stack)
	{
		CompoundTag nbt = stack.getTag();
		return nbt != null && nbt.contains(KEY_ID, Tag.TAG_INT);
	}

	// Read-only counterpart to getOrSetId, which writes a random id into whatever stack it is handed —
	// including ItemStack.EMPTY, whose tag is a shared singleton. Use this anywhere the stack might be
	// empty or might legitimately have no id yet.
	public static OptionalInt readId(ItemStack stack)
	{
		return hasId(stack) ? OptionalInt.of(stack.getTag().getInt(KEY_ID)) : OptionalInt.empty();
	}

	// Locks and keys get their id the moment they are crafted, so a lock taken straight from a crafting
	// result into a crafting grid already carries one. inventoryTick below remains the backstop for
	// /give, loot, worldgen and third-party creation paths that never pass through crafting.
	@Override
	public void onCraftedBy(ItemStack stack, Level world, net.minecraft.world.entity.player.Player player)
	{
		super.onCraftedBy(stack, world, player);
		if(!world.isClientSide)
			getOrSetId(stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected)
	{
		if(!world.isClientSide)
			getOrSetId(stack);
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> lines, TooltipFlag flag)
	{
		if(!LocksServerConfig.HIDE_LOCK_ID.get() && hasId(stack))
			lines.add(Component.translatable(Locks.ID + ".tooltip.id", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(getOrSetId(stack))).withStyle(ChatFormatting.DARK_GREEN));
	}
}
