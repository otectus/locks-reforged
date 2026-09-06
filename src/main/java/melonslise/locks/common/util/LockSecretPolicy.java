package melonslise.locks.common.util;

import java.util.Random;

/**
 * The secret half of a {@link Lock}, expressed over plain arrays so it can be reasoned about and unit-tested
 * without a running game. Nothing here touches Minecraft.
 *
 * <p>Up to 1.7.4 a combination was {@code new Random(id)} shuffled, and the id travels to the client in every
 * lock packet — so any client could reproduce the pin order exactly. Combinations are now drawn from a caller
 * supplied generator, and the helpers below let already-saved locks be recognized as id-derived and rerolled
 * once, deterministically, from a server-side salt.
 */
public final class LockSecretPolicy
{
	private LockSecretPolicy() {}

	/** The largest combination the decoders will allocate for. Mirrors the lock length range the items offer. */
	public static final int MIN_LENGTH = 1, MAX_LENGTH = 20;

	/**
	 * A fresh permutation of {@code 0..length-1} drawn from {@code rng}. Identical Fisher-Yates to
	 * {@link LocksUtil#shuffle}, duplicated here so this class stays free of Minecraft imports and so the
	 * legacy detector below reproduces the historic shuffle bit-for-bit.
	 */
	public static byte[] generate(Random rng, int length)
	{
		byte[] combo = new byte[length];
		for(int a = 0; a < length; ++a)
			combo[a] = (byte) a;
		for(int a = combo.length - 1; a > 0; --a)
		{
			int index = rng.nextInt(a + 1);
			byte temp = combo[index];
			combo[index] = combo[a];
			combo[a] = temp;
		}
		return combo;
	}

	/** Whether {@code combo} is exactly one permutation of {@code 0..length-1}: right size, no gaps, no repeats. */
	public static boolean isValidPermutation(byte[] combo, int length)
	{
		if(combo == null || length < MIN_LENGTH || length > MAX_LENGTH || combo.length != length)
			return false;
		boolean[] seen = new boolean[length];
		for(byte pin : combo)
		{
			if(pin < 0 || pin >= length || seen[pin])
				return false;
			seen[pin] = true;
		}
		return true;
	}

	/**
	 * Whether {@code combo} is exactly what the pre-1.7.5 {@code new Random(id)} shuffle would have produced.
	 * Provenance is therefore decidable rather than guessed: equal means the combination is public knowledge
	 * and must be rerolled, unequal means somebody deliberately reshuffled it and it must be kept.
	 */
	public static boolean isLegacyIdDerived(int id, byte[] combo)
	{
		if(combo == null || combo.length == 0)
			return false;
		byte[] legacy = generate(new Random(id), combo.length);
		for(int a = 0; a < combo.length; ++a)
			if(legacy[a] != combo[a])
				return false;
		return true;
	}

	/**
	 * The replacement combination for an id-derived lock. Deterministic in {@code (salt, id, length)} so every
	 * stale copy of the same record — a duplicated chunk, a Carry On payload written before the upgrade —
	 * migrates to the same value, while a client that knows only the id cannot derive it without the salt.
	 */
	public static byte[] deriveLegacyReroll(long salt, int id, int length)
	{
		long seed = salt * 0x9E3779B97F4A7C15L + (id & 0xFFFFFFFFL) * 0xBF58476D1CE4E5B9L + length;
		return generate(new Random(seed), length);
	}
}
