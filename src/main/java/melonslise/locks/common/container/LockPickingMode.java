package melonslise.locks.common.container;

/**
 * How a lock-picking session is being played.
 *
 * The server decides this once, when the menu is opened, and ships it to the client in the menu's
 * extra data purely so the screen can render and animate correctly. It is never re-derived from the
 * held stack on either side: a player who swapped a pick for an empty hand mid-session would
 * otherwise flip modes and slip past the rules the session started under.
 */
public enum LockPickingMode
{
	/** Backed by a real lock pick. Complexity, durability, breakage and pick enchantments all apply. */
	ITEM_BACKED,
	/** Empty main hand, allowed only by the Allow Itemless Lock Picking server option. No item is ever touched. */
	ITEMLESS;

	// Cached so decoding does not clone the array on every menu open.
	public static final LockPickingMode[] VALUES = values();

	/** Decodes a wire byte, falling back to the restrictive mode rather than throwing on a bad value. */
	public static LockPickingMode byId(int id)
	{
		return id >= 0 && id < VALUES.length ? VALUES[id] : ITEM_BACKED;
	}
}
