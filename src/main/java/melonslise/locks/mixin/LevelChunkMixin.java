package melonslise.locks.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

@Mixin(LevelChunk.class)
public class LevelChunkMixin
{
	@Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V")
	private void init(ServerLevel world, ProtoChunk pr, LevelChunk.PostLoadProcessor proc, CallbackInfo ci)
	{
		LevelChunk ch = (LevelChunk) (Object) this;
		List<Lockable> source = ((ILockableProvider) pr).getLockables();
		// The source is a synchronized list that worldgen workers may still be appending to (border-spanning
		// locks from neighbouring chunks). Copy it under its monitor so the drain below sees a stable view.
		List<Lockable> provided;
		synchronized(source)
		{
			if(source.isEmpty())
				return;
			provided = new ArrayList<>(source);
		}
		ILockableStorage st = ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
		if(st == null)
			return;
		// Populate the per-chunk storage synchronously. This map belongs to a freshly built chunk that no
		// other thread references yet, so it is safe even under async chunk mods (C2ME). It MUST be populated
		// before the chunk goes full, so playerLoadedChunk and the ChunkEvent.Load registrar see the data.
		// Mutating the world-global handler map, registering observers and sending packets are all
		// main-thread-only and are handled later by LockableHandler#registerChunkStorage (driven by
		// ChunkEvent.Load), which reads this now-populated storage.
		for(Lockable lkb : provided)
			st.add(lkb);
	}
}
