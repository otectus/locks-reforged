package melonslise.locks.common.event;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.Locks;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.capability.ISelection;
import melonslise.locks.common.compat.CarryOnCompat;
import melonslise.locks.common.compat.CuriosHelper;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.config.LocksClientConfig;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.container.KeyRingContainer;
import melonslise.locks.common.container.LockPickingContainer;
import melonslise.locks.common.container.LockPickingMode;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksItemTags;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.init.LocksNetwork;
import melonslise.locks.common.network.toclient.AddLockableToChunkPacket;
import melonslise.locks.common.init.LocksTagHelper;
import melonslise.locks.common.init.LockStatsReloadListener;
import melonslise.locks.common.init.LocksSoundEvents;
import melonslise.locks.common.steel.NativeSteelPolicy;
import melonslise.locks.common.steel.NativeSteelState;
import melonslise.locks.common.steel.SteelMaterialMode;
import melonslise.locks.common.item.KeyRingItem;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockPickItem;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.capability.LocksSavedData;
import melonslise.locks.common.util.Lock;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksThreadUtil;
import melonslise.locks.common.util.LocksUtil;
import melonslise.locks.common.util.PassiveLockPolicy;
import melonslise.locks.common.util.LootValueCalculator;
import melonslise.locks.common.util.ShockingHelper;
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
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.phys.Vec3;
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
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = Locks.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LocksForgeEvents
{
	public static final Component LOCKED_MESSAGE = Component.translatable(Locks.ID + ".status.locked");
	public static final Component KEY_BLANK_PAIRING_MESSAGE = Component.translatable(Locks.ID + ".status.key_blank_pairing");

	private LocksForgeEvents() {}

	// Server-side menu open, shared by the physical-pick and itemless entry points. The mode is decided
	// here, once, and shipped to the client purely so the screen renders the right tool; the client never
	// gets to tell the server which mode it is playing.
	private static void openPicking(Player player, Lockable lkb, LockPickingMode mode)
	{
		NetworkHooks.openScreen((ServerPlayer) player,
			new LockPickingContainer.Provider(InteractionHand.MAIN_HAND, mode, lkb),
			new LockPickingContainer.Writer(InteractionHand.MAIN_HAND, mode, lkb));
	}

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
		if (e.getLevel() instanceof ServerLevel level)
		{
			// Resolve the persisted lockable id counter on the main thread before any chunk/structure
			// generation can allocate ids off-thread (C2ME), keeping ids unique across server restarts.
			level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).ifPresent(ILockableHandler::initIds);
			if (level.dimension() == Level.OVERWORLD)
			{
				loadComboSalt(level.getServer());
				LootValueCalculator.precomputeAll(level.getServer());
			}
		}
	}

	// Resolves the lock-combination migration salt into Lock's plain static field, on the main thread, before any
	// chunk can deserialize a lock. Deliberately read from the OVERWORLD's storage rather than the loading level's:
	// LocksSavedData is per-dimension, and a per-dimension salt would migrate the same carried lock differently in
	// each dimension. This must stay on the FORGE bus — on the MOD bus it would never fire, the salt would stay 0,
	// and every reroll would collapse back to a value the client can derive.
	private static void loadComboSalt(net.minecraft.server.MinecraftServer server)
	{
		if (server == null)
			return;
		LocksSavedData data = server.overworld().getDataStorage().computeIfAbsent(LocksSavedData::load, LocksSavedData::new, LocksSavedData.NAME);
		long salt = data.getOrCreateComboSalt();
		Lock.setMigrationSalt(salt);
		if (salt == 0L)
			Locks.LOGGER.error("Lock combination salt resolved to zero — legacy combinations would migrate to a client-derivable value!");
		else
			Locks.LOGGER.info("Loaded the lock combination salt from the overworld save; legacy id-derived combinations will be rerolled once.");
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent e)
	{
		LootValueCalculator.clearCache();
		// Drop the steel snapshot so a stale state never leaks into the next world/server session.
		NativeSteelPolicy.clear();
	}

	/**
	 * Refresh the native-steel compatibility snapshot whenever tags are (re)loaded, and log a single diagnostic
	 * summary. Only the authoritative server-data load recomputes the snapshot and logs; the client packet update
	 * refreshes the snapshot too (so the client-side creative tab reflects the server), but never logs.
	 */
	@SubscribeEvent
	public static void onTagsUpdated(TagsUpdatedEvent e)
	{
		SteelMaterialMode mode = LocksServerConfig.SPEC.isLoaded()
			? LocksServerConfig.STEEL_MATERIAL_MODE.get()
			: SteelMaterialMode.AUTO;
		NativeSteelState state = NativeSteelPolicy.refresh(e.getRegistryAccess(), mode);

		if (e.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)
			return;

		Locks.LOGGER.info("Steel material mode: {}", mode);
		Locks.LOGGER.info("Foreign ingots: {}", state.foreignIngots());
		Locks.LOGGER.info("Foreign nuggets: {}", state.foreignNuggets());
		Locks.LOGGER.info("Foreign ores: {}", state.foreignOres());
		Locks.LOGGER.info("Locks native fallback: ingot={}, nugget={}, oreGeneration={}",
			state.nativeIngotActive(), state.nativeNuggetActive(), state.nativeOreActive());

		if (mode == SteelMaterialMode.EXTERNAL_ONLY)
		{
			List<String> missing = NativeSteelPolicy.missingForms(state);
			if (!missing.isEmpty())
				Locks.LOGGER.warn("Steel Material Mode is EXTERNAL_ONLY but no steel provider supplies: {}. "
					+ "Affected Locks recipes will be uncraftable until a mod/datapack populates the forge steel tags.", missing);
		}
	}

	private static final Gson LOOT_GSON = net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create();

	@SubscribeEvent
	public static void onLootTableLoad(LootTableLoadEvent e)
	{
		ResourceLocation name = e.getName();
		if(!LocksServerConfig.matchesLootTablePattern(name))
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
		if(!LocksServerConfig.ENABLE_VILLAGER_TRADES.get())
			return;
		ResourceLocation wantedProfession = ResourceLocation.tryParse(LocksServerConfig.VILLAGER_PROFESSION.get());
		if(wantedProfession == null || !wantedProfession.equals(ForgeRegistries.VILLAGER_PROFESSIONS.getKey(e.getType())))
			return;
		boolean picks = LocksServerConfig.ENABLE_VILLAGER_LOCKPICK_TRADES.get();
		boolean mechs = LocksServerConfig.ENABLE_VILLAGER_MECHANISM_TRADES.get();
		boolean locks = LocksServerConfig.ENABLE_VILLAGER_LOCK_TRADES.get();
		Int2ObjectMap<List<ItemListing>> levels = e.getTrades();
		List<ItemListing> trades;

		trades = levels.get(1);
		if(picks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.WOOD_LOCK_PICK.get()), 1, 2, 16, 2, 0.05f));
		if(mechs) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.WOOD_LOCK_MECHANISM.get()), 2, 1, 12, 1, 0.2f));
		if(locks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.WOOD_LOCK.get()), 3, 1, 12, 2, 0.2f));

		trades = levels.get(2);
		if(picks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.IRON_LOCK_PICK.get()), 2, 2, 16, 5, 0.05f));
		if(locks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.COPPER_LOCK.get()), 4, 1, 12, 5, 0.2f));

		trades = levels.get(3);
		if(picks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.GOLD_LOCK_PICK.get()), 6, 2, 12, 20, 0.05f));
		if(mechs) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.IRON_LOCK_MECHANISM.get()), 5, 1, 8, 10, 0.2f));
		if(locks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.IRON_LOCK.get()), 6, 1, 8, 10, 0.2f));

		trades = levels.get(4);
		if(picks) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.STEEL_LOCK_PICK.get()), 4, 2, 16, 20, 0.05f));

		trades = levels.get(5);
		if(picks)
		{
			trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.DIAMOND_LOCK_PICK.get()), 8, 2, 12, 30, 0.05f));
			trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.NETHERITE_LOCK_PICK.get()), 16, 1, 6, 30, 0.05f));
		}
		if(mechs) trades.add(new VillagerTrades.ItemsForEmeralds(new ItemStack(LocksItems.STEEL_LOCK_MECHANISM.get()), 8, 1, 8, 30, 0.2f));
	}

	@SubscribeEvent
	public static void addWandererTrades(WandererTradesEvent e)
	{
		if(!LocksServerConfig.ENABLE_WANDERER_TRADES.get())
			return;
		boolean picks = LocksServerConfig.ENABLE_WANDERER_LOCKPICK_TRADES.get();
		boolean locks = LocksServerConfig.ENABLE_WANDERER_LOCK_TRADES.get();
		boolean mechs = LocksServerConfig.ENABLE_WANDERER_MECHANISM_TRADES.get();
		List<ItemListing> generic = e.getGenericTrades();
		List<ItemListing> rare = e.getRareTrades();
		if(picks)
		{
			generic.add(new VillagerTrades.ItemsForEmeralds(LocksItems.GOLD_LOCK_PICK.get(), 5, 2, 6, 1));
			generic.add(new VillagerTrades.ItemsForEmeralds(LocksItems.STEEL_LOCK_PICK.get(), 3, 2, 8, 1));
		}
		if(locks)
		{
			generic.add(new VillagerTrades.EnchantedItemForEmeralds(LocksItems.STEEL_LOCK.get(), 16, 4, 1));
			rare.add(new VillagerTrades.EnchantedItemForEmeralds(LocksItems.DIAMOND_LOCK.get(), 28, 4, 1));
			rare.add(new VillagerTrades.EnchantedItemForEmeralds(LocksItems.NETHERITE_LOCK.get(), 40, 4, 1));
		}
		if(mechs)
			rare.add(new VillagerTrades.ItemsForEmeralds(LocksItems.STEEL_LOCK_MECHANISM.get(), 6, 1, 4, 1));
	}

	// Single registration trigger for ALL chunk loads (disk + freshly generated). The chunk capability NBT
	// (LockableStorage#deserializeNBT) is now pure chunk-local parsing that may run on an async worker thread
	// (C2ME); moving the parsed lockables into the world-global handler is main-thread-only work, so it is
	// deferred here. Without async chunk mods this runs inline and behaviour is unchanged.
	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load e)
	{
		if(!(e.getChunk() instanceof LevelChunk ch))
			return;
		Level level = ch.getLevel();
		if(level.isClientSide)
			return; // the client populates its handler from packets, not chunk NBT
		// `ch` IS the live LevelChunk whose load fired this event, and its LockableStorage was already populated by
		// LevelChunkMixin on the building thread. Capture both NOW and NEVER re-fetch via a blocking Level#getChunk:
		// under C2ME this handler can fire re-entrantly on the main thread while that thread is draining the chunk
		// executor (BlockableEventLoop#managedBlock), and getChunk(...,FULL,true) would re-park on the very future
		// this thread is in the middle of completing -> permanent hang.
		ILockableStorage storage = ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
		if(storage == null)
			return;
		int chX = ch.getPos().x, chZ = ch.getPos().z;
		LocksThreadUtil.runOnServerThread(level.getServer(), () ->
		{
			// Non-blocking staleness guard for the deferred (worker -> next-tick) path: the chunk may have unloaded
			// before this task runs. hasChunk == ServerChunkCache#getChunkNow != null (no future await). NEVER call
			// level.getChunk(...) here.
			if(!level.hasChunk(chX, chZ))
				return;
			ILockableHandler handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
			if(handler == null)
				return;
			handler.registerChunkStorage(ch, storage, false);
		});
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload e)
	{
		if(!(e.getChunk() instanceof LevelChunk ch))
			return;
		Level level = ch.getLevel();
		ILockableHandler handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return;
		ILockableStorage storage = ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
		if(storage == null)
			return;
		// Snapshot the storage now: reading the per-chunk map is thread-safe, but the handler mutation must
		// happen on the main thread, and the storage capability may be invalidated by the time a deferred task
		// runs. A lockable straddling a chunk border is only dropped from the handler once no OTHER chunk it
		// occupies is still loaded — otherwise it vanishes from rendering/sync while a neighbour stays loaded.
		List<Lockable> present = new ArrayList<>(storage.get().values());
		if(present.isEmpty())
			return;
		int chX = ch.getPos().x, chZ = ch.getPos().z;
		if(level.isClientSide)
		{
			handler.unregisterChunkStorage(chX, chZ, present); // no server thread to defer to client-side
			return;
		}
		LocksThreadUtil.runOnServerThread(level.getServer(), () -> handler.unregisterChunkStorage(chX, chZ, present));
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
		// Self-heal a chunk-storage <-> world-index divergence before acting on a lock (server only, and only when
		// a lockable is actually here). Door-blocking reads the per-chunk storage, but rendering and the pick
		// minigame read the world index / client mirror. registerChunkStorage is idempotent and sends no packets;
		// it makes every lockable in this chunk the canonical, handler-observed instance (so lock-state changes
		// persist and sync), then we rebuild `intersect` from the now-canonical storage so we operate on those
		// instances. Without this a door could stay blocked by a lock that never reached the index (e.g. a missed
		// sync under async chunk loading), invisible and unpickable.
		LevelChunk lockChunk = null;
		if(!world.isClientSide && !intersect.isEmpty())
		{
			lockChunk = world.getChunkAt(pos);
			ILockableStorage st = lockChunk.getCapability(LocksCapabilities.LOCKABLE_STORAGE).orElse(null);
			if(st != null)
			{
				handler.registerChunkStorage(lockChunk, st, false);
				intersect.clear();
				for(Lockable lkb : chunkLockables.values())
					if(lkb.bb.intersects(pos))
						intersect.add(lkb);
			}
		}
		if(intersect.isEmpty())
		{
			// Adventure mode fallback: vanilla ItemStack.useOn() short-circuits when
			// !mayBuild, so LockItem.useOn() never fires. Invoke it directly on the
			// sneak-place gesture so lock placement works without changing gamemode.
			ItemStack held = e.getItemStack();
			if(e.getHand() == InteractionHand.MAIN_HAND
				&& !player.getAbilities().mayBuild
				&& player.isSecondaryUseActive()
				&& held.getItem() instanceof LockItem lockItem)
			{
				UseOnContext ctx = new UseOnContext(player, e.getHand(), e.getHitVec());
				InteractionResult result = lockItem.useOn(ctx);
				if(result != InteractionResult.PASS)
				{
					e.setCanceled(true);
					e.setCancellationResult(result);
				}
			}
			return;
		}
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
			boolean yieldToBlock = false;
			e.setUseBlock(Event.Result.DENY);
			e.setUseItem(Event.Result.DENY);

			// Self-heal the client: push the blocking lock to the interacting player so a desynced or missing
			// client copy re-renders and the pick minigame can resolve it. Bounded to actual blocked interactions.
			if(!world.isClientSide && lockChunk != null && player instanceof ServerPlayer sp)
			{
				LevelChunk syncChunk = lockChunk;
				LocksNetwork.MAIN.send(PacketDistributor.PLAYER.with(() -> sp), new AddLockableToChunkPacket(lkb, syncChunk));
			}

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
						LocksUtil.setLocked(world, lkb, false, player);
						world.playSound(null, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
						LocksUtil.resolveLootTables(world, lkb, player);
					}
					else if(!world.isClientSide)
					{
						openPicking(player, lkb, LockPickingMode.ITEM_BACKED);
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
						LocksUtil.setLocked(world, l, !wasLocked, player);
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
							LocksUtil.setLocked(world, l, !wasLocked, player);
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
					// readId, not getOrSetId: an empty slot would otherwise get a random id written into
					// ItemStack.EMPTY, whose tag is a shared singleton.
					OptionalInt slotId = LockingItem.readId(inv.getStackInSlot(a));
					if(slotId.isEmpty())
						continue;
					int id = slotId.getAsInt();
					boolean matched = false;
					for(Lockable l : intersect)
					{
						if(l.lock.id == id)
						{
							matched = true;
							if(!world.isClientSide)
							{
								boolean wasLocked = l.lock.isLocked();
								LocksUtil.setLocked(world, l, !wasLocked, player);
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
				// Awareness: a lock does not apply to the player who placed it. An ordinary click is handed
				// straight to the block and never touches the lock, so the chest or door opens while staying
				// shut to everyone else; sneaking is the one gesture that acts on the lock itself.
				//
				// Both tallies are taken before anything is mutated, so the client and the server decide
				// identically and neither can be steered by the order of the list.
				boolean awarenessHandled = false;
				boolean anyOwnedLocked = false, everyLockedOwned = true;
				for (Lockable l : intersect)
				{
					if (!l.lock.isLocked())
						continue;
					if (LocksUtil.ownsAwareness(l, player))
						anyOwnedLocked = true;
					else
						everyLockedOwned = false;
				}
				if (anyOwnedLocked)
				{
					PassiveLockPolicy.Action action = PassiveLockPolicy.onOwnedLocked(player.isShiftKeyDown(), stack.isEmpty(), everyLockedOwned);
					if (action == PassiveLockPolicy.Action.UNLOCK)
					{
						world.playSound(player, pos, LocksSoundEvents.LOCK_OPEN.get(), SoundSource.BLOCKS, 1f, 1f);
						if (!world.isClientSide)
							for (Lockable l : intersect)
								if (LocksUtil.ownsAwareness(l, player) && l.lock.isLocked())
								{
									// Explicit false, never a toggle: toggling per lockable would LOCK an
									// already-open lock of the player's own that overlaps this block, which is
									// its own endless flip-flop.
									LocksUtil.setLocked(world, l, false, player);
									LocksUtil.resolveLootTables(world, l, player);
								}
						awarenessHandled = true;
					}
					else if (action == PassiveLockPolicy.Action.PASS_THROUGH)
					{
						// The lock is deliberately left alone. resolveLootTables is idempotent and keeps a
						// generated loot chest unpacking on the owner's first access, as it did before.
						if (!world.isClientSide)
							for (Lockable l : intersect)
								if (LocksUtil.ownsAwareness(l, player))
									LocksUtil.resolveLootTables(world, l, player);
						awarenessHandled = true;
						yieldToBlock = true;
					}
					// NONE falls through: somebody else's lock is still shut here, and it is that lock
					// doing the denying.
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
							// readId, not getOrSetId: an empty slot would otherwise get a random id written into
							// ItemStack.EMPTY, whose tag is a shared singleton.
							OptionalInt slotId = LockingItem.readId(curioInv.getStackInSlot(a));
							if(slotId.isEmpty())
								continue;
							int id = slotId.getAsInt();
							boolean matched = false;
							for (Lockable l : intersect)
							{
								if (l.lock.id == id)
								{
									matched = true;
									if (!world.isClientSide)
									{
										boolean wasLocked = l.lock.isLocked();
										LocksUtil.setLocked(world, l, !wasLocked, player);
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
					else if(stack.isEmpty() && LocksServerConfig.ALLOW_ITEMLESS_LOCK_PICKING.get())
					{
						// Last resort, after every authorization path above: an empty main hand plays the
						// normal pin minigame with no item when the server allows it. A matching key, held
						// ring, Curios ring or Awareness lock still wins, so a player who can simply open the
						// lock is never forced into the minigame just because her hand is empty.
						//
						// Only an empty hand qualifies. Treating an arbitrary held item as a virtual pick
						// would hijack interactions with food, tools, blocks and other mods' items.
						//
						// This cannot shadow the sneak + empty-hand lock removal further down: that path is
						// only reachable when every lockable here is already unlocked, and this branch only
						// runs while one is locked.
						if(!world.isClientSide)
							openPicking(player, lkb, LockPickingMode.ITEMLESS);
					}
					else
					{
						// No matching item anywhere: rattle (unless Silent enchantment)
						lkb.swing(20);
						// Optional theft punishment: shock the player for interacting with a locked block without a key (off by default)
						ShockingHelper.tryShock(player, lkb.stack, Vec3.atCenterOf(pos), ShockingHelper.Trigger.UNAUTHORIZED_INTERACTION);
						boolean keyBlank = stack.getItem() == LocksItems.KEY_BLANK.get();
						if(!LocksServerConfig.ENABLE_SILENT.get() || EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.SILENT.get(), lkb.stack) == 0)
						{
							world.playSound(player, pos, LocksSoundEvents.LOCK_RATTLE.get(), SoundSource.BLOCKS, 1f, 1f);
							if(world.isClientSide && !keyBlank && LocksClientConfig.DEAF_MODE.get())
								player.displayClientMessage(LOCKED_MESSAGE, true);
						}
						// A blank cannot copy a lock that is already placed — that would let any visitor cut a
						// matching key and defeat the point of the mod. Explain the pre-placement workflow
						// instead of the generic locked message. Nothing is read from or written to either the
						// lock or the stack. Instructional rather than an accessibility fallback, so unlike the
						// deaf-mode hint it always shows; one click fires the event once per side, so there is
						// nothing to rate-limit.
						if(world.isClientSide && keyBlank)
							player.displayClientMessage(KEY_BLANK_PAIRING_MESSAGE, true);
					}
				}
			}

			// The owner cannot stop presenting an Awareness credential, so this click must not be spent
			// purely on the lock or the block could never be opened at all. Hand its own interaction back
			// to vanilla: DEFAULT rather than ALLOW, so a sneak-place still behaves normally, and useItem
			// stays DENY so the click opens the chest or door but can never place or consume what is held.
			// No swing — vanilla swings from the InteractionResult. Returns before the lock-removal branch
			// so one click cannot both open the chest and pry the lock off it.
			if(yieldToBlock)
			{
				e.setUseBlock(Event.Result.DEFAULT);
				return;
			}

			player.swing(InteractionHand.MAIN_HAND);
			e.setCancellationResult(InteractionResult.SUCCESS);
			e.setCanceled(true);
			return;
		}
		else
		{
			// All lockables at this position are unlocked — handle re-locking
			boolean relocked = false;
			// Passive credentials stand aside for the lock-removal gesture below instead of swallowing it.
			boolean removalGesture = PassiveLockPolicy.isRemovalGesture(
				LocksServerConfig.ALLOW_REMOVING_LOCKS.get(), player.isShiftKeyDown(), stack.isEmpty());

			if(stack.getItem() == LocksItems.MASTER_KEY.get())
			{
				e.setUseBlock(Event.Result.DENY);
				e.setUseItem(Event.Result.DENY);
				world.playSound(player, pos, LocksSoundEvents.LOCK_CLOSE.get(), SoundSource.BLOCKS, 1f, 1f);
				if(!world.isClientSide)
					for(Lockable l : intersect)
						LocksUtil.setLocked(world, l, true, player);
				relocked = true;
			}
			else if(LocksTagHelper.isKey(stack))
			{
				int id = LockingItem.getOrSetId(stack);
				boolean hasMatch = false;
				for(Lockable l : intersect)
					if(l.lock.id == id) { hasMatch = true; break; }
				if(hasMatch)
				{
					e.setUseBlock(Event.Result.DENY);
					e.setUseItem(Event.Result.DENY);
					world.playSound(player, pos, LocksSoundEvents.LOCK_CLOSE.get(), SoundSource.BLOCKS, 1f, 1f);
					if(!world.isClientSide)
						for(Lockable l : intersect)
							if(l.lock.id == id)
								LocksUtil.setLocked(world, l, true, player);
					relocked = true;
				}
			}
			else if(stack.getItem() == LocksItems.KEY_RING.get())
			{
				IItemHandler inv = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
				if(inv != null)
				{
					for(int a = 0; a < inv.getSlots(); ++a)
					{
						// readId, not getOrSetId: an empty slot would otherwise get a random id written into
					// ItemStack.EMPTY, whose tag is a shared singleton.
					OptionalInt slotId = LockingItem.readId(inv.getStackInSlot(a));
					if(slotId.isEmpty())
						continue;
					int id = slotId.getAsInt();
						boolean matched = false;
						for(Lockable l : intersect)
						{
							if(l.lock.id == id)
							{
								matched = true;
								if(!world.isClientSide)
									LocksUtil.setLocked(world, l, true, player);
							}
						}
						if(matched)
						{
							e.setUseBlock(Event.Result.DENY);
							e.setUseItem(Event.Result.DENY);
							world.playSound(player, pos, LocksSoundEvents.LOCK_CLOSE.get(), SoundSource.BLOCKS, 1f, 1f);
							relocked = true;
							break;
						}
					}
				}
			}

			// No Awareness re-lock branch here on purpose. An owner's plain click already passes through
			// to the block, and sneaking with an empty hand belongs to lock removal below — claiming it
			// here is what made an owner's own lock impossible to take off. Re-lock by picking the lock
			// up and placing it again; a placed lock is locked.

			if(!relocked && !removalGesture)
			{
				for(Lockable candidate : intersect)
				{
					ItemStack curioRing = CuriosHelper.findMatchingKeyRing(player, candidate.lock.id);
					if(!curioRing.isEmpty())
					{
						IItemHandler curioInv = curioRing.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
						if(curioInv != null)
						{
							for(int a = 0; a < curioInv.getSlots(); ++a)
							{
								// readId, not getOrSetId: an empty slot would otherwise get a random id written into
							// ItemStack.EMPTY, whose tag is a shared singleton.
							OptionalInt slotId = LockingItem.readId(curioInv.getStackInSlot(a));
							if(slotId.isEmpty())
								continue;
							int id = slotId.getAsInt();
								boolean matched = false;
								for(Lockable l : intersect)
								{
									if(l.lock.id == id)
									{
										matched = true;
										if(!world.isClientSide)
											LocksUtil.setLocked(world, l, true, player);
									}
								}
								if(matched)
								{
									e.setUseBlock(Event.Result.DENY);
									e.setUseItem(Event.Result.DENY);
									world.playSound(player, pos, LocksSoundEvents.LOCK_CLOSE.get(), SoundSource.BLOCKS, 1f, 1f);
									relocked = true;
									break;
								}
							}
						}
						if(relocked) break;
					}
				}
			}

			if(relocked)
			{
				player.swing(InteractionHand.MAIN_HAND);
				e.setCancellationResult(InteractionResult.SUCCESS);
				e.setCanceled(true);
				return;
			}
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

	// lockedAgainst, not locked: an Awareness lock does not apply to its owner, and since it now never
	// unlocks through ordinary use they would otherwise never be able to break their own block.
	public static boolean canBreakLockable(Player player, BlockPos pos)
	{
		return !LocksServerConfig.PROTECT_LOCKABLES.get() || player.isCreative() || !LocksUtil.lockedAgainst(player.level(), pos, player);
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
		// Carry On posts a BreakEvent as its pickup gate. When our Carry On compat has already authorized
		// carrying this locked block, let it through instead of vetoing the pickup as an illegal break.
		if(CarryOnCompat.isAuthorizedPickup(e.getPos()))
			return;
		if(canBreakLockable(e.getPlayer(), e.getPos()))
			return;
		e.setCanceled(true);
		// Optional theft punishment: shock the player for trying to break a protected locked block (off by default).
		// canBreakLockable already exempts creative players, so this only fires in survival.
		if(ShockingHelper.isTriggerEnabled(ShockingHelper.Trigger.BLOCK_BREAK_ATTEMPT))
		{
			Lockable lkb = findLockedAt(e.getPlayer().level(), e.getPos());
			if(lkb != null)
				ShockingHelper.tryShock(e.getPlayer(), lkb.stack, Vec3.atCenterOf(e.getPos()), ShockingHelper.Trigger.BLOCK_BREAK_ATTEMPT);
		}
	}

	private static Lockable findLockedAt(Level world, BlockPos pos)
	{
		ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return null;
		Int2ObjectMap<Lockable> chunkLockables = handler.getInChunk(pos);
		if(chunkLockables == null)
			return null;
		for(Lockable lkb : chunkLockables.values())
			if(lkb.lock.isLocked() && lkb.bb.intersects(pos))
				return lkb;
		return null;
	}
}
