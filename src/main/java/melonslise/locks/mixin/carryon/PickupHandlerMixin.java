package melonslise.locks.mixin.carryon;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import melonslise.locks.common.compat.CarryOnCompat;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Authorization + safety gate for Carry On block pickups. The actual lock capture happens in
 * {@link CarryOnDataMixin} at the guaranteed pickup-success point; this mixin only decides whether the
 * pickup is allowed and, when it is, marks it so Locks' own break-protection ({@code onBlockBreak})
 * lets it through (Carry On posts a {@code BlockEvent.BreakEvent} as its pickup gate).
 *
 * Target: {@code tschipp.carryon.common.carry.PickupHandler} (referenced by name; not on the compile classpath).
 */
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public class PickupHandlerMixin
{
	@Inject(method = "tryPickUpBlock", at = @At("HEAD"), cancellable = true, remap = false)
	private static void locks$onTryPickUpBlock(ServerPlayer player, BlockPos pos, Level level, BiFunction<BlockState, BlockPos, Boolean> pickupCallback, CallbackInfoReturnable<Boolean> cir)
	{
		if (!CarryOnCompat.enabled())
			return;

		List<Lockable> lockables = LocksUtil.intersecting(level, pos).collect(Collectors.toList());
		if (lockables.isEmpty())
			return;

		boolean anyLocked = false;
		for (Lockable lkb : lockables)
		{
			// A lock spanning more than the single carried block (e.g. a double chest) cannot be moved
			// safely by carrying one block — deny rather than corrupt it.
			if (CarryOnCompat.denyPartialMultiBlock() && lkb.bb.volume() > 1)
			{
				cir.setReturnValue(false);
				return;
			}
			if (lkb.lock.isLocked())
			{
				anyLocked = true;
				if (!CarryOnCompat.canMoveLockedBlock(player, lkb))
				{
					cir.setReturnValue(false);
					return;
				}
			}
		}

		// The lock rides inside the block entity's persisted data. Without a block entity (e.g. a locked
		// door when Carry On's pickupAllBlocks is enabled) there is nowhere durable to store it, so deny
		// the pickup instead of orphaning the lock.
		if (level.getBlockEntity(pos) == null)
		{
			cir.setReturnValue(false);
			return;
		}

		if (anyLocked)
			CarryOnCompat.markAuthorizedPickup(pos);
	}

	@Inject(method = "tryPickUpBlock", at = @At("RETURN"), remap = false)
	private static void locks$clearPickupMarker(ServerPlayer player, BlockPos pos, Level level, BiFunction<BlockState, BlockPos, Boolean> pickupCallback, CallbackInfoReturnable<Boolean> cir)
	{
		CarryOnCompat.clearAuthorizedPickup();
	}
}
