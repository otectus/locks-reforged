package melonslise.locks.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.util.LocksPredicates;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

@Mixin(ExplosionDamageCalculator.class)
public class ExplosionDamageCalculatorMixin
{
	@Inject(at = @At("RETURN"), method = "getBlockExplosionResistance(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Ljava/util/Optional;", cancellable = true)
	private void getBlockExplosionResistance(Explosion ex, BlockGetter world, BlockPos pos, BlockState state, FluidState fluid, CallbackInfoReturnable<Optional<Float>> cir)
	{
		cir.setReturnValue(cir.getReturnValue().map(r -> Math.max(r, LocksUtil.intersecting(ex.level, pos).filter(LocksPredicates.LOCKED).findFirst().map(lkb -> LockItem.getResistance(lkb.stack)).orElse(0))));
	}
}
