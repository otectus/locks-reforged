package melonslise.locks.common.worldgen;

import com.mojang.serialization.Codec;

import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.util.Cuboid6i;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lock;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LootValueCalculator;
import melonslise.locks.common.util.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.fml.ModList;

public class LockChestsFeature extends Feature<NoneFeatureConfiguration>
{
	public LockChestsFeature(Codec<NoneFeatureConfiguration> codec)
	{
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
	{
		WorldGenLevel world = context.level();
		RandomSource rng = context.random();
		BlockPos pos = context.origin();

		ItemStack stack = ItemStack.EMPTY;
		boolean usedLootScaling = false;
		if (LocksConfig.LOOT_SCALED_LOCKS.get())
		{
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof RandomizableContainerBlockEntity container && container.lootTable != null)
			{
				double lootValue = LootValueCalculator.getLootValue(world.getLevel().getServer(), container.lootTable);
				if (!Double.isNaN(lootValue))
				{
					stack = LocksConfig.getLockForLootValue(lootValue, rng);
					usedLootScaling = true;
				}
			}
		}

		if (!usedLootScaling)
		{
			if (!LocksConfig.canGen(rng))
				return false;
			stack = LocksConfig.getRandomLock(rng);
		}

		if (stack.isEmpty())
			return false;

		BlockState state = world.getBlockState(pos);
		BlockPos pos1 = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE || ModList.get().isLoaded("lootr") ? pos : pos.relative(ChestBlock.getConnectedDirection(state));
		Lockable lkb = new Lockable(new Cuboid6i(pos, pos1), Lock.from(stack), Transform.fromDirection(state.getValue(ChestBlock.FACING), Direction.NORTH), stack, world.getLevel());
		lkb.bb.getContainedChunks((x, z) ->
		{
			((ILockableProvider) world.getChunk(x, z)).getLockables().add(lkb);
			return true;
		});
		return true;
	}
}
