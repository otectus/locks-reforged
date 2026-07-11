package melonslise.locks.common.steel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * {@link SteelTagView} backed by a live {@link RegistryAccess}, used after tags load (e.g. from
 * {@code TagsUpdatedEvent}). For ores it reads <em>both</em> the item tag and the block tag {@code forge:ores/steel}
 * so a foreign steel ore that only tags its block is still detected.
 */
public final class RegistryTagView implements SteelTagView
{
	private final RegistryAccess access;

	public RegistryTagView(RegistryAccess access)
	{
		this.access = access;
	}

	@Override
	public Collection<ResourceLocation> ingots()
	{
		return itemMembers(NativeSteelPolicy.TAG_INGOTS_STEEL);
	}

	@Override
	public Collection<ResourceLocation> nuggets()
	{
		return itemMembers(NativeSteelPolicy.TAG_NUGGETS_STEEL);
	}

	@Override
	public Collection<ResourceLocation> ores()
	{
		List<ResourceLocation> out = new ArrayList<>(itemMembers(NativeSteelPolicy.TAG_ORES_STEEL));
		out.addAll(blockMembers(NativeSteelPolicy.TAG_ORES_STEEL));
		return out;
	}

	private Collection<ResourceLocation> itemMembers(ResourceLocation tagId)
	{
		return members(Registries.ITEM, TagKey.create(Registries.ITEM, tagId));
	}

	private Collection<ResourceLocation> blockMembers(ResourceLocation tagId)
	{
		return members(Registries.BLOCK, TagKey.create(Registries.BLOCK, tagId));
	}

	private <T> Collection<ResourceLocation> members(ResourceKey<? extends Registry<T>> registryKey, TagKey<T> tag)
	{
		List<ResourceLocation> ids = new ArrayList<>();
		access.registry(registryKey).ifPresent(registry ->
			registry.getTag(tag).ifPresent(named ->
			{
				for (Holder<T> holder : named)
					holder.unwrapKey().ifPresent(key -> ids.add(key.location()));
			}));
		return ids;
	}
}
