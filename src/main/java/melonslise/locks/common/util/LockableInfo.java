package melonslise.locks.common.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

public class LockableInfo
{
	public final Cuboid6i bb;
	public final Lock lock;
	public final Transform tr;
	public final ItemStack stack;
	public final int id;

	// The lock and the stack are copied, never aliased: a template captured from the world would otherwise share
	// its Lock object (and its combo array) with the live lockable it was read from, so every placement of that
	// template and the original would all be the same mutable lock.
	public LockableInfo(Cuboid6i bb, Lock lock, Transform tr, ItemStack stack, int id)
	{
		this.bb = bb;
		this.lock = lock.copy();
		this.tr = tr;
		this.stack = stack.copy();
		this.id = id;
	}

	public static LockableInfo fromNbt(CompoundTag nbt)
	{
		int trIdx = nbt.getByte(Lockable.KEY_TRANSFORM) & 0xFF;
		Transform[] transforms = Transform.values();
		Transform tr = trIdx < transforms.length ? transforms[trIdx] : transforms[0];
		return new LockableInfo(Cuboid6i.fromNbt(nbt.getCompound(Lockable.KEY_BB)), Lock.fromNbt(nbt.getCompound(Lockable.KEY_LOCK)), tr, ItemStack.of(nbt.getCompound(Lockable.KEY_STACK)), nbt.getInt(Lockable.KEY_ID));
	}

	public static CompoundTag toNbt(LockableInfo lkb)
	{
		CompoundTag nbt = new CompoundTag();
		nbt.put(Lockable.KEY_BB, Cuboid6i.toNbt(lkb.bb));
		nbt.put(Lockable.KEY_LOCK, Lock.toNbt(lkb.lock));
		nbt.putByte(Lockable.KEY_TRANSFORM, (byte) lkb.tr.ordinal());
		nbt.put(Lockable.KEY_STACK, lkb.stack.serializeNBT());
		nbt.putInt(Lockable.KEY_ID, lkb.id);
		return nbt;
	}
}
