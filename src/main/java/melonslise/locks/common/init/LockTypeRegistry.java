package melonslise.locks.common.init;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.registries.ForgeRegistries;

public final class LockTypeRegistry
{
	public record LockStats(int length, int enchantmentValue, int resistance) {}
	public record LockPickStats(float strength) {}

	private static final Gson GSON = new GsonBuilder().create();

	private static final Map<ResourceLocation, LockStats> LOCK_DEFAULTS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, LockPickStats> LOCKPICK_DEFAULTS = new LinkedHashMap<>();

	private static Map<ResourceLocation, LockStats> LOCK_STATS = new LinkedHashMap<>();
	private static Map<ResourceLocation, LockPickStats> LOCKPICK_STATS = new LinkedHashMap<>();

	private static boolean definitionsLoaded = false;

	private LockTypeRegistry() {}

	public static boolean isLoaded()
	{
		return definitionsLoaded;
	}

	public static void loadDefinitions()
	{
		LOCK_DEFAULTS.clear();
		LOCKPICK_DEFAULTS.clear();

		// Load from mod JAR first
		loadJarDefinitions();

		// Load from config folder (can override JAR definitions)
		loadConfigDefinitions();

		// Initialize effective stats from defaults
		resetStats();

		definitionsLoaded = true;

		Locks.LOGGER.info("Loaded {} lock definitions and {} lockpick definitions",
			LOCK_DEFAULTS.size(), LOCKPICK_DEFAULTS.size());
	}

	private static void loadJarDefinitions()
	{
		var modFileInfo = ModList.get().getModFileById(Locks.ID);
		if (modFileInfo == null)
		{
			Locks.LOGGER.warn("Could not find mod file for {}, skipping JAR definitions", Locks.ID);
			return;
		}
		IModFile modFile = modFileInfo.getFile();

		loadJarDirectory(modFile, "data/" + Locks.ID + "/lock_types", true);
		loadJarDirectory(modFile, "data/" + Locks.ID + "/lockpick_types", false);
	}

