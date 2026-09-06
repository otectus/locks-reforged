package melonslise.locks.common.capability;

import java.util.concurrent.ThreadLocalRandom;

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
	private static final String KEY_COMBO_SALT = "ComboSalt";

	private int lastId;

	// Server-wide secret used to reroll the pre-1.7.5 id-derived lock combinations exactly once. Generated on
	// first use and never sent anywhere; only the overworld's copy is ever read, so one salt governs every
	// dimension and a lock carried into the Nether migrates the same way it would have at home.
	private long comboSalt;

	public LocksSavedData() {}

	public static LocksSavedData load(CompoundTag nbt)
	{
		LocksSavedData data = new LocksSavedData();
		data.lastId = nbt.getInt(KEY_LAST_ID);
		data.comboSalt = nbt.getLong(KEY_COMBO_SALT);
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag nbt)
	{
		nbt.putInt(KEY_LAST_ID, this.lastId);
		nbt.putLong(KEY_COMBO_SALT, this.comboSalt);
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

	/** The migration salt, generating and persisting one on first use. Never zero, so a caller can assert on it. */
	public long getOrCreateComboSalt()
	{
		if(this.comboSalt == 0L)
		{
			while(this.comboSalt == 0L)
				this.comboSalt = ThreadLocalRandom.current().nextLong();
			this.setDirty();
		}
		return this.comboSalt;
	}
}
