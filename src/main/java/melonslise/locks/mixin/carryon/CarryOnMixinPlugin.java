package melonslise.locks.mixin.carryon;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/**
 * Gates the {@code locks_carryon.mixins.json} config so its mixins only apply when the Carry On mod
 * ("carryon") is present. Locks Reforged has no compile-time dependency on Carry On, so these mixins
 * target Carry On classes by name; without this guard they would fail to resolve their target class
 * when Carry On is absent. Uses {@link LoadingModList} because this runs during early mixin bootstrap,
 * before {@code ModList} is available.
 */
public class CarryOnMixinPlugin implements IMixinConfigPlugin
{
	private static boolean carryOnPresent()
	{
		try
		{
			return LoadingModList.get() != null && LoadingModList.get().getModFileById("carryon") != null;
		}
		catch (Throwable t)
		{
			return false;
		}
	}

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig()
	{
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
	{
		return carryOnPresent();
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins()
	{
		return null;
	}

	@Override
	public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
