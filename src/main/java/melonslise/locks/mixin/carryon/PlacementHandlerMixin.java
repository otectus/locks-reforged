package melonslise.locks.mixin.carryon;

import java.util.function.BiFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import melonslise.locks.common.compat.CarriedLockTransfer;
import melonslise.locks.common.compat.CarryOnCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Restores the carried lock after Carry On places the block. Both placement paths — normal placement
 * ({@code tryPlaceBlock}) and forced placement on death/drop ({@code placeCarried}) — reconstruct the
 * block entity from the carried {@code "tile"} NBT (restoring its Forge persistent data) and install it
 * with {@code Level.setBlockEntity}. Injecting right after that call lets us read the captured lock off
 * the freshly placed block entity and re-register it at the new position.
 *
 * Target: {@code tschipp.carryon.common.carry.PlacementHandler}.
 */
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public class PlacementHandlerMixin
{
	@Inject(
		method = "tryPlaceBlock",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER, remap = true),
		remap = false)
	private static void locks$restoreOnPlace(ServerPlayer player, BlockPos pos, Direction facing, BiFunction<BlockPos, BlockState, Boolean> placementCallback, CallbackInfoReturnable<Boolean> cir)
	{
		// 'pos' has already been reassigned to the final placement position by Carry On before this point.
		restore(player, pos);
	}

	@Inject(
		method = "placeCarried",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER, remap = true),
		remap = false)
	private static void locks$restoreOnForcedPlace(ServerPlayer player, CallbackInfo ci, @Local(ordinal = 0) BlockPos pos)
	{
		restore(player, pos);
	}

	private static void restore(ServerPlayer player, BlockPos pos)
	{
		if (pos == null || !CarryOnCompat.enabled())
			return;
		if (!(player.level() instanceof ServerLevel level))
			return;
		CarriedLockTransfer.restore(level, pos, level.getBlockEntity(pos));
	}
}
