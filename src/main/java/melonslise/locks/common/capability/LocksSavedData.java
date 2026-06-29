package melonslise.locks.common.capability;

import melonslise.locks.Locks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/*
 * Per-level persistent storage for the lockable id counter.
 *
 * The LockableHandler is attached as a Level capability, and Forge does NOT persist Level capabilities to
 * disk. Previously this meant the id counter reset to 0 on every server restart, so newly placed locks reused
 * ids belonging to already-saved (but unloaded) locks. When the old chunk reloaded, the by-id merge made the
 * old lock adopt the new lock's object/position and visually vanish. Persisting the counter here keeps lock
 * ids globally unique across restarts.
 */
public class LocksSavedData extends SavedData
{
	public static final String NAME = Locks.ID + "_lockables";
	private static final String KEY_LAST_ID = "LastId";

	private int lastId;

	public LocksSavedData() {}

	public static LocksSavedData load(CompoundTag nbt)
	{
		LocksSavedData data = new LocksSavedData();
		data.lastId = nbt.getInt(KEY_LAST_ID);
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag nbt)
	{
		nbt.putInt(KEY_LAST_ID, this.lastId);
		return nbt;
	}

	public int getLastId()
	{
		return this.lastId;
	}

	public void setLastId(int id)
	{
		this.lastId = id;
	}
}
