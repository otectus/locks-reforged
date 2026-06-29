package melonslise.locks.common.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import melonslise.locks.Locks;
import melonslise.locks.common.init.LocksPlacements;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class ChestPlacement extends PlacementModifier
{
	public static final Codec<ChestPlacement> CODEC = Codec.unit(ChestPlacement::new);

	public ChestPlacement() {}

	@Override
	public Stream<BlockPos> getPositions(PlacementContext context, RandomSource rng, BlockPos pos)
	{
		var chunk = context.getLevel().getChunk(pos);
		var allBEPos = chunk.getBlockEntitiesPos();
		// Most chunks have no block entities — skip building the filter stream entirely.
		if (allBEPos.isEmpty())
			return Stream.empty();
		return allBEPos.stream()
			.filter(tePos ->
			{
				BlockState state = context.getLevel().getBlockState(tePos);
				if (!state.hasProperty(ChestBlock.TYPE))
					return false;
				if (state.getValue(ChestBlock.TYPE) == ChestType.RIGHT && !ModList.get().isLoaded("lootr"))
					return false;
				BlockEntity be = context.getLevel().getBlockEntity(tePos);
				if (be == null)
				{
					Locks.LOGGER.warn("ChestPlacement: skipping {} — getBlockEntity returned null for {}", tePos, state.getBlock());
					return false;
				}
				if (!(be instanceof RandomizableContainerBlockEntity))
					return false;
				RandomizableContainerBlockEntity container = (RandomizableContainerBlockEntity) be;
				return container.lootTable != null;
			});
	}

	@Override
	public PlacementModifierType<?> type()
	{
		return LocksPlacements.CHEST.get();
	}
}
