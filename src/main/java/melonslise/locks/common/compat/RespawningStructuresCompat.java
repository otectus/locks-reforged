package melonslise.locks.common.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import melonslise.locks.Locks;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Cuboid6i;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.fml.ModList;

/**
 * Soft compatibility with the "Respawning Structures" mod (someaddons/respawningstructures).
 *
 * That mod periodically regenerates structures by re-placing their structure pieces
 * ({@code StructureStart.placeInChunk}), which restores chests and loot but does NOT re-run biome
 * decoration features — so our {@code LockChestsFeature} never fires again and respawned chests come
 * back unlocked. Respawning Structures exposes a public callback API
 * ({@code com.respawningstructures.event.StructureRespawnEvents}); we register on its
 * {@code AFTER_RESPAWN_EVENT} (fired once the structure's blocks/chests/loot are fully placed) and
 * re-apply locks to the respawned chests using the exact same rules as world generation.
 *
 * Wired in via reflection so we keep a soft dependency (no build.gradle change, no hard class
 * references), mirroring the {@link CuriosHelper} pattern. If the mod is absent or its API has
 * changed, init() simply no-ops.
 */
public final class RespawningStructuresCompat
{
	private static Field afterRespawnLevelField;
	private static Field afterRespawnStructureDataField;
	private static Method getStructureStartMethod;

	private RespawningStructuresCompat() {}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void init()
	{
		if(!ModList.get().isLoaded("respawningstructures"))
			return;
		try
		{
			Class<?> eventsClass = Class.forName("com.respawningstructures.event.StructureRespawnEvents");
			List afterRespawnList = (List) eventsClass.getField("AFTER_RESPAWN_EVENT").get(null);

			Class<?> afterRespawnEventClass = Class.forName("com.respawningstructures.event.StructureRespawnEvents$AfterRespawnEvent");
			afterRespawnLevelField = afterRespawnEventClass.getField("level");
			afterRespawnStructureDataField = afterRespawnEventClass.getField("structureData");
			getStructureStartMethod = Class.forName("com.respawningstructures.structure.StructureData").getMethod("getStructureStart");

			afterRespawnList.add((Consumer<Object>) RespawningStructuresCompat::onAfterRespawn);
			Locks.LOGGER.info("Respawning Structures detected — respawned chests will be re-locked");
		}
		catch(Throwable t)
		{
			Locks.LOGGER.warn("Respawning Structures is installed but its compat hook could not be set up (API mismatch?). Respawned chests will not be re-locked.", t);
		}
	}

	// Called by Respawning Structures (via the registered Consumer) after a structure is re-placed.
	// Runs on the server thread (the respawn is driven from RespawnManager.onLevelTick), so handler
	// mutations and the packet sync they trigger are already thread-safe.
	private static void onAfterRespawn(Object event)
	{
		try
		{
			ServerLevel level = (ServerLevel) afterRespawnLevelField.get(event);
			Object structureData = afterRespawnStructureDataField.get(event);
			StructureStart start = (StructureStart) getStructureStartMethod.invoke(structureData);
			if(start == null || !start.isValid())
				return;
			relock(level, start);
		}
		catch(Throwable t)
		{
			Locks.LOGGER.warn("Failed to re-lock chests after a structure respawn", t);
		}
	}

	private static void relock(ServerLevel level, StructureStart start)
	{
		ILockableHandler handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return;

		BoundingBox bb = start.getBoundingBox();
		Cuboid6i region = new Cuboid6i(new BlockPos(bb.minX(), bb.minY(), bb.minZ()), new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()));

		// 1. Clear lockables overlapping the freshly re-placed structure. The chests they referenced
		// were just replaced, so the old lockables are stale; removing them also frees the space so
		// the re-lock pass below isn't rejected by handler.add's overlap check.
		// handler.remove force-loads each lockable's chunks and clears it from every chunk's storage (not just
		// loaded ones), so a stale copy in a structure chunk that happens to be unloaded cannot survive and be
		// resurrected on reload -> no duplicate locks after repeated respawns. getLoaded() here is the world
		// index, which the interaction-time reconcile keeps consistent with chunk storage.
		for(Lockable lkb : handler.snapshotLoaded())
			if(lkb.bb.intersects(region))
				handler.remove(lkb.id);

		// 2. Re-lock the respawned chests using the same rules as world generation.
		RandomSource rng = level.getRandom();
		int cx1 = SectionPos.blockToSectionCoord(bb.minX());
		int cx2 = SectionPos.blockToSectionCoord(bb.maxX());
		int cz1 = SectionPos.blockToSectionCoord(bb.minZ());
		int cz2 = SectionPos.blockToSectionCoord(bb.maxZ());
		for(int cx = cx1; cx <= cx2; ++cx)
		{
			for(int cz = cz1; cz <= cz2; ++cz)
			{
				if(!level.hasChunk(cx, cz))
					continue;
				LevelChunk chunk = level.getChunk(cx, cz);
				for(BlockPos pos : new ArrayList<>(chunk.getBlockEntitiesPos()))
				{
					if(!bb.isInside(pos))
						continue;
					// Right halves of double chests return null, so double chests get a single lock
					Lockable lkb = LocksUtil.createChestLockable(level, level, pos, rng);
					if(lkb != null)
						handler.add(lkb);
				}
			}
		}
	}
}
