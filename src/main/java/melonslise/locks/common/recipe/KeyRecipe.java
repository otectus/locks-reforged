package melonslise.locks.common.recipe;

import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.init.LocksRecipeSerializers;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.recipe.KeyPairing.SlotKind;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import java.util.OptionalInt;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class KeyRecipe extends CustomRecipe
{
	public KeyRecipe(ResourceLocation id, CraftingBookCategory category)
	{
		super(id, category);
	}

	@Override
	public RecipeSerializer<?> getSerializer()
	{
		return LocksRecipeSerializers.KEY.get();
	}

	@Override
	public boolean matches(CraftingContainer inv, Level world)
	{
		return KeyPairing.matches(inv);
	}

	@Override
	public ItemStack assemble(CraftingContainer inv, net.minecraft.core.RegistryAccess registryAccess)
	{
		ItemStack source = KeyPairing.findSource(inv);
		if(source.isEmpty())
			return ItemStack.EMPTY;
		ItemStack out = new ItemStack(LocksItems.KEY.get());
		OptionalInt id = LockingItem.readId(source);
		if(id.isPresent())
			out.getOrCreateTag().putInt(LockingItem.KEY_ID, id.getAsInt());
		// A source with no id yet (a lock taken straight from the creative menu into the grid, say) is
		// stamped here so pairing still works — but only on the server, which owns the id. Doing it
		// client-side would write a random id into the client copy of the source that the server would
		// never send back, leaving a bogus id showing in its tooltip. The client simply previews a key
		// with no id line; the server's result replaces it when the craft is taken.
		else if(EffectiveSide.get() == LogicalSide.SERVER)
			out.getOrCreateTag().putInt(LockingItem.KEY_ID, LockingItem.getOrSetId(source));
		return out;
	}

	// The source lock or key is returned to the grid untouched, with all its NBT, enchantments, custom
	// name and owner data. Only the blank is consumed, one per craft, so shift-crafting a stack of
	// blanks yields one key each.
	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv)
	{
		NonNullList<ItemStack> list = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
		for (int a = 0; a < list.size(); ++a)
			if(KeyPairing.classify(inv.getItem(a)) == SlotKind.SOURCE)
			{
				list.set(a, inv.getItem(a).copy());
				break;
			}
		return list;
	}

	@Override
	public boolean canCraftInDimensions(int x, int y)
	{
		// Two occupied slots is all this needs, so it fits the 2x2 inventory grid as well as a table.
		// (Nothing in vanilla actually consults this for a CustomRecipe, which is always isSpecial and so
		// never reaches the recipe book — but the honest bound belongs here anyway.)
		return x * y >= 2;
	}
}
