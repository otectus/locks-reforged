# Locks Reforged 1.7.3 Implementation Specification

## Lock picks use durability instead of a random break chance

**Target repository:** [otectus/locks-reforged](https://github.com/otectus/locks-reforged)  
**Audited branch:** `release-1.7.2`  
**Audited revision:** [`45f0c23`](https://github.com/otectus/locks-reforged/commit/45f0c235469e3d2796d3b02950aa28c8a9724df7)  
**Current release at that revision:** 1.7.2  
**Target release:** 1.7.3  
**Platform:** Minecraft 1.20.1, Forge 47.2.0+, Java 17, official Mojang mappings

---

## 1. Purpose

1. **A lock pick's survival was a coin flip, and a bad flip cost the whole item.** `LockPickingContainer#tryBreakPick` rolled `random.nextFloat()` against a survival chance derived from the pick's `strength`, and a losing roll ran `pickStack.shrink(1)`. A wood pick (`strength` 0.2) was destroyed by 80% of far misses — a player could lose a freshly crafted pick on their first guess with nothing to show for it and no way to see it coming.
2. **Only one item in the mod had durability at all.** The netherite pick was special-cased with `.durability(128)`, and even then durability was consumed only *after* the same losing roll, so the roll still governed everything. Six of seven tiers had no durability bar and no repair path.
3. **Lock tier barely mattered to the economics of picking.** A lock's difficulty was expressed almost entirely as pin count. Nothing about picking a netherite lock cost a thief more equipment than picking a wood one.

1.7.3 replaces the roll with a durability economy. Every pick gets a per-tier durability pool; every lock gets a `pick_wear` value that decides what one wrong pin costs; a pick breaks if and only if its pool reaches zero. Difficulty gating is deliberately untouched — `strength` keeps its existing job of deciding *which* locks a pick may attempt (the Complexity/Attunement gate in `LockPickItem#canPick`) and simply no longer decides whether the pick survives.

---

## 2. Executive implementation outcome

- Every lock pick tier is a damageable tool with durability from its JSON definition: wood 32, gold 40, copper 48, iron 96, steel 192, diamond 384, netherite 768.
- Every lock tier carries `pick_wear`, the durability a wrong pin costs the held pick: wood 1, gold 2, copper 2, iron 3, steel 6, diamond 12, netherite 24.
- The ladder is tuned so a pick gets roughly **32 wrong pins against a lock of its own tier**, and degrades sharply across tiers: a wood pick gets one mistake on a netherite lock, a netherite pick gets 768 on a wood lock.
- A near miss (off by exactly one pin) costs about a third as much, never rounding below 1.
- Sturdy, Finesse and Last Catch are remapped onto wear. Last Catch is the only die roll left anywhere in the path, and it can only ever *save* durability — it never breaks anything.
- No network change. `PROTOCOL_VERSION` stays `3`.

---

## 3. Verified repository baseline

Everything below was read at the audited revision, not assumed.

| Area | 1.7.2 implementation | Consequence |
|---|---|---|
| Break decision | `LockPickingContainer#tryBreakPick` (`:189-236`): `survive = ex + ch`, then `random.nextFloat() < survive` | The single choke point. A grep for `hurtAndBreak\|broadcastBreakEvent\|usesDurability\|damagePick\|setDamage\|isDamageableItem` returns only this method and `LockPickItem:64-71`, so the change is genuinely localized. |
| Pick destruction | `pickStack.shrink(1)` in the non-durability branch (`:221-222`) | Six of seven tiers were deleted whole. Nothing to repair, nothing to see coming. |
| Durability | `LocksItems:69-70` special-cases `netherite_lock_pick` with `.durability(128)` | The precedent for per-tier durability already exists; it just needs to come from data. |
| Stat records | `LockStats(length, enchantmentValue, resistance, fireResistant)`, `LockPickStats(strength, fireResistant, enchantmentValue)` in `LockTypeRegistry:32-33` | Neither has a durability or wear field, but both already flow through a four-layer override pipeline that new fields inherit for free. |
| Near-miss rule | `getBreakChanceMultiplier` (`:238-241`), hardcoded `0.33f` | Worth preserving as a wear discount, and worth making configurable while it moves. |
| Sturdy | `strength / (0.75 + sturdy * 0.5)` (`:197`), formula hardcoded | Needs a wear-side equivalent and its first config knob. |
| Finesse | `strength *= 1 + level * 0.15`, then `survive` clamped to ≤ 0.95 (`:199-208`) | The clamp exists so Finesse can never make a pick unbreakable. The durability model needs the same guarantee. |
| Last Catch | Second roll after the survive roll already failed (`:213-216`) | Can be remapped to negating a wear tick without reintroducing random *breaking*. |
| Client animation | `LockPickingScreen#handlePin` infers the snap purely from `reset == true` in `ITEM_BACKED` mode | A worn-but-alive pick is `WRONG_CONTINUE`, which already sends `reset=false`. **No new packet field, no protocol bump.** |
| `strength` storage | `LockPickItem#getOrSetStrength` (`:41-47`) writes NBT on every read | A pick that had merely been looked at stopped stacking with a fresh one, and `canAttempt`/`tryPin` carry comments warning it must never see an empty stack. |
| Pick stack sizes | Picks were stackable; 5 of 7 recipes yield `count: 2`; villager and wanderer trades sell `numberOfItems = 2` | `durability(n)` forces the base size to 1, so `LockPickItem` must restore a stack-sensitive maximum while isolating the one pick that carries damage. |
| Loot | `data/locks/loot_tables/inject/chests/*.json` use `set_count` 2–4 | These remain ordinary pristine stacks; no loot edits are needed. |

---

## 4. Release invariants

1. **The server owns the break decision.** The client sends a pin index and is told two booleans. Nothing about durability crosses the wire independently.
2. **A pick breaks only when its durability reaches zero.** No code path may destroy a pick for any other reason.
3. **A wrong pin always costs something.** No enchantment level and no config value may reduce wear below 1.
4. **"Did it break?" is read from the item, never predicted.** Unbreaking can swallow damage and creative mode ignores it entirely; a prediction would fire `PICK_BROKE` for a pick still in the player's hand.
5. **Difficulty gating is unchanged.** `strength`, Complexity and Attunement keep deciding which locks a pick may attempt; they no longer touch survival.
6. **Itemless picking is untouched.** An empty hand has no durability to spend and must never enter the wear path.
7. **Durability is fixed at registration.** Anything that claims to change it later must be refused loudly rather than reported inaccurately.
8. **Existing worlds keep working.** Locks placed before 1.7.3 identify their tier from their stored `ItemStack`; JSON written for 1.7.2 keeps loading via field defaults.
9. **No protocol bump unless the wire format actually changes.** It does not.

---

## 5. Workstream A — the durability model

### 5.1 Two new data-driven stats

`common/init/LockTypeRegistry`:

```java
public record LockStats(int length, int enchantmentValue, int resistance, boolean fireResistant, int pickWear) {}
public record LockPickStats(float strength, boolean fireResistant, int enchantmentValue, int durability) {}

public static final int DEFAULT_PICK_WEAR = 1;
public static final int DEFAULT_PICK_DURABILITY = 64;
public static final int UNBREAKABLE_DURABILITY = 0;
```

Both fields are **optional** in JSON, so lock and lockpick definitions written for 1.7.2 — including user files already sitting in `config/locks/` — keep loading. `pick_wear` clamps to 1–1000; `durability` clamps to 0–10000, where **0 is legal and means the pick never wears out**. That is the escape hatch for pack authors who want the pre-1.7.0 feel, and it is why the clamp starts at 0 rather than 1.

Both flow through the existing four-layer pipeline (JAR JSON → `config/locks/*` JSON → TOML → datapack), which meant touching `parseLockDefinition`, `parseLockPickDefinition`, `applyConfigLockOverride`, `applyLockOverrides`, `applyLockPickOverrides`, the two `createConfigDirectories` example templates, and both fallback constructors.

### 5.2 The wear math is a pure policy class

New file `common/container/LockPickWearPolicy`, following the convention set by `LockPickingPolicy`, `KeyPairing`, `PassiveLockPolicy` and `NativeSteelPolicy` — a private constructor and static methods over primitives, so it is unit-testable with no FML bootstrap.

```java
public static int wearFor(int baseWear, boolean nearMiss, double nearMissMultiplier,
    int sturdy, double sturdyPerLevel, int finesse, double finessePerLevel)
```

Order of application:

1. `wear = max(1, baseWear)` — the lock tier's `pick_wear`.
2. Near miss: `wear *= nearMissMultiplier` (0.33).
3. Sturdy: `wear *= 1 + level * sturdyPerLevel` (0.5).
4. Finesse: `wear *= max(0, 1 - level * finessePerLevel)` (0.15).
5. Round, then floor at `MIN_WEAR`.

Every tuning input is passed through `Math.max(0, …)` so a hand-edited negative config value is ignored rather than inverting the effect. The floor at step 5 is the durability-era successor to 1.7.2's "keep at least a 5% break chance" clamp.

**The class deliberately does not answer "did this break the pick?"** — see invariant 4. That question is answered in the container by reading the stack.

### 5.3 Rewiring the container

`tryBreakPick` became `wearPick`, keeping its `boolean` contract, so `tryPin` and `LockPickingPolicy.resolve` keep their existing shape and `PinOutcome` is unchanged:

- Early-outs: not a lock pick; `NETHERITE_PICK_UNBREAKABLE` on a netherite pick; a pick with no durability pool at all.
- Last Catch rolls first and, on a save, returns before any damage is applied.
- `LockPickWearPolicy.wearFor(...)` is fed the lock's `pick_wear` via `LockTypeRegistry.getLockStats(this.lockable.stack.getItem())`, so a lock placed in an older world reports its tier correctly.
- `LockPickItem.damagePick(pickStack, player, hand, wear)` applies damage to exactly one singleton and returns the observed break result. Unbreaking and creative mode can leave it unchanged. A surviving worn pick becomes the held singleton and the pristine remainder returns to inventory; a broken pick removes only one count.
- The replacement-pick scan still runs when the final held pick breaks. A nonempty stacked remainder is already its own replacement.

`getBreakChanceMultiplier(int) : float` became `isNearMiss(int) : boolean`. Both were `protected`; this is a deliberate, compile-visible API change for third-party subclasses.

`LockPickingPolicy#shouldRollPickBreak` was renamed `#shouldWearPick` — there is no roll left for it to describe. Its behaviour and its test assertions are unchanged.

### 5.4 `LockPickItem` cleanup

- `usesDurability(stack)` is now just `stack.isDamageableItem()`; the netherite hardcode and the `NETHERITE_DURABILITY` constant are gone.
- `getMaxStackSize(stack)` returns 64 through Forge's stack-sensitive hook, overriding the base size forced by `durability(n)`.
- `damagePick` takes an `int amount`, isolates one worn pick from a pristine stack, and returns whether that pick actually broke.
- `getOrSetStrength` became a read-only `getStrength(ItemStack)`: registry first, legacy `Strength` NBT honoured when present, `0f` for an empty stack. This removes the NBT-on-read hazard the container and `canAttempt` were carrying comments about.

### 5.5 Wear tests

`src/test/java/melonslise/locks/common/container/LockPickWearPolicyTest.java`, 10 tests:

- An unmodified far miss costs exactly the lock's `pick_wear`.
- A `pick_wear` below 1 is treated as 1.
- A near miss costs strictly less than a far miss, and still costs 1 on a cheap lock.
- Sturdy raises wear monotonically; Finesse lowers it monotonically; the two compose.
- **Finesse can never make a wrong pin free** — asserted at maxed level with an absurd config value.
- Negative tuning values are ignored rather than inverted.
- The shipped ladder gives a matched pick 32 mistakes, and a wood pick exactly 1 on a netherite lock.

`LockPickingPolicyTest` and `LockPickingModeTest` pass unchanged apart from the method rename — that is the regression signal that `PinOutcome` semantics did not move.

---

## 6. Workstream B — stackable durability

`Item.Properties.durability(n)` sets the base `maxStackSize = 1`. Forge exposes a stack-sensitive item hook,
so `LockPickItem#getMaxStackSize(ItemStack)` restores the preexisting maximum of 64 without giving up
vanilla durability.

| Surface | Status |
|---|---|
| Recipes | `copper/diamond/gold/iron/steel_lock_pick.json` retain their original `count: 2` results. |
| Trades | Villager and wandering-trader pick trades retain their original `numberOfItems = 2` where applicable. |
| Loot tables | Unchanged. Their `set_count` 2–4 entries now remain normal pristine stacks. |

Damage belongs to stack NBT, so a damaged multi-item stack cannot safely represent "one worn pick": splitting
it would copy that damage onto both results. `damagePick` therefore works on a singleton copy. If damage
lands, that singleton becomes the held item and the pristine remainder is placed back into inventory (or
dropped if full). If the singleton breaks, only one count is removed and the remainder stays in hand.

---

## 7. Workstream C — configuration

### 7.1 `locks-server.toml`

Under `[Enchantments.Lock Pick]`, whose section comment now describes the durability model rather than only enchantment tuning:

| Key | Default | Range | Notes |
|---|---|---|---|
| `Finesse Wear Reduction Per Level` | 0.15 | 0.0–1.0 | **Replaces** `Finesse Strength Per Level`, which described a break-roll bonus that no longer exists. The orphaned key sits unused in existing configs. |
| `Sturdy Wear Per Level` | 0.5 | 0.0–10.0 | New. Sturdy's formula was hardcoded before. |
| `Near Miss Wear Multiplier` | 0.33 | 0.0–1.0 | New. Was the hardcoded `0.33f`. |

`Last Catch Save Chance` keeps its key and default; its comment now says it cancels a wrong pin's durability cost. `Netherite Lockpick Unbreakable` keeps its key, default and observable behaviour. `Shocking Triggers On Pick Break` gains a note that it now fires far less often, pointing at `Shocking Triggers On Wrong Pin` for the old frequency.

### 7.2 `locks-common.toml`

`[Lock Stats].<Tier> Lock` gains **`Pick Wear`** (sentinel `-1` = JSON default), matching the existing `Length` / `Enchantment Value` / `Resistance` one-liner style.

`[Lockpick Stats]` gains **no durability entry**, and its section comment explains why. This is the one place where the plan changed during implementation: `Item.Properties.durability(n)` is read when `LocksItems.register()` builds the properties during mod construction, *before* the TOML config is loaded and long before any datapack. A TOML or datapack durability override would move the number the tooltip reports without moving the item's actual max damage. Rather than ship that discrepancy:

- A `durability` key in a datapack `lockpick_stat_overrides` file is ignored and logs a warning naming the correct location.
- `config/locks/lockpick_types/<name>.json` **is** read before registration and is the supported place to set it.
- A lock's `pick_wear`, by contrast, is read fresh on every wrong pin, so both its TOML entry and its datapack override work normally.

---

## 8. File-by-file implementation map

| File | 1.7.3 change |
|---|---|
| `common/init/LockTypeRegistry.java` | `pickWear` on `LockStats`, `durability` on `LockPickStats`; parsing with clamps and defaults; all four override paths; example templates; fallbacks; datapack durability warning |
| `data/locks/lock_types/*.json` (7) | `"pick_wear"` |
| `data/locks/lockpick_types/*.json` (7) | `"durability"` |
| `common/init/LocksItems.java` | `.durability(...)` for every pick from its stats, guarded on `> UNBREAKABLE_DURABILITY` |
| `common/item/LockPickItem.java` | general `usesDurability`, `damagePick(…, int amount)`, read-only `getStrength`, `NETHERITE_DURABILITY` removed |
| `common/container/LockPickWearPolicy.java` | **new** — pure wear arithmetic |
| `common/container/LockPickingContainer.java` | `tryBreakPick` → `wearPick`; `getBreakChanceMultiplier` → `isNearMiss`; break read from the stack |
| `common/container/LockPickingPolicy.java` | `shouldRollPickBreak` → `shouldWearPick`; javadoc |
| `common/config/LocksServerConfig.java` | Finesse key replacement, `Sturdy Wear Per Level`, `Near Miss Wear Multiplier`, comment rewrites |
| `common/config/LocksConfig.java` | per-lock `Pick Wear`; `[Lockpick Stats]` comment explaining the durability exception |
| `common/event/LocksForgeEvents.java` | pick trades `numberOfItems` 2 → 1 (villager + wanderer) |
| `data/locks/recipes/{copper,diamond,gold,iron,steel}_lock_pick.json` | `count` 2 → 1 |
| `src/test/.../LockPickWearPolicyTest.java` | **new**, 10 tests |
| `src/test/.../LockPickingPolicyTest.java` | method rename only |
| `gradle.properties` | `mod_version=1.7.3` |
| `CHANGELOG.md`, `CLAUDE.md`, `README.md`, `CURSEFORGE_DESCRIPTION.md`, `KNOWN_ISSUES.md` | version bump, feature copy, tier tables, schema tables, QA script |

Untouched: all six network packets, `LocksNetwork.PROTOCOL_VERSION`, `Lock`, `Lockable`, `LockPickingMode`, every mixin, and the loot-table JSONs.

---

## 9. QA matrix

Automated coverage is 71 unit tests (10 new). Everything below needs a live client, because the test suite may not touch `ItemStack`, config values, tags, `Level` or `Minecraft`. The full checklist lives in `KNOWN_ISSUES.md` under **1.7.3 Manual QA Script**; its headline items are:

### 9.1 The core guarantee
- [ ] A fresh wood pick survives exactly 32 far misses on a wood lock and breaks on the 33rd — **twice**, to demonstrate it is the same number every time.
- [ ] The same iron pick loses 3× more durability per miss on an iron lock than on a wood lock.

### 9.2 Feel and feedback
- [ ] Non-breaking miss: sprite intact, `pin.fail` plays, solved pins retained.
- [ ] Breaking miss: snap animation, progress reset, replacement pick pulled from the inventory.
- [ ] Near miss costs about a third, and never zero on a wood lock.

### 9.3 Enchantments and config
- [ ] Finesse III lowers wear; Sturdy III raises it; Last Catch occasionally costs nothing; Shocking fires only on a real break.
- [ ] Mending and anvil-applied Unbreaking both work.
- [ ] `Netherite Lockpick Unbreakable = true` → no wear at all.
- [ ] `Pick Wear` via TOML and via datapack both apply on `/reload`; `durability` via `config/locks/lockpick_types/` applies on restart and via datapack is refused with a log warning.

### 9.4 Stacking and migration
- [ ] Craft, trade, and loot multiple pristine picks; they merge into stacks up to 64.
- [ ] Miss with a stack: one worn singleton stays held and the pristine remainder returns to inventory (or drops safely when full).
- [ ] Break a pick from a stack: the count falls by exactly one, the next pristine pick stays held, and the break outcome still resets progress.
- [ ] A 1.7.2 save holding a stack of 5 wood picks remains stacked and follows the same per-pick wear behavior.
- [ ] Itemless picking spends no durability anywhere.

---

## 10. Build and verification commands

```bash
./gradlew test
```

```bash
./gradlew build
```

```bash
./gradlew runClient
```

Final JAR inspection should confirm:

- `META-INF/mods.toml` reads `version="1.7.3"` (expanded from `gradle.properties`, never hand-edited).
- `data/locks/lockpick_types/*.json` all carry `durability`, and `data/locks/lock_types/*.json` all carry `pick_wear`.
- The five edited pick recipes read `"count": 1`.

**Status at the time of writing:** `compileJava`, `compileTestJava`, `test` (71 passing, 0 failed, 0 skipped) and `build` are all clean, and `build/libs/locks_reforged-1.7.3.jar` was inspected for the three points above. The manual QA script has **not** been run.

---

## 11. Changelog content

See the `## 1.7.3` section at the top of `CHANGELOG.md`, which carries the user-facing copy for this release in full.

---

## 12. Definition of done

- Every lock pick tier has durability, and no code path destroys a pick for any reason other than that durability reaching zero.
- The wear a wrong pin costs is decided by the lock, is deterministic, and is never zero.
- Sturdy, Finesse and Last Catch all still matter, and the only surviving die roll can only save durability.
- Pristine picks stack to 64, while wear and breakage affect exactly one pick at a time.
- The 1.7.3 manual QA script has been run and its boxes ticked.

The critical design theme: **replace a hidden probability with a visible number.** The player could never see the old break chance and could not plan around it; a durability bar is a promise the game keeps, and every decision in this release — reading the break off the item instead of predicting it, flooring wear at 1, refusing to fake a durability override that cannot take effect — protects the accuracy of that promise.
