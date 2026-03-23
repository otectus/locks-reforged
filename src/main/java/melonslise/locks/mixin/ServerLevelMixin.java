package melonslise.locks.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Lockable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ServerLevel.class)
public class ServerLevelMixin
{
	@Inject(at = @At("HEAD"), method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V")
	private void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flag, CallbackInfo ci)
	{
		if(oldState.is(newState.getBlock()))
			return;
		ServerLevel world = (ServerLevel) (Object) this;
		BlockPos immutablePos = pos.immutable();
		world.getServer().tell(new TickTask(world.getServer().getTickCount() + 1, () ->
		{
			if(LocksServerConfig.canLock(world, immutablePos))
				return;
			ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
			if(handler == null)
				return;
			Int2ObjectMap<Lockable> chunkLockables = handler.getInChunk(immutablePos);
			if(chunkLockables == null)
				return;
			IntArrayList toRemove = new IntArrayList();
			for(Lockable lkb : chunkLockables.values())
				if(lkb.bb.intersects(immutablePos))
					toRemove.add(lkb.id);
			for(int i = 0; i < toRemove.size(); i++)
			{
				Lockable lkb = handler.getLoaded().get(toRemove.getInt(i));
				if(lkb == null)
					continue;
				world.playSound(null, immutablePos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f + world.random.nextFloat() * 0.4f);
				world.addFreshEntity(new ItemEntity(world, immutablePos.getX() + 0.5d, immutablePos.getY() + 0.5d, immutablePos.getZ() + 0.5d, lkb.stack));
				handler.remove(lkb.id);
			}
		}));
	}
}