	private static void loadJarDirectory(IModFile modFile, String dir, boolean isLock)
	{
		Path dirPath = modFile.findResource(dir);
		if (!Files.isDirectory(dirPath))
			return;

		try (Stream<Path> stream = Files.list(dirPath))
		{
			stream.filter(file -> file.getFileName().toString().endsWith(".json"))
				  .forEach(file -> {
					  String fileName = file.getFileName().toString();
					  String name = fileName.substring(0, fileName.length() - 5);
					  ResourceLocation id = new ResourceLocation(Locks.ID, name);
					  try (InputStream is = Files.newInputStream(file);
						   Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
					  {
						  JsonObject json = GsonHelper.fromJson(GSON, reader, JsonObject.class);
						  if (isLock)
							  parseLockDefinition(id, json);
						  else
							  parseLockPickDefinition(id, json);
					  }
					  catch (Exception e)
					  {
						  Locks.LOGGER.error("Failed to parse {} definition: {}",
							  isLock ? "lock" : "lockpick", id, e);
					  }
				  });
		}
		catch (IOException e)
		{
			Locks.LOGGER.error("Failed to read directory: {}", dir, e);
		}
	}

	private static void loadConfigDefinitions()
	{
		Path configDir = FMLPaths.CONFIGDIR.get().resolve(Locks.ID);
		Path lockDir = configDir.resolve("lock_types");
		Path pickDir = configDir.resolve("lockpick_types");

		// Create directories and example files on first launch
		createConfigDirectories(lockDir, pickDir);

		loadConfigDirectory(lockDir, true);
		loadConfigDirectory(pickDir, false);
	}

	private static void createConfigDirectories(Path lockDir, Path pickDir)
	{
		try
		{
			Files.createDirectories(lockDir);
			Files.createDirectories(pickDir);

			Path lockExample = lockDir.resolve("_example.json.disabled");
			if (!Files.exists(lockExample))
				Files.writeString(lockExample,
					"{\n  \"length\": 7,\n  \"enchantment_value\": 14,\n  \"resistance\": 12\n}\n");

			Path pickExample = pickDir.resolve("_example.json.disabled");
			if (!Files.exists(pickExample))
				Files.writeString(pickExample,
					"{\n  \"strength\": 0.35\n}\n");
		}
		catch (IOException e)
		{
			Locks.LOGGER.error("Failed to create config directories", e);
		}
	}

	private static void loadConfigDirectory(Path dir, boolean isLock)
	{
		if (!Files.isDirectory(dir))
			return;

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json"))
		{
			for (Path file : stream)
			{
				String fileName = file.getFileName().toString();
				if (fileName.startsWith("_"))
					continue; // skip example/template files
				String name = fileName.substring(0, fileName.length() - 5);
				ResourceLocation id = new ResourceLocation(Locks.ID, name);

				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
				{
					JsonObject json = GsonHelper.fromJson(GSON, reader, JsonObject.class);
					if (isLock)
						parseLockDefinition(id, json);
					else
						parseLockPickDefinition(id, json);
					Locks.LOGGER.info("Loaded custom {} definition from config: {}", isLock ? "lock" : "lockpick", id);
				}
				catch (Exception e)
				{
					Locks.LOGGER.error("Failed to parse config {} definition: {}", isLock ? "lock" : "lockpick", id, e);
				}
			}
		}
		catch (IOException e)
		{
			Locks.LOGGER.error("Failed to read config directory: {}", dir, e);
		}
	}

	private static void parseLockDefinition(ResourceLocation id, JsonObject json)
	{
		int length = clampWarn(id, "length", GsonHelper.getAsInt(json, "length"), 1, 20);
		int enchantmentValue = clampWarn(id, "enchantment_value", GsonHelper.getAsInt(json, "enchantment_value"), 1, 50);
		int resistance = clampWarn(id, "resistance", GsonHelper.getAsInt(json, "resistance"), 0, 1000);
		LOCK_DEFAULTS.put(id, new LockStats(length, enchantmentValue, resistance));
	}

	private static void parseLockPickDefinition(ResourceLocation id, JsonObject json)
	{
		float raw = GsonHelper.getAsFloat(json, "strength");
		if (raw < 0.01f || raw > 10f)
		{
			Locks.LOGGER.warn("Lockpick {} has out-of-range strength {} — clamping to [0.01, 10.0]", id, raw);
			raw = Math.max(0.01f, Math.min(10f, raw));
		}
		LOCKPICK_DEFAULTS.put(id, new LockPickStats(raw));
	}

	private static int clampWarn(ResourceLocation id, String field, int value, int min, int max)
	{
		if (value < min || value > max)
		{
			Locks.LOGGER.warn("Lock {} has out-of-range {} = {} — clamping to [{}, {}]", id, field, value, min, max);
			return Math.max(min, Math.min(max, value));
		}
		return value;
	}

	public static void resetStats()
	{
		LOCK_STATS = new LinkedHashMap<>(LOCK_DEFAULTS);
		LOCKPICK_STATS = new LinkedHashMap<>(LOCKPICK_DEFAULTS);
		LocksConfig.applyConfigStatOverrides();
	}

	public static void applyConfigLockOverride(ResourceLocation id, int length, int enchant, int resistance)
	{
		LockStats base = LOCK_STATS.get(id);
		if (base == null)
		{
			Locks.LOGGER.warn("TOML config references unknown lock: {}", id);
			return;
		}
		int l = length >= 0 ? length : base.length();
		int e = enchant >= 0 ? enchant : base.enchantmentValue();
		int r = resistance >= 0 ? resistance : base.resistance();
		LOCK_STATS.put(id, new LockStats(l, e, r));
		Locks.LOGGER.debug("Applied TOML config override for lock {}", id);
	}

	public static void applyConfigPickOverride(ResourceLocation id, float strength)
	{
		LockPickStats base = LOCKPICK_STATS.get(id);
		if (base == null)
		{
			Locks.LOGGER.warn("TOML config references unknown lockpick: {}", id);
			return;
		}
		LOCKPICK_STATS.put(id, new LockPickStats(strength));
		Locks.LOGGER.debug("Applied TOML config override for lockpick {}", id);
	}

	public static void applyLockOverrides(Map<ResourceLocation, JsonObject> overrides)
	{
		for (var entry : overrides.entrySet())
		{
			JsonObject json = entry.getValue();
			ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(json, "item"));

			LockStats base = LOCK_STATS.get(itemId);
			if (base == null)
			{
				Locks.LOGGER.warn("Lock stat override references unknown item: {}", itemId);
				continue;
			}

			int length = json.has("length") ? GsonHelper.getAsInt(json, "length") : base.length();
			int enchVal = json.has("enchantment_value") ? GsonHelper.getAsInt(json, "enchantment_value") : base.enchantmentValue();
			int resist = json.has("resistance") ? GsonHelper.getAsInt(json, "resistance") : base.resistance();

			LOCK_STATS.put(itemId, new LockStats(length, enchVal, resist));
			Locks.LOGGER.debug("Applied lock stat override for {}", itemId);
		}
	}

	public static void applyLockPickOverrides(Map<ResourceLocation, JsonObject> overrides)
	{
		for (var entry : overrides.entrySet())
		{
			JsonObject json = entry.getValue();
			ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(json, "item"));

			LockPickStats base = LOCKPICK_STATS.get(itemId);
			if (base == null)
			{
				Locks.LOGGER.warn("Lockpick stat override references unknown item: {}", itemId);
				continue;
			}

			float strength = json.has("strength") ? GsonHelper.getAsFloat(json, "strength") : base.strength();

			LOCKPICK_STATS.put(itemId, new LockPickStats(strength));
			Locks.LOGGER.debug("Applied lockpick stat override for {}", itemId);
		}
	}

	public static LockStats getLockStats(Item item)
	{
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
		LockStats stats = LOCK_STATS.get(id);
		if (stats == null)
		{
			Locks.LOGGER.error("No lock stats found for item: {}. Using fallback.", id);
			return new LockStats(5, 10, 4);
		}
		return stats;
	}

	public static LockPickStats getLockPickStats(Item item)
	{
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
		LockPickStats stats = LOCKPICK_STATS.get(id);
		if (stats == null)
		{
			Locks.LOGGER.error("No lockpick stats found for item: {}. Using fallback.", id);
			return new LockPickStats(0.2f);
		}
		return stats;
	}

	public static Map<ResourceLocation, LockStats> allLockDefinitions()
	{
		return Collections.unmodifiableMap(LOCK_DEFAULTS);
	}

	public static Map<ResourceLocation, LockPickStats> allLockPickDefinitions()
	{
		return Collections.unmodifiableMap(LOCKPICK_DEFAULTS);
	}
}
