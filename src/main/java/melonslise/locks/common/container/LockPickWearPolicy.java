package melonslise.locks.common.container;

/**
 * How much durability a wrong pin costs a lock pick. Expressed over plain numbers so it can be reasoned
 * about and unit-tested without a running game, the same way {@link LockPickingPolicy} handles the rules
 * of a session. LockPickingContainer gathers the world state and delegates here.
 *
 * <p>Before 1.7.3 a wrong pin rolled a random number against the pick's strength and, on a losing roll,
 * deleted the pick outright — a fresh wood pick was destroyed by 80% of far misses. Wear is now fully
 * deterministic: the LOCK decides the cost, the pick's own durability pool decides how many of those
 * costs it can absorb, and it breaks only when that pool runs out.
 *
 * <p>Deliberately not answering "did this break the pick?": that is read off the ItemStack after the
 * damage lands, because Unbreaking can silently eat the hit and creative mode ignores it entirely, so
 * any prediction made here would sometimes disagree with what actually happened to the item.
 */
public final class LockPickWearPolicy
{
	/** A wrong pin always costs something. Nothing below may round the cost away to nothing. */
	public static final int MIN_WEAR = 1;

	private LockPickWearPolicy() {}

	/**
	 * The durability a single wrong pin takes off the pick.
	 *
	 * @param baseWear            the lock tier's {@code pick_wear}: the whole reason a netherite lock
	 *                            eats picks faster than a wood one
	 * @param nearMiss            true when the guess was off by exactly one pin
	 * @param nearMissMultiplier  scales the cost of a near miss (1.7.2 used a hardcoded 0.33)
	 * @param sturdy              level of the lock's Sturdy enchantment, 0 when absent or disabled
	 * @param sturdyPerLevel      extra wear fraction added per Sturdy level
	 * @param finesse             level of the pick's Finesse enchantment, 0 when absent or disabled
	 * @param finessePerLevel     wear fraction removed per Finesse level
	 * @return the wear to apply, never below {@link #MIN_WEAR}
	 */
	public static int wearFor(int baseWear, boolean nearMiss, double nearMissMultiplier,
		int sturdy, double sturdyPerLevel, int finesse, double finessePerLevel)
	{
		double wear = Math.max(MIN_WEAR, baseWear);
		if(nearMiss)
			wear *= Math.max(0d, nearMissMultiplier);
		if(sturdy > 0)
			wear *= 1d + sturdy * Math.max(0d, sturdyPerLevel);
		if(finesse > 0)
			wear *= Math.max(0d, 1d - finesse * Math.max(0d, finessePerLevel));
		// The floor is the durability-era successor to 1.7.2's "Finesse can never make a pick
		// unbreakable" clamp: no combination of enchantments or config values makes a miss free.
		return (int) Math.max((long) MIN_WEAR, Math.round(wear));
	}
}
