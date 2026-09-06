package melonslise.locks.common.container;

import java.util.function.Consumer;

import melonslise.locks.Locks;
import melonslise.locks.client.gui.LockPickingScreen;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksMenuTypes;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LockTypeRegistry;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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

	// The session's identity: the dimension it was opened in and the id of the record it was opened against.
	// Both are re-checked before every mutation, so a session can never act on another dimension's copy of a
	// lockable or on a record that has been removed and replaced under the same id.
	public final ResourceKey<Level> dimension;
	public final int targetId;

	public final int shocking, sturdy, complexity;

	protected int currIndex = 0;

	// The highest client sequence acted on, plus the verdict it produced. A duplicate or reordered request
	// replays that verdict instead of mutating anything a second time (no extra pin, no extra pick wear).
	protected int lastSequence = 0;
	protected TryPinResultPacket lastResult;

	public LockPickingContainer(int id, Player player, InteractionHand hand, LockPickingMode mode, Lockable lkb)
	{
		super(LocksMenuTypes.LOCK_PICKING.get(), id);
		this.player = player;
		this.hand = hand;
		this.mode = mode;
		this.lockable = lkb;

		this.dimension = player.level().dimension();
		this.targetId = lkb.id;

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
	// Only ITEM_BACKED ever touches pick code: an itemless hand is empty and has no durability to spend.
	public boolean canAttempt(Player player)
	{
		return LockPickingPolicy.isSessionValid(
			this.mode,
			this.lockable.lock.isLocked(),
			player.isSpectator(),
			player.distanceToSqr(this.pos),
			this.mode == LockPickingMode.ITEM_BACKED && this.isValidPick(player.getItemInHand(this.hand)),
			player.getItemInHand(this.hand).isEmpty(),
			LocksServerConfig.ALLOW_ITEMLESS_LOCK_PICKING.get(),
			player.level().dimension().equals(this.dimension),
			this.isTargetCanonical(player.level()));
	}

	// The live record under this session's id, or null if it is gone or is no longer the instance the menu was
	// opened against. Client-side there is no authority to re-resolve against, so the client's own copy stands.
	protected Lockable resolveTarget(Level level)
	{
		if(level.isClientSide)
			return this.lockable;
		ILockableHandler handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return null;
		Lockable current = handler.getLoaded().get(this.targetId);
		if(current == handler.getLoaded().defaultReturnValue() || current != this.lockable)
			return null;
		return current;
	}

	protected boolean isTargetCanonical(Level level)
	{
		return this.resolveTarget(level) != null;
	}

	// Ends a session whose target or dimension no longer checks out, telling the client why before it goes.
	protected void terminate()
	{
		this.sendResult(this.lastSequence, -1, this.currIndex, false, false, true);
		if(this.player instanceof ServerPlayer sp)
			sp.closeContainer();
	}

	protected void sendResult(int sequence, int pin, int progress, boolean correct, boolean reset, boolean terminal)
	{
		TryPinResultPacket pkt = new TryPinResultPacket(this.containerId, sequence, pin, progress, correct, reset, terminal);
		this.lastResult = pkt;
		LocksNetwork.MAIN.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) this.player), pkt);
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
	public void tryPin(int currPin, int containerId, int sequence)
	{
		// A result may only ever be applied to the request that produced it, so the request must name this menu...
		if(containerId != this.containerId)
			return;
		// ...and must advance. A duplicate replays the previous verdict verbatim: no second pin, no second wear.
		if(sequence <= this.lastSequence)
		{
			if(this.lastResult != null && sequence == this.lastSequence)
				LocksNetwork.MAIN.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) this.player), this.lastResult);
			return;
		}
		Level level = this.player.level();
		if(!level.dimension().equals(this.dimension))
		{
			this.terminate();
			return;
		}
		// Re-resolve the record from the level's handler before touching it: the lockable this menu holds may
		// have been removed (block broken, chunk unloaded, carried away) since the session opened.
		Lockable target = this.resolveTarget(level);
		if(target == null)
		{
			this.terminate();
			return;
		}
		this.lastSequence = sequence;
		if(this.isOpen())
			return;
		boolean correct = this.lockable.lock.checkPin(this.currIndex, currPin);
		// && short-circuits, so an itemless attempt never enters wearPick at all: no durability spent, no
		// break event, and no replacement-pick scan.
		boolean pickBroke = LockPickingPolicy.shouldWearPick(this.mode, correct) && this.wearPick(player, currPin);
		PinOutcome outcome = LockPickingPolicy.resolve(this.mode, correct, pickBroke);

		if(correct)
			++this.currIndex;
		if(LockPickingPolicy.resetsProgress(outcome))
			this.reset();
		// The unlock is committed here, on the pin that finishes the combination, against the record just
		// re-resolved above — not in removed(), where the target may no longer exist or may no longer be ours.
		boolean opened = this.isOpen();
		if(opened && target.lock.isLocked())
			this.commitUnlock(target);

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
		// The client picks its animation from the menu mode; the packet carries the session identity and the
		// server's own progress so the client never has to infer either.
		this.sendResult(sequence, currPin, this.currIndex, correct, LockPickingPolicy.resetsProgress(outcome), opened);
	}

	// The actual opening: sound, lock state and loot resolution, all against the re-resolved record.
	protected void commitUnlock(Lockable target)
	{
		LocksUtil.setLocked(this.player.level(), target, false, this.player);
		// null, not player: this runs on the server, where passing the player would EXCLUDE them from the sound.
		this.player.level().playSound(null, this.pos.x, this.pos.y, this.pos.z, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
		LocksUtil.resolveLootTables(this.player.level(), target, this.player);
	}

	@OnlyIn(Dist.CLIENT)
	public void handlePin(int sequence, int pin, int progress, boolean correct, boolean reset, boolean terminal)
	{
		// The server's progress is authoritative; the client no longer counts pins for itself.
		this.currIndex = progress;
		Screen screen = Minecraft.getInstance().screen;
		if(screen instanceof LockPickingScreen)
			((LockPickingScreen) screen).handlePin(sequence, pin, progress, correct, reset, terminal);
	}

	// Spends pick durability for one wrong pin and reports whether that finished the pick. Replaces the
	// 1.7.2 random break roll, which deleted the whole item on a losing throw: the cost is now fixed by
	// the LOCK being picked, and the pick dies only when its own pool runs out.
	protected boolean wearPick(Player player, int pin)
	{
		ItemStack pickStack = player.getItemInHand(this.hand);
		if (!LocksTagHelper.isLockPick(pickStack))
			return false;
		if (LocksServerConfig.NETHERITE_PICK_UNBREAKABLE.get()
			&& LockPickItem.isNetheriteLockPick(pickStack))
			return false;
		// A pick registered without a durability pool (definition durability 0, or a third-party tagged
		// item that is not damageable) simply never wears down.
		if (!LockPickItem.usesDurability(pickStack))
			return false;
		// Last Catch is the only die roll left anywhere in this path, and it can only ever SAVE durability.
		// It never breaks anything, so a pick still dies exactly when its pool hits zero and not before.
		int lastCatch = LocksServerConfig.ENABLE_LAST_CATCH.get()
			? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.LAST_CATCH.get(), pickStack) : 0;
		if (lastCatch > 0 && player.level().getRandom().nextFloat() < (float) (double) LocksServerConfig.LAST_CATCH_SAVE_CHANCE.get())
			return false;
		int finesse = LocksServerConfig.ENABLE_FINESSE.get()
			? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.FINESSE.get(), pickStack) : 0;
		int wear = LockPickWearPolicy.wearFor(
			LockTypeRegistry.getLockStats(this.lockable.stack.getItem()).pickWear(),
			this.isNearMiss(pin),
			LocksServerConfig.NEAR_MISS_WEAR_MULTIPLIER.get(),
			this.sturdy,
			LocksServerConfig.STURDY_WEAR_PER_LEVEL.get(),
			finesse,
			LocksServerConfig.FINESSE_WEAR_REDUCTION_PER_LEVEL.get());
		boolean pickBroke = LockPickItem.damagePick(pickStack, player, this.hand, wear);
		// damagePick observes hurtAndBreak's actual result, including Unbreaking and creative mode, and only
		// removes the one active pick from a stack. Never predict a break from damage + wear.
		if (!pickBroke)
			return false;
		// A stacked pick already has its next pristine item ready in the same hand.
		if (!pickStack.isEmpty())
			return true;
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

	// Off by exactly one pin: the guess was nearly right, so the pick is only lightly strained. This was
	// the 0.33 break-chance multiplier in 1.7.2 and is now a wear multiplier.
	protected boolean isNearMiss(int pin)
	{
		return Math.abs(this.lockable.lock.getPin(this.currIndex) - pin) == 1;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void removed(Player player)
	{
		super.removed(player);
		// Nothing but session bookkeeping happens here now. The unlock is committed in tryPin, on the pin that
		// finishes the combination and against the record re-resolved there, so closing the screen can neither
		// open a lock the session no longer owns nor be raced by the record disappearing first.
		this.lastResult = null;
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
