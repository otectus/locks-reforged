package melonslise.locks.common.item;

import java.util.List;

import melonslise.locks.Locks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * An uncut key. Exists only to explain, on the item itself, how pairing works — the recipe copies NBT
 * and so cannot be shown by an ordinary recipe listing.
 *
 * Deliberately extends Item rather than LockingItem: a blank carries no lock id (LockingItem would
 * stamp one every inventory tick and show a meaningless ID line), and LockingItem forces stacksTo(1),
 * which would stop blanks stacking and silently truncate the stacks in existing worlds.
 */
public class KeyBlankItem extends Item
{
	public static final Component PAIR_TOOLTIP = Component.translatable(Locks.ID + ".tooltip.key_blank.pair");
	public static final Component COPY_TOOLTIP = Component.translatable(Locks.ID + ".tooltip.key_blank.copy");

	public KeyBlankItem(Properties props)
	{
		super(props);
	}

	// Says nothing about any particular lock, so it stays useful and safe with Hide Lock ID enabled.
	@OnlyIn(Dist.CLIENT)
	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> lines, TooltipFlag flag)
	{
		super.appendHoverText(stack, world, lines, flag);
		lines.add(PAIR_TOOLTIP.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		lines.add(COPY_TOOLTIP.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
