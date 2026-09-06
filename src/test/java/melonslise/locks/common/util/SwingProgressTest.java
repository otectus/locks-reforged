package melonslise.locks.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The swing interpolation. A lock that has never swung has all three tick counters at 0, and the renderer used
 * to divide by maxSwingTicks directly — 0/0 = NaN, which then travelled into a rotation matrix. Every idle lock
 * in the world hits this, so "finite for every input" is the whole point of the class.
 */
public class SwingProgressTest
{
	@Test
	void idleLockReturnsExactlyZero()
	{
		assertEquals(0f, SwingProgress.normalized(0, 0, 0, 0.5f), "an idle lock must produce no rotation at all");
	}

	@Test
	void negativeMaxTicksReturnsZero()
	{
		assertEquals(0f, SwingProgress.normalized(3, 2, -1, 0.5f));
	}

	@Test
	void normalInputStaysInRange()
	{
		for(int max = 1; max <= 20; ++max)
			for(int old = 0; old <= max; ++old)
				for(int curr = 0; curr <= max; ++curr)
				{
					float p = SwingProgress.normalized(old, curr, max, 0.5f);
					assertTrue(p >= 0f && p <= 1f, "progress out of range: " + p);
				}
	}

	@Test
	void everyCombinationIsFinite()
	{
		int[] values = {-5, -1, 0, 1, 7, 20, 100};
		float[] partials = {0f, 0.5f, 1f};
		for(int old : values)
			for(int curr : values)
				for(int max : values)
					for(float pt : partials)
					{
						float p = SwingProgress.normalized(old, curr, max, pt);
						assertTrue(Float.isFinite(p), "non-finite progress for " + old + "/" + curr + "/" + max);
						assertTrue(p >= 0f && p <= 1f);
					}
	}
}
