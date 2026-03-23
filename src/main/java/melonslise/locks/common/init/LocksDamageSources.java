package melonslise.locks.common.init;

import melonslise.locks.Locks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public final class LocksDamageSources
{
	public static final ResourceKey<DamageType> SHOCK = ResourceKey.create(
		Registries.DAMAGE_TYPE, new ResourceLocation(Locks.ID, "shock"));

	private LocksDamageSources() {}

	public static DamageSource shock(Level level)
	{
		return new DamageSource(level.registryAccess()
			.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SHOCK));
	}
}
