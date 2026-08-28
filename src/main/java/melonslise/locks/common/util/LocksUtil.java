package melonslise.locks.common.util;

import java.util.Random;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;

import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.init.LocksCapabilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.fml.ModList;

public final class LocksUtil
{
	private LocksUtil() {}

	public static void shuffle(byte[] array, Random rng)
	{
		for (int a = array.length - 1; a > 0; --a)
		{
			int index = rng.nextInt(a + 1);
			byte temp = array[index];
			array[index] = array[a];
			array[a] = temp;
		}
	}

	public static boolean chance(RandomSource rng, double ch)
	{
		return ch == 1d || ch != 0d && rng.nextDouble() <= ch;
	}

	public static BlockPos transform(int x, int y, int z, StructurePlaceSettings settings)
	{
		switch(settings.getMirror())
		{
		case LEFT_RIGHT:
			z = -z + 1;
			break;
		case FRONT_BACK:
			x = -x + 1;
			break;
		default:
			break;
		}
		int x1 = settings.getRotationPivot().getX();
		int z1 = settings.getRotationPivot().getZ();
		switch(settings.getRotation())
		{
		case COUNTERCLOCKWISE_90:
			return new BlockPos(x1 - z1 + z, y, x1 + z1 - x + 1);
		case CLOCKWISE_90:
			return new BlockPos(x1 + z1 - z + 1, y, z1 - x1 + x);
		case CLOCKWISE_180:
			return new BlockPos(x1 + x1 - x + 1, y, z1 + z1 - z + 1);
		default:
			return new BlockPos(x, y, z);
		}
	}

	public static AttachFace faceFromDir(Direction dir)
	{
		return dir == Direction.UP ? AttachFace.CEILING : dir == Direction.DOWN ? AttachFace.FLOOR : AttachFace.WALL;
	}

	public static AABB rotateY(AABB bb)
	{
		return new AABB(bb.minZ, bb.minY, bb.minX, bb.maxZ, bb.maxY, bb.maxX);
	}

	public static AABB rotateX(AABB bb)
	{
		return new AABB(bb.minX, bb.minZ, bb.minY, bb.maxX, bb.maxZ, bb.maxY);
	}

	public static boolean intersectsInclusive(AABB bb1, AABB bb2)
	{
		return bb1.minX <= bb2.maxX && bb1.maxX >= bb2.minX && bb1.minY <= bb2.maxY && bb1.maxY >= bb2.minY && bb1.minZ <= bb2.maxZ && bb1.maxZ >= bb2.minZ;
	}

	public static Vec3 sideCenter(AABB bb, Direction side)
	{
		Vec3i dir = side.getNormal();
		return new Vec3((bb.minX + bb.maxX + (bb.maxX - bb.minX) * dir.getX()) * 0.5d, (bb.minY + bb.maxY + (bb.maxY - bb.minY) * dir.getY()) * 0.5d, (bb.minZ + bb.maxZ + (bb.maxZ - bb.minZ) * dir.getZ()) * 0.5d);
	}

