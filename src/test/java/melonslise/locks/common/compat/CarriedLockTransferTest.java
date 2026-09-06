package melonslise.locks.common.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * What stays on a carried block when re-registering its lockables partly fails. The old code cleared the whole
 * payload unconditionally, so a lockable the handler refused (unloaded chunk, overlap, over-volume) was gone for
 * good. Only the entries that were actually accepted may leave. Pure ListTag work, no Bootstrap.
 */
public class CarriedLockTransferTest
{
	private static ListTag listOf(int... ids)
	{
		ListTag list = new ListTag();
		for(int id : ids)
		{
			CompoundTag entry = new CompoundTag();
			entry.putInt("Id", id);
			list.add(entry);
		}
		return list;
	}

	private static Set<Integer> indices(int... idx)
	{
		Set<Integer> set = new LinkedHashSet<>();
		for(int i : idx)
			set.add(i);
		return set;
	}

	@Test
	void allSuccessRetainsNothing()
	{
		ListTag retained = CarriedLockTransfer.retain(listOf(1, 2, 3), Collections.emptySet());
		assertTrue(retained.isEmpty(), "with nothing refused the payload is fully consumed and both keys are dropped");
	}

	@Test
	void partialFailureRetainsExactlyTheFailedEntriesInOrder()
	{
		ListTag retained = CarriedLockTransfer.retain(listOf(10, 11, 12, 13), indices(1, 3));
		assertEquals(2, retained.size());
		assertEquals(11, retained.getCompound(0).getInt("Id"));
		assertEquals(13, retained.getCompound(1).getInt("Id"));
	}

	@Test
	void totalFailureRetainsEverything()
	{
		ListTag original = listOf(4, 5);
		ListTag retained = CarriedLockTransfer.retain(original, indices(0, 1));
		assertEquals(original.size(), retained.size());
		assertEquals(4, retained.getCompound(0).getInt("Id"));
		assertEquals(5, retained.getCompound(1).getInt("Id"));
		// A non-empty retention is what keeps the origin key in place, so a later placement can still offset it.
		assertTrue(!retained.isEmpty());
	}
}
