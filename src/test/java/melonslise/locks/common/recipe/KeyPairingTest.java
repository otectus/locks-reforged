package melonslise.locks.common.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import melonslise.locks.common.recipe.KeyPairing.SlotKind;

/**
 * What the key-pairing craft accepts, expressed over slot kinds so it can be checked without a running
 * game. The classification of a real stack needs registries and tags and stays untested here; this covers
 * the rule applied to the result, which is where the 1.7.1 hole was: any item carrying an "Id" NBT field
 * counted as a pairing source, so unrelated mods' items could cut a key.
 */
public class KeyPairingTest
{
	private static final SlotKind E = SlotKind.EMPTY;
	private static final SlotKind B = SlotKind.BLANK;
	private static final SlotKind S = SlotKind.SOURCE;
	private static final SlotKind X = SlotKind.INVALID;

	@Test
	void oneSourceAndOneBlankPairs()
	{
		// 2x2 inventory grid and 3x3 table alike — the rule is about occupancy, not grid size.
		assertTrue(KeyPairing.isValidLayout(new SlotKind[] { S, B, E, E }));
		assertTrue(KeyPairing.isValidLayout(new SlotKind[] { E, E, E, E, S, E, E, B, E }));
	}

	@Test
	void orderAndPositionDoNotMatter()
	{
		assertTrue(KeyPairing.isValidLayout(new SlotKind[] { E, B, S, E }));
		assertTrue(KeyPairing.isValidLayout(new SlotKind[] { B, E, E, S }));
	}

	@Test
	void aBlankAloneIsNotACraft()
	{
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { B, E, E, E }));
	}

	@Test
	void aSourceAloneIsNotACraft()
	{
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { S, E, E, E }));
	}

	@Test
	void twoBlanksWithoutASourceFail()
	{
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { B, B, E, E }));
	}

	@Test
	void twoSourcesFail()
	{
		// Otherwise which lock the key belongs to would be ambiguous.
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { S, S, B, E }));
	}

	@Test
	void twoBlanksWithASourceFail()
	{
		// Exactly one key comes out, so exactly one blank goes in.
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { S, B, B, E }));
	}

	@Test
	void anyDisallowedItemFailsTheWholeCraft()
	{
		// INVALID covers the master key, the key ring, lock picks, and any unrelated item — including one
		// that merely happens to carry an "Id" NBT field, which 1.7.1 would have accepted as the source.
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { S, B, X, E }));
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { X, B, E, E }));
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { S, X, E, E }));
	}

	@Test
	void anEmptyGridIsNotACraft()
	{
		assertFalse(KeyPairing.isValidLayout(new SlotKind[] { E, E, E, E }));
		assertFalse(KeyPairing.isValidLayout(new SlotKind[0]));
	}
}
