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
		Locks.LOGGER.debug("ChestPlacement: chunk {} has {} block entities (chunk type: {})", chunk.getPos(), allBEPos.size(), chunk.getClass().getSimpleName());
		return allBEPos.stream()
			.filter(tePos ->
			{
				BlockState state = context.getLevel().getBlockState(tePos);
				if (!state.hasProperty(ChestBlock.TYPE))
				{
					Locks.LOGGER.debug("ChestPlacement: skipping {} — block {} has no ChestBlock.TYPE", tePos, state.getBlock());
					return false;
				}
				if (state.getValue(ChestBlock.TYPE) == ChestType.RIGHT && !ModList.get().isLoaded("lootr"))
					return false;
				BlockEntity be = context.getLevel().getBlockEntity(tePos);
				if (be == null)
				{
					Locks.LOGGER.warn("ChestPlacement: skipping {} — getBlockEntity returned null for {}", tePos, state.getBlock());
					return false;
				}
				if (!(be instanceof RandomizableContainerBlockEntity))
				{
					Locks.LOGGER.debug("ChestPlacement: skipping {} — BE is {} not RandomizableContainerBlockEntity", tePos, be.getClass().getSimpleName());
					return false;
				}
				RandomizableContainerBlockEntity container = (RandomizableContainerBlockEntity) be;
				if (container.lootTable == null)
				{
					Locks.LOGGER.debug("ChestPlacement: skipping {} — lootTable is null (BE type: {})", tePos, be.getClass().getSimpleName());
					return false;
				}
				Locks.LOGGER.debug("ChestPlacement: ACCEPTED {} — lootTable={} BE={}", tePos, container.lootTable, be.getClass().getSimpleName());
				return true;
			});
	}

	@Override
	public PlacementModifierType<?> type()
	{
		return LocksPlacements.CHEST.get();
	}
}
