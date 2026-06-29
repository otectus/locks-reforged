package melonslise.locks.common.worldgen;

import com.mojang.serialization.Codec;

import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksThreadUtil;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class LockChestsFeature extends Feature<NoneFeatureConfiguration>
{
	public LockChestsFeature(Codec<NoneFeatureConfiguration> codec)
	{
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
	{
		WorldGenLevel world = context.level();
		RandomSource rng = context.random();
		BlockPos pos = context.origin();

		Lockable lkb = LocksUtil.createChestLockable(world, world.getLevel(), pos, rng);
		if (lkb == null)
			return false;

		lkb.bb.getContainedChunks((x, z) ->
		{
			ChunkAccess access = world.getChunk(x, z);
			if (access instanceof ImposterProtoChunk imposter)
			{
				// This neighbour already finished generating: it is an ImposterProtoChunk wrapping a full
				// LevelChunk whose lock-list drain (LevelChunkMixin) already ran. Adding to the inherited
				// ProtoChunk list would be silently lost (nothing drains it again). Instead write the lockable
				// straight into the wrapped chunk's (thread-safe) storage and register it with the world handler
				// on the main thread, exactly as a normal chunk load would.
				LevelChunk wrapped = imposter.getWrapped();
				ILockableStorage st = wrapped.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
				if (st != null)
				{
					st.add(lkb);
					ServerLevel sl = world.getLevel();
					ILockableHandler handler = sl.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
					if (handler != null)
						LocksThreadUtil.runOnServerThread(sl.getServer(), () -> handler.registerChunkStorage(wrapped, st, false));
				}
			}
			else
			{
				// Still a ProtoChunk: queue into its synchronized lock list, drained into per-chunk storage when
				// it converts to a LevelChunk (LevelChunkMixin).
				((ILockableProvider) access).getLockables().add(lkb);
			}
			return false;
		});
		return true;
	}
}
