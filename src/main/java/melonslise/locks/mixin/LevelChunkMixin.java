package melonslise.locks.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksNetwork;
import melonslise.locks.common.network.toclient.AddLockableToChunkPacket;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksThreadUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraftforge.network.PacketDistributor;

@Mixin(LevelChunk.class)
public class LevelChunkMixin
{
	@Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V")
	private void init(ServerLevel world, ProtoChunk pr, LevelChunk.PostLoadProcessor proc, CallbackInfo ci)
	{
		LevelChunk ch = (LevelChunk) (Object) this;
		List<Lockable> provided = ((ILockableProvider) pr).getLockables();
		if(provided.isEmpty())
			return;
		// Copy now: under async chunk mods (e.g. C2ME) the registration below is deferred to the next
		// server tick, by which point the ProtoChunk's list must not be read again.
		List<Lockable> toAdd = new ArrayList<>(provided);
		// Mutating the world-global handler map, registering observers and sending packets are all
		// main-thread-only. Without C2ME this constructor already runs on the server thread, so the work
		// executes inline and behavior is unchanged; under C2ME it is deferred to avoid corrupting the
		// non-thread-safe handler map (issue #10: ArrayIndexOutOfBoundsException in LevelChunk).
		LocksThreadUtil.runOnServerThread(world.getServer(), () ->
		{
			ILockableStorage st = ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
			ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
			if(st == null || handler == null)
				return;
			// We trust that all checks pass (such as volume and intersect checks) due to this happening only during world gen
			for(Lockable lkb : toAdd)
			{
				st.add(lkb);
				handler.getLoaded().put(lkb.id, lkb);
				lkb.addObserver(handler);
				LocksNetwork.MAIN.send(PacketDistributor.TRACKING_CHUNK.with(() -> ch), new AddLockableToChunkPacket(lkb, ch));
			}
		});
	}
}
