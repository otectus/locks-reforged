package melonslise.locks.common.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraftforge.registries.ForgeRegistries;

public final class LootValueCalculator
{
	private static final Gson GSON = new Gson();
	private static final Map<ResourceLocation, Double> cache = new HashMap<>();

	private LootValueCalculator() {}

	public static void precomputeAll(MinecraftServer server)
	{
		cache.clear();
		int computed = 0;
		for (ResourceLocation id : server.getLootData().getKeys(LootDataType.TABLE))
		{
			if (!id.getPath().startsWith("chests/"))
				continue;
			Double lootValue = computeLootValue(server, id, new HashSet<>());
			if (lootValue == null)
				continue;
			cache.put(id, lootValue);
			computed++;
		}
		Locks.LOGGER.info("Pre-computed loot value estimates for {} chest loot tables", computed);
	}

	public static double getCachedLootValue(ResourceLocation lootTableId)
	{
		return cache.getOrDefault(lootTableId, 0.0);
	}

	public static double getLootValue(MinecraftServer server, ResourceLocation lootTableId)
	{
		Double cached = cache.get(lootTableId);
		if (cached != null)
			return cached;
		Double computed = computeLootValue(server, lootTableId, new HashSet<>());
		if (computed != null)
		{
			cache.put(lootTableId, computed);
			return computed;
		}
		return Double.NaN;
	}

	public static void clearCache()
	{
		cache.clear();
	}

	private static Double computeLootValue(MinecraftServer server, ResourceLocation lootTableId, Set<ResourceLocation> visiting)
	{
		Double cached = cache.get(lootTableId);
		if (cached != null)
			return cached;
		if (!visiting.add(lootTableId))
		{
			Locks.LOGGER.debug("Detected recursive loot table reference while estimating {}", lootTableId);
			return null;
		}

		ResourceLocation resourceId = new ResourceLocation(lootTableId.getNamespace(), "loot_tables/" + lootTableId.getPath() + ".json");
		try
		{
			Resource resource = server.getResourceManager().getResource(resourceId).orElse(null);
			if (resource == null)
				return null;
			try (var reader = resource.openAsReader())
			{
				JsonObject json = GsonHelper.fromJson(GSON, reader, JsonObject.class);
				if (json == null)
					return null;
				double estimated = estimateTableValue(server, json, visiting);
				cache.put(lootTableId, estimated);
				return estimated;
			}
		}
		catch (Exception e)
		{
			Locks.LOGGER.debug("Could not estimate loot value for {}: {}", lootTableId, e.getMessage());
			return null;
		}
		finally
		{
			visiting.remove(lootTableId);
		}
	}

	private static double estimateTableValue(MinecraftServer server, JsonObject tableJson, Set<ResourceLocation> visiting)
	{
		JsonArray pools = tableJson.getAsJsonArray("pools");
		if (pools == null || pools.isEmpty())
			return 0;

		double total = 0;
		for (JsonElement poolElement : pools)
			if (poolElement.isJsonObject())
				total += estimatePoolValue(server, poolElement.getAsJsonObject(), visiting);
		return total;
	}

	private static double estimatePoolValue(MinecraftServer server, JsonObject poolJson, Set<ResourceLocation> visiting)
	{
		JsonArray entries = poolJson.getAsJsonArray("entries");
		if (entries == null || entries.isEmpty())
			return 0;

		double rolls = getNumberProviderAverage(poolJson.get("rolls"), 1.0);
		rolls += getNumberProviderAverage(poolJson.get("bonus_rolls"), 0.0);
		if (rolls <= 0)
			return 0;

		double totalWeight = 0;
		double weightedValue = 0;
		for (JsonElement entryElement : entries)
		{
			if (!entryElement.isJsonObject())
				continue;
			JsonObject entryJson = entryElement.getAsJsonObject();
			double weight = Math.max(0, GsonHelper.getAsInt(entryJson, "weight", 1));
			double value = estimateEntryValue(server, entryJson, visiting);
			totalWeight += weight;
			weightedValue += weight * value;
		}

		if (totalWeight <= 0)
			return 0;
		return rolls * (weightedValue / totalWeight);
	}

