package melonslise.locks.common.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The durability a wrong pin costs a lock pick. This replaced the 1.7.2 random break roll, whose real
 * problem was that a wood pick was destroyed outright by 80% of far misses — so the guarantee under test
 * is that wear is deterministic, that it always costs something, and that no enchantment or config value
 * can drive it to zero and make picking free. Pure ints and doubles, no Bootstrap.
 */
public class LockPickWearPolicyTest
{
	// The shipped defaults, so a change to them shows up here rather than only in a play session.
	private static final double NEAR_MISS = 0.33d, STURDY_PER_LEVEL = 0.5d, FINESSE_PER_LEVEL = 0.15d;

	private static int wear(int baseWear, boolean nearMiss, int sturdy, int finesse)
	{
		return LockPickWearPolicy.wearFor(baseWear, nearMiss, NEAR_MISS, sturdy, STURDY_PER_LEVEL, finesse, FINESSE_PER_LEVEL);
	}

	// --- the base case: the lock alone decides the cost ---

	@Test
	void anUnmodifiedFarMissCostsExactlyTheLocksPickWear()
	{
		// A wood lock (pick_wear 1) and a netherite lock (24) are the two ends of the shipped ladder.
		assertEquals(1, wear(1, false, 0, 0));
		assertEquals(3, wear(3, false, 0, 0));
		assertEquals(24, wear(24, false, 0, 0));
	}

	@Test
	void aBaseWearBelowOneIsTreatedAsOne()
	{
		// Nothing should be able to register a lock that costs a pick nothing, including a bad override.
		assertEquals(1, wear(0, false, 0, 0));
		assertEquals(1, wear(-5, false, 0, 0));
	}

	// --- near miss: the off-by-one discount ---

	@Test
	void aNearMissCostsLessThanAFarMiss()
	{
		assertEquals(8, wear(24, true, 0, 0));
		assertTrue(wear(12, true, 0, 0) < wear(12, false, 0, 0));
	}

	@Test
	void aNearMissOnACheapLockStillCostsOne()
	{
		// 1 * 0.33 rounds to 0, which would make wood locks free to brute-force one pin at a time.
		assertEquals(1, wear(1, true, 0, 0));
		assertEquals(1, wear(2, true, 0, 0));
	}

	// --- Sturdy: the lock's enchantment raises the cost ---

	@Test
	void sturdyRaisesWearMonotonically()
	{
		int plain = wear(4, false, 0, 0);
		int one = wear(4, false, 1, 0);
		int two = wear(4, false, 2, 0);
		int three = wear(4, false, 3, 0);
		assertTrue(plain < one && one < two && two < three);
		assertEquals(6, one);    // 4 * 1.5
		assertEquals(8, two);    // 4 * 2.0
		assertEquals(10, three); // 4 * 2.5
	}

	// --- Finesse: the pick's enchantment lowers the cost, but never to nothing ---

	@Test
	void finesseLowersWearMonotonically()
	{
		int plain = wear(100, false, 0, 0);
		int one = wear(100, false, 0, 1);
		int three = wear(100, false, 0, 3);
		assertTrue(three < one && one < plain);
		assertEquals(85, one);  // 100 * 0.85
		assertEquals(55, three); // 100 * 0.55
	}

	@Test
	void finesseCanNeverMakeAWrongPinFree()
	{
		// The durability-era successor to 1.7.2's "keep at least a 5% break chance" clamp. Even with an
		// absurd config value and a maxed enchantment, a mistake still costs the pick something.
		assertEquals(1, LockPickWearPolicy.wearFor(24, false, NEAR_MISS, 0, STURDY_PER_LEVEL, 3, 1.0d));
		assertEquals(1, LockPickWearPolicy.wearFor(1000, true, 0d, 0, STURDY_PER_LEVEL, 10, 1.0d));
		assertEquals(1, wear(1, false, 0, 3));
	}

	@Test
	void negativeTuningValuesAreIgnoredRatherThanInverted()
	{
		// A hand-edited config must not turn Finesse into a wear bonus or Sturdy into a discount.
		assertEquals(10, LockPickWearPolicy.wearFor(10, false, NEAR_MISS, 2, -1.0d, 0, FINESSE_PER_LEVEL));
		assertEquals(10, LockPickWearPolicy.wearFor(10, false, NEAR_MISS, 0, STURDY_PER_LEVEL, 2, -1.0d));
		assertEquals(1, LockPickWearPolicy.wearFor(10, true, -1.0d, 0, STURDY_PER_LEVEL, 0, FINESSE_PER_LEVEL));
	}

	// --- the two enchantments together ---

	@Test
	void sturdyAndFinesseCompose()
	{
		// 12 * 2.0 (Sturdy II) * 0.7 (Finesse II) = 16.8, rounded to 17.
		assertEquals(17, wear(12, false, 2, 2));
	}

	// --- the headline guarantee ---

	@Test
	void theShippedLadderGivesAMatchedPickAboutThirtyTwoMistakes()
	{
		// wood pick 32 durability vs wood lock pick_wear 1; netherite pick 768 vs netherite lock 24.
		// This is the whole point of the release: a fixed, countable number of mistakes instead of a roll.
		assertEquals(32, 32 / wear(1, false, 0, 0));
		assertEquals(32, 768 / wear(24, false, 0, 0));
		// And a wood pick has no business on a netherite lock: one miss and it is gone.
		assertEquals(1, 32 / wear(24, false, 0, 0));
	}
}
