package melonslise.locks.common.compat;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.init.LocksTagHelper;
import melonslise.locks.common.item.KeyRingItem;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.util.Lockable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

/**
 * Soft compatibility with the Carry On mod ("carryon"): when a player picks up a locked/lockable
 * block, the existing lock is moved with it instead of being orphaned. This class is the gate +
 * authorization policy; the actual lock movement lives in {@link CarriedLockTransfer} and the thin
 * mixins under {@code melonslise.locks.mixin.carryon}. Follows the same optional-dep pattern as
 * {@link CuriosHelper} — everything here is inert unless {@code carryon} is installed and enabled.
 */
public final class CarryOnCompat
{
	private static boolean carryOnLoaded = false;
	private static boolean curiosLoaded = false;

	// Marks the block position currently being picked up by an authorized Carry On carry, so that
	// LocksForgeEvents.onBlockBreak (which Carry On triggers as its pickup gate) does not veto it.
	// Server pickup runs on the main thread; the marker is scoped to the single tryPickUpBlock call.
	private static final ThreadLocal<BlockPos> AUTHORIZED_PICKUP = new ThreadLocal<>();

	private CarryOnCompat() {}

	public static void init()
	{
		carryOnLoaded = ModList.get().isLoaded("carryon");
		curiosLoaded = ModList.get().isLoaded("curios");
		if (carryOnLoaded)
			Locks.LOGGER.info("Carry On detected — locks will be carried with their blocks");
	}

	public static boolean isCarryOnLoaded()
	{
		return carryOnLoaded;
	}

	/** True when Carry On is present and the compatibility master toggle is on. */
	public static boolean enabled()
	{
		return carryOnLoaded && LocksServerConfig.CARRYON_ENABLE.get();
	}

	public static boolean denyPartialMultiBlock()
	{
		return LocksServerConfig.CARRYON_DENY_PARTIAL_MULTIBLOCK.get();
	}

	public static boolean logTransfers()
	{
		return LocksServerConfig.CARRYON_LOG_TRANSFERS.get();
	}

	public static void log(String msg, Object... args)
	{
		if (logTransfers())
			Locks.LOGGER.info("[CarryOn] " + msg, args);
	}

	/**
	 * Whether {@code player} may carry away a block covered by {@code lkb}. Unlocked lockables are
	 * always movable. Locked ones respect the config: optionally forbidden outright, optionally
	 * requiring authorization (matching key / key ring / master key anywhere in the inventory,
	 * Awareness owner, or creative). Because Carry On requires empty hands to pick up, the whole
	 * inventory is scanned rather than just the held item.
	 */
	public static boolean canMoveLockedBlock(Player player, Lockable lkb)
	{
		if (!lkb.lock.isLocked())
			return true;
		if (!LocksServerConfig.CARRYON_ALLOW_LOCKED.get())
			return false;
		if (!LocksServerConfig.CARRYON_REQUIRE_AUTH.get())
			return true;
		if (player.isCreative())
			return true;

		int id = lkb.lock.id;

		Inventory inv = player.getInventory();
		if (hasMatchingCredential(inv.items, id) || hasMatchingCredential(inv.offhand, id))
			return true;

		// Awareness: the player who placed the lock can carry it without a key.
		if (LocksServerConfig.ENABLE_AWARENESS.get()
			&& EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.AWARENESS.get(), lkb.stack) > 0)
		{
			UUID owner = LockItem.getOwner(lkb.stack);
			if (owner != null && owner.equals(player.getUUID()))
				return true;
		}

		// Curios key rings (worn in a curio slot rather than the main inventory).
		if (curiosLoaded && !CuriosCompat.findMatchingKeyRing(player, id).isEmpty())
			return true;

		return false;
	}

	private static boolean hasMatchingCredential(Iterable<ItemStack> stacks, int lockId)
	{
		for (ItemStack stack : stacks)
		{
			if (stack.isEmpty())
				continue;
			if (stack.getItem() == LocksItems.MASTER_KEY.get())
				return true;
			if (LocksTagHelper.isKey(stack) && LockingItem.getOrSetId(stack) == lockId)
				return true;
			if (stack.getItem() == LocksItems.KEY_RING.get() && KeyRingItem.containsId(stack, lockId))
				return true;
		}
		return false;
	}

	public static void markAuthorizedPickup(BlockPos pos)
	{
		AUTHORIZED_PICKUP.set(pos);
	}

	public static void clearAuthorizedPickup()
	{
		AUTHORIZED_PICKUP.remove();
	}

	/** Read by LocksForgeEvents.onBlockBreak to let an authorized Carry On pickup through. */
	public static boolean isAuthorizedPickup(BlockPos pos)
	{
		BlockPos marked = AUTHORIZED_PICKUP.get();
		return marked != null && marked.equals(pos);
	}
}
