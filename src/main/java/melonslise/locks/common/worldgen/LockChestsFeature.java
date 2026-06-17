package melonslise.locks.common.worldgen;

import com.mojang.serialization.Codec;

import melonslise.locks.Locks;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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

		Lockable lkb = LocksUtil.createChestLockable(world, world.getLevel(), pos, rng);
		if (lkb == null)
		{
			Locks.LOGGER.debug("LockChestsFeature: skipping {} — no lock generated", pos);
			return false;
		}

		Locks.LOGGER.debug("LockChestsFeature: placing lock at {} — item={}", pos, ForgeRegistries.ITEMS.getKey(lkb.stack.getItem()));
		lkb.bb.getContainedChunks((x, z) ->
		{
			((ILockableProvider) world.getChunk(x, z)).getLockables().add(lkb);
			return false;
		});
		return true;
	}
}
