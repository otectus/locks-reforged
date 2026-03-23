package melonslise.locks.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import net.minecraft.world.level.chunk.ProtoChunk;

@Mixin(ProtoChunk.class)
public class ProtoChunkMixin implements ILockableProvider
{
	private final List<Lockable> lockableList = new ArrayList<>();

	@Override
	public List<Lockable> getLockables()
	{
		return this.lockableList;
	}
}
