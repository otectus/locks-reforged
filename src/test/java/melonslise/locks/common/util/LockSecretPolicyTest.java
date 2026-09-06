package melonslise.locks.common.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The secret half of a lock. Up to 1.7.4 a combination was {@code new Random(id)} shuffled and the id is public,
 * so every client could derive the pin order of every lock it could see. These are the rules that end that: what
 * counts as a well-formed combination, how an already-saved id-derived one is recognized, and what it becomes.
 */
public class LockSecretPolicyTest
{
	@Test
	void generateProducesAPermutation()
	{
		for(int length = 1; length <= LockSecretPolicy.MAX_LENGTH; ++length)
			assertTrue(LockSecretPolicy.isValidPermutation(LockSecretPolicy.generate(new Random(length), length), length));
	}

	@Test
	void permutationValidatorRejectsDuplicates()
	{
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[] {0, 1, 1}, 3));
	}

	@Test
	void permutationValidatorRejectsOutOfRangeValues()
	{
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[] {0, 1, 5}, 3));
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[] {0, 1, -1}, 3));
	}

	@Test
	void permutationValidatorRejectsWrongLength()
	{
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[] {0, 1, 2}, 4));
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[] {0, 1, 2}, 2));
		assertFalse(LockSecretPolicy.isValidPermutation(null, 3));
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[0], 0), "a zero-pin lock is not a lock");
		assertFalse(LockSecretPolicy.isValidPermutation(new byte[LockSecretPolicy.MAX_LENGTH + 1], LockSecretPolicy.MAX_LENGTH + 1));
	}

	@Test
	void legacyDetectorRecognizesAnIdSeededCombo()
	{
		// This is exactly how every combination written before 1.7.5 was produced.
		assertTrue(LockSecretPolicy.isLegacyIdDerived(4711, LockSecretPolicy.generate(new Random(4711), 6)));
	}

	@Test
	void legacyDetectorRejectsADeliberatelyReshuffledCombo()
	{
		byte[] rotated = LockSecretPolicy.generate(new Random(4711), 6);
		byte[] shifted = new byte[rotated.length];
		for(int a = 0; a < rotated.length; ++a)
			shifted[a] = rotated[(a + 1) % rotated.length];
		assertFalse(LockSecretPolicy.isLegacyIdDerived(4711, shifted), "a rotated combo is somebody's deliberate choice, not the id seed");
	}

	@Test
	void rerollIsStableForTheSameInputs()
	{
		assertArrayEquals(LockSecretPolicy.deriveLegacyReroll(99L, 7, 5), LockSecretPolicy.deriveLegacyReroll(99L, 7, 5),
			"every stale copy of one record must migrate to the same combination");
		assertTrue(LockSecretPolicy.isValidPermutation(LockSecretPolicy.deriveLegacyReroll(99L, 7, 5), 5));
	}

	@Test
	void rerollDiffersAcrossSalts()
	{
		// The salt is the only thing a client does not have; two servers must not agree on the reroll.
		assertNotEquals(
			java.util.Arrays.toString(LockSecretPolicy.deriveLegacyReroll(1L, 7, 8)),
			java.util.Arrays.toString(LockSecretPolicy.deriveLegacyReroll(2L, 7, 8)));
	}
}
