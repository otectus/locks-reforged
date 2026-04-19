# Locks Reforged

**An unofficial port of the Locks mod by [Melonslise](https://github.com/Melonslise) to Minecraft Forge 1.20.1.**

Locks is a small but unique mod that lets you attach flexible, universal locks to any block in the game — including blocks from other mods. Protect your doors, chests, furnaces, and more with a dynamic locking system and an interactive lock picking minigame.

---

## Features

### Lock Any Block
Attach locks to **any block** — not just chests. Doors, trapdoors, furnaces, dispensers, hoppers, and modded blocks all work. Locks are spatial, so a single lock can cover multiple blocks at once.

### Seven Tiers of Locks (+ Custom!)

| Tier | Pins | Enchantability | Resistance | Pick Strength |
|------|------|----------------|------------|---------------|
| Wood | 5 | 15 | 4 | 0.20 |
| Copper | 6 | 16 | 8 | 0.28 |
| Iron | 7 | 14 | 12 | 0.35 |
| Gold | 6 | 22 | 6 | 0.25 |
| Steel | 9 | 12 | 20 | 0.70 |
| Diamond | 11 | 10 | 100 | 0.85 |
| Netherite | 14 | 8 | 200 | 0.95 |

- **Pins** — Number of pins in the lock picking minigame. More pins = harder to pick.
- **Enchantability** — How likely the lock is to receive powerful enchantments.
- **Resistance** — Explosion resistance. Diamond and Netherite locks are virtually indestructible.
- **Pick Strength** — How effective the matching lock pick is. Higher = better.

Netherite items are **fire-resistant** and survive in lava, just like vanilla netherite gear. They're crafted at a smithing table using a Netherite Upgrade Template.

All locks and lock picks are **fully data-driven** — add your own custom tiers via simple JSON files, or tweak existing stats through config files, TOML overrides, or datapacks. No code changes needed!

### Lock Picking Minigame
Pick locks with an interactive pin-matching minigame. Match each pin to crack the combination. Higher-tier picks are more effective against tougher locks.

**Lock Picks:** Wood, Bobby Pin (copper), Iron, Steel, Gold, Diamond, Netherite (+ custom)

### Keys & Key Rings
Craft **keys** that match your locks, or use a **Key Ring** to carry multiple keys. A **Master Key** opens any lock.

### Seven Enchantments
- **Shocking** — Zaps players who fail a pick attempt (bypasses armor)
- **Sturdy** — Reduces pick effectiveness
- **Complexity** — Blocks lower-tier picks entirely
- **Silent** — Suppresses the rattle sound when access is denied
- **Auto-Pick** — Chance to instantly open the lock, bypassing the minigame
- **Reinforced** — Increases explosion resistance per level
- **Awareness** — Remembers who placed the lock; that player can open it without a key

Each enchantment can be individually enabled or disabled in the server config.

### Full Protection
Locked blocks are protected from:
- Redstone activation
- Hopper extraction
- Piston movement
- Explosions
- Breaking (configurable)

### Loot-Scaled Lock Generation
Lock tier is determined by the **value of a chest's loot table contents**. Village chests get wood or copper locks, while end city chests get gold or diamond locks. Every generated chest gets at least a wooden lock — no chest is left unprotected.

- **Multi-sample averaging** — each loot table is sampled 32 times and averaged for consistent tier assignments across server restarts.
- **Sub-linear stack count** — item value scales with the square root of stack size, so 64 cobblestone doesn't outrank a diamond sword.
- **Item value overrides** — configurable per-item base values for materials like diamonds, emeralds, and netherite that are valuable but have common rarity. 15 vanilla items have sensible defaults.
- Fully configurable with per-tier value thresholds, rarity multipliers, and enchantment bonuses. Can be switched to random weighted selection if preferred.

### World Integration
- Locked chests spawn naturally in structures with loot-value-scaled tiers
- Lock picks and mechanisms found in dungeon and temple loot
- Toolsmith villagers sell lock picks and mechanisms
- Wandering traders offer rare picks and enchanted locks

---

## Adding Custom Locks & Lock Picks

Locks Reforged has a fully data-driven item system. You can add entirely new lock and lock pick tiers — or tweak existing ones — using three different methods depending on your needs.

### Method 1: Config Folder (New Custom Items)

Drop a JSON file into the config folder to register a brand new lock or lock pick item. The mod auto-discovers these at startup.

**To add a custom lock**, create `config/locks/lock_types/<name>.json`:
```json
{
  "length": 7,
  "enchantment_value": 14,
  "resistance": 12
}
```
- `length` — Number of pins (1-20)
- `enchantment_value` — Enchantability (1-50)
- `resistance` — Explosion resistance (0-1000)

**To add a custom lock pick**, create `config/locks/lockpick_types/<name>.json`:
```json
{
  "strength": 0.5
}
```
- `strength` — Pick effectiveness (0.01-10.0)

The filename becomes the item's registry name under the `locks:` namespace (e.g., `netherite_lock.json` registers as `locks:netherite_lock`). Config-folder items override JAR-bundled items of the same name, so you can also use this to replace built-in stats.

**Important:** Custom items registered this way will appear in-game automatically, but they will be invisible and uncraftable without:
- A **resource pack** providing a model and texture (`assets/locks/models/item/<name>.json`, `assets/locks/textures/item/<name>.png`)
- A **datapack** providing a crafting recipe (`data/locks/recipes/<name>.json`) and adding the item to the appropriate tag (`data/locks/tags/items/locks.json` or `lock_picks.json`)

The mod creates example files at `config/locks/lock_types/_example.json.disabled` and `config/locks/lockpick_types/_example.json.disabled` on first launch for reference.

### Method 2: Datapack Stat Overrides (Tweak Existing Items Per-World)

Datapacks can override the stats of any existing lock or lock pick on a per-world basis. Overrides are applied on world load and whenever `/reload` is run, making them ideal for modpack authors or adventure maps.

**To override lock stats**, place a JSON file at `data/<namespace>/locks/lock_stat_overrides/<name>.json`:
```json
{
  "item": "locks:iron_lock",
  "length": 3,
  "resistance": 50
}
```

**To override lock pick stats**, place a JSON file at `data/<namespace>/locks/lockpick_stat_overrides/<name>.json`:
```json
{
  "item": "locks:iron_lock_pick",
  "strength": 1.5
}
```

Only the fields you list are changed — omitted fields keep their current values. Multiple datapacks can provide overrides; they stack in datapack load order.

### Method 3: TOML Config (Override Stats Without Datapacks)

Every built-in lock and lock pick has TOML config entries in `locks-common.toml` that let you override individual stats without needing a datapack. Set any value to `-1` (or `-1.0` for pick strength) to use the JSON default.

```toml
# Example: make the copper lock have 8 pins instead of 6
["Lock Stats"."Copper Lock"]
    Length = 8
    "Enchantment Value" = -1
    Resistance = -1

# Example: double the copper pick strength
["Lockpick Stats"]
    "Copper Lockpick Strength" = 0.56
```

TOML overrides are applied before datapack overrides, so datapacks take final precedence.

### Override Priority

When multiple systems provide values for the same stat, the order of precedence is:

1. **Datapack stat overrides** (highest priority, applied on `/reload`)
2. **TOML config overrides** (applied on config load)
3. **Config folder JSON** (overrides JAR defaults at startup)
4. **JAR-bundled JSON** (lowest priority, built into the mod)

### A Note on Mechanisms

Lock mechanisms (Wood, Copper, Iron, Steel) are basic crafting components — they are not data-driven and cannot be added via config or datapack. Each lock recipe requires its corresponding mechanism as the center ingredient.

---

## Configuration

All settings are customizable via config files. Note that `locks-server.toml` is **per-world** and lives in a different location than the other two:

**`config/locks-common.toml`** — World generation and item stats
- **Generation Chance** — Legacy setting, no longer used. All generated chests now receive a lock unconditionally
- **Enchant Chance** — How often generated locks are enchanted (default: 40%)
- **Generated Locks / Weights** — Which lock tiers appear in worldgen and their relative rarity
- **Loot-Scaled Locks** — Lock tier based on chest loot value (enabled by default). Configurable item values, rarity multipliers, enchantment bonuses, per-tier value thresholds, sample count, and per-item value overrides
- **Lock Stats / Lockpick Stats** — Per-tier stat overrides (see above)

**`saves/<world>/serverconfig/locks-server.toml`** — Server-side gameplay rules (generated per-world on first load)
- **Lockable Blocks** — Regex patterns for which blocks can be locked (default: chests, doors, hoppers, etc.)
- **Lockable Tags** — Block tags whose members can be locked
- **Max Lockable Volume** — Maximum blocks a single lock can cover (default: 6)
- **Allow Removing Locks** — Whether unlocked locks can be removed by sneaking (default: true)
- **Protect Lockables** — Whether locked blocks are break-proof in survival (default: true)
- **Easy Lock** — One-click lock placement (default: true)
- **Hide Lock ID / Hide HUD Enchantments** — Tooltip display options
- **Enchantment Toggles** — Individually enable or disable each of the 7 enchantments
- **Netherite Lockpick Unbreakable** — When enabled, netherite lock picks never break (default: false)

**`config/locks-client.toml`** — Client-side settings
- **Deaf Mode** — Visual feedback for lock picking accessibility (default: true)

**Stale config files:** When the mod is updated with new tiers (e.g. Copper was added after the initial release), existing TOML files keep their old defaults. For example, the "Generated Locks" list won't include `locks:copper_lock` unless you manually add it or delete `locks-common.toml` to let it regenerate with the new defaults.

---

## Requirements

- Minecraft **1.20.1**
- Forge **47.2.0** or later
- Java **17**

---

## Credits

- **Original Author:** [Melonslise](https://github.com/Melonslise)
- **Original Mod:** [Locks on CurseForge](https://minecraft.curseforge.com/projects/locks)
- **Textures:** Hoonts, Artsy (ydgy)
- **Sounds:** [freesound.org](https://freesound.org)

This is an unofficial port that preserves all original gameplay. Licensed under **CC BY-NC 3.0**, consistent with the original mod.
