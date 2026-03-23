package melonslise.locks.common.compat;

import melonslise.locks.Locks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class CuriosHelper
{
	private static boolean curiosLoaded = false;

	private CuriosHelper() {}

	public static void init()
	{
		curiosLoaded = ModList.get().isLoaded("curios");
		if (curiosLoaded)
			Locks.LOGGER.info("Curios detected — key ring curio support enabled");
	}

	public static ItemStack findMatchingKeyRing(Player player, int lockId)
	{
		if (!curiosLoaded)
			return ItemStack.EMPTY;
		return CuriosCompat.findMatchingKeyRing(player, lockId);
	}

	public static ItemStack findAnyKeyRing(Player player)
	{
		if (!curiosLoaded)
			return ItemStack.EMPTY;
		return CuriosCompat.findAnyKeyRing(player);
	}
}
