package melonslise.locks.common.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import melonslise.locks.common.init.LocksPlacements;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class ChestPlacement extends PlacementModifier
{
	public static final Codec<ChestPlacement> CODEC = Codec.unit(ChestPlacement::new);

	public ChestPlacement() {}

	@Override
	public Stream<BlockPos> getPositions(PlacementContext context, RandomSource rng, BlockPos pos)
	{
		return context.getLevel().getChunk(pos).getBlockEntitiesPos().stream()
			.filter(tePos ->
			{
				BlockState state = context.getLevel().getBlockState(tePos);
				// Prevent from adding double chests twice
				if (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.RIGHT)
					return false;
				// Skip chests without a loot table (empty chests not placed by structures)
				BlockEntity be = context.getLevel().getBlockEntity(tePos);
				return be instanceof RandomizableContainerBlockEntity container && container.lootTable != null;
			});
	}

	@Override
	public PlacementModifierType<?> type()
	{
		return LocksPlacements.CHEST.get();
	}
}
