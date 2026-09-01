package melonslise.locks.common.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import melonslise.locks.common.container.LockPickingPolicy.PinOutcome;

/**
 * The rules of a picking session. These decide who may open the minigame, who may keep acting on it, and
 * what a wrong pin costs — so a mistake here is either an access-control hole (picking a lock you should
 * not reach) or a silent nerf to the physical lock picks that shipped in 1.7.1. Pure booleans, no Bootstrap.
 */
public class LockPickingPolicyTest
{
	// --- decide: which mode a fresh interaction starts in ---

	@Test
	void emptyHandStartsNothingWhenItemlessIsOff()
	{
		// The 1.7.1 default. An empty hand against a locked block must fall through to the normal denial.
		assertNull(LockPickingPolicy.decide(false, true, false));
	}

	@Test
	void emptyHandStartsItemlessWhenAllowed()
	{
		assertEquals(LockPickingMode.ITEMLESS, LockPickingPolicy.decide(false, true, true));
	}

	@Test
	void arbitraryHeldItemNeverStartsItemless()
	{
		// A held stack that is not a pick must not act as a virtual one, or every food, tool and block
		// interaction against a locked block would open the minigame.
		assertNull(LockPickingPolicy.decide(false, false, true));
	}

	@Test
	void validPickAlwaysWinsOverItemless()
	{
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingPolicy.decide(true, false, true));
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingPolicy.decide(true, false, false));
	}

	// --- isSessionValid: whether an open session may keep going ---

	private static boolean itemBacked(boolean lockLocked, boolean spectator, double distSqr, boolean pick)
	{
		return LockPickingPolicy.isSessionValid(LockPickingMode.ITEM_BACKED, lockLocked, spectator, distSqr, pick, false, true);
	}

	private static boolean itemless(boolean lockLocked, boolean spectator, double distSqr, boolean handEmpty, boolean allowed)
	{
		return LockPickingPolicy.isSessionValid(LockPickingMode.ITEMLESS, lockLocked, spectator, distSqr, false, handEmpty, allowed);
	}

	@Test
	void bothModesRequireTheLockToStillBeLocked()
	{
		assertFalse(itemBacked(false, false, 0d, true));
		assertFalse(itemless(false, false, 0d, true, true));
	}

	@Test
	void bothModesEndOutOfReachOrInSpectator()
	{
		double justInside = LockPickingPolicy.MAX_REACH_SQR;
		double justOutside = LockPickingPolicy.MAX_REACH_SQR + 0.01d;
		assertTrue(itemBacked(true, false, justInside, true));
		assertFalse(itemBacked(true, false, justOutside, true));
		assertFalse(itemBacked(true, true, justInside, true));

		assertTrue(itemless(true, false, justInside, true, true));
		assertFalse(itemless(true, false, justOutside, true, true));
		assertFalse(itemless(true, true, justInside, true, true));
	}

	@Test
	void itemBackedEndsWhenThePickIsGone()
	{
		assertFalse(itemBacked(true, false, 0d, false));
	}

	@Test
	void itemlessEndsWhenTheHandFillsOrTheOptionIsTurnedOff()
	{
		assertTrue(itemless(true, false, 0d, true, true));
		assertFalse(itemless(true, false, 0d, false, true));
		assertFalse(itemless(true, false, 0d, true, false));
	}

	@Test
	void itemlessIgnoresPickState()
	{
		// Complexity, pick strength and Attunement all feed the holdingValidPick flag. An itemless session
		// must be immune to them, or the maximum-Complexity locks would gate loot behind an absent item.
		assertTrue(LockPickingPolicy.isSessionValid(LockPickingMode.ITEMLESS, true, false, 0d, false, true, true));
	}

	// --- pin outcomes ---

	@Test
	void itemlessNeverSpendsPickDurability()
	{
		assertFalse(LockPickingPolicy.shouldWearPick(LockPickingMode.ITEMLESS, false));
		assertFalse(LockPickingPolicy.shouldWearPick(LockPickingMode.ITEMLESS, true));
		assertTrue(LockPickingPolicy.shouldWearPick(LockPickingMode.ITEM_BACKED, false));
		assertFalse(LockPickingPolicy.shouldWearPick(LockPickingMode.ITEM_BACKED, true));
	}

	@Test
	void correctPinAdvancesInEitherMode()
	{
		assertEquals(PinOutcome.CORRECT, LockPickingPolicy.resolve(LockPickingMode.ITEM_BACKED, true, false));
		assertEquals(PinOutcome.CORRECT, LockPickingPolicy.resolve(LockPickingMode.ITEMLESS, true, false));
	}

	@Test
	void wrongItemlessPinResetsInsteadOfBreakingAnything()
	{
		PinOutcome outcome = LockPickingPolicy.resolve(LockPickingMode.ITEMLESS, false, false);
		assertEquals(PinOutcome.WRONG_RESET, outcome);
		assertTrue(LockPickingPolicy.resetsProgress(outcome));
		// Nothing broke, so the pick-break shock must not fire; the opt-in wrong-pin one still may.
		assertFalse(LockPickingPolicy.triggersPickBreakShock(outcome));
		assertTrue(LockPickingPolicy.triggersWrongPinShock(outcome));
	}

	@Test
	void physicalWrongPinKeepsItsExistingTwoOutcomes()
	{
		PinOutcome survived = LockPickingPolicy.resolve(LockPickingMode.ITEM_BACKED, false, false);
		assertEquals(PinOutcome.WRONG_CONTINUE, survived);
		assertFalse(LockPickingPolicy.resetsProgress(survived));
		assertFalse(LockPickingPolicy.triggersPickBreakShock(survived));
		assertTrue(LockPickingPolicy.triggersWrongPinShock(survived));

		PinOutcome broke = LockPickingPolicy.resolve(LockPickingMode.ITEM_BACKED, false, true);
		assertEquals(PinOutcome.PICK_BROKE, broke);
		assertTrue(LockPickingPolicy.resetsProgress(broke));
		assertTrue(LockPickingPolicy.triggersPickBreakShock(broke));
		// Mutually exclusive with the pick-break trigger, as in 1.7.1.
		assertFalse(LockPickingPolicy.triggersWrongPinShock(broke));
	}
}
