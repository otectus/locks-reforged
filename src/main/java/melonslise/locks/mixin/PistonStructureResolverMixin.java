package melonslise.locks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

@Mixin(PistonStructureResolver.class)
public class PistonStructureResolverMixin
{
	@Inject(at = @At("HEAD"), method = "resolve", cancellable = true)
	private void resolve(CallbackInfoReturnable<Boolean> cir)
	{
		PistonStructureResolver h = (PistonStructureResolver) (Object) this;
		if(LocksUtil.locked(h.level, h.startPos))
			cir.setReturnValue(false);
	}
}
