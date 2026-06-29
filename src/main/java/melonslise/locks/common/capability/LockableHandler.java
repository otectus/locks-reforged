package melonslise.locks.common.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Observable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksNetwork;
import melonslise.locks.common.init.LocksPacketDistributors;
import melonslise.locks.common.network.toclient.AddLockablePacket;
import melonslise.locks.common.network.toclient.AddLockableToChunkPacket;
import melonslise.locks.common.network.toclient.RemoveLockablePacket;
import melonslise.locks.common.network.toclient.UpdateLockablePacket;
import melonslise.locks.common.util.Lockable;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

/*
 * Manages and handles logic for all LOADED lockables by accessing internal ILockableStorage objects.
 * This means that there is no way of getting a list of ALL lockables in a world like before
 */
public class LockableHandler implements ILockableHandler
{
	public static final ResourceLocation ID = new ResourceLocation(Locks.ID, "lockable_handler");

	public final Level world;

	public AtomicInteger lastId = new AtomicInteger();

	public Int2ObjectMap<Lockable> lockables = new Int2ObjectLinkedOpenHashMap<Lockable>();

	// Guards all STRUCTURAL access to the lockables map. Mutations are otherwise main-thread-only, but async
	// chunk mods (C2ME) can read the map from a worker thread (StructureTemplate#fillFromWorld). Building a
	// snapshot of a fastutil open-addressing map while the main thread rehashes it reads a torn backing array
	// -> ArrayIndexOutOfBoundsException (the same "Index -1" crash, on the read side). Every put/remove and the
	// snapshotLoaded() read therefore happen under this monitor.
	private final Object mutex = new Object();

	// Server-side persistent backing for lastId (resolved lazily). Null on the client.
	private LocksSavedData savedData;

	// One-time diagnostic: an off-thread id allocation before initIds() bootstrapped the persisted counter.
	private static final AtomicBoolean OFF_THREAD_NEXTID_LOGGED = new AtomicBoolean(false);

	public LockableHandler(Level world)
	{
		this.world = world;
	}

	// Resolves (and on first access bootstraps from) the persisted id counter. Returns null on the client,
	// and null when called off the main server thread before it has been resolved — DimensionDataStorage is
	// not thread-safe, and under async chunk mods (C2ME) structure placement can construct lockables (calling
	// nextId) on a worker thread. initIds() resolves it early on the main thread (LevelEvent.Load) so the
	// in-memory counter is always correct before any off-thread allocation.
	private LocksSavedData savedData()
	{
		if(this.savedData != null)
			return this.savedData;
		if(!(this.world instanceof ServerLevel sl))
			return null;
		net.minecraft.server.MinecraftServer server = sl.getServer();
		if(server == null || !server.isSameThread())
			return null;
		this.savedData = sl.getDataStorage().computeIfAbsent(LocksSavedData::load, LocksSavedData::new, LocksSavedData.NAME);
		// Bootstrap the in-memory counter from the persisted value so ids stay unique across restarts.
		if(this.savedData.getLastId() > this.lastId.get())
			this.lastId.set(this.savedData.getLastId());
		return this.savedData;
	}

	// Resolves the persisted counter on the main thread (call from LevelEvent.Load). No-op on the client.
	public void initIds()
	{
		this.savedData();
	}

	// Persists the id high-water-mark, but only on the main server thread (DimensionDataStorage mutation).
	// Off-thread allocations skip persistence; the next main-thread nextId/advanceLastId/add catches up.
	private void persistLastId(int id)
	{
		LocksSavedData data = this.savedData();
		if(data != null && id > data.getLastId())
		{
			data.setLastId(id);
			data.setDirty();
		}
	}

	public int nextId()
	{
		this.savedData(); // bootstrap lastId from disk on the main thread (no-op once resolved / off-thread)
		// If a worker allocates an id before initIds() (LevelEvent.Load) bootstrapped the persisted counter, the
		// in-memory lastId may start from 0 and collide with persisted ids. The advanceLastId backstop in
		// registerChunkStorage/add reconciles this when chunks load; surface the ordering so it is observable.
		if(this.savedData == null && this.world instanceof ServerLevel && OFF_THREAD_NEXTID_LOGGED.compareAndSet(false, true))
			Locks.LOGGER.warn("nextId() allocated an id before the persisted counter was bootstrapped (initIds/LevelEvent.Load). Ids will be reconciled via advanceLastId on chunk load. This message is logged once.");
		int id = this.lastId.incrementAndGet();
		this.persistLastId(id);
		return id;
	}

