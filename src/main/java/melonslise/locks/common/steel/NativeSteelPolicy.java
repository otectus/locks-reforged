package melonslise.locks.common.steel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import melonslise.locks.Locks;

/**
 * Single source of truth for whether Locks provides (and generates) its own native steel material.
 *
 * <p>The core decision is the pure, side-effect-free {@link #compute(SteelTagView, SteelMaterialMode)} method,
 * which is unit-tested without launching Minecraft. Every consumer — the recipe {@link NativeSteelCondition},
 * the {@link melonslise.locks.common.worldgen.SteelOrePlacement} worldgen gate, the creative tab, and the
 * diagnostic log — routes through this class so the "ignore Locks' own tag entries" rule and the AUTO/
 * FORCE_NATIVE/EXTERNAL_ONLY semantics live in exactly one place.
 *
 * <p>Detection must never conclude "external steel exists" merely because {@code forge:ingots/steel} is
 * non-empty: Locks always registers {@code locks:steel_ingot} into that tag. {@link #isNativeId} filters out
 * Locks' own namespace and the known fallback IDs before counting foreign providers.
 *
 * <p>A cached {@link NativeSteelState} snapshot is refreshed on the server after tags load and read by worldgen
 * threads through a {@code volatile} reference (the state is immutable, so publication is safe).
 */
public final class NativeSteelPolicy
{
	/** Known native fallback registry IDs — excluded from foreign-steel detection. */
	public static final ResourceLocation STEEL_INGOT_ID = new ResourceLocation(Locks.ID, "steel_ingot");
	public static final ResourceLocation STEEL_NUGGET_ID = new ResourceLocation(Locks.ID, "steel_nugget");
	public static final ResourceLocation STEEL_ORE_ID = new ResourceLocation(Locks.ID, "steel_ore");
	public static final ResourceLocation DEEPSLATE_STEEL_ORE_ID = new ResourceLocation(Locks.ID, "deepslate_steel_ore");

	/** Common Forge steel tag locations. */
	public static final ResourceLocation TAG_INGOTS_STEEL = new ResourceLocation("forge", "ingots/steel");
	public static final ResourceLocation TAG_NUGGETS_STEEL = new ResourceLocation("forge", "nuggets/steel");
	public static final ResourceLocation TAG_ORES_STEEL = new ResourceLocation("forge", "ores/steel");

	private static final NativeSteelState DEFAULT_ALL_NATIVE =
		new NativeSteelState(true, true, true, List.of(), List.of(), List.of());

	private static volatile NativeSteelState current = null;

	private NativeSteelPolicy() {}

	/**
	 * Whether an ID belongs to Locks' native steel material (own namespace or a known fallback ID). Such IDs
	 * are never counted as evidence of an external steel provider.
	 */
	public static boolean isNativeId(ResourceLocation id)
	{
		if (id == null)
			return false;
		if (Locks.ID.equals(id.getNamespace()))
			return true;
		return id.equals(STEEL_INGOT_ID) || id.equals(STEEL_NUGGET_ID)
			|| id.equals(STEEL_ORE_ID) || id.equals(DEEPSLATE_STEEL_ORE_ID);
	}

	/**
	 * Pure detection logic. Given the raw tag membership and the selected mode, decide which native forms are
	 * active and report the detected foreign IDs. No Minecraft state is touched — this is the unit-tested core.
	 */
	public static NativeSteelState compute(SteelTagView view, SteelMaterialMode mode)
	{
		List<ResourceLocation> foreignIngots = foreign(view.ingots());
		List<ResourceLocation> foreignNuggets = foreign(view.nuggets());
		List<ResourceLocation> foreignOres = foreign(view.ores());

		boolean ingot;
		boolean nugget;
		boolean ore;
		switch (mode)
		{
			case FORCE_NATIVE:
				ingot = true;
				nugget = true;
				ore = true;
				break;
			case EXTERNAL_ONLY:
				ingot = false;
				nugget = false;
				ore = false;
				break;
			case AUTO:
			default:
				// Each form falls back independently: provide it only when no foreign mod already does.
				ingot = foreignIngots.isEmpty();
				nugget = foreignNuggets.isEmpty();
				// Ore only generates when no foreign ore exists AND the native ingot is the one being smelted to.
				ore = foreignOres.isEmpty() && ingot;
				break;
		}

		return new NativeSteelState(ingot, nugget, ore, foreignIngots, foreignNuggets, foreignOres);
	}

	/** Filter out Locks' own IDs, de-duplicate, and sort for deterministic reporting. */
	private static List<ResourceLocation> foreign(Collection<ResourceLocation> raw)
	{
		Set<ResourceLocation> out = new LinkedHashSet<>();
		if (raw != null)
			for (ResourceLocation id : raw)
				if (id != null && !isNativeId(id))
					out.add(id);
		List<ResourceLocation> list = new ArrayList<>(out);
		list.sort(Comparator.comparing(ResourceLocation::toString));
		return List.copyOf(list);
	}

	// ---- Cached server-side snapshot (worldgen / creative tab / diagnostics) ----

	/**
	 * The current authoritative snapshot. Returns an all-native default until the first refresh, so native forms
	 * are never wrongly hidden before tags have loaded.
	 */
	public static NativeSteelState get()
	{
		NativeSteelState state = current;
		return state != null ? state : DEFAULT_ALL_NATIVE;
	}

	/** Recompute and publish the snapshot from a live {@link RegistryAccess} (item + block steel tags). */
	public static NativeSteelState refresh(RegistryAccess access, SteelMaterialMode mode)
	{
		NativeSteelState state = compute(new RegistryTagView(access), mode);
		current = state;
		return state;
	}

	/** Drop the cached snapshot (e.g. on server stop) so a stale state never leaks into the next session. */
	public static void clear()
	{
		current = null;
	}

	/**
	 * The steel forms that are entirely absent (no native fallback and no foreign provider) — used to warn under
	 * {@link SteelMaterialMode#EXTERNAL_ONLY} when a pack forgot to supply the standard tags. Never throws.
	 */
	public static List<String> missingForms(NativeSteelState state)
	{
		List<String> missing = new ArrayList<>();
		if (!state.steelAvailable())
			missing.add("ingot");
		if (!state.nuggetAvailable())
			missing.add("nugget");
		if (!state.oreAvailable())
			missing.add("ore");
		return missing;
	}
}
