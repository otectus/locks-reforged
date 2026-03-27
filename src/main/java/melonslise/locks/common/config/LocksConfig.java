package melonslise.locks.common.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.google.common.collect.Lists;

import melonslise.locks.Locks;
import melonslise.locks.common.init.LockTypeRegistry;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksConfig
{
	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.DoubleValue GENERATION_CHANCE;
	public static final ForgeConfigSpec.DoubleValue GENERATION_ENCHANT_CHANCE;
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GENERATED_LOCKS;
	public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> GENERATED_LOCK_WEIGHTS;
	public static final ForgeConfigSpec.BooleanValue RANDOMIZE_LOADED_LOCKS;

	// Lock stat overrides (sentinel -1 = use JSON default)
	public static final ForgeConfigSpec.IntValue WOOD_LOCK_LENGTH, WOOD_LOCK_ENCHANT, WOOD_LOCK_RESISTANCE;
	public static final ForgeConfigSpec.IntValue COPPER_LOCK_LENGTH, COPPER_LOCK_ENCHANT, COPPER_LOCK_RESISTANCE;
	public static final ForgeConfigSpec.IntValue IRON_LOCK_LENGTH, IRON_LOCK_ENCHANT, IRON_LOCK_RESISTANCE;
	public static final ForgeConfigSpec.IntValue STEEL_LOCK_LENGTH, STEEL_LOCK_ENCHANT, STEEL_LOCK_RESISTANCE;
	public static final ForgeConfigSpec.IntValue GOLD_LOCK_LENGTH, GOLD_LOCK_ENCHANT, GOLD_LOCK_RESISTANCE;
	public static final ForgeConfigSpec.IntValue DIAMOND_LOCK_LENGTH, DIAMOND_LOCK_ENCHANT, DIAMOND_LOCK_RESISTANCE;
	public static final ForgeConfigSpec.IntValue NETHERITE_LOCK_LENGTH, NETHERITE_LOCK_ENCHANT, NETHERITE_LOCK_RESISTANCE;

	// Lockpick stat overrides (sentinel -1.0 = use JSON default)
	public static final ForgeConfigSpec.DoubleValue WOOD_PICK_STRENGTH, COPPER_PICK_STRENGTH, IRON_PICK_STRENGTH, STEEL_PICK_STRENGTH;
	public static final ForgeConfigSpec.DoubleValue GOLD_PICK_STRENGTH, DIAMOND_PICK_STRENGTH, NETHERITE_PICK_STRENGTH;

	// Loot-scaled lock generation
	public static final ForgeConfigSpec.BooleanValue LOOT_SCALED_LOCKS;
	public static final ForgeConfigSpec.DoubleValue DEFAULT_ITEM_VALUE;
	public static final ForgeConfigSpec.DoubleValue ENCHANT_VALUE_BONUS;
	public static final ForgeConfigSpec.DoubleValue RARITY_COMMON_MULT;
	public static final ForgeConfigSpec.DoubleValue RARITY_UNCOMMON_MULT;
	public static final ForgeConfigSpec.DoubleValue RARITY_RARE_MULT;
	public static final ForgeConfigSpec.DoubleValue RARITY_EPIC_MULT;
	public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> LOOT_VALUE_TIERS;
	public static final ForgeConfigSpec.IntValue LOOT_VALUE_SAMPLES;
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_VALUE_OVERRIDES;

	public static NavigableMap<Integer, Item> weightedGeneratedLocks;
	public static int weightTotal;
	public static NavigableMap<Double, Item> lootValueTierMap;
	public static Map<ResourceLocation, Double> itemValueOverrides;

	static
	{
		ForgeConfigSpec.Builder cfg = new ForgeConfigSpec.Builder();

		GENERATION_CHANCE = cfg
			.comment("Chance to generate a random lock on every new chest during world generation. Set to 0 to disable")
			.defineInRange("Generation Chance", 0.85d, 0d, 1d);
		GENERATION_ENCHANT_CHANCE = cfg
			.comment("Chance to randomly enchant a generated lock during world generation. Set to 0 to disable")
			.defineInRange("Generation Enchant Chance", 0.4d, 0d, 1d);
		GENERATED_LOCKS = cfg
			.comment("Items that can be generated as locks (must be instance of LockItem in code!)")
			.defineList("Generated Locks", Lists.newArrayList("locks:wood_lock", "locks:copper_lock", "locks:iron_lock", "locks:steel_lock", "locks:gold_lock", "locks:diamond_lock", "locks:netherite_lock"), e -> e instanceof String);
		GENERATED_LOCK_WEIGHTS= cfg
			.comment("WARNING: THE AMOUNT OF NUMBERS SHOULD BE EQUAL TO THE AMOUNT OF GENERATED LOCK ITEMS!!!", "The relative probability that the corresponding lock item will be generated on a chest. Higher number = higher chance to generate")
			.defineList("Generated Lock Chances", Lists.newArrayList(3, 3, 3, 2, 2, 1, 1), e -> e instanceof Integer);
		RANDOMIZE_LOADED_LOCKS = cfg
			.comment("Randomize lock IDs and combinations when loading them from a structure file. Randomization works just like during world generation")
			.define("Randomize Loaded Locks", false);

		// Lock Stats section
		cfg.comment("Override built-in lock stats without datapacks. Set any value to -1 to use the JSON default.").push("Lock Stats");

		cfg.push("Wood Lock");
		WOOD_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 5. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		WOOD_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 15.").defineInRange("Enchantment Value", -1, -1, 50);
		WOOD_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 4.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.push("Copper Lock");
		COPPER_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 6. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		COPPER_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 16.").defineInRange("Enchantment Value", -1, -1, 50);
		COPPER_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 8.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.push("Iron Lock");
		IRON_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 7. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		IRON_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 14.").defineInRange("Enchantment Value", -1, -1, 50);
		IRON_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 12.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.push("Steel Lock");
		STEEL_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 9. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		STEEL_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 12.").defineInRange("Enchantment Value", -1, -1, 50);
		STEEL_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 20.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.push("Gold Lock");
		GOLD_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 6. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		GOLD_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 22.").defineInRange("Enchantment Value", -1, -1, 50);
		GOLD_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 6.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.push("Diamond Lock");
		DIAMOND_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 11. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		DIAMOND_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 10.").defineInRange("Enchantment Value", -1, -1, 50);
		DIAMOND_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 100.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.push("Netherite Lock");
		NETHERITE_LOCK_LENGTH = cfg.comment("Number of pins (1-20). JSON default: 14. Set to -1 to use default.").defineInRange("Length", -1, -1, 20);
		NETHERITE_LOCK_ENCHANT = cfg.comment("Enchantability (1-50). JSON default: 8.").defineInRange("Enchantment Value", -1, -1, 50);
		NETHERITE_LOCK_RESISTANCE = cfg.comment("Explosion resistance (0-1000). JSON default: 200.").defineInRange("Resistance", -1, -1, 1000);
		cfg.pop();

		cfg.pop(); // Lock Stats

		// Lockpick Stats section
		cfg.comment("Override built-in lockpick stats without datapacks. Set any value to -1.0 to use the JSON default.").push("Lockpick Stats");
		WOOD_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.2. Set to -1.0 to use default.").defineInRange("Wood Lockpick Strength", -1.0, -1.0, 10.0);
		COPPER_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.28.").defineInRange("Copper Lockpick Strength", -1.0, -1.0, 10.0);
		IRON_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.35.").defineInRange("Iron Lockpick Strength", -1.0, -1.0, 10.0);
		STEEL_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.7.").defineInRange("Steel Lockpick Strength", -1.0, -1.0, 10.0);
		GOLD_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.25.").defineInRange("Gold Lockpick Strength", -1.0, -1.0, 10.0);
		DIAMOND_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.85.").defineInRange("Diamond Lockpick Strength", -1.0, -1.0, 10.0);
		NETHERITE_PICK_STRENGTH = cfg.comment("Strength (0.01-10.0). JSON default: 0.95.").defineInRange("Netherite Lockpick Strength", -1.0, -1.0, 10.0);
		cfg.pop(); // Lockpick Stats

		// Loot-Scaled Locks section
		cfg.comment("When enabled, lock tier is determined by the value of a chest's loot table contents instead of random weighted selection. Chests below the minimum value threshold get no lock at all.").push("Loot-Scaled Locks");
		LOOT_SCALED_LOCKS = cfg
			.comment("Enable loot-value-based lock tier selection. When disabled, the random weighted system (Generation Chance + Generated Lock Chances) is used instead")
			.define("Enable Loot-Scaled Locks", true);
		DEFAULT_ITEM_VALUE = cfg
			.comment("Base value assigned to each item before multipliers")
			.defineInRange("Default Item Value", 1.0d, 0.01d, 1000d);
		ENCHANT_VALUE_BONUS = cfg
			.comment("Value multiplier added per enchantment level on an item (e.g. 0.25 means Sharpness V adds 1.25x)")
			.defineInRange("Enchantment Value Bonus", 0.25d, 0d, 10d);
		RARITY_COMMON_MULT = cfg
			.comment("Value multiplier for COMMON rarity items")
			.defineInRange("Common Rarity Multiplier", 1.0d, 0.01d, 100d);
		RARITY_UNCOMMON_MULT = cfg
			.comment("Value multiplier for UNCOMMON rarity items")
			.defineInRange("Uncommon Rarity Multiplier", 1.5d, 0.01d, 100d);
		RARITY_RARE_MULT = cfg
			.comment("Value multiplier for RARE rarity items")
			.defineInRange("Rare Rarity Multiplier", 2.5d, 0.01d, 100d);
		RARITY_EPIC_MULT = cfg
			.comment("Value multiplier for EPIC rarity items")
			.defineInRange("Epic Rarity Multiplier", 5.0d, 0.01d, 100d);
		LOOT_VALUE_TIERS = cfg
			.comment("Minimum loot value thresholds for each lock in the 'Generated Locks' list.",
				"WARNING: THE AMOUNT OF NUMBERS SHOULD BE EQUAL TO THE AMOUNT OF GENERATED LOCK ITEMS!!!",
				"A chest's total loot value is compared against these thresholds to select the lock tier.",
				"If the value is below the lowest threshold, no lock is generated.",
				"Defaults: wood=3, copper=6, iron=10, steel=16, gold=24, diamond=40, netherite=60")
			.defineList("Loot Value Tiers", Lists.newArrayList(3.0, 6.0, 10.0, 16.0, 24.0, 40.0, 60.0), e -> e instanceof Double);
		LOOT_VALUE_SAMPLES = cfg
			.comment("Number of times each loot table is sampled to compute its average value.",
				"Higher values produce more consistent tier assignments across server restarts but slightly increase startup time.")
			.defineInRange("Loot Value Samples", 32, 1, 256);
		ITEM_VALUE_OVERRIDES = cfg
			.comment("Override the base value for specific items. Format: 'modid:item_name=value'.",
				"Items not listed here use the Default Item Value.",
				"Useful for materials like diamonds or emeralds that have COMMON rarity but are inherently valuable.")
			.defineList("Item Value Overrides", Lists.newArrayList(
				"minecraft:diamond=5.0",
				"minecraft:emerald=4.0",
				"minecraft:netherite_ingot=10.0",
				"minecraft:netherite_scrap=6.0",
				"minecraft:enchanted_golden_apple=15.0",
				"minecraft:totem_of_undying=12.0",
				"minecraft:elytra=20.0",
				"minecraft:heart_of_the_sea=8.0",
				"minecraft:golden_apple=3.0",
				"minecraft:ender_pearl=2.0",
				"minecraft:experience_bottle=2.0",
				"minecraft:saddle=3.0",
				"minecraft:name_tag=3.0",
				"minecraft:iron_ingot=2.0",
				"minecraft:gold_ingot=3.0"
			), e -> e instanceof String && ((String) e).contains("="));
		cfg.pop(); // Loot-Scaled Locks

		SPEC = cfg.build();
	}

	private LocksConfig() {}

	// https://gist.github.com/raws/1667807
	public static void init()
	{
		weightedGeneratedLocks = new TreeMap<>();
		weightTotal = 0;
		List<? extends String> locks = GENERATED_LOCKS.get();
		List<? extends Integer> weights = GENERATED_LOCK_WEIGHTS.get();
		if (weights.size() != locks.size())
			Locks.LOGGER.warn("Generated Lock Chances list size ({}) doesn't match Generated Locks list size ({}). Some locks may not appear.", weights.size(), locks.size());
		for(int a = 0; a < Math.min(locks.size(), weights.size()); ++a)
		{
			weightTotal += weights.get(a);
			weightedGeneratedLocks.put(weightTotal, ForgeRegistries.ITEMS.getValue(new ResourceLocation(locks.get(a))));
		}

		// Build loot value tier map
		lootValueTierMap = new TreeMap<>();
		List<? extends Double> tiers = LOOT_VALUE_TIERS.get();
		if (tiers.size() != locks.size())
			Locks.LOGGER.warn("Loot Value Tiers list size ({}) doesn't match Generated Locks list size ({}). Some lock tiers may be unreachable.", tiers.size(), locks.size());
		for(int a = 0; a < Math.min(locks.size(), tiers.size()); ++a)
			lootValueTierMap.put(tiers.get(a), ForgeRegistries.ITEMS.getValue(new ResourceLocation(locks.get(a))));

		// Parse item value overrides
		itemValueOverrides = new HashMap<>();
		for (String entry : ITEM_VALUE_OVERRIDES.get())
		{
			int eq = entry.indexOf('=');
			if (eq <= 0 || eq >= entry.length() - 1)
			{
				Locks.LOGGER.warn("Invalid item value override entry: '{}'. Expected format: 'modid:item=value'", entry);
				continue;
			}
			try
			{
				ResourceLocation itemId = new ResourceLocation(entry.substring(0, eq));
				double value = Double.parseDouble(entry.substring(eq + 1));
				itemValueOverrides.put(itemId, value);
			}
			catch (Exception e)
			{
				Locks.LOGGER.warn("Failed to parse item value override '{}': {}", entry, e.getMessage());
			}
		}
	}

	public static boolean canGen(RandomSource rng)
	{
		return LocksUtil.chance(rng, GENERATION_CHANCE.get());
	}

	public static boolean canEnchant(RandomSource rng)
	{
		return LocksUtil.chance(rng, GENERATION_ENCHANT_CHANCE.get());
	}

	/**
	 * Returns a lock ItemStack based on the total loot value, or null if the value is below all thresholds.
	 */
	public static ItemStack getLockForLootValue(double lootValue, RandomSource rng)
	{
		var entry = lootValueTierMap.floorEntry(lootValue);
		if (entry == null)
			return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(entry.getValue());
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		if (!canEnchant(rng))
			return stack;
		stack = EnchantmentHelper.enchantItem(rng, stack, 5 + rng.nextInt(30), false);
		var enchantments = EnchantmentHelper.getEnchantments(stack);
		if (enchantments.entrySet().removeIf(e -> !LocksServerConfig.isEnchantmentEnabled(e.getKey())))
			EnchantmentHelper.setEnchantments(enchantments, stack);
		return stack;
	}

	public static ItemStack getRandomLock(RandomSource rng)
	{
		if (weightTotal <= 0 || weightedGeneratedLocks.isEmpty())
			return ItemStack.EMPTY;
		var entry = weightedGeneratedLocks.ceilingEntry(rng.nextInt(weightTotal) + 1);
		if (entry == null)
			return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(entry.getValue());
		if (!canEnchant(rng))
			return stack;
		stack = EnchantmentHelper.enchantItem(rng, stack, 5 + rng.nextInt(30), false);
		// Strip any disabled enchantments from the generated stack
		var enchantments = EnchantmentHelper.getEnchantments(stack);
		if (enchantments.entrySet().removeIf(e -> !LocksServerConfig.isEnchantmentEnabled(e.getKey())))
			EnchantmentHelper.setEnchantments(enchantments, stack);
		return stack;
	}

	public static void applyConfigStatOverrides()
	{
		if (!SPEC.isLoaded())
			return;

		applyLockConfigOverride("wood_lock", WOOD_LOCK_LENGTH, WOOD_LOCK_ENCHANT, WOOD_LOCK_RESISTANCE);
		applyLockConfigOverride("copper_lock", COPPER_LOCK_LENGTH, COPPER_LOCK_ENCHANT, COPPER_LOCK_RESISTANCE);
		applyLockConfigOverride("iron_lock", IRON_LOCK_LENGTH, IRON_LOCK_ENCHANT, IRON_LOCK_RESISTANCE);
		applyLockConfigOverride("steel_lock", STEEL_LOCK_LENGTH, STEEL_LOCK_ENCHANT, STEEL_LOCK_RESISTANCE);
		applyLockConfigOverride("gold_lock", GOLD_LOCK_LENGTH, GOLD_LOCK_ENCHANT, GOLD_LOCK_RESISTANCE);
		applyLockConfigOverride("diamond_lock", DIAMOND_LOCK_LENGTH, DIAMOND_LOCK_ENCHANT, DIAMOND_LOCK_RESISTANCE);
		applyLockConfigOverride("netherite_lock", NETHERITE_LOCK_LENGTH, NETHERITE_LOCK_ENCHANT, NETHERITE_LOCK_RESISTANCE);

		applyPickConfigOverride("wood_lock_pick", WOOD_PICK_STRENGTH);
		applyPickConfigOverride("copper_lock_pick", COPPER_PICK_STRENGTH);
		applyPickConfigOverride("iron_lock_pick", IRON_PICK_STRENGTH);
		applyPickConfigOverride("steel_lock_pick", STEEL_PICK_STRENGTH);
		applyPickConfigOverride("gold_lock_pick", GOLD_PICK_STRENGTH);
		applyPickConfigOverride("diamond_lock_pick", DIAMOND_PICK_STRENGTH);
		applyPickConfigOverride("netherite_lock_pick", NETHERITE_PICK_STRENGTH);
	}

	private static void applyLockConfigOverride(String name, ForgeConfigSpec.IntValue length, ForgeConfigSpec.IntValue enchant, ForgeConfigSpec.IntValue resistance)
	{
		int l = length.get();
		int e = enchant.get();
		int r = resistance.get();
		if (l >= 0 || e >= 0 || r >= 0)
		{
			ResourceLocation id = new ResourceLocation(Locks.ID, name);
			LockTypeRegistry.applyConfigLockOverride(id, l, e, r);
		}
	}

	private static void applyPickConfigOverride(String name, ForgeConfigSpec.DoubleValue strength)
	{
		double s = strength.get();
		if (s >= 0)
		{
			ResourceLocation id = new ResourceLocation(Locks.ID, name);
			LockTypeRegistry.applyConfigPickOverride(id, (float) s);
		}
	}
}
