package melonslise.locks.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import net.minecraft.world.level.chunk.ProtoChunk;

@Mixin(ProtoChunk.class)
public class ProtoChunkMixin implements ILockableProvider
{
	// A border-spanning generated lock is added to NEIGHBOUR ProtoChunks' lists from LockChestsFeature, which
	// runs on worldgen worker threads — so a neighbour chunk generating concurrently can write here while this
	// chunk converts to a LevelChunk and drains the list. The list is synchronized; iterators must hold its
	// monitor (see LevelChunkMixin).
	private final List<Lockable> lockableList = Collections.synchronizedList(new ArrayList<>());

	@Override
	public List<Lockable> getLockables()
	{
		return this.lockableList;
	}
}
