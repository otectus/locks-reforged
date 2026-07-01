package melonslise.locks.mixin.carryon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import melonslise.locks.common.compat.CarriedLockTransfer;
import melonslise.locks.common.compat.CarryOnCompat;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Captures the lock at the moment Carry On stores a picked-up block. {@code CarryOnData.setBlock} is
 * called only from {@code PickupHandler.tryPickUpBlock} after every Carry On abort check has passed and
 * before the block/entity are removed, so it is the safe success point. Writing to the block entity's
 * persistent data here (at HEAD, before Carry On serializes the entity via {@code saveWithId}) makes the
 * lock ride inside Carry On's own carried {@code "tile"} NBT, which is durable across relog/restart.
 *
 * Target: {@code tschipp.carryon.common.carry.CarryOnData}.
 */
@Mixin(targets = "tschipp.carryon.common.carry.CarryOnData", remap = false)
public class CarryOnDataMixin
{
	@Inject(method = "setBlock", at = @At("HEAD"), remap = false)
	private void locks$captureLock(BlockState state, BlockEntity tile, CallbackInfo ci)
	{
		if (tile == null || tile.getLevel() == null || !CarryOnCompat.enabled())
			return;
		CarriedLockTransfer.capture(tile.getLevel(), tile.getBlockPos(), tile);
	}
}
