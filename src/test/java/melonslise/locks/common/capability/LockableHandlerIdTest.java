package melonslise.locks.common.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The lockable id counter. Unique, monotonic ids are the primary key across chunk storage, the world index and
 * every packet; an id that regressed or was reused let a new lock adopt a saved-but-unloaded lock's slot,
 * causing the "vanishing / wrong-position lock" class of bug. Constructed with a null world so the persistent
 * DimensionDataStorage backing is safely skipped (savedData() short-circuits off a ServerLevel) — this exercises
 * the in-memory AtomicInteger logic in isolation, no Minecraft runtime needed.
 */
public class LockableHandlerIdTest
{
	@Test
	void nextIdStrictlyIncrements()
	{
		LockableHandler handler = new LockableHandler(null);
		int a = handler.nextId();
		int b = handler.nextId();
		int c = handler.nextId();
		assertEquals(1, a);
		assertEquals(2, b);
		assertEquals(3, c);
	}

	@Test
	void advanceLastIdNeverRegresses()
	{
		LockableHandler handler = new LockableHandler(null);
		handler.advanceLastId(100);
		assertEquals(100, handler.lastId.get());

		// A lower id must not pull the counter backwards (a stale/older chunk loading must never lower it).
		handler.advanceLastId(50);
		assertEquals(100, handler.lastId.get());

		// The next allocation continues above the high-water mark.
		assertEquals(101, handler.nextId());
	}

	@Test
	void advanceThenNextStaysUnique()
	{
		// Simulate loading a chunk whose persisted lockable has a high id, then allocating new ones: new ids must
		// never collide with the loaded one.
		LockableHandler handler = new LockableHandler(null);
		handler.advanceLastId(7);
		int first = handler.nextId();
		int second = handler.nextId();
		assertTrue(first > 7, "new id must exceed the loaded high-water id");
		assertTrue(second > first, "ids must remain strictly increasing");
	}
}
