package melonslise.locks.common.capability;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.Locks;
import melonslise.locks.common.util.Lockable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;

/*
 * Internal storage for lockables with almost no handling logic
 * Also stores lockables which are shared by multiple chunks. Duplicate shared lockables are handled by checking if they have already been loaded before
 */
public class LockableStorage implements ILockableStorage
{
	public static final ResourceLocation ID = new ResourceLocation(Locks.ID, "lockable_storage");

	public final LevelChunk chunk;

	public Int2ObjectMap<Lockable> lockables = new Int2ObjectLinkedOpenHashMap<Lockable>();

	// Guards the fastutil map. It is written off the main thread (deserializeNBT and LevelChunkMixin.<init> run on
	// C2ME worker threads) and read on the main thread, so every mutation and the cross-thread snapshot() copy
	// happen under this monitor to publish writes safely (a fastutil open-addressing map copied while another
	// thread rehashes it reads a torn backing array).
	private final Object mutex = new Object();

	public LockableStorage(LevelChunk chunk)
	{
		this.chunk = chunk;
	}

	@Override
	public Int2ObjectMap<Lockable> get()
	{
		return this.lockables;
	}

	@Override
	public List<Lockable> snapshot()
	{
		synchronized(this.mutex)
		{
			return new ArrayList<>(this.lockables.values());
		}
	}

	@Override
	public void add(Lockable lkb)
	{
		synchronized(this.mutex)
		{
			this.lockables.put(lkb.id, lkb);
		}
		this.chunk.setUnsaved(true);
	}

	@Override
	public void remove(int id)
	{
		synchronized(this.mutex)
		{
			this.lockables.remove(id);
		}
		this.chunk.setUnsaved(true);
	}

	@Override
	public ListTag serializeNBT()
	{
		ListTag list = new ListTag();
		synchronized(this.mutex)
		{
			for(Lockable lkb : this.lockables.values())
				list.add(Lockable.toNbt(lkb));
		}
		return list;
	}

	@Override
	public void deserializeNBT(ListTag nbt)
	{
		// Pure chunk-local hydration. This must be safe to run off the main server thread, because async
		// chunk mods (e.g. C2ME) deserialize chunk capabilities on worker threads. It therefore NEVER
		// touches the world-global LockableHandler, observers, packets or the chunk's level (doing so
		// corrupted the non-thread-safe handler map -> ArrayIndexOutOfBoundsException). Registration into
		// the handler happens later on the main thread via LockableHandler#registerChunkStorage, driven by
		// ChunkEvent.Load. We also validate defensively so one bad entry can never abort the whole load.
		synchronized(this.mutex)
		{
			this.lockables.clear();
			for(int a = 0; a < nbt.size(); ++a)
			{
				CompoundTag nbt1 = nbt.getCompound(a);
				int id = Lockable.idFromNbt(nbt1);
				try
				{
					Lockable lkb = Lockable.fromNbt(nbt1);
					if(lkb.bb.volume() <= 0)
					{
						Locks.LOGGER.warn("Skipping lockable {} in chunk {}: non-positive bounding box volume", id, this.chunk.getPos());
						continue;
					}
					if(lkb.stack.isEmpty())
					{
						Locks.LOGGER.warn("Skipping lockable {} in chunk {}: empty lock stack", id, this.chunk.getPos());
						continue;
					}
					this.lockables.put(lkb.id, lkb);
				}
				catch(Exception e)
				{
					Locks.LOGGER.warn("Skipping malformed lockable {} in chunk {}: {}", id, this.chunk.getPos(), e.toString());
				}
			}
		}
	}
}
