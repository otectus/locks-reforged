# Locks Reforged

An unofficial port of the **Locks** mod by [Melonslise](https://github.com/Melonslise) to **Minecraft Forge 1.20.1**.

> Locks is a small, but unique Minecraft mod that introduces flexible and universal locks which can be dynamically attached to multiple blocks of any kind, including other mods, a fun lock picking mechanic as well as lots of other little, but useful tools and utilities.

## Credits

- **Original Author:** [Melonslise](https://github.com/Melonslise) (MercurialPony)
- **Original Repository:** [github.com/Melonslise/Locks](https://github.com/Melonslise/Locks)
- **CurseForge:** [curseforge.com/projects/locks](https://minecraft.curseforge.com/projects/locks)
- **Textures:** Hoonts, Artsy (ydgy) from the RLCraft community
- **Sounds:** [freesound.org](https://freesound.org)
- **Coding help:** Diesieben07, Choonster, Gigaherz, Tterrag, McJty, and many others from the MMD community (see `mods.toml` for full credits)

This port preserves the original mod's license: **Attribution-NonCommercial 3.0 Unported (CC BY-NC 3.0)**.

## About This Port

The original Locks mod was built for Minecraft 1.16.5 (Forge 36.x). This port updates it to Minecraft 1.20.1 (Forge 47.x) while preserving all original gameplay mechanics, item IDs, config keys, and network protocol.

**Port Version:** 4.0.0 | **Minecraft:** 1.20.1 | **Forge:** 47.2.0+ | **Java:** 17

## Features

### Universal Lock System
Locks can be dynamically attached to **any** block in the game -- not just chests. This includes doors, trapdoors, furnaces, dispensers, hoppers, and blocks from other mods. Locks are spatial, meaning a single lock can cover multiple blocks at once.

### Data-Driven Locks & Lock Picks
All lock and lock pick types are defined via JSON files, making it easy to add custom items or tweak stats without touching code. The mod ships with five default tiers of each, but you can add your own through the config folder or override stats via datapacks.

### Items

| Category | Items |
|----------|-------|
| **Locks** | Wood Lock, Iron Lock, Steel Lock, Gold Lock, Diamond Lock (+ custom) |
| **Keys** | Key Blank, Key, Master Key, Key Ring |
| **Lock Picks** | Wood, Bobby Pin (copper), Iron, Steel, Gold, Diamond (+ custom) |
| **Components** | Spring, Wood/Iron/Steel Lock Mechanisms |

### Lock Picking Minigame
An interactive lock picking mechanic with a pin-matching system. Each lock has a unique combination based on its complexity. Higher-tier lock picks are more effective against tougher locks.

### Enchantments

| Enchantment | Max Level | Effect |
|-------------|-----------|--------|
| **Shocking** | V | Electrocutes players who fail to pick the lock (bypasses armor) |
| **Sturdy** | III | Reduces lock pick effectiveness against the lock |
| **Complexity** | III | Makes the lock impossible to pick with lower-tier lock picks |
| **Silent** | I | Suppresses the rattle sound when access is denied. Incompatible with Shocking |
| **Auto-Pick** | III | 10%/20%/30% chance to instantly open the lock, bypassing the minigame. Incompatible with Complexity |
| **Reinforced** | III | Increases explosion resistance by 50%/100%/150%. Protects against TNT and creepers |

Each enchantment can be individually enabled or disabled in the server config.

### World Generation
Locked chests spawn naturally in structures. By default, lock tier is determined by **loot value** — chests with better loot get stronger locks. The system samples each loot table multiple times and averages the results for consistent tier assignments, uses sub-linear stack count scaling so bulk common items don't inflate value, and supports per-item value overrides for materials like diamonds and netherite that are valuable but have common rarity. Can be switched to random weighted selection in the common config.

### Villager & Wandering Trader Integration
Toolsmith villagers sell lock picks and lock mechanisms at various profession levels. Wandering traders offer rare lock picks and enchanted locks.

### Loot Table Integration
Lock picks and lock mechanisms can be found in dungeon, temple, and other structure chests.

### Protection Features
- Locked blocks resist redstone activation
- Locked containers block hopper extraction
- Locked blocks resist piston movement
- Locked blocks resist explosion damage
- Configurable break protection for locked blocks

## Custom Locks & Lock Picks

Locks Reforged uses a two-tier data-driven system for defining lock and lock pick items.

### Tier 1: Item Definitions (loaded at startup)

JSON files that define new items. These are read during mod initialization, before the game starts.

**Mod defaults** are shipped in the JAR under `data/locks/lock_types/` and `data/locks/lockpick_types/`.

**Custom items** go in the game's config folder:
- `config/locks/lock_types/<name>.json` — registers a new lock item as `locks:<name>`
- `config/locks/lockpick_types/<name>.json` — registers a new lock pick item as `locks:<name>`

On first launch, the mod creates these directories with a `_example.json.disabled` template in each.

**Lock definition schema:**
```json
{
  "length": 7,
  "enchantment_value": 14,
  "resistance": 12
}
```

| Field | Description |
|-------|-------------|
| `length` | Number of pins in the lock picking minigame |
| `enchantment_value` | Enchantability (higher = better enchantments) |
| `resistance` | Damage resistance of the lock |

**Lock pick definition schema:**
```json
{
  "strength": 0.35
}
```

| Field | Description |
|-------|-------------|
| `strength` | Pick effectiveness (0.0–1.0, higher = stronger) |

**Important:** Custom items added via config also need:
- A model JSON in a resource pack (`assets/locks/models/item/<name>.json`)
- A texture (`assets/locks/textures/item/<name>.png`)
- A GUI texture for the lock picking screen (`assets/locks/textures/gui/<name>.png`) — falls back to the iron lock texture if missing
- A recipe (via datapack)
- Tag entries in `locks:locks` or `locks:lock_picks` (via datapack) for the mod to recognize them

### Tier 2: Stat Overrides (loaded per-world via datapacks)

Datapacks can override the stats of already-registered items without restarting the game. Overrides are applied on world load and on `/reload`.

Place override files at:
- `data/<namespace>/locks/lock_stat_overrides/<name>.json`
- `data/<namespace>/locks/lockpick_stat_overrides/<name>.json`

**Lock stat override schema:**
```json
{
  "item": "locks:iron_lock",
  "length": 3,
  "resistance": 50
}
```

Only fields present in the override are changed; omitted fields keep their default values. The `item` field is required and specifies which registered item to modify.

**Lock pick stat override schema:**
```json
{
  "item": "locks:iron_lock_pick",
  "strength": 0.5
}
```

> **Note:** Stat overrides cannot create new items — they can only modify items that were already registered at startup. Existing items in the world that have already had their stats baked into NBT (e.g., a lock whose length was written on first placement) will retain their original values.

## Configuration

### Common Config (`locks-common.toml`)
- **Generation Chance** -- Probability of locks spawning on generated chests (default: 85%, only used when loot-scaled locks is disabled)
- **Enchant Chance** -- Probability of generated locks being enchanted (default: 40%)
- **Lock Types & Weights** -- Which locks generate and their relative rarity
- **Randomize Loaded Locks** -- Whether to randomize lock combinations on chunk load
- **Loot-Scaled Locks** -- When enabled (default), lock tier is chosen based on chest loot value instead of random selection. Configurable item value formula with rarity multipliers, enchantment bonuses, per-tier value thresholds, multi-sample averaging (default: 32 samples), sub-linear stack count scaling, and per-item value overrides.

### Client Config (`locks-client.toml`)
- **Deaf Mode** -- Enables visual feedback for the lock picking mechanic

### Server Config (`locks-server.toml`)
- **Allow Removing Locks** -- Whether players can remove unlocked locks by shift-right-clicking
- **Protect Lockables** -- Whether locked blocks are protected from being broken
- **Hide Lock ID** -- Hides the lock ID line from both inventory and HUD tooltips (default: false)
- **Hide HUD Enchantments** -- Hides enchantment lines from the HUD floating tooltip only; inventory tooltips are unaffected (default: false)
- **Enchantment Toggles** -- Each of the 6 enchantments (Shocking, Sturdy, Complexity, Silent, Auto-Pick, Reinforced) can be individually enabled or disabled

## Building from Source

**Requirements:** JDK 17

```bash
# Clone the repository
git clone <repo-url>
cd "Locks Reforged"

# Build the mod JAR
JAVA_HOME="/path/to/jdk-17" ./gradlew build

# Output: build/libs/locks-4.0.0.jar

# Run the development client
JAVA_HOME="/path/to/jdk-17" ./gradlew runClient
```

## Installation

1. Install [Minecraft Forge 1.20.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) (47.2.0 or later)
2. Download `locks-4.0.0.jar` from the releases
3. Place the JAR in your `.minecraft/mods/` folder
4. Launch Minecraft with the Forge profile

## Technical Details

This port involved updating approximately 90 Java source files across the following API changes:

- **Registry System** -- Migrated to deferred lambda suppliers for all registry entries
- **Capabilities** -- Updated to `ForgeCapabilities` and `CapabilityToken` pattern
- **World Generation** -- Converted to data-driven JSON biome modifiers
- **Damage Types** -- Migrated to data-driven damage type system
- **Rendering** -- Updated to GuiGraphics API and JOML math library
- **Creative Tabs** -- Migrated from `ItemGroup` to `CreativeModeTab` via `DeferredRegister`
- **Mixins** -- All 15 mixins updated for 1.20.1 class/method changes, including a workaround for `SignalGetter` interface default methods
- **Loot Tables** -- Reimplemented injection via `LootTableLoadEvent`

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for the full list of changes and current testing status.

## License

This project is distributed under the **Attribution-NonCommercial 3.0 Unported (CC BY-NC 3.0)** license, consistent with the original Locks mod.
