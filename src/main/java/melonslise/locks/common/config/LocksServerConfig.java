package melonslise.locks.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import melonslise.locks.Locks;
import melonslise.locks.common.init.LocksBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import melonslise.locks.common.init.LocksEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
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
	public static final ForgeConfigSpec.BooleanValue HIDE_HUD_TOOLTIP;

	public static final ForgeConfigSpec.BooleanValue ENABLE_SHOCKING;
	public static final ForgeConfigSpec.BooleanValue ENABLE_STURDY;
	public static final ForgeConfigSpec.BooleanValue ENABLE_COMPLEXITY;
	public static final ForgeConfigSpec.BooleanValue ENABLE_SILENT;
	public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_PICK;
	public static final ForgeConfigSpec.BooleanValue ENABLE_REINFORCED;
	public static final ForgeConfigSpec.BooleanValue ENABLE_AWARENESS;

	// Shocking enchantment tuning
	public static final ForgeConfigSpec.DoubleValue SHOCKING_DAMAGE_BASE;
	public static final ForgeConfigSpec.DoubleValue SHOCKING_DAMAGE_PER_LEVEL;
	public static final ForgeConfigSpec.DoubleValue SHOCKING_MAX_DAMAGE;
	public static final ForgeConfigSpec.BooleanValue SHOCKING_REQUIRES_ENCHANTMENT;
	public static final ForgeConfigSpec.IntValue SHOCKING_COOLDOWN_TICKS;
	public static final ForgeConfigSpec.BooleanValue SHOCKING_ON_PICK_BREAK;
	public static final ForgeConfigSpec.BooleanValue SHOCKING_ON_WRONG_PIN;
	public static final ForgeConfigSpec.BooleanValue SHOCKING_ON_UNAUTHORIZED_INTERACTION;
	public static final ForgeConfigSpec.BooleanValue SHOCKING_ON_BLOCK_BREAK_ATTEMPT;

	public static final ForgeConfigSpec.BooleanValue NETHERITE_PICK_UNBREAKABLE;

	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOOT_TABLE_PATTERNS;

	// Trades
	public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_TRADES;
	public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_LOCKPICK_TRADES;
	public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_LOCK_TRADES;
	public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_MECHANISM_TRADES;
	public static final ForgeConfigSpec.ConfigValue<String> VILLAGER_PROFESSION;
	public static final ForgeConfigSpec.BooleanValue ENABLE_WANDERER_TRADES;
	public static final ForgeConfigSpec.BooleanValue ENABLE_WANDERER_LOCKPICK_TRADES;
	public static final ForgeConfigSpec.BooleanValue ENABLE_WANDERER_LOCK_TRADES;
	public static final ForgeConfigSpec.BooleanValue ENABLE_WANDERER_MECHANISM_TRADES;

	public static Pattern[] lockableBlocks;
	public static List<TagKey<Block>> lockableTags;
	public static String[][] lootTablePatterns;

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
		HIDE_HUD_TOOLTIP = cfg
			.comment("Hide the floating HUD tooltip entirely when looking at a lock in the world (hides item name, enchantments, and all other info)")
			.define("Hide HUD Tooltip", false);
		cfg.pop();

		cfg.comment("Enable or disable individual lock enchantments. Disabled enchantments will not appear in the enchanting table, trades, or loot, and their effects will be ignored on existing items.").push("Enchantments");
		ENABLE_SHOCKING = cfg
			.comment("Damages the player when a lock pick breaks")
			.define("Enable Shocking", true);
		ENABLE_STURDY = cfg
			.comment("Makes locks harder to pick (reduces lock pick break chance)")
			.define("Enable Sturdy", true);
		ENABLE_COMPLEXITY = cfg
			.comment("Restricts which lock picks can open the lock")
			.define("Enable Complexity", true);
		ENABLE_SILENT = cfg
			.comment("Suppresses the rattle sound when accessing a locked block without a key")
			.define("Enable Silent", true);
		ENABLE_AUTO_PICK = cfg
			.comment("Chance to instantly unlock without the lock picking minigame")
			.define("Enable Auto Pick", true);
		ENABLE_REINFORCED = cfg
			.comment("Increases explosion resistance of locked blocks")
			.define("Enable Reinforced", true);
		ENABLE_AWARENESS = cfg
			.comment("Remembers who placed the lock; that player can open it without a key")
			.define("Enable Awareness", true);

		cfg.comment("Tuning for the Shocking enchantment and the theft punishments it enables.",
				"By default, Shocking deals 1.5 damage per enchantment level when a lock pick breaks.",
				"Final damage = Damage Base + level * Damage Per Level, clamped to Max Damage.").push("Shocking");
		SHOCKING_DAMAGE_BASE = cfg
			.comment("Flat shock damage dealt regardless of enchantment level (2.0 = 1 heart).")
			.defineInRange("Shocking Damage Base", 0.0d, 0.0d, 1024.0d);
		SHOCKING_DAMAGE_PER_LEVEL = cfg
			.comment("Shock damage added per level of the Shocking enchantment. Default 1.5 reproduces the original behavior (level * 1.5).")
			.defineInRange("Shocking Damage Per Level", 1.5d, 0.0d, 1024.0d);
		SHOCKING_MAX_DAMAGE = cfg
			.comment("Upper clamp on shock damage after Base + level * Per Level. The default is effectively uncapped.")
			.defineInRange("Shocking Max Damage", 1024.0d, 0.0d, 1024.0d);
		SHOCKING_REQUIRES_ENCHANTMENT = cfg
			.comment("If true, only locks that actually carry the Shocking enchantment can shock players.",
				"If false, every lock shocks (treated as level 1 when unenchanted) — useful for punishing all theft attempts.")
			.define("Shocking Requires Enchantment", true);
		SHOCKING_COOLDOWN_TICKS = cfg
			.comment("Minimum ticks between shocks dealt to the same player (20 ticks = 1 second). 0 = no cooldown (original behavior).",
				"Raise this (e.g. to 20) when enabling the interaction or wrong-pin triggers below to avoid shock spam.")
			.defineInRange("Shocking Cooldown Ticks", 0, 0, 72000);
		SHOCKING_ON_PICK_BREAK = cfg
			.comment("Shock the player when their lock pick breaks while picking. This is the original Shocking behavior.")
			.define("Shocking Triggers On Pick Break", true);
		SHOCKING_ON_WRONG_PIN = cfg
			.comment("Shock the player each time they set a wrong pin while picking, even if the pick does not break.")
			.define("Shocking Triggers On Wrong Pin", false);
		SHOCKING_ON_UNAUTHORIZED_INTERACTION = cfg
			.comment("Shock the player when they interact with a locked block without the correct key, lock pick or key ring (the 'rattle').")
			.define("Shocking Triggers On Unauthorized Interaction", false);
		SHOCKING_ON_BLOCK_BREAK_ATTEMPT = cfg
			.comment("Shock the player when they attempt to break a protected locked block. Requires 'Protect Lockables' to be enabled.")
			.define("Shocking Triggers On Block Break Attempt", false);
		cfg.pop(); // Shocking

		cfg.pop();

		NETHERITE_PICK_UNBREAKABLE = cfg
			.comment("When enabled, netherite lock picks never lose durability or break during lock picking")
			.define("Netherite Lockpick Unbreakable", false);

		LOOT_TABLE_PATTERNS = cfg
			.comment("Loot tables matching these patterns will receive lock pick / key loot injection.",
				"Each entry is 'namespace:path_prefix' (e.g. 'minecraft:chests/' matches all vanilla chest loot tables).",
				"Add entries for modded namespaces to inject lock picks into modded dungeon chests.")
			.defineList("Loot Table Injection Patterns", Lists.newArrayList("minecraft:chests/"), e -> e instanceof String);

		cfg.comment("Configure which lock-related items villagers and wandering traders sell.",
				"Existing default trades are unchanged. Lock picks can be disabled independently of locks and mechanisms,",
				"so you can remove easy lock picks while still letting players buy locks for early-game chest protection.").push("Trades");
		cfg.push("Villager");
		ENABLE_VILLAGER_TRADES = cfg
			.comment("Master switch for all lock-related trades offered by the lock villager profession.")
			.define("Enable Villager Trades", true);
		ENABLE_VILLAGER_LOCKPICK_TRADES = cfg
			.comment("Allow the lock villager to sell lock picks. Set this to false to remove lock pick sales (they can be considered overpowered) while keeping lock and mechanism sales.")
			.define("Enable Villager Lockpick Trades", true);
		ENABLE_VILLAGER_LOCK_TRADES = cfg
			.comment("Allow the lock villager to sell finished locks (wood/copper/iron at trade levels 1-3).",
				"Disabled by default — enable it to make early-game chest protection purchasable from villagers.")
			.define("Enable Villager Lock Trades", false);
		ENABLE_VILLAGER_MECHANISM_TRADES = cfg
			.comment("Allow the lock villager to sell lock mechanisms (crafting components).")
			.define("Enable Villager Lock Mechanism Trades", true);
		VILLAGER_PROFESSION = cfg
			.comment("Which villager profession offers lock trades. Format 'namespace:profession' (e.g. 'minecraft:toolsmith', 'minecraft:librarian').")
			.define("Villager Profession", "minecraft:toolsmith");
		cfg.pop(); // Villager
		cfg.push("Wandering Trader");
		ENABLE_WANDERER_TRADES = cfg
			.comment("Master switch for all lock-related trades offered by the wandering trader.")
			.define("Enable Wandering Trader Trades", true);
		ENABLE_WANDERER_LOCKPICK_TRADES = cfg
			.comment("Allow the wandering trader to sell lock picks (gold and steel).")
			.define("Enable Wandering Trader Lockpick Trades", true);
		ENABLE_WANDERER_LOCK_TRADES = cfg
			.comment("Allow the wandering trader to sell finished, enchanted locks (steel/diamond/netherite).")
			.define("Enable Wandering Trader Lock Trades", true);
		ENABLE_WANDERER_MECHANISM_TRADES = cfg
			.comment("Allow the wandering trader to sell lock mechanisms (steel).")
			.define("Enable Wandering Trader Lock Mechanism Trades", true);
		cfg.pop(); // Wandering Trader
		cfg.pop(); // Trades

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
			{
				Locks.LOGGER.warn("Invalid lockable tag entry '{}' — not a valid resource location, skipping", s);
				continue;
			}
			TagKey<Block> tag = TagKey.create(Registries.BLOCK, loc);
			if(!tags.contains(tag))
				tags.add(tag);
		}
		lockableTags = tags;

		lootTablePatterns = LOOT_TABLE_PATTERNS.get().stream()
			.map(s -> {
				int colon = s.indexOf(':');
				if(colon < 0)
					return new String[] { "", s };
				return new String[] { s.substring(0, colon), s.substring(colon + 1) };
			}).toArray(String[][]::new);
	}

	public static boolean isEnchantmentEnabled(Enchantment enchantment)
	{
		if (enchantment == LocksEnchantments.SHOCKING.get()) return ENABLE_SHOCKING.get();
		if (enchantment == LocksEnchantments.STURDY.get()) return ENABLE_STURDY.get();
		if (enchantment == LocksEnchantments.COMPLEXITY.get()) return ENABLE_COMPLEXITY.get();
		if (enchantment == LocksEnchantments.SILENT.get()) return ENABLE_SILENT.get();
		if (enchantment == LocksEnchantments.AUTO_PICK.get()) return ENABLE_AUTO_PICK.get();
		if (enchantment == LocksEnchantments.REINFORCED.get()) return ENABLE_REINFORCED.get();
		if (enchantment == LocksEnchantments.AWARENESS.get()) return ENABLE_AWARENESS.get();
		return true;
	}

	public static boolean matchesLootTablePattern(ResourceLocation name)
	{
		if(lootTablePatterns == null)
			return false;
		for(String[] pattern : lootTablePatterns)
			if(name.getNamespace().equals(pattern[0]) && name.getPath().startsWith(pattern[1]))
				return true;
		return false;
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
