package melonslise.locks.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import melonslise.locks.common.init.LocksBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

public class LocksServerConfig
{
	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.IntValue MAX_LOCKABLE_VOLUME;
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOCKABLE_BLOCKS;
	public static final ForgeConfigSpec.BooleanValue ALLOW_REMOVING_LOCKS;
	public static final ForgeConfigSpec.BooleanValue PROTECT_LOCKABLES;
	public static final ForgeConfigSpec.BooleanValue EASY_LOCK;

	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOCKABLE_TAGS;

	public static final ForgeConfigSpec.BooleanValue HIDE_LOCK_ID;
	public static final ForgeConfigSpec.BooleanValue HIDE_HUD_ENCHANTMENTS;

	public static Pattern[] lockableBlocks;
	public static List<TagKey<Block>> lockableTags;

	static
	{
		ForgeConfigSpec.Builder cfg = new ForgeConfigSpec.Builder();

		MAX_LOCKABLE_VOLUME = cfg
			.comment("Maximum amount of blocks that can be locked at once")
			.defineInRange("Max Lockable Volume", 6, 1, Integer.MAX_VALUE);
		LOCKABLE_BLOCKS = cfg
			.comment("Blocks that can be locked. Each entry is the mod domain followed by the block's registry name. Can include regular expressions")
			.defineList("Lockable Blocks", Lists.newArrayList(".*chest", ".*barrel", ".*hopper", ".*door", ".*trapdoor", ".*fence_gate", ".*shulker_box"), e -> e instanceof String);
		LOCKABLE_TAGS = cfg
			.comment("Block tags whose members can be locked. Each entry is a tag resource location (e.g. 'locks:lockable'). The 'locks:lockable' tag is always included")
			.defineList("Lockable Tags", Lists.newArrayList("locks:lockable"), e -> e instanceof String);
		ALLOW_REMOVING_LOCKS = cfg
			.comment("Open locks can be removed with an empty hand while sneaking")
			.define("Allow Removing Locks", true);
		PROTECT_LOCKABLES = cfg
			.comment("Locked blocks cannot be destroyed in survival mode")
			.define("Protect Lockables", true);
		EASY_LOCK = cfg
			.comment("Lock blocks with just one click! It's magic! (Will probably fail spectacularly with custom doors, custom double chests, etc)")
			.define("Easy Lock", true);

		cfg.push("Display");
		HIDE_LOCK_ID = cfg
			.comment("Hide the lock ID line from tooltips (both inventory and HUD)")
			.define("Hide Lock ID", false);
		HIDE_HUD_ENCHANTMENTS = cfg
			.comment("Hide enchantment lines from the HUD floating tooltip (inventory tooltips are unaffected)")
			.define("Hide HUD Enchantments", false);
		cfg.pop();

		SPEC = cfg.build();
	}

	private LocksServerConfig() {}

	public static void init()
	{
		lockableBlocks = LOCKABLE_BLOCKS.get().stream().map(s -> Pattern.compile(s)).toArray(Pattern[]::new);

		List<TagKey<Block>> tags = new ArrayList<>();
		tags.add(LocksBlockTags.LOCKABLE);
		for(String s : LOCKABLE_TAGS.get())
		{
			ResourceLocation loc = ResourceLocation.tryParse(s);
			if(loc == null)
				continue;
			TagKey<Block> tag = TagKey.create(Registries.BLOCK, loc);
			if(!tags.contains(tag))
				tags.add(tag);
		}
		lockableTags = tags;
	}

	public static boolean canLock(Level world, BlockPos pos)
	{
		BlockState state = world.getBlockState(pos);
		for(TagKey<Block> tag : lockableTags)
			if(state.is(tag))
				return true;
		String name = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
		for(Pattern p : lockableBlocks)
			if(p.matcher(name).matches())
				return true;
		return false;
	}
}
