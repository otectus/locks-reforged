package melonslise.locks.common.steel;

import java.util.Collection;

import net.minecraft.resources.ResourceLocation;

/**
 * Abstracts the source of steel tag membership so the {@link NativeSteelPolicy} detection logic can be
 * driven from different lifecycle points with the same rules:
 * <ul>
 *   <li>{@link ConditionTagView} — from an {@code ICondition.IContext} during recipe loading.</li>
 *   <li>{@link RegistryTagView} — from a {@code RegistryAccess} after tags are (re)loaded.</li>
 *   <li>a plain in-memory implementation in unit tests.</li>
 * </ul>
 *
 * Implementations return the <em>raw</em> members of each tag (including Locks' own entries); the policy is
 * responsible for filtering out Locks' native IDs. This keeps the "ignore our own namespace" rule in exactly
 * one place.
 */
public interface SteelTagView
{
	/** Raw members of {@code forge:ingots/steel}. */
	Collection<ResourceLocation> ingots();

	/** Raw members of {@code forge:nuggets/steel}. */
	Collection<ResourceLocation> nuggets();

	/** Raw members of {@code forge:ores/steel} (item and/or block members, depending on the source). */
	Collection<ResourceLocation> ores();
}