	public static Stream<Lockable> intersecting(Level world, BlockPos pos)
	{
		return world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).lazyMap(cap ->
		{
			Int2ObjectMap<Lockable> chunk = cap.getInChunk(pos);
			return chunk != null ? chunk.values().stream().filter(lkb -> lkb.bb.intersects(pos)) : Stream.<Lockable>empty();
		}).orElse(Stream.empty());
	}

	public static boolean locked(Level world, BlockPos pos)
	{
		return intersecting(world, pos).anyMatch(LocksPredicates.LOCKED);
	}

	// Physically closes every open door inside bb. Server-side only.
	//
	// Kept separate from setLocked, and called on EVERY lock-to-locked request rather than only on a
	// state transition, because Lock#setLocked is idempotent: a lock that already reports locked fires
	// no observer, so a door left open underneath it would never close. That idempotence is load-bearing
	// elsewhere (the re-lock branches call setLocked in loops and each redundant call would otherwise
	// dirty chunks and send a packet), so the invariant is enforced here instead.
	//
	// Never force-loads: Level#getBlockState resolves through getChunk(..., true), so each position is
	// gated on the non-blocking hasChunkAt first — same discipline as Lockable#getLockState.
	//
	// Goes through DoorBlock#setOpen rather than writing OPEN directly so vanilla's two-half sync, sound
	// and game event still happen. DoorBlockMixin only refuses open == true, so this is allowed. Closing
	// one half updates the other via updateShape, after which the loop sees it closed and no-ops, so the
	// sound plays once. Changing only the OPEN property keeps the same Block, so ServerLevelMixin's
	// sendBlockUpdated hook early-returns and the lock is not popped.
	public static void closeDoors(Level world, Cuboid6i bb, @Nullable Entity source)
	{
		if(world.isClientSide)
			return;
		for(BlockPos pos : bb.getContainedPos())
		{
			if(!world.hasChunkAt(pos))
				continue;
			BlockState state = world.getBlockState(pos);
			if(!(state.getBlock() instanceof DoorBlock door) || !state.getValue(DoorBlock.OPEN))
				continue;
			door.setOpen(source, world, state, pos.immutable(), false);
		}
	}

	// The one place a lockable's lock state changes. Enforces "locked implies physically closed" so a
	// door can never stay open under a lock that reports locked. Every unlock/re-lock path routes here.
	//
	// Closes before flipping the lock so no client ever observes locked + open, not even for a tick.
	// Deliberately does not play a sound (each caller owns its own) or resolve loot tables (that stays
	// tied to a real locked-to-unlocked transition at the call sites that own it).
	public static void setLocked(Level world, Lockable lockable, boolean locked, @Nullable Entity source)
	{
		if(locked)
			closeDoors(world, lockable.bb, source);
		lockable.lock.setLocked(locked);
	}

	public static void resolveLootTables(Level level, Lockable lockable, Player player)
	{
		if(level.isClientSide)
			return;
		for(BlockPos pos : lockable.bb.getContainedPos())
		{
			BlockEntity be = level.getBlockEntity(pos);
			if(be == null)
				continue;
			// Skip Lootr block entities — Lootr manages its own per-player loot generation
			if(be.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("lootr"))
				continue;
			if(be instanceof RandomizableContainerBlockEntity container && container.lootTable != null)
				container.unpackLootTable(player);
		}
	}

	// Single source of truth for "what lock goes on this chest", shared by worldgen
	// (LockChestsFeature) and the Respawning Structures compat hook. Returns a ready-to-register
	// Lockable for the chest at pos, or null if it shouldn't be locked (not a valid lootable chest,
	// failed the generation chance, the right half of a double chest, or no lock tier matched).
	// access is used for block/block-entity lookups; level provides the lockable handler + server.
	public static Lockable createChestLockable(LevelAccessor access, Level level, BlockPos pos, RandomSource rng)
	{
		BlockState state = access.getBlockState(pos);
		if(!state.hasProperty(ChestBlock.TYPE))
			return null;
		boolean lootr = ModList.get().isLoaded("lootr");
		// The right half of a double chest is covered by the left half's lockable, so skip it
		// (unless Lootr is present, where each half is an independent single chest)
		if(state.getValue(ChestBlock.TYPE) == ChestType.RIGHT && !lootr)
			return null;
		BlockEntity be = access.getBlockEntity(pos);
		if(!(be instanceof RandomizableContainerBlockEntity container) || container.lootTable == null)
			return null;

		if(!chance(rng, LocksConfig.GENERATION_CHANCE.get()))
			return null;

		ItemStack stack = ItemStack.EMPTY;
		boolean usedLootScaling = false;
		if(LocksConfig.LOOT_SCALED_LOCKS.get())
		{
			double lootValue = LootValueCalculator.getLootValue(level.getServer(), container.lootTable);
			if(!Double.isNaN(lootValue))
			{
				stack = LocksConfig.getLockForLootValue(lootValue, rng);
				usedLootScaling = true;
			}
		}
		if(!usedLootScaling)
			stack = LocksConfig.getRandomLock(rng);

		// When loot-scaled locks are enabled and the loot value is below all tier thresholds, skip this chest
		if(stack.isEmpty())
			return null;

		BlockPos pos1 = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE || lootr ? pos : pos.relative(ChestBlock.getConnectedDirection(state));
		return new Lockable(new Cuboid6i(pos, pos1), Lock.from(stack), Transform.fromDirection(state.getValue(ChestBlock.FACING), Direction.NORTH), stack, level);
	}
}
