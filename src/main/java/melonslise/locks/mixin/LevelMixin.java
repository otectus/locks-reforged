package melonslise.locks.mixin;

import org.spongepowered.asm.mixin.Mixin;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;

/**
 * Overrides the SignalGetter.hasNeighborSignal default method on Level
 * to prevent locked blocks from receiving redstone signals.
 *
 * Mixin cannot @Inject or @Overwrite interface default methods that are
 * not directly declared on the target class. Instead, we implement the
 * interface and provide the method directly, which gets merged into Level.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements SignalGetter
{
	@Override
	public boolean hasNeighborSignal(BlockPos pos)
	{
		Level self = (Level) (Object) this;

		if(LocksUtil.locked(self, pos))
			return false;

		// Original SignalGetter.hasNeighborSignal logic
		if(this.getSignal(pos.below(), Direction.DOWN) > 0) return true;
		if(this.getSignal(pos.above(), Direction.UP) > 0) return true;
		if(this.getSignal(pos.north(), Direction.NORTH) > 0) return true;
		if(this.getSignal(pos.south(), Direction.SOUTH) > 0) return true;
		if(this.getSignal(pos.west(), Direction.WEST) > 0) return true;
		return this.getSignal(pos.east(), Direction.EAST) > 0;
	}
}
