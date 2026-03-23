package melonslise.locks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PistonBaseBlock.class)
public class PistonBlockMixin
{
	// Before getPistonPushReaction call
	@Inject(at = @At(value = "INVOKE", target = "net/minecraft/world/level/block/state/BlockState.getPistonPushReaction()Lnet/minecraft/world/level/material/PushReaction;", ordinal = 0), method = "isPushable(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;ZLnet/minecraft/core/Direction;)Z", cancellable = true)
	private static void isPushable(BlockState state, Level world, BlockPos pos, Direction dir, boolean flag, Direction dir1, CallbackInfoReturnable<Boolean> cir)
	{
		if(LocksUtil.locked(world, pos))
			cir.setReturnValue(false);
	}
}