	// Ensures the id counter never hands out an id already used by a loaded lockable (defensive: also covers
	// pre-existing worlds whose persisted counter predates this fix).
	public void advanceLastId(int id)
	{
		int updated = this.lastId.updateAndGet(prev -> Math.max(prev, id));
		this.persistLastId(updated);
	}

	@Override
	public Int2ObjectMap<Lockable> getLoaded()
	{
		return this.lockables;
	}

	// Thread-safe snapshot of the loaded lockables for cross-thread readers (C2ME worker threads). Iterating or
	// copying getLoaded() directly off the main thread can crash; use this instead.
	@Override
	public List<Lockable> snapshotLoaded()
	{
		synchronized(this.mutex)
		{
			return new ArrayList<>(this.lockables.values());
		}
	}

	@Override
	public Int2ObjectMap<Lockable> getInChunk(BlockPos pos)
	{
		// getChunkNow is non-blocking (returns null if the chunk is not loaded to FULL); never use a blocking
		// getChunkAt here, which can re-park the main thread under C2ME's re-entrant chunk drains.
		LevelChunk ch = this.world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if(ch == null)
			return null;
		ILockableStorage st = ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
		return st != null ? st.get() : null;
	}

	@Override
	public boolean add(Lockable lkb)
	{
		if(lkb.bb.volume() > LocksServerConfig.MAX_LOCKABLE_VOLUME.get())
			return false;
		List<ILockableStorage> sts = lkb.bb.<ILockableStorage>containedChunksTo((x, z) ->
		{
			LevelChunk ch = this.world.getChunkSource().getChunkNow(x, z); // non-blocking; null if not loaded
			if(ch == null)
				return null;
			ILockableStorage st = ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
			if(st == null)
				return null;
			return st.get().values().stream().anyMatch(lkb1 -> lkb1.bb.intersects(lkb.bb)) ? null : st;
		}, true);
		if(sts == null)
			return false;

		// Add to chunk
		for(int a = 0; a < sts.size(); ++a)
			sts.get(a).add(lkb);
		// Add to world
		this.advanceLastId(lkb.id);
		synchronized(this.mutex)
		{
			this.lockables.put(lkb.id, lkb);
		}
		lkb.addObserver(this);
		// Do client/server extras
		if(this.world.isClientSide)
			lkb.swing(10);
		else
			LocksNetwork.MAIN.send(LocksPacketDistributors.TRACKING_AREA.with(() -> sts.stream().map(st -> ((LockableStorage) st).chunk)), new AddLockablePacket(lkb));
		return true;
	}

