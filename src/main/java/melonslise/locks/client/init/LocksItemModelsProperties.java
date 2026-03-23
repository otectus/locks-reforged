package melonslise.locks.client.init;

import melonslise.locks.Locks;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.item.LockItem;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

@OnlyIn(Dist.CLIENT)
public final class LocksItemModelsProperties
{
	private LocksItemModelsProperties() {}

	public static void register()
	{
		ItemProperties.register(LocksItems.KEY_RING.get(), new ResourceLocation(Locks.ID, "keys"), (stack, level, entity, seed) ->
		{
			return stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
				.map(inv ->
				{
					int keys = 0;
					for(int a = 0; a < inv.getSlots(); ++a)
						if(!inv.getStackInSlot(a).isEmpty())
							++keys;
					return (float) keys / inv.getSlots();
				})
				.orElse(0f);
		});

		ResourceLocation id = new ResourceLocation(Locks.ID, "open");
		ClampedItemPropertyFunction getter = (stack, level, entity, seed) -> LockItem.isOpen(stack) ? 1f : 0f;

		// Register the "open" property for all data-driven locks
		for (var entry : LocksItems.getLockItems().values())
			ItemProperties.register(entry.get(), id, getter);
	}
}
