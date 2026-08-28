package melonslise.locks.common.container;

import java.util.function.Consumer;

import melonslise.locks.Locks;
import melonslise.locks.client.gui.LockPickingScreen;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksMenuTypes;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LocksItemTags;
import melonslise.locks.common.init.LocksTagHelper;
import melonslise.locks.common.init.LocksNetwork;
import melonslise.locks.common.init.LocksSoundEvents;
import melonslise.locks.common.item.LockPickItem;
import melonslise.locks.common.network.toclient.TryPinResultPacket;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import melonslise.locks.common.util.ShockingHelper;
import melonslise.locks.common.container.LockPickingPolicy.PinOutcome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.network.PacketDistributor;

public class LockPickingContainer extends AbstractContainerMenu
{
	public static class HiddenSlot extends Slot
	{
		public HiddenSlot(Container inventoryIn, int index, int xPosition, int yPosition)
		{
			super(inventoryIn, index, xPosition, yPosition);
		}

		@OnlyIn(Dist.CLIENT)
		@Override
		public boolean isActive()
		{
			return false;
		}
	}

	public static final Component TITLE = Component.translatable(Locks.ID + ".gui.lockpicking.title");

	public final Player player;
	public final InteractionHand hand;
	public final LockPickingMode mode;
	public final Lockable lockable;

	public final Vec3 pos;

	public final int shocking, sturdy, complexity;

	protected int currIndex = 0;

