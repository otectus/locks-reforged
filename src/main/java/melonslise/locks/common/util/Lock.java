package melonslise.locks.common.util;

import java.util.Observable;
import java.util.Random;

import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockingItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class Lock extends Observable
{
	public final int id;
	// index is the order, value is the pin number
	protected final byte[] combo;
	protected boolean locked;

	public final Random rng;

	public Lock(int id, int length, boolean locked)
	{
		this.id = id;
		this.rng = new Random(id);
		this.combo = this.shuffle(length);
		this.locked = locked;
	}

	// Constructor that restores a previously saved combo (for NBT/network persistence)
	public Lock(int id, byte[] combo, boolean locked)
	{
		this.id = id;
		this.rng = new Random(id);
		this.combo = combo;
		this.locked = locked;
	}

	public static Lock from(ItemStack stack)
	{
		return new Lock(LockingItem.getOrSetId(stack), LockItem.getOrSetLength(stack), !LockItem.isOpen(stack));
	}

	public static final String KEY_ID = "Id", KEY_LENGTH = "Length", KEY_LOCKED = "Locked", KEY_COMBO = "Combo";

	public static Lock fromNbt(CompoundTag nbt)
	{
		int id = nbt.getInt(KEY_ID);
		boolean locked = nbt.getBoolean(KEY_LOCKED);
		// If combo array is saved, restore it directly (preserves reshuffled combos)
		if(nbt.contains(KEY_COMBO))
			return new Lock(id, nbt.getByteArray(KEY_COMBO), locked);
		// Backward compatibility: regenerate combo from ID seed if not saved
		return new Lock(id, nbt.getByte(KEY_LENGTH), locked);
	}

	public static CompoundTag toNbt(Lock lock)
	{
		CompoundTag nbt = new CompoundTag();
		nbt.putInt(KEY_ID, lock.id);
		nbt.putByteArray(KEY_COMBO, lock.combo);
		nbt.putBoolean(KEY_LOCKED, lock.locked);
		return nbt;
	}

	public static Lock fromBuf(FriendlyByteBuf buf)
	{
		int id = buf.readInt();
		int length = buf.readInt();
		boolean locked = buf.readBoolean();
		// Client receives only the length, not the actual combo
		return new Lock(id, new byte[length], locked);
	}

	public static void toBuf(FriendlyByteBuf buf, Lock lock)
	{
		buf.writeInt(lock.id);
		// Only send the combo length to the client — the pin order is server-authoritative
		buf.writeInt(lock.combo.length);
		buf.writeBoolean(lock.isLocked());
	}

	public byte[] shuffle(int length)
	{
		byte[] combo = new byte[length];
		for(byte a = 0; a < length; ++a)
			combo[a] = a;
		LocksUtil.shuffle(combo, this.rng);
		return combo;
	}

	public int getLength()
	{
		return this.combo.length;
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
		return this.combo[index];
	}

	public boolean checkPin(int index, int pin)
	{
		return this.getPin(index) == pin;
	}
}
