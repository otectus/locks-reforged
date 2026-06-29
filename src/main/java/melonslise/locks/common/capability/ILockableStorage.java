package melonslise.locks.common.capability;

import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.common.util.Lockable;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.common.util.INBTSerializable;

public interface ILockableStorage extends INBTSerializable<ListTag>
{
	// Returns the LIVE backing map. Main-thread callers only — for cross-thread reads (worker threads under
	// async chunk mods like C2ME) use snapshot() instead, which copies under the storage monitor.
	Int2ObjectMap<Lockable> get();

	// Thread-safe copy of the loaded lockables for cross-thread readers.
	List<Lockable> snapshot();

	void add(Lockable lkb);

	void remove(int id);
}
