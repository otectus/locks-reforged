package melonslise.locks.common.recipe;

import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.init.LocksTagHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * What a key-pairing craft accepts.
 *
 * Split into an adapter half that needs registries and tags (classify) and a pure half that does not
 * (isValidLayout), so the actual rule can be reasoned about and unit-tested without a running game.
 */
public final class KeyPairing
{
	public enum SlotKind
	{
		EMPTY,
		/** A Key Blank: the thing being cut. */
		BLANK,
		/** An unplaced lock, or an already-paired key, to copy the id from. */
		SOURCE,
		/** Anything else. Its presence fails the whole craft. */
		INVALID
	}

	private KeyPairing() {}

	public static SlotKind classify(ItemStack stack)
	{
		if(stack.isEmpty())
			return SlotKind.EMPTY;
		if(stack.getItem() == LocksItems.KEY_BLANK.get())
			return SlotKind.BLANK;
		// Exclusions before the tag check, deliberately: a datapack that added the master key to
		// locks:keys must not thereby turn it into a pairing source.
		if(stack.getItem() == LocksItems.MASTER_KEY.get()
			|| stack.getItem() == LocksItems.KEY_RING.get()
			|| LocksTagHelper.isLockPick(stack))
			return SlotKind.INVALID;
		// Recognised by item type and tag, never by the presence of an "Id" NBT field. The old check
		// accepted any item from any mod that happened to use a tag by that name.
		if(LocksTagHelper.isLock(stack) || LocksTagHelper.isKey(stack))
			return SlotKind.SOURCE;
		return SlotKind.INVALID;
	}

	/** Exactly one source and exactly one blank, and nothing else in the grid. */
	public static boolean isValidLayout(SlotKind[] kinds)
	{
		int sources = 0, blanks = 0;
		for(SlotKind kind : kinds)
		{
			if(kind == SlotKind.INVALID)
				return false;
			if(kind == SlotKind.BLANK)
				++blanks;
			else if(kind == SlotKind.SOURCE)
				++sources;
			if(sources > 1 || blanks > 1)
				return false;
		}
		return sources == 1 && blanks == 1;
	}

	public static SlotKind[] classify(CraftingContainer inv)
	{
		SlotKind[] kinds = new SlotKind[inv.getContainerSize()];
		for(int a = 0; a < kinds.length; ++a)
			kinds[a] = classify(inv.getItem(a));
		return kinds;
	}

	public static boolean matches(CraftingContainer inv)
	{
		return isValidLayout(classify(inv));
	}

	/** The source stack itself (not a copy), or EMPTY when the grid is not a valid pairing. */
	public static ItemStack findSource(CraftingContainer inv)
	{
		if(!matches(inv))
			return ItemStack.EMPTY;
		for(int a = 0; a < inv.getContainerSize(); ++a)
			if(classify(inv.getItem(a)) == SlotKind.SOURCE)
				return inv.getItem(a);
		return ItemStack.EMPTY;
	}
}
