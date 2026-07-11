package melonslise.locks.common.steel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure unit tests for the native-steel detection policy. These run without launching Minecraft — they only
 * exercise {@link NativeSteelPolicy#compute(SteelTagView, SteelMaterialMode)} and its ID filtering via an
 * in-memory {@link SteelTagView}.
 */
class NativeSteelPolicyTest
{
	/** In-memory tag view for testing. */
	private record FakeView(
		Collection<ResourceLocation> ingots,
		Collection<ResourceLocation> nuggets,
		Collection<ResourceLocation> ores) implements SteelTagView {}

	private static ResourceLocation rl(String s)
	{
		return new ResourceLocation(s);
	}

	private static List<ResourceLocation> ids(String... s)
	{
		return Arrays.stream(s).map(NativeSteelPolicyTest::rl).toList();
	}

	// Locks' own native entries always present in the tags (as they are in-game).
	private static final List<ResourceLocation> NATIVE_INGOTS = List.of(NativeSteelPolicy.STEEL_INGOT_ID);
	private static final List<ResourceLocation> NATIVE_NUGGETS = List.of(NativeSteelPolicy.STEEL_NUGGET_ID);
	private static final List<ResourceLocation> NATIVE_ORES =
		List.of(NativeSteelPolicy.STEEL_ORE_ID, NativeSteelPolicy.DEEPSLATE_STEEL_ORE_ID);

	// 1. No external steel: native ingot, nugget, and ore generation all active.
	@Test
	void noExternalSteel_allNativeActive()
	{
		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(NATIVE_INGOTS, NATIVE_NUGGETS, NATIVE_ORES), SteelMaterialMode.AUTO);

		assertTrue(s.nativeIngotActive());
		assertTrue(s.nativeNuggetActive());
		assertTrue(s.nativeOreActive());
		assertTrue(s.foreignIngots().isEmpty());
		assertTrue(s.foreignNuggets().isEmpty());
		assertTrue(s.foreignOres().isEmpty());
		assertTrue(NativeSteelPolicy.missingForms(s).isEmpty());
	}

	// 2. Complete external provider: native ingot, nugget, and ore generation all inactive; IDs reported.
	@Test
	void completeExternalProvider_allNativeInactive()
	{
		Collection<ResourceLocation> ingots = concat(NATIVE_INGOTS, ids("examplemod:steel_ingot"));
		Collection<ResourceLocation> nuggets = concat(NATIVE_NUGGETS, ids("examplemod:steel_nugget"));
		Collection<ResourceLocation> ores = concat(NATIVE_ORES, ids("examplemod:steel_ore"));

		NativeSteelState s = NativeSteelPolicy.compute(new FakeView(ingots, nuggets, ores), SteelMaterialMode.AUTO);

		assertFalse(s.nativeIngotActive());
		assertFalse(s.nativeNuggetActive());
		assertFalse(s.nativeOreActive());
		assertEquals(ids("examplemod:steel_ingot"), s.foreignIngots());
		assertEquals(ids("examplemod:steel_nugget"), s.foreignNuggets());
		assertEquals(ids("examplemod:steel_ore"), s.foreignOres());
		// Steel still exists (external), so the lock/mechanism/pick recipes stay craftable.
		assertTrue(s.steelAvailable());
	}

	// 3. External ingot only: native ingot + ore inactive; native nugget remains as a missing-form fallback.
	@Test
	void externalIngotOnly_nuggetRemains()
	{
		Collection<ResourceLocation> ingots = concat(NATIVE_INGOTS, ids("examplemod:steel_ingot"));

		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(ingots, NATIVE_NUGGETS, NATIVE_ORES), SteelMaterialMode.AUTO);

		assertFalse(s.nativeIngotActive());
		assertTrue(s.nativeNuggetActive());
		assertFalse(s.nativeOreActive(), "ore generation requires the native ingot to be active");
	}

	// 4. External nugget only: native nugget inactive; native ingot + ore remain active.
	@Test
	void externalNuggetOnly_ingotAndOreRemain()
	{
		Collection<ResourceLocation> nuggets = concat(NATIVE_NUGGETS, ids("examplemod:steel_nugget"));

		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(NATIVE_INGOTS, nuggets, NATIVE_ORES), SteelMaterialMode.AUTO);

		assertTrue(s.nativeIngotActive());
		assertFalse(s.nativeNuggetActive());
		assertTrue(s.nativeOreActive());
	}

	// 5. Multiple external providers: deterministic, all IDs reported, no arbitrary single-provider pick.
	@Test
	void multipleProviders_deterministicReporting()
	{
		Collection<ResourceLocation> ingots =
			concat(NATIVE_INGOTS, ids("zmod:steel_ingot", "amod:steel_ingot", "examplemod:steel_ingot"));

		NativeSteelState s1 = NativeSteelPolicy.compute(
			new FakeView(ingots, NATIVE_NUGGETS, NATIVE_ORES), SteelMaterialMode.AUTO);
		NativeSteelState s2 = NativeSteelPolicy.compute(
			new FakeView(ingots, NATIVE_NUGGETS, NATIVE_ORES), SteelMaterialMode.AUTO);

		// Sorted, de-duplicated, and stable across runs.
		assertEquals(ids("amod:steel_ingot", "examplemod:steel_ingot", "zmod:steel_ingot"), s1.foreignIngots());
		assertEquals(s1.foreignIngots(), s2.foreignIngots());
		assertFalse(s1.nativeIngotActive());
	}

	// 6. FORCE_NATIVE: native production/generation stay on even with a complete external provider.
	@Test
	void forceNative_overridesExternal()
	{
		Collection<ResourceLocation> ingots = concat(NATIVE_INGOTS, ids("examplemod:steel_ingot"));
		Collection<ResourceLocation> nuggets = concat(NATIVE_NUGGETS, ids("examplemod:steel_nugget"));
		Collection<ResourceLocation> ores = concat(NATIVE_ORES, ids("examplemod:steel_ore"));

		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(ingots, nuggets, ores), SteelMaterialMode.FORCE_NATIVE);

		assertTrue(s.nativeIngotActive());
		assertTrue(s.nativeNuggetActive());
		assertTrue(s.nativeOreActive());
		// Foreign IDs are still reported for diagnostics.
		assertEquals(ids("examplemod:steel_ingot"), s.foreignIngots());
	}

	// 7. EXTERNAL_ONLY: all native disabled; empty tags produce a missing-form list (no throw).
	@Test
	void externalOnly_emptyTagsWarnNoCrash()
	{
		// Only Locks' own entries in the tags — under EXTERNAL_ONLY these do not count, so all forms are missing.
		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(NATIVE_INGOTS, NATIVE_NUGGETS, NATIVE_ORES), SteelMaterialMode.EXTERNAL_ONLY);

		assertFalse(s.nativeIngotActive());
		assertFalse(s.nativeNuggetActive());
		assertFalse(s.nativeOreActive());
		assertEquals(List.of("ingot", "nugget", "ore"), NativeSteelPolicy.missingForms(s));
	}

	// 7b. EXTERNAL_ONLY with a foreign provider present: native disabled but nothing missing.
	@Test
	void externalOnly_withProvider_noMissing()
	{
		Collection<ResourceLocation> ingots = concat(NATIVE_INGOTS, ids("examplemod:steel_ingot"));
		Collection<ResourceLocation> nuggets = concat(NATIVE_NUGGETS, ids("examplemod:steel_nugget"));
		Collection<ResourceLocation> ores = concat(NATIVE_ORES, ids("examplemod:steel_ore"));

		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(ingots, nuggets, ores), SteelMaterialMode.EXTERNAL_ONLY);

		assertFalse(s.nativeIngotActive());
		assertTrue(NativeSteelPolicy.missingForms(s).isEmpty());
	}

	// 8. Locks' own IDs never count as foreign (filtering correctness), including any locks-namespace entry.
	@Test
	void locksOwnIdsNeverForeign()
	{
		assertTrue(NativeSteelPolicy.isNativeId(NativeSteelPolicy.STEEL_INGOT_ID));
		assertTrue(NativeSteelPolicy.isNativeId(rl("locks:anything_else")));
		assertFalse(NativeSteelPolicy.isNativeId(rl("examplemod:steel_ingot")));

		// A tag containing only Locks entries must read as "no external provider".
		NativeSteelState s = NativeSteelPolicy.compute(
			new FakeView(NATIVE_INGOTS, NATIVE_NUGGETS, NATIVE_ORES), SteelMaterialMode.AUTO);
		assertTrue(s.foreignIngots().isEmpty());
		assertTrue(s.nativeIngotActive());
	}

	private static Collection<ResourceLocation> concat(Collection<ResourceLocation> a, Collection<ResourceLocation> b)
	{
		return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
	}
}
