package melonslise.locks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks entity-driven door opening on locked doors.
 *
 * DoorBlock#setOpen is the single choke point every non-player door opener funnels through:
 * villager Brain behavior (InteractWithDoor), legacy DoorInteractGoal, raider goals, and modded AI
 * that drives a vanilla or subclassed DoorBlock. Players go through DoorBlock#use instead, which
 * does its own state.cycle(OPEN) + setBlock and never calls this — so there is no overlap with the
 * existing LocksForgeEvents#onRightClick denial, and player behavior is untouched. Redstone is
 * handled separately by LevelMixin#hasNeighborSignal.
 *
 * Deliberately narrow: only open == true is refused, so anything (including AI closing a door it
 * remembered) may still close it, and LocksUtil#setLocked can close a door as it re-locks. No sound,
 * no game event, no AI-memory or navigation mutation — AI retries frequently and any feedback here
 * would be spam. Cancellation alone is the protection.
 *
 * Server-only: LocksUtil#locked reads the client's lockable mirror on the logical client, which can
 * legitimately be stale or missing (that is what the self-heal in LocksForgeEvents exists to repair),
 * and a false positive there would desync the door in the wrong direction. setOpen has no vanilla
 * client callers anyway. Uses LocksUtil#locked so door enforcement consumes the same "locked" truth
 * as every other protection, via the existing non-forcing chunk lookup.
 */
@Mixin(DoorBlock.class)
public class DoorBlockMixin
{
	@Inject(at = @At("HEAD"), method = "setOpen(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Z)V", cancellable = true)
	private void setOpen(Entity source, Level world, BlockState state, BlockPos pos, boolean open, CallbackInfo ci)
	{
		if(!open || world.isClientSide)
			return;
		if(LocksUtil.locked(world, pos))
			ci.cancel();
	}
}
