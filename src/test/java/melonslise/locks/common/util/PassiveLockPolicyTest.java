package melonslise.locks.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import melonslise.locks.common.util.PassiveLockPolicy.Action;

/**
 * The rules for a credential the player cannot put away — currently an Awareness lock's owner.
 *
 * These exist because of a real lockout: every branch that authorized an Awareness owner also cancelled
 * the interaction, so the owner's click toggled the lock and never reached the chest. Unlock, re-lock,
 * unlock, re-lock, with the chest never opening and the lock impossible to remove. Pure booleans,
 * no Bootstrap.
 */
public class PassiveLockPolicyTest
{
	@Test
	void anOrdinaryClickOpensTheBlockAndLeavesTheLockAlone()
	{
		assertEquals(Action.PASS_THROUGH, PassiveLockPolicy.onOwnedLocked(false, true, true));
		assertEquals(Action.PASS_THROUGH, PassiveLockPolicy.onOwnedLocked(false, false, true));
	}

	@Test
	void sneakingWithAnEmptyHandUnlocks()
	{
		assertEquals(Action.UNLOCK, PassiveLockPolicy.onOwnedLocked(true, true, true));
	}

	@Test
	void sneakingWithSomethingHeldDoesNotTouchTheLock()
	{
		// Sneaking with an item is how a player places a block against the chest they are standing at.
		// Silently unlocking their storage instead would be a nasty surprise mid-build.
		assertNotEquals(Action.UNLOCK, PassiveLockPolicy.onOwnedLocked(true, false, true));
	}

	@Test
	void theClickIsNeverHandedToTheBlockWhileSomeoneElsesLockIsStillShut()
	{
		// The security test. Yielding here would open a stranger's chest, since the container GUI is
		// gated solely by the interaction handler denying the click.
		assertEquals(Action.NONE, PassiveLockPolicy.onOwnedLocked(false, true, false));
		assertEquals(Action.NONE, PassiveLockPolicy.onOwnedLocked(false, false, false));
	}

	@Test
	void unlockingIsStillAllowedAlongsideAStrangersLock()
	{
		// Only the player's own locks open, and the stranger's still holds the block shut, so there is
		// nothing to protect against here.
		assertEquals(Action.UNLOCK, PassiveLockPolicy.onOwnedLocked(true, true, false));
	}

	@Test
	void removalGestureIsExactlyTheRemovalBranchCondition()
	{
		for(boolean allow : new boolean[] { false, true })
			for(boolean sneak : new boolean[] { false, true })
				for(boolean empty : new boolean[] { false, true })
					assertEquals(allow && sneak && empty,
						PassiveLockPolicy.isRemovalGesture(allow, sneak, empty),
						"allowRemoving=" + allow + " sneaking=" + sneak + " handEmpty=" + empty);
	}

	@Test
	void anAwarenessOwnerAlwaysHasAClickThatOpensTheirOwnBlock()
	{
		// The regression test for the reported bug. Whatever the player is doing with their hands, a lock
		// that is entirely their own must never claim every input: some click has to reach the block.
		for(boolean sneak : new boolean[] { false, true })
			for(boolean empty : new boolean[] { false, true })
				assertNotEquals(Action.NONE, PassiveLockPolicy.onOwnedLocked(sneak, empty, true),
					"sneaking=" + sneak + " handEmpty=" + empty);

		// And specifically: not sneaking always opens the block, whatever is held.
		assertEquals(Action.PASS_THROUGH, PassiveLockPolicy.onOwnedLocked(false, true, true));
		assertEquals(Action.PASS_THROUGH, PassiveLockPolicy.onOwnedLocked(false, false, true));
	}

	@Test
	void unlockingAndRemovingShareOneGestureAcrossTheTwoLockStates()
	{
		// Sneak + empty hand means "act on this lock": it opens a locked one, and the removal branch
		// takes the same input once the lock is open. The two never contend, because they apply to
		// opposite lock states, and together they guarantee the owner can always get their lock back.
		assertEquals(Action.UNLOCK, PassiveLockPolicy.onOwnedLocked(true, true, true));
		assertTrue(PassiveLockPolicy.isRemovalGesture(true, true, true));
		// With removal disabled by config, the unlock half still works.
		assertFalse(PassiveLockPolicy.isRemovalGesture(false, true, true));
	}
}
