package melonslise.locks.common.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SerializableCapabilityProvider<A extends INBTSerializable<T>, T extends Tag> implements ICapabilitySerializable<T>
{
	protected final Capability<? super A> cap;
	protected final A inst;
	protected final LazyOptional<A> lazyInst;

	public SerializableCapabilityProvider(Capability<? super A> cap, A inst)
	{
		this.cap = cap;
		this.inst = inst;
		this.lazyInst = LazyOptional.of(() -> this.inst);
	}

	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	public <U> LazyOptional<U> getCapability(@Nonnull Capability<U> queryCap, @Nullable Direction side)
	{
		return ((Capability<A>) this.cap).orEmpty(queryCap, this.lazyInst);
	}

	@Override
	public T serializeNBT()
	{
		return this.inst.serializeNBT();
	}

	@Override
	public void deserializeNBT(T nbt)
	{
		this.inst.deserializeNBT(nbt);
	}
}
