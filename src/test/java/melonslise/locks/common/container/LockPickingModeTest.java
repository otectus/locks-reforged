package melonslise.locks.common.container;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Wire encoding of the picking mode, which rides in the lock-picking menu's extra data. A malformed or
 * hostile byte must not throw inside the menu-open path — that is why the mode is a bounded byte rather
 * than {@code FriendlyByteBuf#readEnum}, which indexes the values array unchecked. Plain buffer work,
 * no Bootstrap.
 */
public class LockPickingModeTest
{
	private static LockPickingMode roundTrip(LockPickingMode mode)
	{
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(mode.ordinal());
		return LockPickingMode.byId(buf.readByte());
	}

	@Test
	void everyModeSurvivesARoundTrip()
	{
		for(LockPickingMode mode : LockPickingMode.VALUES)
			assertEquals(mode, roundTrip(mode));
	}

	@Test
	void outOfRangeIdsFallBackToTheRestrictiveMode()
	{
		// Falling back to ITEM_BACKED means a garbled packet yields a session that still demands a real
		// pick, rather than one that grants itemless access.
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingMode.byId(-1));
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingMode.byId(LockPickingMode.VALUES.length));
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingMode.byId(127));
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingMode.byId(Integer.MIN_VALUE));
	}

	@Test
	void aSignedNegativeByteDoesNotIndexTheArray()
	{
		// readByte returns a signed byte, so 0xFF arrives as -1 rather than 255.
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(0xFF);
		assertEquals(LockPickingMode.ITEM_BACKED, LockPickingMode.byId(buf.readByte()));
	}
}