	private static double estimateEntryValue(MinecraftServer server, JsonObject entryJson, Set<ResourceLocation> visiting)
	{
		String type = normalizeType(GsonHelper.getAsString(entryJson, "type", "minecraft:item"));
		return switch (type)
		{
			case "item" -> estimateItemEntryValue(entryJson);
			case "loot_table" -> estimateReferencedTableValue(server, entryJson, visiting);
			case "group", "sequence" -> estimateChildEntriesSum(server, entryJson.getAsJsonArray("children"), visiting);
			case "alternatives" -> estimateChildEntriesAverage(server, entryJson.getAsJsonArray("children"), visiting);
			case "tag" -> estimateTagEntryValue(server, entryJson);
			case "empty", "dynamic" -> 0.0;
			default -> estimateChildEntriesAverage(server, entryJson.getAsJsonArray("children"), visiting);
		};
	}

	private static double estimateItemEntryValue(JsonObject entryJson)
	{
		ResourceLocation itemId = ResourceLocation.tryParse(GsonHelper.getAsString(entryJson, "name", ""));
		if (itemId == null)
			return 0;

		Item item = ForgeRegistries.ITEMS.getValue(itemId);
		if (item == null)
			return 0;

		double count = 1.0;
		double enchantLevels = 0.0;
		JsonArray functions = entryJson.getAsJsonArray("functions");
		if (functions != null)
			for (JsonElement functionElement : functions)
			{
				if (!functionElement.isJsonObject())
					continue;
				JsonObject functionJson = functionElement.getAsJsonObject();
				String functionType = normalizeType(GsonHelper.getAsString(functionJson, "function", ""));
				switch (functionType)
				{
					case "set_count" -> {
						double value = getNumberProviderAverage(functionJson.get("count"), count);
						count = GsonHelper.getAsBoolean(functionJson, "add", false) ? count + value : value;
					}
					case "limit_count" -> count = clampCount(count, functionJson);
					case "set_enchantments" -> enchantLevels += estimateSetEnchantments(functionJson);
					case "enchant_with_levels" -> enchantLevels += getNumberProviderAverage(functionJson.get("levels"), 10.0) / 6.0;
					case "enchant_randomly" -> enchantLevels += 4.0;
					default -> {}
				}
			}

		return getItemValue(item, count, enchantLevels);
	}

	private static double estimateReferencedTableValue(MinecraftServer server, JsonObject entryJson, Set<ResourceLocation> visiting)
	{
		ResourceLocation tableId = ResourceLocation.tryParse(GsonHelper.getAsString(entryJson, "name", ""));
		if (tableId == null)
			return 0;

		Double referencedValue = computeLootValue(server, tableId, visiting);
		return referencedValue == null ? 0.0 : referencedValue;
	}

	private static double estimateTagEntryValue(MinecraftServer server, JsonObject entryJson)
	{
		ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(entryJson, "name", ""));
		if (tagId == null)
			return 0;

		var itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
		TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
		var tag = itemRegistry.getTag(tagKey).orElse(null);
		if (tag == null)
			return 0;

