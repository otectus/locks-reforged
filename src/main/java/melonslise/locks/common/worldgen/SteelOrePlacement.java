package melonslise.locks.common.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import melonslise.locks.common.init.LocksPlacements;
import melonslise.locks.common.steel.NativeSteelPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Worldgen gate for native steel ore. Emits the incoming position unchanged when
 * {@link NativeSteelPolicy#get()} reports native ore generation is active, and no positions otherwise —
 * so the steel-ore placed feature produces nothing when a modpack already has an external steel economy (or
 * when the mode disables native ore).
 *
 * <p>Reads only the immutable {@link melonslise.locks.common.steel.NativeSteelState} snapshot through a volatile
 * reference, so it is safe to call from off-thread chunk generation without touching mutable registries/config.
 */
public class SteelOrePlacement extends PlacementModifier
{
	public static final Codec<SteelOrePlacement> CODEC = Codec.unit(SteelOrePlacement::new);

	public SteelOrePlacement() {}

	@Override
	public Stream<BlockPos> getPositions(PlacementContext context, RandomSource rng, BlockPos pos)
	{
		return NativeSteelPolicy.get().nativeOreActive() ? Stream.of(pos) : Stream.empty();
	}

	@Override
	public PlacementModifierType<?> type()
	{
		return LocksPlacements.STEEL_ORE.get();
	}
}