	@Override
	public void addDirect(Lockable lkb)
	{
		synchronized(this.mutex)
		{
			this.lockables.put(lkb.id, lkb);
		}
		lkb.addObserver(this);
		lkb.bb.getContainedChunks((x, z) ->
		{
			LevelChunk ch = this.world.getChunkSource().getChunkNow(x, z); // non-blocking; null if not loaded
			if(ch != null)
				ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).ifPresent(st -> st.add(lkb));
			return false;
		});
		if(this.world.isClientSide)
			lkb.swing(10);
	}

	@Override
	public boolean remove(int id)
	{
		Lockable lkb = this.lockables.get(id);
		if(lkb == this.lockables.defaultReturnValue())
			return false;
		// On the server, FORCE-LOAD every chunk the lockable spans so its on-disk copy is removed from all of
		// them. Previously a multi-chunk lockable removed while one of its chunks was unloaded left a stale copy
		// on disk; registerChunkStorage then resurrected it as canonical on reload (a removed lock reappearing).
		// The chunks already exist on disk (the lockable was placed there), so this loads, it does not generate.
		// On the client we never force-load (it would corrupt the client chunk lifecycle) — best-effort only.
		List<LevelChunk> chs = this.world.isClientSide
			? lkb.bb.containedChunksTo((x, z) -> this.world.hasChunk(x, z) ? this.world.getChunk(x, z) : null, false)
			: lkb.bb.containedChunksTo((x, z) -> this.world.getChunk(x, z), false);

		// Remove from chunk
		for(int a = 0; a < chs.size(); ++a)
		{
			LevelChunk ch = chs.get(a);
			if(ch != null)
				ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).ifPresent(st -> st.remove(id));
		}
		// Remove from world
		synchronized(this.mutex)
		{
			this.lockables.remove(id);
		}
		lkb.deleteObserver(this);
		// Do client/server extras
		if(this.world.isClientSide)
			return true;
		LocksNetwork.MAIN.send(LocksPacketDistributors.TRACKING_AREA.with(() -> chs.stream().filter(Objects::nonNull)), new RemoveLockablePacket(id));
		return true;
	}

	// Registers a freshly-loaded chunk's storage into the world-global loaded index. MAIN THREAD ONLY
	// (callers route off-thread chunk loads through LocksThreadUtil). Idempotent: safe to call more than
	// once for the same chunk, and deterministic regardless of the order adjacent chunks load.
	public void registerChunkStorage(LevelChunk chunk, ILockableStorage storage, boolean sync)
	{
		for(Lockable parsed : storage.snapshot())
		{
			this.advanceLastId(parsed.id);
			Lockable canonical;
			boolean added;
			synchronized(this.mutex)
			{
				canonical = this.lockables.get(parsed.id);
				added = canonical == this.lockables.defaultReturnValue();
				if(added)
				{
					// First occupied chunk to load this lockable -> it becomes the canonical runtime instance.
					this.lockables.put(parsed.id, parsed);
					canonical = parsed;
				}
			}
			if(added)
				parsed.addObserver(this);
			else if(canonical != parsed)
			{
				// A neighbouring occupied chunk already loaded this lockable (chunk-border case). Point this
				// chunk's storage at the canonical instance so every occupied chunk references the same object
				// and a stale chunk copy can never overwrite live runtime state.
				storage.add(canonical);
			}
			if(sync && !this.world.isClientSide)
			{
				Lockable send = canonical;
				LocksNetwork.MAIN.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), new AddLockableToChunkPacket(send, chunk));
			}
		}
	}

	// Drops the given (snapshotted) lockables from the world index when their chunk unloads, but only once
	// no OTHER occupied chunk they straddle is still loaded. The caller snapshots the storage and supplies the
	// unloading chunk pos, because the storage capability may already be invalidated by the time this runs.
	// MAIN THREAD ONLY on the server (the client runs it inline, having no server thread to defer to).
	public void unregisterChunkStorage(int chX, int chZ, List<Lockable> present)
	{
		for(Lockable lkb : present)
		{
			boolean anyOtherLoaded = !lkb.bb.getContainedChunks((x, z) -> !(x == chX && z == chZ) && this.world.hasChunk(x, z));
			if(!anyOtherLoaded)
			{
				synchronized(this.mutex)
				{
					this.lockables.remove(lkb.id);
				}
				lkb.deleteObserver(this);
			}
		}
	}

	// Marks every currently-loaded chunk the lockable occupies as unsaved, so lock-state changes that span a
	// chunk border persist consistently. Server-side only.
	public void markDirty(Lockable lkb)
	{
		if(this.world.isClientSide)
			return;
		lkb.bb.getContainedChunks((x, z) ->
		{
			LevelChunk ch = this.world.getChunkSource().getChunkNow(x, z); // non-blocking; null if not loaded
			if(ch != null)
				ch.setUnsaved(true);
			return false;
		});
	}

	@Override
	public void update(Observable o, Object arg)
	{
		if(this.world.isClientSide || !(o instanceof Lockable))
			return;
		Lockable lockable = (Lockable) o;
		// Persist the state change across ALL occupied chunks, not just whichever one triggered it.
		this.markDirty(lockable);
		LocksNetwork.MAIN.send(LocksPacketDistributors.TRACKING_AREA.with(() -> lockable.bb.containedChunksTo((x, z) -> this.world.getChunkSource().getChunkNow(x, z), false).stream().filter(Objects::nonNull)), new UpdateLockablePacket(lockable));
	}

	@Override
	public IntTag serializeNBT()
	{
		return IntTag.valueOf(this.lastId.get());
	}

	@Override
	public void deserializeNBT(IntTag nbt)
	{
		this.lastId.set(nbt.getAsInt());
	}
}