		double total = 0;
		int count = 0;
		for (Holder<Item> holder : tag)
		{
			total += getItemValue(holder.value(), 1.0, 0.0);
			count++;
		}
		if (count == 0)
			return 0;
		return total / count;
	}

	private static double estimateChildEntriesSum(MinecraftServer server, JsonArray children, Set<ResourceLocation> visiting)
	{
		if (children == null || children.isEmpty())
			return 0;

		double total = 0;
		for (JsonElement childElement : children)
			if (childElement.isJsonObject())
				total += estimateEntryValue(server, childElement.getAsJsonObject(), visiting);
		return total;
	}

	private static double estimateChildEntriesAverage(MinecraftServer server, JsonArray children, Set<ResourceLocation> visiting)
	{
		if (children == null || children.isEmpty())
			return 0;

		double total = 0;
		int count = 0;
		for (JsonElement childElement : children)
			if (childElement.isJsonObject())
			{
				total += estimateEntryValue(server, childElement.getAsJsonObject(), visiting);
				count++;
			}
		return count == 0 ? 0 : total / count;
	}

	private static double clampCount(double count, JsonObject functionJson)
	{
		JsonElement limitElement = functionJson.get("limit");
		JsonObject limitJson = limitElement != null && limitElement.isJsonObject() ? limitElement.getAsJsonObject() : functionJson;
		double min = getNumberProviderAverage(limitJson.get("min"), Double.NEGATIVE_INFINITY);
		double max = getNumberProviderAverage(limitJson.get("max"), Double.POSITIVE_INFINITY);
		return Math.max(min, Math.min(max, count));
	}

	private static double estimateSetEnchantments(JsonObject functionJson)
	{
		JsonElement enchantmentsElement = functionJson.get("enchantments");
		if (enchantmentsElement == null || enchantmentsElement.isJsonNull())
			return 0;

		double total = 0;
		if (enchantmentsElement.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> entry : enchantmentsElement.getAsJsonObject().entrySet())
				total += getNumberProviderAverage(entry.getValue(), 1.0);
		}
		else if (enchantmentsElement.isJsonArray())
		{
			for (JsonElement enchantmentElement : enchantmentsElement.getAsJsonArray())
				if (enchantmentElement.isJsonObject())
					total += getNumberProviderAverage(enchantmentElement.getAsJsonObject().get("level"), 1.0);
		}
		return total;
	}

	private static double getNumberProviderAverage(JsonElement element, double fallback)
	{
		if (element == null || element.isJsonNull())
			return fallback;
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
			return element.getAsDouble();
		if (!element.isJsonObject())
			return fallback;

		JsonObject json = element.getAsJsonObject();
		String type = normalizeType(GsonHelper.getAsString(json, "type", "minecraft:constant"));
		return switch (type)
		{
			case "constant" -> getNumberProviderAverage(json.get("value"), fallback);
			case "uniform" -> (getNumberProviderAverage(json.get("min"), fallback) + getNumberProviderAverage(json.get("max"), fallback)) / 2.0;
			case "binomial" -> getNumberProviderAverage(json.get("n"), 1.0) * getNumberProviderAverage(json.get("p"), 0.0);
			default -> fallback;
		};
	}

	private static String normalizeType(String type)
	{
		int separator = type.indexOf(':');
		return separator >= 0 ? type.substring(separator + 1) : type;
	}

	private static double getItemValue(Item item, double count, double enchantLevels)
	{
		if (item == null || count <= 0)
			return 0;

		ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
		double base = LocksConfig.itemValueOverrides != null && itemId != null
			? LocksConfig.itemValueOverrides.getOrDefault(itemId, LocksConfig.DEFAULT_ITEM_VALUE.get())
			: LocksConfig.DEFAULT_ITEM_VALUE.get();

		Rarity rarity = item.getDefaultInstance().getRarity();
		if (enchantLevels > 0)
			rarity = switch (rarity)
			{
				case COMMON, UNCOMMON -> Rarity.RARE;
				case RARE -> Rarity.EPIC;
				default -> rarity;
			};
		double rarityMult = switch (rarity)
		{
			case UNCOMMON -> LocksConfig.RARITY_UNCOMMON_MULT.get();
			case RARE -> LocksConfig.RARITY_RARE_MULT.get();
			case EPIC -> LocksConfig.RARITY_EPIC_MULT.get();
			default -> LocksConfig.RARITY_COMMON_MULT.get();
		};

		double enchMult = 1.0 + enchantLevels * LocksConfig.ENCHANT_VALUE_BONUS.get();
		return base * rarityMult * enchMult * Math.sqrt(count);
	}
}
