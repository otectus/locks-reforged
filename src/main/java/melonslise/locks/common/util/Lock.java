package melonslise.locks.common.util;

import java.util.Observable;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import melonslise.locks.Locks;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockingItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A lock: a public credential (the id, which keys carry and which every client sees) and a secret combination.
 *
 * <p>The two must never be derivable from each other. A Lock exists in exactly one of three roles:
 * <ul>
 * <li><b>generated</b> — a new server-side lock, its combination drawn from a caller-supplied generator;</li>
 * <li><b>restored</b> — a server-side lock read back from NBT, its stored permutation validated and copied;</li>
 * <li><b>client view</b> — id, length and locked state only. The combination is genuinely unknown here, so
 * {@link #getPin}/{@link #checkPin} throw rather than return a plausible answer.</li>
 * </ul>
 */
public class Lock extends Observable
{
	public final int id;
	// index is the order, value is the pin number. Null on a client view, where the combination is unknown.
	@Nullable
	protected final byte[] combo;
	// Only meaningful on a client view, which has no combo array to measure.
	protected final int length;
	protected boolean locked;

	private Lock(int id, @Nullable byte[] combo, int length, boolean locked)
	{
		this.id = id;
		this.combo = combo;
		this.length = length;
		this.locked = locked;
	}

	/** A new lock with a freshly drawn combination. The id never seeds the generator. */
	public static Lock generate(int id, int length, boolean locked, Random rng)
	{
		return new Lock(id, LockSecretPolicy.generate(rng, length), length, locked);
	}

	/**
	 * A lock read back from storage. The permutation is validated by the caller and copied here, so no other
	 * object keeps a writable alias into this lock's secret.
	 */
	public static Lock restore(int id, byte[] combo, boolean locked)
	{
		return new Lock(id, combo.clone(), combo.length, locked);
	}

	/** The client's view of a lock: everything it is allowed to know, and nothing else. */
	public static Lock clientView(int id, int length, boolean locked)
	{
		return new Lock(id, null, length, locked);
	}

	/** An independent lock with the same credential, state and combination — no shared array. */
	public Lock copy()
	{
		return new Lock(this.id, this.combo == null ? null : this.combo.clone(), this.length, this.locked);
	}

	/** Whether this instance actually knows its combination (i.e. is not a client view). */
	public boolean hasCombo()
	{
		return this.combo != null;
	}

	public static Lock from(ItemStack stack)
	{
		return generate(LockingItem.getOrSetId(stack), LockItem.getOrSetLength(stack), !LockItem.isOpen(stack), ThreadLocalRandom.current());
	}

	public static final String KEY_ID = "Id", KEY_LENGTH = "Length", KEY_LOCKED = "Locked", KEY_COMBO = "Combo";
	// Provenance marker. Present means the combination has already been decided under the 1.7.5 rules, so the
	// id-derived migration below never runs twice and never rerolls a combination a player has already learned.
	public static final String KEY_SECRET_VERSION = "ComboVersion";
	private static final int SECRET_VERSION = 1;

	// The server-wide migration salt, read from the overworld's LocksSavedData at server start and cached here
	// as a plain field. fromNbt runs off-thread during chunk deserialization, where DimensionDataStorage must
	// not be touched, so this is the only value it may consult.
	private static volatile long migrationSalt;

	public static void setMigrationSalt(long salt)
	{
		migrationSalt = salt;
	}

	public static long getMigrationSalt()
	{
		return migrationSalt;
	}

	public static Lock fromNbt(CompoundTag nbt)
	{
		int id = nbt.getInt(KEY_ID);
		boolean locked = nbt.getBoolean(KEY_LOCKED);
		byte[] combo = nbt.contains(KEY_COMBO) ? nbt.getByteArray(KEY_COMBO) : null;
		// Pre-combo-persistence saves stored only Id/Length/Locked; that combination was the id-derived shuffle
		// by definition, so reconstruct it and let the migration below reroll it like any other.
		if(combo == null)
		{
			int length = nbt.getByte(KEY_LENGTH) & 0xFF;
			if(length < LockSecretPolicy.MIN_LENGTH || length > LockSecretPolicy.MAX_LENGTH)
			{
				Locks.LOGGER.warn("Rejecting a lock (id {}) with an out-of-range stored length of {}", id, length);
				length = LockSecretPolicy.MIN_LENGTH;
			}
			combo = LockSecretPolicy.generate(new Random(id), length);
		}
		if(!LockSecretPolicy.isValidPermutation(combo, combo.length))
		{
			// Never allocate or accept state from a malformed record: replace it with a fresh salted combination
			// of a safe length rather than handing back a lock whose pins do not exist.
			int length = combo.length < LockSecretPolicy.MIN_LENGTH || combo.length > LockSecretPolicy.MAX_LENGTH
				? LockSecretPolicy.MIN_LENGTH : combo.length;
			Locks.LOGGER.warn("Lock id {} has a malformed stored combination; regenerating it", id);
			return restore(id, LockSecretPolicy.deriveLegacyReroll(migrationSalt, id, length), locked);
		}
		// One-shot migration off the public id-derived combinations. Provenance is decidable: if the stored
		// permutation is exactly what new Random(id) produced, every client already knew it.
		if(nbt.getInt(KEY_SECRET_VERSION) < SECRET_VERSION && LockSecretPolicy.isLegacyIdDerived(id, combo))
			combo = LockSecretPolicy.deriveLegacyReroll(migrationSalt, id, combo.length);
		return restore(id, combo, locked);
	}

	public static CompoundTag toNbt(Lock lock)
	{
		CompoundTag nbt = new CompoundTag();
		nbt.putInt(KEY_ID, lock.id);
		nbt.putByteArray(KEY_COMBO, lock.combo == null ? new byte[0] : lock.combo.clone());
		nbt.putBoolean(KEY_LOCKED, lock.locked);
		nbt.putInt(KEY_SECRET_VERSION, SECRET_VERSION);
		return nbt;
	}

	public static Lock fromBuf(FriendlyByteBuf buf)
	{
		int id = buf.readInt();
		int length = buf.readInt();
		boolean locked = buf.readBoolean();
		// Bound before anything is sized off it. The client is only ever told how many pins there are, so this
		// is a view, never a functional lock.
		if(length < LockSecretPolicy.MIN_LENGTH || length > LockSecretPolicy.MAX_LENGTH)
		{
			Locks.LOGGER.warn("Received a lock (id {}) with an out-of-range length of {}", id, length);
			length = LockSecretPolicy.MIN_LENGTH;
		}
		return clientView(id, length, locked);
	}

	public static void toBuf(FriendlyByteBuf buf, Lock lock)
	{
		buf.writeInt(lock.id);
		// Only send the combo length to the client — the pin order is server-authoritative
		buf.writeInt(lock.getLength());
		buf.writeBoolean(lock.isLocked());
	}

	public int getLength()
	{
		return this.combo == null ? this.length : this.combo.length;
	}

	public boolean isLocked()
	{
		return this.locked;
	}

	public void setLocked(boolean locked)
	{
		if(this.locked == locked)
			return;
		this.locked = locked;
		this.setChanged();
		this.notifyObservers();
	}

	public int getPin(int index)
	{
		// A client view has no combination. Failing loudly here is deliberate: a silent plausible answer is
		// exactly the regression this class exists to prevent.
		if(this.combo == null)
			throw new IllegalStateException("Lock " + this.id + " is a client view and does not know its combination");
		return this.combo[index];
	}

	public boolean checkPin(int index, int pin)
	{
		return this.getPin(index) == pin;
	}
}
