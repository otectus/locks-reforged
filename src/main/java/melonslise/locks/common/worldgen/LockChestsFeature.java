package melonslise.locks.common.worldgen;

import com.mojang.serialization.Codec;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.util.Cuboid6i;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lock;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import melonslise.locks.common.util.LootValueCalculator;
import melonslise.locks.common.util.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

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

		double genChance = LocksConfig.GENERATION_CHANCE.get();
		if (!LocksUtil.chance(rng, genChance))
		{
			Locks.LOGGER.debug("LockChestsFeature: skipping {} — failed generation chance ({})", pos, genChance);
			return false;
		}

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
			stack = LocksConfig.getRandomLock(rng);

		// Safety net: guarantee at least a wooden lock on every generated chest
		if (stack.isEmpty())
			stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(Locks.ID, "wood_lock")));

		BlockState state = world.getBlockState(pos);
		BlockPos pos1 = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE || ModList.get().isLoaded("lootr") ? pos : pos.relative(ChestBlock.getConnectedDirection(state));
		Locks.LOGGER.debug("LockChestsFeature: placing lock at {} — item={} lootScaled={}", pos, ForgeRegistries.ITEMS.getKey(stack.getItem()), usedLootScaling);
		Lockable lkb = new Lockable(new Cuboid6i(pos, pos1), Lock.from(stack), Transform.fromDirection(state.getValue(ChestBlock.FACING), Direction.NORTH), stack, world.getLevel());
		lkb.bb.getContainedChunks((x, z) ->
		{
			((ILockableProvider) world.getChunk(x, z)).getLockables().add(lkb);
			return false;
		});
		return true;
	}
}