	public LockPickingContainer(int id, Player player, InteractionHand hand, LockPickingMode mode, Lockable lkb)
	{
		super(LocksMenuTypes.LOCK_PICKING.get(), id);
		this.player = player;
		this.hand = hand;
		this.mode = mode;
		this.lockable = lkb;

		Lockable.State state = lkb.getLockState(player.level());
		this.pos = state == null ? lkb.bb.center() : state.pos;

		this.shocking = LocksServerConfig.ENABLE_SHOCKING.get() ? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.SHOCKING.get(), this.lockable.stack) : 0;
		this.sturdy = LocksServerConfig.ENABLE_STURDY.get() ? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.STURDY.get(), this.lockable.stack) : 0;
		this.complexity = LocksServerConfig.ENABLE_COMPLEXITY.get() ? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.COMPLEXITY.get(), this.lockable.stack) : 0;

		// Syncs the player inventory

		for (int rows = 0; rows < 3; ++rows)
			for (int cols = 0; cols < 9; ++cols)
				this.addSlot(new HiddenSlot(player.getInventory(), cols + rows * 9 + 9, 0, 0));

		for (int slots = 0; slots < 9; ++slots)
			this.addSlot(new HiddenSlot(player.getInventory(), slots, 0, 0));
	}

	public boolean isValidPick(ItemStack stack)
	{
		return LocksTagHelper.isLockPick(stack) && LockPickItem.canPick(stack, this.complexity);
	}

	// The single gate for "may this player still act on this session", shared by stillValid (polled every
	// tick) and TryPinPacket, so a pin can never be accepted under conditions that would have closed the menu.
	//
	// Only ITEM_BACKED ever touches pick code: an itemless hand is empty, and the LockPickItem stat helpers
	// write NBT on read, so they must never see air.
	public boolean canAttempt(Player player)
	{
		return LockPickingPolicy.isSessionValid(
			this.mode,
			this.lockable.lock.isLocked(),
			player.isSpectator(),
			player.distanceToSqr(this.pos),
			this.mode == LockPickingMode.ITEM_BACKED && this.isValidPick(player.getItemInHand(this.hand)),
			player.getItemInHand(this.hand).isEmpty(),
			LocksServerConfig.ALLOW_ITEMLESS_LOCK_PICKING.get());
	}

	@Override
	public boolean stillValid(Player player)
	{
		return this.canAttempt(player);
	}

	public boolean isOpen()
	{
		return this.currIndex == this.lockable.lock.getLength();
	}

	protected void reset()
	{
		this.currIndex = 0;
	}

	// SERVER ONLY
	public void tryPin(int currPin)
	{
		if(this.isOpen())
			return;
		boolean correct = this.lockable.lock.checkPin(this.currIndex, currPin);
		// && short-circuits, so an itemless attempt never enters tryBreakPick at all: no durability, no
		// break event, no replacement-pick scan, and no stat helper ever sees an empty stack.
		boolean pickBroke = LockPickingPolicy.shouldRollPickBreak(this.mode, correct) && this.tryBreakPick(player, currPin);
		PinOutcome outcome = LockPickingPolicy.resolve(this.mode, correct, pickBroke);

		if(correct)
			++this.currIndex;
		if(LockPickingPolicy.resetsProgress(outcome))
			this.reset();

		if(correct)
			this.player.level().playSound(null, this.pos.x, this.pos.y, this.pos.z, LocksSoundEvents.PIN_MATCH.get(), SoundSource.BLOCKS, 1f, 1f);
		else
		{
			if(LockPickingPolicy.triggersPickBreakShock(outcome))
				ShockingHelper.tryShock(this.player, this.lockable.stack, this.player.position(), ShockingHelper.Trigger.PICK_BREAK);
			// Opt-in punishment, off by default. Mutually exclusive with PICK_BREAK above; an itemless miss
			// reaches this one instead, since nothing broke.
			else if(LockPickingPolicy.triggersWrongPinShock(outcome))
				ShockingHelper.tryShock(this.player, this.lockable.stack, this.pos, ShockingHelper.Trigger.WRONG_PIN);
			if(outcome != PinOutcome.PICK_BROKE)
			{
				// No Quiet Hand special case for itemless: the hand is empty, so the enchantment level is 0
				// and this already yields full volume.
				ItemStack pickStack = this.player.getItemInHand(this.hand);
				float failVolume = LocksServerConfig.ENABLE_QUIET_HAND.get()
					&& EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.QUIET_HAND.get(), pickStack) > 0
					? (float) (double) LocksServerConfig.QUIET_HAND_VOLUME.get() : 1f;
				this.player.level().playSound(null, this.pos.x, this.pos.y, this.pos.z, LocksSoundEvents.PIN_FAIL.get(), SoundSource.BLOCKS, failVolume, 1f);
			}
		}
		// The client picks its animation from the menu mode, so the packet stays two plain booleans.
		LocksNetwork.MAIN.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) this.player), new TryPinResultPacket(correct, LockPickingPolicy.resetsProgress(outcome)));
	}

	@OnlyIn(Dist.CLIENT)
	public void handlePin(boolean correct, boolean reset)
	{
		Screen screen = Minecraft.getInstance().screen;
		if(screen instanceof LockPickingScreen)
			((LockPickingScreen) screen).handlePin(correct, reset);
		if(correct)
			++this.currIndex;
		if(reset)
			this.reset();
	}

	protected boolean tryBreakPick(Player player, int pin)
	{
		ItemStack pickStack = player.getItemInHand(this.hand);
		if (!LocksTagHelper.isLockPick(pickStack))
			return false;
		if (LocksServerConfig.NETHERITE_PICK_UNBREAKABLE.get()
			&& LockPickItem.isNetheriteLockPick(pickStack))
			return false;
		float sturdyModifier = this.sturdy == 0 ? 1f : 0.75f + this.sturdy * 0.5f;
		float strength = LockPickItem.getOrSetStrength(pickStack);
		int finesse = LocksServerConfig.ENABLE_FINESSE.get()
			? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.FINESSE.get(), pickStack) : 0;
		if (finesse > 0)
			strength *= 1f + finesse * (float) (double) LocksServerConfig.FINESSE_STRENGTH_PER_LEVEL.get();
		float ch = strength / sturdyModifier;
		float ex = (1f - ch) * (1f - this.getBreakChanceMultiplier(pin));
		float survive = ex + ch;
		// Finesse can never make a pick unbreakable: keep at least a 5% break chance when it is boosting.
		if (finesse > 0)
			survive = Math.min(survive, 0.95f);

		if (player.level().getRandom().nextFloat() < survive)
			return false;
		// Last Catch: one more chance to save the pick before it breaks.
		int lastCatch = LocksServerConfig.ENABLE_LAST_CATCH.get()
			? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.LAST_CATCH.get(), pickStack) : 0;
		if (lastCatch > 0 && player.level().getRandom().nextFloat() < (float) (double) LocksServerConfig.LAST_CATCH_SAVE_CHANCE.get())
			return false;
		if (LockPickItem.usesDurability(pickStack))
			LockPickItem.damagePick(pickStack, player, this.hand);
		else
		{
			this.player.broadcastBreakEvent(this.hand);
			pickStack.shrink(1);
		}
		if (pickStack.isEmpty())
			for (int a = 0; a < player.getInventory().getContainerSize(); ++a)
			{
				ItemStack stack = player.getInventory().getItem(a);
				if (this.isValidPick(stack))
				{
					player.setItemInHand(hand, stack);
					player.getInventory().removeItemNoUpdate(a);
					break;
				}
			}
		return true;
	}

	protected float getBreakChanceMultiplier(int pin)
	{
		return Math.abs(this.lockable.lock.getPin(this.currIndex) - pin) == 1 ? 0.33f : 1f;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void removed(Player player)
	{
		super.removed(player);
		// Server-only: completion is judged against the server currIndex, so a client cannot forge it.
		if(player.level().isClientSide)
			return;
		if(!this.isOpen() || !this.lockable.lock.isLocked())
			return;
		// Explicit target, never a toggle — a finished pick must never re-lock because state moved underneath it.
		// Deliberately not re-checking canAttempt here: the pins are already solved, and an item landing in the
		// hand on the closing tick should not swallow the result.
		LocksUtil.setLocked(player.level(), this.lockable, false, player);
		// null, not player: this now runs on the server, where passing the player would EXCLUDE them from the sound.
		this.player.level().playSound(null, this.pos.x, this.pos.y, this.pos.z, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
		LocksUtil.resolveLootTables(this.player.level(), this.lockable, this.player);
	}

	public static final IContainerFactory<LockPickingContainer> FACTORY = (id, inv, buf) ->
	{
		InteractionHand hand = buf.readEnum(InteractionHand.class);
		// Bounded byte rather than readEnum: readEnum indexes the values array unchecked and would throw on a
		// malformed id. An unknown mode falls back to the restrictive one.
		LockPickingMode mode = LockPickingMode.byId(buf.readByte());
		Lockable received = Lockable.fromBuf(buf);
		// Prefer the client's already-loaded instance (keeps the picked lock and the rendered lock the same
		// object), but fall back to the reconstructed one if the client hasn't loaded it yet — so the minigame
		// never dead-ends on a sync race.
		ILockableHandler handler = inv.player.level().getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		Lockable lkb = received;
		if(handler != null)
		{
			Lockable existing = handler.getLoaded().get(received.id);
			if(existing != handler.getLoaded().defaultReturnValue())
				lkb = existing;
		}
		return new LockPickingContainer(id, inv.player, hand, mode, lkb);
	};

	public static class Writer implements Consumer<FriendlyByteBuf>
	{
		public final InteractionHand hand;
		public final LockPickingMode mode;
		public final Lockable lockable;

		public Writer(InteractionHand hand, LockPickingMode mode, Lockable lkb)
		{
			this.hand = hand;
			this.mode = mode;
			this.lockable = lkb;
		}

		@Override
		public void accept(FriendlyByteBuf buf)
		{
			buf.writeEnum(this.hand);
			// Mode goes before the lockable so the variable-length tail stays last.
			buf.writeByte(this.mode.ordinal());
			// Write the FULL lockable, not just its id. The client opens this screen from a vanilla menu packet
			// that can race ahead of our lock-sync packets, so resolving by id against the client's loaded map
			// could miss and dead-end the minigame ("Lockable not found"). Sending the lockable lets the client
			// reconstruct it directly; the pin order stays server-authoritative (Lockable.toBuf is lossy on it).
			Lockable.toBuf(buf, this.lockable);
		}
	}

	public static class Provider implements MenuProvider
	{
		public final InteractionHand hand;
		public final LockPickingMode mode;
		public final Lockable lockable;

		public Provider(InteractionHand hand, LockPickingMode mode, Lockable lkb)
		{
			this.hand = hand;
			this.mode = mode;
			this.lockable = lkb;
		}

		@Override
		public AbstractContainerMenu createMenu(int id, Inventory inv, Player player)
		{
			return new LockPickingContainer(id, player, this.hand, this.mode, this.lockable);
		}

		@Override
		public Component getDisplayName()
		{
			return TITLE;
		}
	}
}
