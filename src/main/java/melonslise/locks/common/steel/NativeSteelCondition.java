package melonslise.locks.common.steel;

import java.util.Locale;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;

/**
 * A recipe/advancement condition ({@code "type": "locks:steel"}) that gates a recipe on the current
 * {@link NativeSteelPolicy} decision. Unlike Forge's stock {@code forge:tag_empty}, it filters out Locks' own
 * tag entries, so it can tell whether a <em>foreign</em> mod actually provides steel even though Locks always
 * populates {@code forge:ingots/steel}.
 *
 * <p>Usage — one {@code query} per condition:
 * <pre>{@code
 *   { "type": "locks:steel", "query": "native_ingot" }      // load only when the native ingot is active
 *   { "type": "locks:steel", "query": "native_nugget" }     // load only when the native nugget is active
 *   { "type": "locks:steel", "query": "steel_available" }   // load when any steel ingot exists (native or foreign)
 * }</pre>
 * Negate with the stock {@code forge:not} wrapper (e.g. the iron-mechanism fallback loads when steel is absent).
 */
public final class NativeSteelCondition implements ICondition
{
	public static final ResourceLocation NAME = new ResourceLocation(Locks.ID, "steel");

	public enum Query
	{
		/** Native steel ingot fallback is active. */
		NATIVE_INGOT,
		/** Native steel nugget fallback is active. */
		NATIVE_NUGGET,
		/** Native steel ore generation is active. */
		NATIVE_ORE,
		/** A steel ingot exists at all (native fallback or any foreign provider). */
		STEEL_AVAILABLE
	}

	private final Query query;

	public NativeSteelCondition(Query query)
	{
		this.query = query;
	}

	@Override
	public ResourceLocation getID()
	{
		return NAME;
	}

	@Override
	public boolean test(IContext context)
	{
		NativeSteelState state = NativeSteelPolicy.compute(new ConditionTagView(context), currentMode());
		switch (query)
		{
			case NATIVE_INGOT:
				return state.nativeIngotActive();
			case NATIVE_NUGGET:
				return state.nativeNuggetActive();
			case NATIVE_ORE:
				return state.nativeOreActive();
			case STEEL_AVAILABLE:
			default:
				return state.steelAvailable();
		}
	}

	/**
	 * The configured steel mode, defaulting to {@link SteelMaterialMode#AUTO} if the SERVER config is not loaded
	 * yet (recipe conditions can be evaluated in contexts where the server spec has not been populated).
	 */
	public static SteelMaterialMode currentMode()
	{
		try
		{
			if (LocksServerConfig.SPEC.isLoaded())
				return LocksServerConfig.STEEL_MATERIAL_MODE.get();
		}
		catch (RuntimeException ignored)
		{
			// Config not available in this context — fall through to AUTO.
		}
		return SteelMaterialMode.AUTO;
	}

	@Override
	public String toString()
	{
		return "locks:steel(" + query.name().toLowerCase(Locale.ROOT) + ")";
	}

	public static final class Serializer implements IConditionSerializer<NativeSteelCondition>
	{
		public static final Serializer INSTANCE = new Serializer();

		@Override
		public void write(JsonObject json, NativeSteelCondition value)
		{
			json.addProperty("query", value.query.name().toLowerCase(Locale.ROOT));
		}

		@Override
		public NativeSteelCondition read(JsonObject json)
		{
			String raw = GsonHelper.getAsString(json, "query");
			Query query;
			try
			{
				query = Query.valueOf(raw.toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException e)
			{
				throw new com.google.gson.JsonSyntaxException("Unknown locks:steel condition query '" + raw + "'");
			}
			return new NativeSteelCondition(query);
		}

		@Override
		public ResourceLocation getID()
		{
			return NAME;
		}
	}
}
