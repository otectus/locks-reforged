package melonslise.locks.common.item;

import melonslise.locks.common.capability.CapabilityProvider;
import melonslise.locks.common.capability.KeyRingInventory;
import melonslise.locks.common.container.KeyRingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class KeyRingItem extends Item
{
	public final int rows;

	public KeyRingItem(int rows, Properties props)
	{
		super(props.stacksTo(1));
		this.rows = rows;
	}

	@Override
	public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt)
	{
		return new CapabilityProvider(ForgeCapabilities.ITEM_HANDLER, new KeyRingInventory(stack, this.rows, 9));
	}

	public static boolean containsId(ItemStack stack, int id)
	{
		ListTag list = stack.getOrCreateTag().getList("Items", Tag.TAG_COMPOUND);
		for(int a = 0; a < list.size(); ++a)
		{
			CompoundTag tag = list.getCompound(a).getCompound("tag");
			if(tag.contains("Id") && tag.getInt("Id") == id)
				return true;
		}
		return false;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand)
	{
		ItemStack stack = player.getItemInHand(hand);
		if(!player.level().isClientSide)
			NetworkHooks.openScreen((ServerPlayer) player, new KeyRingContainer.Provider(stack), new KeyRingContainer.Writer(hand));
		return new InteractionResultHolder<>(InteractionResult.PASS, stack);
	}

}
