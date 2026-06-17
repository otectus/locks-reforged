package melonslise.locks.common.compat;

import melonslise.locks.Locks;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Lockable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
		if (!curiosLoaded || !isAimingAtLock(player, lockId))
			return ItemStack.EMPTY;
		return CuriosCompat.findMatchingKeyRing(player, lockId);
	}

	// Curios key rings are passive (no held item to aim with), so we only authorize
	// lock toggling when the player is deliberately looking at the lock model itself.
	// Otherwise the keychain would toggle on any right-click of the host block,
	// re-locking a door/chest the instant the player tries to open it.
	private static boolean isAimingAtLock(Player player, int lockId)
	{
		ILockableHandler handler = player.level()
			.getCapability(LocksCapabilities.LOCKABLE_HANDLER)
			.orElse(null);
		if (handler == null)
			return false;

		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 target = eye.add(player.getViewVector(1.0F).scale(5.0D));

		for (Lockable lkb : handler.getLoaded().values())
		{
			if (lkb.lock.id != lockId)
				continue;
			Lockable.State state = lkb.getLockState(player.level());
			if (state != null && state.bb.inflate(1d / 32d).clip(eye, target).isPresent())
				return true;
		}
		return false;
	}

	public static ItemStack findAnyKeyRing(Player player)
	{
		if (!curiosLoaded)
			return ItemStack.EMPTY;
		return CuriosCompat.findAnyKeyRing(player);
	}
}
