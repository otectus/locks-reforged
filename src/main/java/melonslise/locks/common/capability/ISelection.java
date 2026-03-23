package melonslise.locks.common.capability;

import net.minecraft.core.BlockPos;

public interface ISelection
{
	BlockPos get();

	void set(BlockPos pos);
}
