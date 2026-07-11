package melonslise.locks.common.steel;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable result of a {@link NativeSteelPolicy} evaluation. Safe to publish across threads (worldgen workers
 * read the current snapshot through a volatile reference).
 *
 * @param nativeIngotActive  whether Locks should provide its native steel ingot (recipes + creative tab)
 * @param nativeNuggetActive whether Locks should provide its native steel nugget
 * @param nativeOreActive    whether Locks should generate its native steel ore
 * @param foreignIngots      non-Locks IDs found in {@code forge:ingots/steel} (sorted, de-duplicated)
 * @param foreignNuggets     non-Locks IDs found in {@code forge:nuggets/steel}
 * @param foreignOres        non-Locks IDs found in {@code forge:ores/steel}
 */
public record NativeSteelState(
	boolean nativeIngotActive,
	boolean nativeNuggetActive,
	boolean nativeOreActive,
	List<ResourceLocation> foreignIngots,
	List<ResourceLocation> foreignNuggets,
	List<ResourceLocation> foreignOres)
{
	/** True when a steel ingot exists at all — native fallback or any foreign provider. */
	public boolean steelAvailable()
	{
		return nativeIngotActive || !foreignIngots.isEmpty();
	}

	/** True when a steel nugget exists at all — native fallback or any foreign provider. */
	public boolean nuggetAvailable()
	{
		return nativeNuggetActive || !foreignNuggets.isEmpty();
	}

	/** True when a steel ore exists at all — native or any foreign provider. */
	public boolean oreAvailable()
	{
		return nativeOreActive || !foreignOres.isEmpty();
	}
}
