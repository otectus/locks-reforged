package melonslise.locks.common.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public final class LootValueCalculator
{
	private static final Map<ResourceLocation, Double> cache = new HashMap<>();

	private LootValueCalculator() {}

	public static void precomputeAll(MinecraftServer server)
	{
		cache.clear();
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null)
			return;

		int samples = LocksConfig.LOOT_VALUE_SAMPLES.get();
		for (ResourceLocation id : server.getLootData().getKeys(LootDataType.TABLE))
		{
			try
			{
				double total = 0;
				for (int i = 0; i < samples; i++)
					total += calculateLootValue(level, id, BlockPos.ZERO, 0);
				cache.put(id, total / samples);
			}
			catch (Exception e)
			{
				Locks.LOGGER.debug("Could not pre-compute loot value for {}: {}", id, e.getMessage());
			}
		}
		Locks.LOGGER.info("Pre-computed loot values for {} loot tables ({} samples each)", cache.size(), samples);
	}

	public static double getCachedLootValue(ResourceLocation lootTableId)
	{
		return cache.getOrDefault(lootTableId, 0.0);
	}

	public static void clearCache()
	{
		cache.clear();
	}

	public static double calculateLootValue(ServerLevel level, ResourceLocation lootTableId, BlockPos pos, long seed)
	{
		LootTable lootTable = level.getServer().getLootData().getLootTable(lootTableId);
		if (lootTable == null || lootTable == LootTable.EMPTY)
			return 0;

		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.create(LootContextParamSets.CHEST);

		List<ItemStack> items;
		if (seed != 0)
			items = lootTable.getRandomItems(params, seed);
		else
			items = lootTable.getRandomItems(params);

		double total = 0;
		for (ItemStack stack : items)
			total += getItemValue(stack);
		return total;
	}

	public static double getItemValue(ItemStack stack)
	{
		if (stack.isEmpty())
			return 0;

		// Check item value overrides first, fall back to default
		ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
		double base = LocksConfig.itemValueOverrides != null && itemId != null
			? LocksConfig.itemValueOverrides.getOrDefault(itemId, LocksConfig.DEFAULT_ITEM_VALUE.get())
			: LocksConfig.DEFAULT_ITEM_VALUE.get();

		// Rarity multiplier
		Rarity rarity = stack.getRarity();
		double rarityMult = switch (rarity)
		{
			case UNCOMMON -> LocksConfig.RARITY_UNCOMMON_MULT.get();
			case RARE -> LocksConfig.RARITY_RARE_MULT.get();
			case EPIC -> LocksConfig.RARITY_EPIC_MULT.get();
			default -> LocksConfig.RARITY_COMMON_MULT.get();
		};

		// Enchantment bonus
		int totalEnchLevels = EnchantmentHelper.getEnchantments(stack).values().stream()
			.mapToInt(Integer::intValue).sum();
		double enchMult = 1.0 + totalEnchLevels * LocksConfig.ENCHANT_VALUE_BONUS.get();

		// Sub-linear stack count scaling
		return base * rarityMult * enchMult * Math.sqrt(stack.getCount());
	}
}
