package melonslise.locks.common.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * NBT and network handling of {@link Lock}. Two things are being protected here: the combo (pin order) and the
 * locked flag are the authoritative lock state saved to chunk NBT, so a lossy round-trip would silently re-key
 * every reloaded lock; and the combination must never be reachable from anything a client holds, which is why
 * a decoded client view refuses to answer pin questions at all. Pure CompoundTag/buffer work, no Bootstrap.
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
		Lock original = Lock.generate(42, 6, true, new Random(1L));
		Lock restored = Lock.fromNbt(Lock.toNbt(original));

		assertEquals(original.getLength(), restored.getLength());
		assertArrayEquals(pins(original), pins(restored), "saved combo must survive a round-trip exactly");
		assertTrue(restored.isLocked());
	}

	@Test
	void roundTripPreservesUnlockedState()
	{
		Lock original = Lock.generate(7, 4, false, new Random(2L));
		Lock restored = Lock.fromNbt(Lock.toNbt(original));
		assertFalse(restored.isLocked());
	}

	@Test
	void reshuffledComboSurvivesRoundTrip()
	{
		// A combo that diverged from the id seed was deliberately set by somebody; it is not public knowledge
		// and must be restored verbatim rather than rerolled or regenerated.
		byte[] custom = {3, 0, 2, 1};
		Lock original = Lock.restore(99, custom, true);
		Lock restored = Lock.fromNbt(Lock.toNbt(original));
		assertArrayEquals(custom, pins(restored));
	}

	@Test
	void restoreDoesNotAliasItsInput()
	{
		byte[] combo = {2, 0, 1};
		Lock lock = Lock.restore(5, combo, true);
		combo[0] = 1;
		combo[1] = 2;
		assertArrayEquals(new byte[] {2, 0, 1}, pins(lock), "a lock must not share its combo array with the caller");
	}

	@Test
	void copyIsEqualButIndependent()
	{
		Lock original = Lock.restore(11, new byte[] {1, 3, 0, 2}, true);
		Lock copy = original.copy();
		assertNotSame(original, copy);
		assertEquals(original.id, copy.id);
		assertEquals(original.isLocked(), copy.isLocked());
		assertArrayEquals(pins(original), pins(copy));

		copy.setLocked(false);
		assertTrue(original.isLocked(), "a copy must not share mutable state with its source");
	}

	@Test
	void legacyIdDerivedComboIsRerolledOnLoad()
	{
		// Every pre-1.7.5 save holds a combination that new Random(id) reproduces — i.e. one every client could
		// already derive from the id it is sent. Loading such a record must replace it with the salted reroll.
		Lock.setMigrationSalt(0x0123456789ABCDEFL);
		int id = 123, length = 5;

		CompoundTag legacy = new CompoundTag();
		legacy.putInt(Lock.KEY_ID, id);
		legacy.putByteArray(Lock.KEY_COMBO, LockSecretPolicy.generate(new Random(id), length));
		legacy.putBoolean(Lock.KEY_LOCKED, true);

		Lock restored = Lock.fromNbt(legacy);
		assertArrayEquals(LockSecretPolicy.deriveLegacyReroll(0x0123456789ABCDEFL, id, length), pins(restored));
		assertTrue(restored.isLocked(), "the migration must not disturb the locked state");
		assertEquals(id, restored.id, "the migration must never rotate the credential");
	}

	@Test
	void migrationIsIdempotent()
	{
		Lock.setMigrationSalt(0x0123456789ABCDEFL);
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(Lock.KEY_ID, 123);
		legacy.putByteArray(Lock.KEY_COMBO, LockSecretPolicy.generate(new Random(123), 5));
		legacy.putBoolean(Lock.KEY_LOCKED, true);

		Lock once = Lock.fromNbt(legacy);
		Lock twice = Lock.fromNbt(Lock.toNbt(once));
		assertArrayEquals(pins(once), pins(twice), "the provenance marker must stop a second reroll");
	}

	@Test
	void legacyLengthOnlySaveMigratesToo()
	{
		// Pre-combo-persistence saves stored only Id/Length/Locked, which is the id-derived combination by
		// definition, so they must land on the same rerolled value.
		Lock.setMigrationSalt(4242L);
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(Lock.KEY_ID, 77);
		legacy.putByte(Lock.KEY_LENGTH, (byte) 5);
		legacy.putBoolean(Lock.KEY_LOCKED, true);

		Lock restored = Lock.fromNbt(legacy);
		assertEquals(5, restored.getLength());
		assertArrayEquals(LockSecretPolicy.deriveLegacyReroll(4242L, 77, 5), pins(restored));
	}

	@Test
	void malformedStoredComboIsRejected()
	{
		Lock.setMigrationSalt(9L);
		CompoundTag nbt = new CompoundTag();
		nbt.putInt(Lock.KEY_ID, 3);
		// Duplicated pin and an out-of-range one: not a permutation, so nothing may be restored from it.
		nbt.putByteArray(Lock.KEY_COMBO, new byte[] {0, 0, 9});
		nbt.putBoolean(Lock.KEY_LOCKED, true);

		Lock restored = Lock.fromNbt(nbt);
		assertEquals(3, restored.getLength());
		assertTrue(LockSecretPolicy.isValidPermutation(pins(restored), restored.getLength()));
	}

	@Test
	void oversizedStoredLengthIsRejected()
	{
		Lock.setMigrationSalt(9L);
		CompoundTag nbt = new CompoundTag();
		nbt.putInt(Lock.KEY_ID, 3);
		nbt.putByteArray(Lock.KEY_COMBO, new byte[LockSecretPolicy.MAX_LENGTH + 40]);
		nbt.putBoolean(Lock.KEY_LOCKED, true);

		Lock restored = Lock.fromNbt(nbt);
		assertTrue(restored.getLength() >= LockSecretPolicy.MIN_LENGTH && restored.getLength() <= LockSecretPolicy.MAX_LENGTH);
	}

	@Test
	void decodedClientViewRefusesToAnswerPins()
	{
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		Lock.toBuf(buf, Lock.generate(555, 5, true, new Random(3L)));
		Lock view = Lock.fromBuf(buf);

		assertEquals(555, view.id);
		assertEquals(5, view.getLength());
		assertTrue(view.isLocked());
		assertFalse(view.hasCombo());
		assertThrows(IllegalStateException.class, () -> view.getPin(0), "a client view must never produce a pin");
		assertThrows(IllegalStateException.class, () -> view.checkPin(0, 0));
	}

	@Test
	void malformedDecodedLengthIsBounded()
	{
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeInt(1);
		buf.writeInt(Integer.MAX_VALUE);
		buf.writeBoolean(true);
		Lock view = Lock.fromBuf(buf);
		assertTrue(view.getLength() >= LockSecretPolicy.MIN_LENGTH && view.getLength() <= LockSecretPolicy.MAX_LENGTH);

		FriendlyByteBuf negative = new FriendlyByteBuf(Unpooled.buffer());
		negative.writeInt(1);
		negative.writeInt(-8);
		negative.writeBoolean(true);
		assertTrue(Lock.fromBuf(negative).getLength() >= LockSecretPolicy.MIN_LENGTH);
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
		Lock lock = Lock.generate(7, 5, true, new Random(4L));
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
