package melonslise.locks.common.init;

import melonslise.locks.Locks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class LocksBlockTags
{
	private LocksBlockTags() {}

	public static final TagKey<Block>
		LOCKABLE = bind("lockable");

	public static TagKey<Block> bind(String name)
	{
		return BlockTags.create(new ResourceLocation(Locks.ID, name));
	}
}
