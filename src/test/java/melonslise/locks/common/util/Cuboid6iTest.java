package melonslise.locks.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * Geometry of {@link Cuboid6i}. Chunk-span correctness underpins the multi-chunk lock logic: which chunks a
 * lockable occupies drives storage placement, the remove() force-load fix, and the chunk-border dedup. These
 * are pure integer math (no Minecraft runtime/Bootstrap needed beyond the BlockPos data class).
 */
public class Cuboid6iTest
{
	@Test
	void volumeAndDimensions()
	{
		// A single block at the origin spans 1x1x1.
		Cuboid6i one = new Cuboid6i(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));
		assertEquals(1, one.length());
		assertEquals(1, one.height());
		assertEquals(1, one.width());
		assertEquals(1, one.volume());

		Cuboid6i box = new Cuboid6i(0, 0, 0, 2, 3, 4);
		assertEquals(2 * 3 * 4, box.volume());
	}

	@Test
	void constructorNormalizesMinMax()
	{
		// Args given in reversed order must normalize to the same box.
		Cuboid6i a = new Cuboid6i(5, 6, 7, 1, 2, 3);
		Cuboid6i b = new Cuboid6i(1, 2, 3, 5, 6, 7);
		assertEquals(b, a);
	}

	@Test
	void intersectsBlockPos()
	{
		Cuboid6i box = new Cuboid6i(0, 0, 0, 2, 2, 2); // covers blocks (0,0,0) and (1,1,1)
		assertTrue(box.intersects(new BlockPos(0, 0, 0)));
		assertTrue(box.intersects(new BlockPos(1, 1, 1)));
		assertFalse(box.intersects(new BlockPos(2, 0, 0))); // exclusive upper bound
		assertFalse(box.intersects(new BlockPos(-1, 0, 0)));
	}

	@Test
	void intersectsAndIntersectionAreConsistent()
	{
		Cuboid6i a = new Cuboid6i(0, 0, 0, 4, 4, 4);
		Cuboid6i b = new Cuboid6i(2, 2, 2, 6, 6, 6);
		assertTrue(a.intersects(b));
		assertEquals(new Cuboid6i(2, 2, 2, 4, 4, 4), a.intersection(b));

		Cuboid6i disjoint = new Cuboid6i(10, 0, 0, 12, 2, 2);
		assertFalse(a.intersects(disjoint));
	}

	@Test
	void containedChunksSingleChunk()
	{
		// A box fully inside chunk (0,0) occupies exactly one chunk.
		Cuboid6i box = new Cuboid6i(new BlockPos(1, 64, 1), new BlockPos(3, 66, 3));
		List<long[]> chunks = collectChunks(box);
		assertEquals(1, chunks.size());
		assertEquals(0L, chunks.get(0)[0]);
		assertEquals(0L, chunks.get(0)[1]);
	}

	@Test
	void containedChunksSpanningBorder()
	{
		// A box straddling x=16 (the chunk 0/1 border) occupies two chunks.
		Cuboid6i box = new Cuboid6i(new BlockPos(15, 64, 1), new BlockPos(16, 66, 1));
		List<long[]> chunks = collectChunks(box);
		assertEquals(2, chunks.size());
		assertTrue(chunks.stream().anyMatch(c -> c[0] == 0 && c[1] == 0));
		assertTrue(chunks.stream().anyMatch(c -> c[0] == 1 && c[1] == 0));
	}

	@Test
	void containedChunksNegativeCoords()
	{
		// Negative coordinates must use floor-div (bit shift) chunk math, not truncating division.
		Cuboid6i box = new Cuboid6i(new BlockPos(-1, 64, -1), new BlockPos(-1, 64, -1));
		List<long[]> chunks = collectChunks(box);
		assertEquals(1, chunks.size());
		assertEquals(-1L, chunks.get(0)[0]);
		assertEquals(-1L, chunks.get(0)[1]);
	}

	@Test
	void containedChunksToEndEarlyShortCircuits()
	{
		Cuboid6i box = new Cuboid6i(new BlockPos(15, 64, 1), new BlockPos(16, 66, 1)); // spans 2 chunks
		// Returning null for the second chunk with endEarly=true aborts and returns null overall.
		List<String> result = box.containedChunksTo((x, z) -> x == 1 ? null : "ok", true);
		org.junit.jupiter.api.Assertions.assertNull(result);
	}

	@Test
	void containedPosCoversBothHalvesOfASingleDoor()
	{
		// LockItem.easyLock spans a door's two halves, and LocksUtil.closeDoors walks exactly these
		// positions looking for an open door to shut. Missing one would leave half a door open under a
		// lock reporting locked.
		Cuboid6i box = new Cuboid6i(new BlockPos(4, 64, 9), new BlockPos(4, 65, 9));
		assertEquals(List.of(new BlockPos(4, 64, 9), new BlockPos(4, 65, 9)), collectPos(box));
	}

	@Test
	void containedPosCoversAllFourBlocksOfADoubleDoor()
	{
		// easyLock jumps sideways to the paired leaf when a door has a hinge partner, so the box is 2x2x1
		// and every leaf must be protected and closable.
		Cuboid6i box = new Cuboid6i(new BlockPos(4, 64, 9), new BlockPos(5, 65, 9));
		assertEquals(4, collectPos(box).size());
		assertTrue(collectPos(box).containsAll(List.of(
			new BlockPos(4, 64, 9), new BlockPos(4, 65, 9),
			new BlockPos(5, 64, 9), new BlockPos(5, 65, 9))));
	}

	// Helper: BlockPos.betweenClosed reuses one mutable cursor, so positions must be copied to be kept.
	private static List<BlockPos> collectPos(Cuboid6i box)
	{
		List<BlockPos> out = new ArrayList<>();
		for(BlockPos pos : box.getContainedPos())
			out.add(pos.immutable());
		return out;
	}

	// Helper: collect (chunkX, chunkZ) pairs the box reports as contained.
	private static List<long[]> collectChunks(Cuboid6i box)
	{
		return box.containedChunksTo((x, z) -> new long[]{x, z}, false);
	}
}
