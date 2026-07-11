package melonslise.locks.common.steel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.crafting.conditions.ICondition;

/**
 * {@link SteelTagView} backed by an {@code ICondition.IContext}, used while recipes are being parsed. Recipe
 * conditions must recompute from the context (not read the cached snapshot) because the snapshot may not yet be
 * refreshed during datapack parsing. Only item tags are consulted here — the steel item tags are all that
 * recipe gating needs, and item ore tags cover native ore detection for completeness.
 */
public final class ConditionTagView implements SteelTagView
{
	private static final TagKey<Item> INGOTS = TagKey.create(Registries.ITEM, NativeSteelPolicy.TAG_INGOTS_STEEL);
	private static final TagKey<Item> NUGGETS = TagKey.create(Registries.ITEM, NativeSteelPolicy.TAG_NUGGETS_STEEL);
	private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, NativeSteelPolicy.TAG_ORES_STEEL);

	private final ICondition.IContext context;

	public ConditionTagView(ICondition.IContext context)
	{
		this.context = context;
	}

	@Override
	public Collection<ResourceLocation> ingots()
	{
		return members(INGOTS);
	}

	@Override
	public Collection<ResourceLocation> nuggets()
	{
		return members(NUGGETS);
	}

	@Override
	public Collection<ResourceLocation> ores()
	{
		return members(ORES);
	}

	private Collection<ResourceLocation> members(TagKey<Item> tag)
	{
		List<ResourceLocation> ids = new ArrayList<>();
		for (Holder<Item> holder : context.getTag(tag))
			holder.unwrapKey().ifPresent(key -> ids.add(key.location()));
		return ids;
	}
}
