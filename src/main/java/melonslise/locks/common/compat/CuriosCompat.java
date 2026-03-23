package melonslise.locks.common.compat;

import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.item.LockingItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

public final class CuriosCompat
{
	private CuriosCompat() {}

	public static ItemStack findMatchingKeyRing(Player player, int lockId)
	{
		return CuriosApi.getCuriosInventory(player).map(inv ->
		{
			var found = inv.findCurios(LocksItems.KEY_RING.get());
			for (var result : found)
			{
				ItemStack ring = result.stack();
				IItemHandler handler = ring.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
				if (handler == null)
					continue;
				for (int i = 0; i < handler.getSlots(); i++)
				{
					ItemStack key = handler.getStackInSlot(i);
					if (!key.isEmpty() && LockingItem.getOrSetId(key) == lockId)
						return ring;
				}
			}
			return ItemStack.EMPTY;
		}).orElse(ItemStack.EMPTY);
	}

	public static ItemStack findAnyKeyRing(Player player)
	{
		return CuriosApi.getCuriosInventory(player).map(inv ->
		{
			var found = inv.findCurios(LocksItems.KEY_RING.get());
			if (!found.isEmpty())
				return found.get(0).stack();
			return ItemStack.EMPTY;
		}).orElse(ItemStack.EMPTY);
	}
}
