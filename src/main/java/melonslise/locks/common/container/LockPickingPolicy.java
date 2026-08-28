package melonslise.locks.common.container;

import javax.annotation.Nullable;

/**
 * The rules of a lock-picking session, expressed over plain booleans so they can be reasoned about
 * and unit-tested without a running game. LockPickingContainer and LocksForgeEvents gather the world
 * state and delegate here; all the actual decisions live in this file.
 */
public final class LockPickingPolicy
{
	/** Standard container reach, matching vanilla's ContainerLevelAccess check. */
	public static final double MAX_REACH_SQR = 64.0d;

	/** What a wrong or right pin actually did, so the server never has to ask "did an item break?" to mean "reset". */
	public enum PinOutcome
	{
		CORRECT,
		WRONG_CONTINUE,
		WRONG_RESET,
		PICK_BROKE
	}

	private LockPickingPolicy() {}

	/**
	 * Which mode a fresh interaction should start, or null for none. A valid pick always wins, so
	 * enabling itemless picking never changes what a pick-carrying player experiences.
	 */
	@Nullable
	public static LockPickingMode decide(boolean holdingValidPick, boolean handEmpty, boolean itemlessAllowed)
	{
		if(holdingValidPick)
			return LockPickingMode.ITEM_BACKED;
		if(handEmpty && itemlessAllowed)
			return LockPickingMode.ITEMLESS;
		return null;
	}

	/**
	 * Whether an already-open session may keep going. Checked every tick server-side, so moving away,
	 * dying, going spectator, filling the recorded hand, losing the lock, or an operator turning the
	 * option off mid-session all end it.
	 */
	public static boolean isSessionValid(LockPickingMode mode, boolean lockLocked, boolean spectator,
		double distSqr, boolean holdingValidPick, boolean handEmpty, boolean itemlessAllowed)
	{
		if(!lockLocked || spectator || distSqr > MAX_REACH_SQR)
			return false;
		return switch(mode)
		{
			case ITEM_BACKED -> holdingValidPick;
			case ITEMLESS -> itemlessAllowed && handEmpty;
		};
	}

	/**
	 * Whether the physical break roll should run at all. Never in ITEMLESS: there is no item to break,
	 * so no durability, no replacement-pick search, and no break event.
	 */
	public static boolean shouldRollPickBreak(LockPickingMode mode, boolean correct)
	{
		return !correct && mode == LockPickingMode.ITEM_BACKED;
	}

	/** Callers must pass pickBroke == false whenever shouldRollPickBreak said not to roll. */
	public static PinOutcome resolve(LockPickingMode mode, boolean correct, boolean pickBroke)
	{
		if(correct)
			return PinOutcome.CORRECT;
		// An itemless miss still has to cost something, so it drops every solved pin instead.
		if(mode == LockPickingMode.ITEMLESS)
			return PinOutcome.WRONG_RESET;
		return pickBroke ? PinOutcome.PICK_BROKE : PinOutcome.WRONG_CONTINUE;
	}

	public static boolean resetsProgress(PinOutcome outcome)
	{
		return outcome == PinOutcome.WRONG_RESET || outcome == PinOutcome.PICK_BROKE;
	}

	/** Only a real pick breaking may fire the Shocking pick-break trigger. */
	public static boolean triggersPickBreakShock(PinOutcome outcome)
	{
		return outcome == PinOutcome.PICK_BROKE;
	}

	/** The wrong-pin trigger stays available to both modes, including an itemless miss. */
	public static boolean triggersWrongPinShock(PinOutcome outcome)
	{
		return outcome == PinOutcome.WRONG_CONTINUE || outcome == PinOutcome.WRONG_RESET;
	}
}
