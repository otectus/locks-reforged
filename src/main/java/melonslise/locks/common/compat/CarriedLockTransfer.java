package melonslise.locks.common.compat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import melonslise.locks.Locks;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Cuboid6i;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Moves Locks Reforged {@link Lockable}s together with a block that Carry On picks up and places.
 *
 * <p>The serialized lockables ride inside the block entity's Forge persistent data ({@code ForgeData}),
 * which Carry On already saves into its carried {@code "tile"} NBT ({@code BlockEntity#saveWithId}) and
 * restores on placement ({@code BlockEntity#loadStatic}). This needs no new storage: the canonical lock
 * store remains the per-chunk {@code LockableStorage} + per-level {@code LockableHandler}, updated here
 * through {@link ILockableHandler#remove(int)} and {@link ILockableHandler#add(Lockable)} which already
 * fan out across every affected chunk, mark them unsaved, and sync clients.
 */
public final class CarriedLockTransfer
{
	public static final String KEY_LOCKABLES = "locks:CarriedLockables";
	public static final String KEY_ORIGIN = "locks:CarriedOrigin";

	private CarriedLockTransfer() {}

	/**
	 * At pickup: serialize every lockable covering {@code pickupPos} into the block entity's persistent
	 * data and remove them from the world. Called from the Carry On {@code CarryOnData.setBlock} hook,
	 * which is the guaranteed pickup-success point (after all of Carry On's own abort checks, and before
	 * the block/entity are removed), so removal here is safe.
	 */
	public static void capture(Level level, BlockPos pickupPos, BlockEntity be)
	{
		if (be == null || level.isClientSide || !CarryOnCompat.enabled())
			return;

		List<Lockable> lockables = LocksUtil.intersecting(level, pickupPos).collect(Collectors.toList());
		if (lockables.isEmpty())
			return;

		ILockableHandler handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if (handler == null)
			return;

		ListTag list = new ListTag();
		for (Lockable lkb : lockables)
			list.add(Lockable.toNbt(lkb));

		CompoundTag data = be.getPersistentData();
		data.put(KEY_LOCKABLES, list);
		data.putLong(KEY_ORIGIN, pickupPos.asLong());

		for (Lockable lkb : lockables)
			handler.remove(lkb.id);

		CarryOnCompat.log("Captured {} lockable(s) from {}", lockables.size(), pickupPos);
	}

	/**
	 * At placement: read the captured lockables off the placed block entity, offset their bounding boxes
	 * from the original origin to the new position, and re-register them. Reuses the original ids and
	 * {@link melonslise.locks.common.util.Lock} objects, so keys/key rings/master keys keep matching.
	 */
	public static void restore(ServerLevel level, BlockPos placePos, BlockEntity be)
	{
		if (be == null || !CarryOnCompat.enabled())
			return;

		CompoundTag data = be.getPersistentData();
		if (!data.contains(KEY_LOCKABLES, Tag.TAG_LIST))
			return;

		BlockPos oldOrigin = BlockPos.of(data.getLong(KEY_ORIGIN));
		int dx = placePos.getX() - oldOrigin.getX();
		int dy = placePos.getY() - oldOrigin.getY();
		int dz = placePos.getZ() - oldOrigin.getZ();

		ILockableHandler handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if (handler == null)
			return;

		ListTag list = data.getList(KEY_LOCKABLES, Tag.TAG_COMPOUND);
		Set<Integer> failed = new LinkedHashSet<>();
		int restored = 0;
		for (int i = 0; i < list.size(); ++i)
		{
			Lockable original = Lockable.fromNbt(list.getCompound(i));
			Cuboid6i newBox = original.bb.offset(dx, dy, dz);
			Lockable moved = movedCopy(original, newBox);
			if (handler.add(moved))
			{
				++restored;
			}
			else
			{
				// add() rejects on unloaded target chunks, an overlapping lockable, or over-volume.
				failed.add(i);
				Locks.LOGGER.warn("[CarryOn] Failed to restore lockable id={} at {} (handler rejected add); keeping it on the carried block", moved.id, placePos);
			}
		}

		// Only the entries that were actually accepted leave the payload. Clearing it unconditionally — as this
		// did before — permanently destroyed any lockable the handler refused.
		ListTag retained = retain(list, failed);
		if (retained.isEmpty())
		{
			data.remove(KEY_LOCKABLES);
			data.remove(KEY_ORIGIN);
		}
		else
		{
			// The origin stays untouched so a later placement offsets the retained boxes from the same base.
			data.put(KEY_LOCKABLES, retained);
			Locks.LOGGER.warn("[CarryOn] Retained {} lockable(s) on the carried block at {} for a later placement", retained.size(), placePos);
		}
		be.setChanged();

		CarryOnCompat.log("Restored {} lockable(s) at {}", restored, placePos);
	}

	/**
	 * The payload to keep on the block: exactly the entries at {@code failedIndices}, in their original order.
	 * Pure, so the retention decision is testable without a world.
	 */
	public static ListTag retain(ListTag original, Set<Integer> failedIndices)
	{
		ListTag retained = new ListTag();
		for (int i = 0; i < original.size(); ++i)
			if (failedIndices.contains(i))
				retained.add(original.get(i));
		return retained;
	}

	/** A copy of {@code original} at {@code newBox}, preserving its lock, transform, item and id (a move, not a re-lock). */
	public static Lockable movedCopy(Lockable original, Cuboid6i newBox)
	{
		return new Lockable(newBox, original.lock, original.tr, original.stack.copy(), original.id);
	}
}
