package melonslise.locks.common.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.Locks;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ISelection;
import melonslise.locks.common.compat.CuriosHelper;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.config.LocksClientConfig;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.container.KeyRingContainer;
import melonslise.locks.common.container.LockPickingContainer;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksItemTags;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.init.LocksTagHelper;
import melonslise.locks.common.init.LockStatsReloadListener;
import melonslise.locks.common.init.LocksSoundEvents;
import melonslise.locks.common.item.KeyRingItem;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockPickItem;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import melonslise.locks.common.util.LootValueCalculator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = Locks.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LocksForgeEvents
{
	public static final Component LOCKED_MESSAGE = Component.translatable(Locks.ID + ".status.locked");

	private LocksForgeEvents() {}

	@SubscribeEvent
	public static void attachCapabilitiesToWorld(AttachCapabilitiesEvent<Level> e)
	{
		LocksCapabilities.attachToWorld(e);
	}

	@SubscribeEvent
	public static void attachCapabilitiesToChunk(AttachCapabilitiesEvent<LevelChunk> e)
	{
		LocksCapabilities.attachToChunk(e);
	}

	@SubscribeEvent
	public static void attachCapabilitiesToEntity(AttachCapabilitiesEvent<Entity> e)
	{
		LocksCapabilities.attachToEntity(e);
	}

	@SubscribeEvent
	public static void addReloadListeners(AddReloadListenerEvent e)
	{
		e.addListener(new LockStatsReloadListener());
	}

	@SubscribeEvent
	public static void onLevelLoad(LevelEvent.Load e)
	{
		if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
			LootValueCalculator.precomputeAll(level.getServer());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent e)
	{
		LootValueCalculator.clearCache();
	}

	private static final Gson LOOT_GSON = net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create();

	@SubscribeEvent
	public static void onLootTableLoad(LootTableLoadEvent e)
	{
		ResourceLocation name = e.getName();
		if(!name.getNamespace().equals("minecraft") || !name.getPath().startsWith("chests/"))
			return;
		ResourceLocation injectLoc = new ResourceLocation(Locks.ID, "loot_tables/inject/" + name.getPath() + ".json");
		net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if(server == null)
			return;
		ResourceManager manager = server.getResourceManager();
		Optional<Resource> resource = manager.getResource(injectLoc);
		if(resource.isEmpty())
			return;
		try(java.io.Reader reader = resource.get().openAsReader())
		{
			JsonElement json = GsonHelper.fromJson(LOOT_GSON, reader, JsonElement.class);
			if(json.isJsonObject() && json.getAsJsonObject().has("pools"))
			{
				for(JsonElement poolJson : json.getAsJsonObject().getAsJsonArray("pools"))
				{
					LootPool pool = LOOT_GSON.fromJson(poolJson, LootPool.class);
					e.getTable().addPool(pool);
				}
			}
		}
		catch(Exception ex)
		{
			Locks.LOGGER.error("Failed to inject loot table {}", injectLoc, ex);
		}
	}

	@SubscribeEvent
	public static void addVillagerTrades(VillagerTradesEvent e)
	{
		if(e.getType() != VillagerProfession.TOOLSMITH)
			return;
		Int2ObjectMap<List<ItemListing>> levels = e.getTrades();
		List<ItemListing> trades;
		trades = levels.get(1);
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.WOOD_LOCK_PICK.get()), 1, 2, 16, 2, 0.05f));
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.WOOD_LOCK_MECHANISM.get()), 2, 1, 12, 1, 0.2f));
		trades = levels.get(2);
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.IRON_LOCK_PICK.get()), 2, 2, 16, 5, 0.05f));
		trades = levels.get(3);
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.GOLD_LOCK_PICK.get()), 6, 2, 12, 20, 0.05f));
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.IRON_LOCK_MECHANISM.get()), 5, 1, 8, 10, 0.2f));
		trades = levels.get(4);
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.STEEL_LOCK_PICK.get()), 4, 2, 16, 20, 0.05f));
		trades = levels.get(5);
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.DIAMOND_LOCK_PICK.get()), 8, 2, 12, 30, 0.05f));
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.NETHERITE_LOCK_PICK.get()), 16, 1, 6, 30, 0.05f));
		trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.STEEL_LOCK_MECHANISM.get()), 8, 1, 8, 30, 0.2f));
	}

	@SubscribeEvent
	public static void addWandererTrades(WandererTradesEvent e)
	{
		List<ItemListing> trades;
		trades = e.getGenericTrades();
		trades.add(new VillagerTrades.ItemsForEmeralds(LocksItems.GOLD_LOCK_PICK.get(), 5, 2, 6, 1));
		trades.add(new VillagerTrades.ItemsForEmeralds(LocksItems.STEEL_LOCK_PICK.get(), 3, 2, 8, 1));
		trades.add(new VillagerTrades.EnchantedItemForEmeralds(LocksItems.STEEL_LOCK.get(), 16, 4, 1));
		trades = e.getRareTrades();
		trades.add(new VillagerTrades.ItemsForEmeralds(LocksItems.STEEL_LOCK_MECHANISM.get(), 6, 1, 4, 1));
		trades.add(new VillagerTrades.EnchantedItemForEmeralds(LocksItems.DIAMOND_LOCK.get(), 28, 4, 1));
		trades.add(new VillagerTrades.EnchantedItemForEmeralds(LocksItems.NETHERITE_LOCK.get(), 40, 4, 1));
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload e)
	{
		if(!(e.getChunk() instanceof LevelChunk ch))
			return;
		ILockableHandler handler = ch.getLevel().getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return;
		ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).ifPresent(storage -> storage.get().values().forEach(lkb ->
		{
			handler.getLoaded().remove(lkb.id);
			lkb.deleteObserver(handler);
		}));
	}

	@SubscribeEvent
	public static void onRightClick(PlayerInteractEvent.RightClickBlock e)
	{
		BlockPos pos = e.getPos();
		Level world = e.getLevel();
		Player player = e.getEntity();
		ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return;
		Int2ObjectMap<Lockable> chunkLockables = handler.getInChunk(pos);
		if(chunkLockables == null)
			return;
		List<Lockable> intersect = new ArrayList<>(4);
		for(Lockable lkb : chunkLockables.values())
			if(lkb.bb.intersects(pos))
				intersect.add(lkb);
		if(intersect.isEmpty())
			return;
		// PlayerInteractEvent.RightClickBlock fires once per hand. We process only MAIN_HAND
		// and deny block interaction on OFFHAND to prevent double-firing. This is correct even
		// with shields or other offhand items — Forge fires the main-hand event independently.
		if(e.getHand() != InteractionHand.MAIN_HAND)
		{
			e.setUseBlock(Event.Result.DENY);
			return;
		}
		ItemStack stack = e.getItemStack();
		Lockable locked = null;
		for(Lockable lkb : intersect)
			if(lkb.lock.isLocked()) { locked = lkb; break; }
		if(locked != null)
		{
			Lockable lkb = locked;
			e.setUseBlock(Event.Result.DENY);
			e.setUseItem(Event.Result.DENY);

			if(LocksTagHelper.isLockPick(stack))
			{
				// Lock pick: open lock picking minigame (or auto-pick if enchanted)
				if(!LockPickItem.canPick(stack, lkb))
				{
					if(world.isClientSide)
						player.displayClientMessage(LockPickItem.TOO_COMPLEX_MESSAGE, true);
				}
				else
				{
					int autoPick = LocksServerConfig.ENABLE_AUTO_PICK.get() ? EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.AUTO_PICK.get(), lkb.stack) : 0;
					if(autoPick > 0 && !world.isClientSide && world.getRandom().nextFloat() < autoPick * 0.10f)
					{
						// Auto-Pick triggered: instant unlock without minigame
						lkb.lock.setLocked(false);
						world.playSound(null, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
						LocksUtil.resolveLootTables(world, lkb, player);
					}
					else if(!world.isClientSide)
					{
						NetworkHooks.openScreen((ServerPlayer) player, new LockPickingContainer.Provider(InteractionHand.MAIN_HAND, lkb), new LockPickingContainer.Writer(InteractionHand.MAIN_HAND, lkb));
					}
				}
			}
			else if(stack.getItem() == LocksItems.MASTER_KEY.get())
			{
				// Master key: toggle all lockables at position
				world.playSound(player, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
				if(!world.isClientSide)
					for(Lockable l : intersect)
					{
						boolean wasLocked = l.lock.isLocked();
						l.lock.setLocked(!wasLocked);
						if(wasLocked)
							LocksUtil.resolveLootTables(world, l, player);
					}
			}
			else if(LocksTagHelper.isKey(stack) && LockingItem.getOrSetId(stack) == lkb.lock.id)
			{
				// Key with matching ID: toggle matching lockables
				int id = LockingItem.getOrSetId(stack);
				world.playSound(player, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
				if(!world.isClientSide)
					for(Lockable l : intersect)
						if(l.lock.id == id)
						{
							boolean wasLocked = l.lock.isLocked();
							l.lock.setLocked(!wasLocked);
							if(wasLocked)
								LocksUtil.resolveLootTables(world, l, player);
						}
			}
			else if(stack.getItem() == LocksItems.KEY_RING.get() && KeyRingItem.containsId(stack, lkb.lock.id))
			{
				// Key ring with matching key: find first matching key and toggle
				IItemHandler inv = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
				if(inv == null) return;
				for(int a = 0; a < inv.getSlots(); ++a)
				{
					int id = LockingItem.getOrSetId(inv.getStackInSlot(a));
					boolean matched = false;
					for(Lockable l : intersect)
					{
						if(l.lock.id == id)
						{
							matched = true;
							if(!world.isClientSide)
							{
								boolean wasLocked = l.lock.isLocked();
								l.lock.setLocked(!wasLocked);
								if(wasLocked)
									LocksUtil.resolveLootTables(world, l, player);
							}
						}
					}
					if(matched)
					{
						world.playSound(player, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
						break;
					}
				}
			}
			else
			{
				// Check Awareness enchantment: scan all locked lockables for one owned by this player
				boolean awarenessHandled = false;
				if (LocksServerConfig.ENABLE_AWARENESS.get())
				{
					for (Lockable candidate : intersect)
					{
						if (!candidate.lock.isLocked()) continue;
						if (EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.AWARENESS.get(), candidate.stack) <= 0) continue;
						java.util.UUID owner = LockItem.getOwner(candidate.stack);
						if (owner != null && owner.equals(player.getUUID()))
						{
							world.playSound(player, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
							if (!world.isClientSide)
								for (Lockable l : intersect)
								{
									java.util.UUID lOwner = LockItem.getOwner(l.stack);
									if (lOwner != null && lOwner.equals(player.getUUID())
										&& EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.AWARENESS.get(), l.stack) > 0)
									{
										boolean wasLocked = l.lock.isLocked();
										l.lock.setLocked(!wasLocked);
										if (wasLocked)
											LocksUtil.resolveLootTables(world, l, player);
									}
								}
							awarenessHandled = true;
							break;
						}
					}
				}
				if (!awarenessHandled)
				{
					// Check curio slots for a key ring with matching key
					ItemStack curioRing = CuriosHelper.findMatchingKeyRing(player, lkb.lock.id);
					if (!curioRing.isEmpty())
					{
						IItemHandler curioInv = curioRing.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
						if (curioInv == null) return;
						for (int a = 0; a < curioInv.getSlots(); ++a)
						{
							int id = LockingItem.getOrSetId(curioInv.getStackInSlot(a));
							boolean matched = false;
							for (Lockable l : intersect)
							{
								if (l.lock.id == id)
								{
									matched = true;
									if (!world.isClientSide)
									{
										boolean wasLocked = l.lock.isLocked();
										l.lock.setLocked(!wasLocked);
										if (wasLocked)
											LocksUtil.resolveLootTables(world, l, player);
									}
								}
							}
							if (matched)
							{
								world.playSound(player, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
								break;
							}
						}
					}
					else
					{
						// No matching item anywhere: rattle (unless Silent enchantment)
						lkb.swing(20);
						if(!LocksServerConfig.ENABLE_SILENT.get() || EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.SILENT.get(), lkb.stack) == 0)
						{
							world.playSound(player, pos, LocksSoundEvents.LOCK_RATTLE.get(), SoundSource.BLOCKS, 1f, 1f);
							if(world.isClientSide && LocksClientConfig.DEAF_MODE.get())
								player.displayClientMessage(LOCKED_MESSAGE, true);
						}
					}
				}
			}

			player.swing(InteractionHand.MAIN_HAND);
			e.setCancellationResult(InteractionResult.SUCCESS);
			e.setCanceled(true);
			return;
		}
		if(LocksServerConfig.ALLOW_REMOVING_LOCKS.get() && player.isShiftKeyDown() && stack.isEmpty())
		{
			List<Lockable> match = new ArrayList<>(4);
			for(Lockable lkb : intersect)
				if(!lkb.lock.isLocked())
					match.add(lkb);
			if(match.isEmpty())
				return;
			e.setUseBlock(Event.Result.DENY);
			world.playSound(player, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f + world.random.nextFloat() * 0.4f);
			player.swing(InteractionHand.MAIN_HAND);
			if(!world.isClientSide)
				for(Lockable lkb : match)
				{
					world.addFreshEntity(new ItemEntity(world, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, lkb.stack));
					handler.remove(lkb.id);
				}
		}
	}

	@SubscribeEvent
	public static void onEmptyHandRightClick(PlayerInteractEvent.RightClickItem e)
	{
		if (e.getHand() != InteractionHand.MAIN_HAND)
			return;
		Player player = e.getEntity();
		if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty())
			return;
		if (!player.isShiftKeyDown())
			return;
		if (player.level().isClientSide)
			return;
		ItemStack curioRing = CuriosHelper.findAnyKeyRing(player);
		if (curioRing.isEmpty())
			return;
		NetworkHooks.openScreen((ServerPlayer) player,
			new KeyRingContainer.Provider(curioRing),
			new KeyRingContainer.CurioWriter());
		e.setCanceled(true);
		e.setCancellationResult(InteractionResult.SUCCESS);
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent e)
	{
		if(e.phase != Phase.START)
			return;
		ISelection select = e.player.getCapability(LocksCapabilities.SELECTION).orElse(null);
		if (select == null || select.get() == null)
			return;
		for (ItemStack stack : e.player.getHandSlots())
			if(LocksTagHelper.isLock(stack))
				return;
		select.set(null);
	}

	public static boolean canBreakLockable(Player player, BlockPos pos)
	{
		return !LocksServerConfig.PROTECT_LOCKABLES.get() || player.isCreative() || !LocksUtil.locked(player.level(), pos);
	}

	@SubscribeEvent
	public static void onBlockBreaking(PlayerEvent.BreakSpeed e)
	{
		Optional<BlockPos> optPos = e.getPosition();
		if(optPos.isEmpty())
			return;
		if(!canBreakLockable(e.getEntity(), optPos.get()))
			e.setCanceled(true);
	}

	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent e)
	{
		if(!canBreakLockable(e.getPlayer(), e.getPos()))
			e.setCanceled(true);
	}
}
