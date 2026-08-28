package melonslise.locks.common.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.CompoundTag;

/**
 * NBT persistence of {@link Lock}. The combo (pin order) and the locked flag are the authoritative lock state
 * saved to chunk NBT; if a round-trip lost or reshuffled them, a reloaded lock would no longer accept the
 * player's learned combination — a subtle "lock changed after reload" bug. Pure CompoundTag work, no Bootstrap.
 */
public class LockTest
{
	private static byte[] pins(Lock lock)
	{
		byte[] out = new byte[lock.getLength()];
		for(int i = 0; i < out.length; ++i)
			out[i] = (byte) lock.getPin(i);
		return out;
	}

	@Test
	void roundTripPreservesComboAndLocked()
	{
		Lock original = new Lock(42, 6, true);
		Lock restored = Lock.fromNbt(Lock.toNbt(original));

		assertEquals(original.getLength(), restored.getLength());
		assertArrayEquals(pins(original), pins(restored), "saved combo must survive a round-trip exactly");
		assertTrue(restored.isLocked());
	}

	@Test
	void roundTripPreservesUnlockedState()
	{
		Lock original = new Lock(7, 4, false);
		Lock restored = Lock.fromNbt(Lock.toNbt(original));
		assertFalse(restored.isLocked());
	}

	@Test
	void reshuffledComboSurvivesRoundTrip()
	{
		// Simulate a combo that diverged from the id seed (e.g. a future re-key). The explicit Combo array must
		// be restored verbatim rather than regenerated from the seed.
		byte[] custom = {3, 0, 2, 1};
		Lock original = new Lock(99, custom, true);
		Lock restored = Lock.fromNbt(Lock.toNbt(original));
		assertArrayEquals(custom, pins(restored));
	}

	@Test
	void backwardCompatRegeneratesComboFromSeedWhenAbsent()
	{
		// Pre-combo-persistence saves stored only Id/Length/Locked. fromNbt must regenerate deterministically
		// from the id seed, matching a freshly seeded lock of the same id/length.
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(Lock.KEY_ID, 123);
		legacy.putByte(Lock.KEY_LENGTH, (byte) 5);
		legacy.putBoolean(Lock.KEY_LOCKED, true);

		Lock restored = Lock.fromNbt(legacy);
		Lock seeded = new Lock(123, 5, true);

		assertEquals(5, restored.getLength());
		assertArrayEquals(pins(seeded), pins(restored), "seed-regenerated combo must be deterministic for an id");
	}

	@Test
	void comboIsDeterministicForId()
	{
		// Same id => same seeded shuffle. This determinism is what lets the client regenerate a dummy combo of
		// the correct length and what the backward-compat path above relies on.
		assertArrayEquals(pins(new Lock(555, 8, true)), pins(new Lock(555, 8, false)));
	}

	@Test
	void setLockedIsIdempotent()
	{
		// Re-locking an already-locked lock must stay silent: the re-lock branches call setLocked in loops
		// over every intersecting lockable, and a notification per redundant call would dirty chunks and
		// push a sync packet each time.
		//
		// This is also why LocksUtil.closeDoors runs outside setLocked and on every lock-to-locked request
		// rather than on the transition. If door closing hung off the observer, a lock that already reported
		// locked would never close a door left open underneath it.
		Lock lock = new Lock(7, 5, true);
		AtomicInteger notifications = new AtomicInteger();
		Observer counter = new Observer()
		{
			@Override
			public void update(Observable o, Object arg)
			{
				notifications.incrementAndGet();
			}
		};
		lock.addObserver(counter);

		lock.setLocked(true);
		assertEquals(0, notifications.get(), "re-locking an already-locked lock must not notify");
		assertTrue(lock.isLocked());

		lock.setLocked(false);
		assertEquals(1, notifications.get(), "a real transition must notify exactly once");
		assertFalse(lock.isLocked());

		lock.setLocked(false);
		assertEquals(1, notifications.get(), "re-opening an already-open lock must not notify");
	}
}
