package melonslise.locks.common.init;

import melonslise.locks.common.item.KeyItem;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockPickItem;
import net.minecraft.world.item.ItemStack;

public final class LocksTagHelper
{
	private LocksTagHelper() {}

	public static boolean isLock(ItemStack stack)
	{
		return stack.getItem() instanceof LockItem || stack.is(LocksItemTags.LOCKS);
	}

	public static boolean isLockPick(ItemStack stack)
	{
		return stack.getItem() instanceof LockPickItem || stack.is(LocksItemTags.LOCK_PICKS);
	}

	public static boolean isKey(ItemStack stack)
	{
		return stack.getItem() instanceof KeyItem || stack.is(LocksItemTags.KEYS);
	}
}
