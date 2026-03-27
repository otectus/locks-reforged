package melonslise.locks.common.init;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import melonslise.locks.Locks;
import melonslise.locks.common.item.KeyItem;
import melonslise.locks.common.item.KeyRingItem;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.LockPickItem;
import melonslise.locks.common.item.MasterKeyItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksItems
{
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Locks.ID);

	// Non-data-driven items (hardcoded)
	public static final RegistryObject<Item>
		SPRING = ITEMS.register("spring", () -> new Item(new Item.Properties())),
		WOOD_LOCK_MECHANISM = ITEMS.register("wood_lock_mechanism", () -> new Item(new Item.Properties())),
		IRON_LOCK_MECHANISM = ITEMS.register("iron_lock_mechanism", () -> new Item(new Item.Properties())),
		STEEL_LOCK_MECHANISM = ITEMS.register("steel_lock_mechanism", () -> new Item(new Item.Properties())),
		COPPER_LOCK_MECHANISM = ITEMS.register("copper_lock_mechanism", () -> new Item(new Item.Properties())),
		KEY_BLANK = ITEMS.register("key_blank", () -> new Item(new Item.Properties())),
		KEY = ITEMS.register("key", () -> new KeyItem(new Item.Properties())),
		MASTER_KEY = ITEMS.register("master_key", () -> new MasterKeyItem(new Item.Properties())),
		KEY_RING = ITEMS.register("key_ring", () -> new KeyRingItem(1, new Item.Properties()));

	// Data-driven lock and lockpick items
	private static final Map<ResourceLocation, RegistryObject<Item>> LOCK_ITEMS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, RegistryObject<Item>> LOCKPICK_ITEMS = new LinkedHashMap<>();

	// Backward-compatible static fields (populated after loadDefinitions + register calls)
	public static RegistryObject<Item> WOOD_LOCK, COPPER_LOCK, IRON_LOCK, STEEL_LOCK, GOLD_LOCK, DIAMOND_LOCK, NETHERITE_LOCK;
	public static RegistryObject<Item> WOOD_LOCK_PICK, COPPER_LOCK_PICK, IRON_LOCK_PICK, STEEL_LOCK_PICK, GOLD_LOCK_PICK, DIAMOND_LOCK_PICK, NETHERITE_LOCK_PICK;

	private LocksItems() {}

	public static void register()
	{
		// Load JSON definitions before registering items
		LockTypeRegistry.loadDefinitions();

		// Register data-driven locks
		for (var entry : LockTypeRegistry.allLockDefinitions().entrySet())
		{
			boolean fireRes = entry.getValue().fireResistant();
			RegistryObject<Item> obj = ITEMS.register(entry.getKey().getPath(), () -> new LockItem(fireRes ? new Item.Properties().fireResistant() : new Item.Properties()));
			LOCK_ITEMS.put(entry.getKey(), obj);
		}

		// Register data-driven lockpicks
		for (var entry : LockTypeRegistry.allLockPickDefinitions().entrySet())
		{
			boolean fireRes = entry.getValue().fireResistant();
			Item.Properties properties = fireRes ? new Item.Properties().fireResistant() : new Item.Properties();
			if (LockPickItem.NETHERITE_LOCK_PICK_ID.equals(entry.getKey()))
				properties = properties.durability(LockPickItem.NETHERITE_DURABILITY);
			Item.Properties finalProperties = properties;
			RegistryObject<Item> obj = ITEMS.register(entry.getKey().getPath(), () -> new LockPickItem(finalProperties));
			LOCKPICK_ITEMS.put(entry.getKey(), obj);
		}

		// Populate backward-compatible static fields
		WOOD_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "wood_lock"));
		COPPER_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "copper_lock"));
		IRON_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "iron_lock"));
		STEEL_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "steel_lock"));
		GOLD_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "gold_lock"));
		DIAMOND_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "diamond_lock"));
		NETHERITE_LOCK = LOCK_ITEMS.get(new ResourceLocation(Locks.ID, "netherite_lock"));

		WOOD_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "wood_lock_pick"));
		COPPER_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "copper_lock_pick"));
		IRON_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "iron_lock_pick"));
		STEEL_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "steel_lock_pick"));
		GOLD_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "gold_lock_pick"));
		DIAMOND_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "diamond_lock_pick"));
		NETHERITE_LOCK_PICK = LOCKPICK_ITEMS.get(new ResourceLocation(Locks.ID, "netherite_lock_pick"));

		ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
	}

	public static Map<ResourceLocation, RegistryObject<Item>> getLockItems()
	{
		return Collections.unmodifiableMap(LOCK_ITEMS);
	}

	public static Map<ResourceLocation, RegistryObject<Item>> getLockPickItems()
	{
		return Collections.unmodifiableMap(LOCKPICK_ITEMS);
	}
}
