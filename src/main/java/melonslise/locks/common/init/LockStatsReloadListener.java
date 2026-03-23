package melonslise.locks.common.init;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import melonslise.locks.Locks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public class LockStatsReloadListener extends SimplePreparableReloadListener<LockStatsReloadListener.OverrideData>
{
	private static final Gson GSON = new GsonBuilder().create();
	private static final String LOCK_DIR = "locks/lock_stat_overrides";
	private static final String PICK_DIR = "locks/lockpick_stat_overrides";

	public record OverrideData(
		Map<ResourceLocation, JsonObject> lockOverrides,
		Map<ResourceLocation, JsonObject> pickOverrides
	) {}

	@Override
	protected OverrideData prepare(ResourceManager manager, ProfilerFiller profiler)
	{
		Map<ResourceLocation, JsonObject> lockOverrides = scanDirectory(manager, LOCK_DIR);
		Map<ResourceLocation, JsonObject> pickOverrides = scanDirectory(manager, PICK_DIR);
		return new OverrideData(lockOverrides, pickOverrides);
	}

	private static Map<ResourceLocation, JsonObject> scanDirectory(ResourceManager manager, String directory)
	{
		Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
		Map<ResourceLocation, Resource> resources = manager.listResources(directory, id -> id.getPath().endsWith(".json"));

		for (var entry : resources.entrySet())
		{
			ResourceLocation fileId = entry.getKey();
			// Extract the short name: "locks/lock_stat_overrides/foo.json" -> "foo"
			String path = fileId.getPath();
			String name = path.substring(directory.length() + 1, path.length() - 5);
			ResourceLocation id = new ResourceLocation(fileId.getNamespace(), name);

			try (Reader reader = entry.getValue().openAsReader())
			{
				JsonObject json = GsonHelper.fromJson(GSON, reader, JsonObject.class);
				result.put(id, json);
			}
			catch (IOException e)
			{
				Locks.LOGGER.error("Failed to read {} override: {}", directory, id, e);
			}
		}
		return result;
	}

	@Override
	protected void apply(OverrideData data, ResourceManager manager, ProfilerFiller profiler)
	{
		LockTypeRegistry.resetStats();

		LockTypeRegistry.applyLockOverrides(data.lockOverrides);
		LockTypeRegistry.applyLockPickOverrides(data.pickOverrides);

		Locks.LOGGER.info("Applied {} lock and {} lockpick stat overrides",
			data.lockOverrides.size(), data.pickOverrides.size());
	}
}
