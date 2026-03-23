package melonslise.locks.common.capability;

import melonslise.locks.Locks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

public class Selection implements ISelection
{
	public static final ResourceLocation ID = new ResourceLocation(Locks.ID, "selection");

	public BlockPos pos;

	@Override
	public BlockPos get()
	{
		return this.pos;
	}

	@Override
	public void set(BlockPos pos)
	{
		this.pos = pos;
	}
}
