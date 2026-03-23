package melonslise.locks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin
{
	@Inject(at = @At("HEAD"), method = "getContainerAt(Lnet/minecraft/world/level/Level;DDD)Lnet/minecraft/world/Container;", cancellable = true)
	private static void getContainerAt(Level world, double x, double y, double z, CallbackInfoReturnable<Container> cir)
	{
		if(LocksUtil.locked(world, new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))))
			cir.setReturnValue(null);
	}
}
