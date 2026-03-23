package melonslise.locks.common.item;

import java.util.List;

import melonslise.locks.Locks;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ISelection;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksSoundEvents;
import melonslise.locks.common.init.LockTypeRegistry;
import melonslise.locks.common.util.Cuboid6i;
import melonslise.locks.common.util.Lock;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.Transform;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class LockItem extends LockingItem
{
	public LockItem(Properties props)
	{
		super(props);
	}

	public static final String KEY_OPEN = "Open";

	public static boolean isOpen(ItemStack stack)
	{
		return stack.getOrCreateTag().getBoolean(KEY_OPEN);
	}

	public static void setOpen(ItemStack stack, boolean open)
	{
		stack.getOrCreateTag().putBoolean(KEY_OPEN, open);
	}

	public static final String KEY_LENGTH = "Length";

	// WARNING: EXPECTS LOCKITEM STACK
	public static byte getOrSetLength(ItemStack stack)
	{
		CompoundTag nbt = stack.getOrCreateTag();
		if(!nbt.contains(KEY_LENGTH))
			nbt.putByte(KEY_LENGTH, (byte) LockTypeRegistry.getLockStats(stack.getItem()).length());
		return nbt.getByte(KEY_LENGTH);
	}

	// WARNING: EXPECTS LOCKITEM STACK
	public static int getResistance(ItemStack stack)
	{
		return LockTypeRegistry.getLockStats(stack.getItem()).resistance();
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx)
	{
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		if (!LocksServerConfig.canLock(world, pos))
			return InteractionResult.PASS;
		ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return InteractionResult.PASS;
		Int2ObjectMap<Lockable> chunkLkbs = handler.getInChunk(pos);
		if(chunkLkbs != null && chunkLkbs.values().stream().anyMatch(lkb -> lkb.bb.intersects(pos)))
			return InteractionResult.PASS;
		return LocksServerConfig.EASY_LOCK.get() ? this.easyLock(ctx) : this.freeLock(ctx);
	}

	public InteractionResult freeLock(UseOnContext ctx)
	{
		Player player = ctx.getPlayer();
		BlockPos pos = ctx.getClickedPos();
		ISelection select = player.getCapability(LocksCapabilities.SELECTION).orElse(null);
		if (select == null)
			return InteractionResult.PASS;
		BlockPos pos1 = select.get();
		if (pos1 == null)
			select.set(pos);
		else
		{
			Level world = ctx.getLevel();
			select.set(null);
			// FIXME Go through the add checks here as well
			world.playSound(player, pos, LocksSoundEvents.LOCK_CLOSE.get(), SoundSource.BLOCKS, 1f, 1f);
			if (world.isClientSide)
				return InteractionResult.SUCCESS;
			ItemStack stack = ctx.getItemInHand();
			ItemStack lockStack = stack.copy();
			lockStack.setCount(1);
			ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
			if (handler == null)
				return InteractionResult.PASS;
			if (!handler.add(new Lockable(new Cuboid6i(pos1, pos), Lock.from(stack), Transform.fromDirection(ctx.getClickedFace(), player.getDirection().getOpposite()), lockStack, world)))
				return InteractionResult.PASS;
			if (!player.isCreative())
				stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	public InteractionResult easyLock(UseOnContext ctx)
	{
		Player player = ctx.getPlayer();
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		world.playSound(player, pos, LocksSoundEvents.LOCK_CLOSE.get(), SoundSource.BLOCKS, 1f, 1f);
		if(world.isClientSide)
			return InteractionResult.SUCCESS;
		BlockState state = world.getBlockState(pos);
		BlockPos pos1 = pos;
		if(state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE)
			pos1 = pos.relative(ChestBlock.getConnectedDirection(state));
		else if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
		{
			pos1 = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
			if(state.hasProperty(BlockStateProperties.DOOR_HINGE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			{
				Direction dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
				BlockPos pos2 = pos1.relative(state.getValue(BlockStateProperties.DOOR_HINGE) == DoorHingeSide.LEFT ? dir.getClockWise() : dir.getCounterClockWise());
				if(world.getBlockState(pos2).is(state.getBlock()))
					pos1 = pos2;
			}
		}
		ItemStack stack = ctx.getItemInHand();
		ItemStack lockStack = stack.copy();
		lockStack.setCount(1);
		ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if (handler == null)
			return InteractionResult.PASS;
		if (!handler.add(new Lockable(new Cuboid6i(pos, pos1), Lock.from(stack), Transform.fromDirection(ctx.getClickedFace(), player.getDirection().getOpposite()), lockStack, world)))
			return InteractionResult.PASS;
		if (!player.isCreative())
			stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand)
	{
		ItemStack stack = player.getItemInHand(hand);
		if(!isOpen(stack))
			return super.use(world, player, hand);
		setOpen(stack, false);
		world.playSound(player, player.getX(), player.getY(), player.getZ(), LocksSoundEvents.PIN_MATCH.get(), SoundSource.PLAYERS, 1f, 1f);
		return super.use(world, player, hand);
	}

	@Override
	public boolean isEnchantable(ItemStack p_77616_1_)
	{
		return true;
	}

	@Override
	public int getEnchantmentValue()
	{
		return LockTypeRegistry.getLockStats(this).enchantmentValue();
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> lines, TooltipFlag flag)
	{
		super.appendHoverText(stack, world, lines, flag);
		int displayLength;
		if (stack.hasTag() && stack.getTag().contains(KEY_LENGTH))
			displayLength = stack.getTag().getByte(KEY_LENGTH);
		else
			displayLength = LockTypeRegistry.getLockStats(this).length();
		lines.add(Component.translatable(Locks.ID + ".tooltip.length", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(displayLength)).withStyle(ChatFormatting.DARK_GREEN));
	}
}
