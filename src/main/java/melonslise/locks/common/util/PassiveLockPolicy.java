package melonslise.locks.common.util;

/**
 * The rules for a passive credential — currently the owner of a lock enchanted with Awareness.
 * Plain booleans and no game state, in the same style as
 * {@link melonslise.locks.common.container.LockPickingPolicy}, so the rules can be reasoned about
 * and unit-tested without a running game.
 *
 * A held key has an off switch: stow it and the block behaves normally again, which is why every
 * key branch gates on the held stack. A passive credential has no off switch, so if it claims every
 * right-click there is no click left that opens the chest or the door — which is exactly the lockout
 * this class exists to make impossible. One rule guarantees a way in and a way out of every state:
 * an ordinary click never touches the lock and is handed straight to the block, and sneaking is the
 * one unambiguous modifier reserved for acting on the lock itself.
 *
 * Aim deliberately plays no part. A lock's model sits centred on the face of the block it guards —
 * on a vanilla chest its clickable box covers roughly the middle third of the front — so "aiming at
 * the padlock" and "aiming at the chest" are the same crosshair position, and any rule built on that
 * distinction reintroduces the lockout for the most common way to click a chest.
 */
public final class PassiveLockPolicy
{
	public enum Action
	{
		/** Not this branch's business — fall through to the rest of the ladder. */
		NONE,
		/** Leave the lock alone and let the click reach the block, so the chest or door opens. */
		PASS_THROUGH,
		/** Open the lock, without opening the block. */
		UNLOCK
	}

	private PassiveLockPolicy() {}

	/**
	 * Exactly the condition guarding the lock-removal branch, so passive credentials can stand aside
	 * for it instead of silently swallowing the gesture.
	 */
	public static boolean isRemovalGesture(boolean allowRemoving, boolean sneaking, boolean handEmpty)
	{
		return allowRemoving && sneaking && handEmpty;
	}

	/**
	 * Decides what an owner's click on their own locked block does. Called only once the player has
	 * been found to own at least one locked lockable here.
	 *
	 * Sneaking acts on the lock only with an empty hand, matching the lock-removal gesture: sneaking
	 * with something held is how a player places a block against the chest they are standing at, and
	 * quietly unlocking their storage instead would be a nasty surprise mid-build.
	 *
	 * @param everyLockedOwned whether every lockable still locked at this position belongs to this
	 *        player. Required before handing the click to the block: yielding while somebody else's
	 *        lock is still shut would open their chest. Not required to unlock, because that only
	 *        opens the player's own locks and the stranger's still holds the block closed.
	 */
	public static Action onOwnedLocked(boolean sneaking, boolean handEmpty, boolean everyLockedOwned)
	{
		if(sneaking && handEmpty)
			return Action.UNLOCK;
		return everyLockedOwned ? Action.PASS_THROUGH : Action.NONE;
	}
}
