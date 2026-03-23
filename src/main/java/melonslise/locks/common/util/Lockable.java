package melonslise.locks.common.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.item.LockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class Lockable extends Observable implements Observer
{
	public static class State
	{
		public static final AABB
			VERT_Z_BB = new AABB(-2d / 16d, -3d / 16d, 0.5d / 16d, 2d / 16d, 3d / 16d, 0.5d / 16d),
			VERT_X_BB = LocksUtil.rotateY(VERT_Z_BB),
			HOR_Z_BB = LocksUtil.rotateX(VERT_Z_BB),
			HOR_X_BB = LocksUtil.rotateY(HOR_Z_BB);

		public static AABB getBounds(Transform tr)
		{
			return tr.face == AttachFace.WALL ? tr.dir.getAxis() == Direction.Axis.Z ? VERT_Z_BB : VERT_X_BB : tr.dir.getAxis() == Direction.Axis.Z ? HOR_Z_BB : HOR_X_BB;
		}

		public final Vec3 pos;
		public final Transform tr;
		public final AABB bb;

		public State(Vec3 pos, Transform tr)
		{
			this(pos, tr, getBounds(tr).move(pos));
		}

		public State(Vec3 pos, Transform tr, AABB bb)
		{
			this.pos = pos;
			this.tr = tr;
			this.bb = bb;
		}

		@OnlyIn(Dist.CLIENT)
		public boolean inView(Frustum ch)
		{
			return ch.cubeInFrustum(this.bb.minX, this.bb.minY, this.bb.minZ, this.bb.maxX, this.bb.maxY, this.bb.maxZ);
		}

		@OnlyIn(Dist.CLIENT)
		public boolean inRange(Vec3 pos)
		{
			Minecraft mc = Minecraft.getInstance();
			double dist = this.pos.distanceToSqr(pos);
			double max = mc.options.renderDistance().get() * 8;
			return dist < max * max;
		}
	}

	public final Cuboid6i bb;
	public final Lock lock;
	public final Transform tr;
	public final ItemStack stack;
	public final int id;

	public int oldSwingTicks, swingTicks, maxSwingTicks;

	private long cachedStateHash;
	private State cachedState;

	public Lockable(Cuboid6i bb, Lock lock, Transform tr, ItemStack stack, Level world)
	{
		this(bb, lock, tr, stack, world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElseThrow(() -> new IllegalStateException("Lockable handler capability missing")).nextId());
	}

	public Lockable(Cuboid6i bb, Lock lock, Transform tr, ItemStack stack, int id)
	{
		this.bb = bb;
		this.lock = lock;
		this.tr = tr;
		this.stack = stack;
		this.id = id;
		lock.addObserver(this);
	}

	public static final String KEY_BB = "Bb", KEY_LOCK = "Lock", KEY_TRANSFORM = "Transform", KEY_STACK = "Stack", KEY_ID = "Id";

	public static Lockable fromNbt(CompoundTag nbt)
	{
		return new Lockable(Cuboid6i.fromNbt(nbt.getCompound(KEY_BB)), Lock.fromNbt(nbt.getCompound(KEY_LOCK)), Transform.values()[(int) nbt.getByte(KEY_TRANSFORM)], ItemStack.of(nbt.getCompound(KEY_STACK)), nbt.getInt(KEY_ID));
	}

	public static CompoundTag toNbt(Lockable lkb)
	{
		CompoundTag nbt = new CompoundTag();
		nbt.put(KEY_BB, Cuboid6i.toNbt(lkb.bb));
		nbt.put(KEY_LOCK, Lock.toNbt(lkb.lock));
		nbt.putByte(KEY_TRANSFORM, (byte) lkb.tr.ordinal());
		nbt.put(KEY_STACK, lkb.stack.serializeNBT());
		nbt.putInt(KEY_ID, lkb.id);
		return nbt;
	}

	public static int idFromNbt(CompoundTag nbt)
	{
		return nbt.getInt(KEY_ID);
	}

	public static Lockable fromBuf(FriendlyByteBuf buf)
	{
		return new Lockable(Cuboid6i.fromBuf(buf), Lock.fromBuf(buf), buf.readEnum(Transform.class), buf.readItem(), buf.readInt());
	}

	public static void toBuf(FriendlyByteBuf buf, Lockable lkb)
	{
		Cuboid6i.toBuf(buf, lkb.bb);
		Lock.toBuf(buf, lkb.lock);
		buf.writeEnum(lkb.tr);
		buf.writeItem(lkb.stack);
		buf.writeInt(lkb.id);
	}

	@Override
	public void update(Observable lock, Object data)
	{
		this.setChanged();
		this.notifyObservers();
		LockItem.setOpen(this.stack, !this.lock.locked);
	}

	public void tick()
	{
		this.oldSwingTicks = this.swingTicks;
		if(this.swingTicks > 0)
			--this.swingTicks;
	}

	public void swing(int ticks)
	{
		this.swingTicks = this.oldSwingTicks = this.maxSwingTicks = ticks;
	}

	public State getLockState(Level world)
	{
		long hash = 0;
		for(BlockPos pos : this.bb.getContainedPos())
		{
			if(!world.hasChunkAt(pos))
				return null;
			hash = hash * 31 + System.identityHashCode(world.getBlockState(pos));
		}
		if(this.cachedState != null && hash == this.cachedStateHash)
			return this.cachedState;
		ArrayList<AABB> boxes = new ArrayList<>(4);
		for(BlockPos pos : this.bb.getContainedPos())
		{
			VoxelShape shape = world.getBlockState(pos).getShape(world, pos);
			if(shape.isEmpty())
				continue;
			AABB bb = shape.bounds();
			bb = bb.move(pos);
			AABB union = bb;
			Iterator<AABB> it = boxes.iterator();
			while(it.hasNext())
			{
				AABB bb1 = it.next();
				if(LocksUtil.intersectsInclusive(union, bb1))
				{
					union = union.minmax(bb1);
					it.remove();
				}
			}
			boxes.add(union);
		}
		if(boxes.isEmpty())
			return null;
		Direction side = this.tr.getCuboidFace();
		Vec3 center = this.bb.sideCenter(side);
		Vec3 point = center;
		double min = -1d;
		for(AABB box : boxes)
		{
			Vec3 point1 = LocksUtil.sideCenter(box, side).add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.05d));
			double dist = center.distanceToSqr(point1);
			if(min != -1d && dist >= min)
				continue;
			point = point1;
			min = dist;
		}
		State state = new State(point, Transform.fromDirection(side, this.tr.dir));
		this.cachedStateHash = hash;
		this.cachedState = state;
		return state;
	}
}
