# Locks Mod 1.20.1 - Known Issues

## Port-Specific Notes

1. **Refmap warning in dev**: The mixin refmap (`locks.refmap.json`) shows "could not be read" in the dev environment. This is a known MixinGradle/ForgeGradle cosmetic issue — dev uses official (Mojang) names which match source annotations directly, so no remapping is needed. The refmap IS correctly included in the production JAR. No fix required.

## Lock Pick Durability (new in 1.7.3)

Lock picks are damageable items with a per-tier durability pool, and a wrong pin spends a fixed amount of
that pool decided by the **lock** being picked (`pick_wear`). A pick breaks only when the pool reaches
zero. This replaced the 1.7.2 roll in `LockPickingContainer#tryBreakPick`, where a losing
`random.nextFloat()` deleted the whole item — a wood pick (strength 0.2) died to 80% of far misses.

**Where the numbers live:** `pick_wear` on `LockStats` and `durability` on `LockPickStats`, both in
`common/init/LockTypeRegistry`, both flowing through the existing JAR JSON to config-folder JSON to TOML
to datapack override pipeline. The arithmetic is in `common/container/LockPickWearPolicy#wearFor`, a pure
static covered by 10 unit tests.

**Durability is fixed at registration and nothing later can move it.** `Item.Properties.durability(n)` is
read when `LocksItems.register()` builds the properties, which happens during mod construction — before
the TOML config is loaded and long before any datapack. So a `durability` key in a datapack
`lockpick_stat_overrides` file is ignored (with a warning), and there is deliberately no
`Lockpick Durability` TOML entry to match the `Lockpick Strength` ones. `config/locks/lockpick_types/`
*is* read before registration and does work. A lock's `pick_wear`, by contrast, is read fresh on every
wrong pin, so both the TOML entry and the datapack override work normally.

**"Did it break?" is read, never predicted.** `wearPick` applies the damage and then checks
`pickStack.isEmpty()`. Predicting the break from `damage + wear >= maxDamage` looks equivalent and is not:
`ItemStack#hurt` lets Unbreaking silently swallow part or all of the damage, and `hurtAndBreak` is a no-op
in creative. A prediction would report `PinOutcome.PICK_BROKE` — resetting pin progress and firing
Shocking — for a pick that is still in the player's hand.

**No protocol bump.** `TryPinResultPacket` carries `(correct, reset)`. A worn-but-alive pick is
`WRONG_CONTINUE`, which already sends `reset=false`, so the client leaves the pick sprite intact; only a
real break sends `reset=true` and plays the snap animation. `PROTOCOL_VERSION` stays `3`.

**`stacksTo(1)` fallout, and what was checked.** `durability(n)` forces a max stack size of 1.
- Recipes: the copper/gold/iron/steel/diamond pick recipes yielded 2 and now yield 1. A shaped recipe
  result above the item's max stack size hands an oversized stack out of the result slot.
- Trades: villager and wandering-trader pick trades sold `numberOfItems = 2` and now sell 1.
- Loot tables were deliberately **not** changed. Vanilla's loot stack-splitter
  (`LootTable#createStackSplitter`) already splits an over-max stack into separate stacks, so the
  `set_count` 2–4 entries yield several single picks. This is the one piece of the fallout that is
  reasoned rather than mechanically enforced — it is item 10 in the QA script below.

**Deliberate behaviour changes:** a third-party item in the `locks:lock_picks` tag that is not damageable
is now unbreakable rather than consumed, and a definition with `"durability": 0` registers an unbreakable,
still-stackable pick on purpose. `strength` no longer influences breakage, only the Complexity gate.

**Verified:** 10 new pure-logic tests (71 in the suite overall), `compileJava`, `compileTestJava` and
`test` all clean. Everything in-game is manual and is listed in the 1.7.3 script below.

## 1.7.3 Manual QA Script (NOT YET RUN)

Durability, wear rates, trades, recipes and loot all need a live client — none of it is reachable from
the unit tests, which may not touch `ItemStack`, config values, tags or `Level`.

- [ ] Every lock pick tier shows a durability bar in the inventory, and F3+H reports the value from its JSON (wood 32 … netherite 768).
- [ ] Miss one pin on a wood lock and on an iron lock with the same iron pick: the iron lock costs 3× the durability.
- [ ] A fresh wood pick survives exactly 32 far misses on a wood lock and breaks on the 33rd. Run this twice — the whole point of the release is that it is the same number every time.
- [ ] On the breaking miss: the pick-snap animation plays, pin progress resets, and a replacement pick from the inventory is pulled into the hand.
- [ ] On a non-breaking miss: the pick sprite stays intact, `pin.fail` plays, and solved pins are **not** reset.
- [ ] A guess off by exactly one pin costs about a third of a far miss, and never zero (check on a wood lock, where 1 × 0.33 must still cost 1).
- [ ] Finesse III visibly reduces wear; Sturdy III on the lock visibly raises it; Last Catch occasionally costs nothing; Shocking fires only on the actual break.
- [ ] Mending repairs a worn pick; Unbreaking III applied at an anvil makes a pick last measurably longer.
- [ ] With `Netherite Lockpick Unbreakable = true`, a netherite pick takes no wear at all.
- [ ] Craft each pick (yields 1), buy each villager and wandering-trader pick trade (yields 1), and open a dungeon chest whose pool rolls 2–4 picks — they must arrive as separate single stacks with none lost.
- [ ] Itemless picking is untouched: with `Allow Itemless Lock Picking = true`, an empty-handed miss still resets progress and spends no durability anywhere.
- [ ] Set `Pick Wear` for wood locks in `locks-common.toml`, and `pick_wear` in a datapack `lock_stat_overrides` file; `/reload` and confirm both take effect.
- [ ] Set `durability` in `config/locks/lockpick_types/wood_lock_pick.json`, restart, and confirm the item's max damage changed. Then put `durability` in a datapack `lockpick_stat_overrides` file and confirm it is ignored with a warning in the log.
- [ ] Load a 1.7.2 save holding a stack of 5 wood picks: no crash, and the stack clamps to single items as they are moved.
- [ ] A lock placed before 1.7.3 wears picks at its tier's rate (the lock's stored ItemStack still identifies its tier).

## Awareness Owner Lockout (fixed in 1.7.2)

An Awareness lock used to make its block permanently unusable for the player who placed it: the LOCKED interaction path denies and cancels the click unconditionally before any authorization branch runs, and the UNLOCKED path's Awareness branch checked only the lock's enchantment and owner UUID — not the held item, not aim, not sneaking — so it fired on every click and re-locked immediately. The chest GUI could never open, and because that branch set `relocked` it also returned before the lock-removal branch, so the lock could not be taken off.

**The general shape:** this is what happens to any credential the player cannot put away. A held key escapes because both toggle branches gate on the held stack — stow it and the block behaves normally. Awareness has no off switch at all, so any design where it claims every right-click leaves no click that opens the block.

**An aim gate cannot fix it, and this was verified rather than assumed.** For a vanilla chest, `Lockable.State` places the lock model's clickable box (after `inflate(1/32)`) at x ∈ [0.344, 0.656], y ∈ [0.219, 0.656], against a front face spanning [0.0625, 0.9375] × [0, 0.875] — centred on the face, covering about 36% × 50% of it. "Aim at the padlock" and "aim at the chest" are the same crosshair position, so gating a lock action on aim reintroduces the lockout for the most common way to click a chest. **Do not add aim-based lock gestures.** Sneak is the only unambiguous modifier.

**Resolution:** an Awareness lock does not apply to its owner. An ordinary click passes straight through to the block and never touches lock state; sneak + right-click with an empty hand unlocks; the existing sneak-with-an-empty-hand removal gesture then takes the open lock off. The rules live in `common/util/PassiveLockPolicy` as plain booleans and are unit-tested, including a regression test asserting that no combination of inputs leaves an owner with no click that reaches their own block. Handing the click back is done with `setUseBlock(DEFAULT)` while `setUseItem` stays `DENY`, so the block opens but the held item can never be placed or consumed; it is gated on every locked lockable at the position being the owner's own, since the container GUI is otherwise protected solely by this handler denying the click.

**Deliberate consequence:** an owner who opens their own Awareness-locked **door** leaves it physically open while the lock still reports locked, so a villager can path through the open doorway until it closes. The "locked implies closed" invariant is enforced on transitions *to* locked, not continuously, and a player opening their own door is not a transition. No worse than any door left open, and better than the alternative reading, where the door would be left unlocked as well.

**Verified:** 8 pure-logic policy tests (61 in the suite overall), `clean build` clean. In-game behaviour is manual and is listed in the 1.7.2 script below.

## Villager-Proof Doors (new in 1.7.2)

Locked doors are now guarded at `DoorBlock#setOpen`, the method every non-player door opener funnels through — the villager Brain behavior, the legacy `DoorInteractGoal`, raider goals, and modded AI driving a vanilla or subclassed `DoorBlock`.

**How it works:** `mixin/DoorBlockMixin` injects at HEAD, cancels when `open == true` and `LocksUtil.locked(world, pos)` reports locked, and does nothing else — no sound, no game event, no AI-memory or navigation mutation, because AI retries constantly. It runs server-side only: on the client `LocksUtil.locked` reads the lockable mirror, which can legitimately be stale, and `setOpen` has no vanilla client callers anyway. It reuses the existing non-forcing chunk lookup, so it adds no `getChunk`/`getChunkAt` call.

**Why not the player path:** `DoorBlock#use` does its own `state.cycle(OPEN)` and never calls `setOpen`, so the mixin and the existing `LocksForgeEvents#onRightClick` denial cannot overlap or double-fire.

**The closed-door invariant:** blocking future opens is not enough if the door was already open. `LocksUtil.setLocked` closes any open door inside the lockable's box before flipping the lock, and every transition — held key, key ring, Curios ring, Master Key, Awareness, Auto-Pick, completed minigame — routes through it. `LocksUtil.closeDoors` runs on *every* lock-to-locked request rather than only on a state change, because `Lock#setLocked` is idempotent and would otherwise skip a door under an already-locked lock. It guards each position with `hasChunkAt` first, since `Level#getBlockState` force-loads.

**Scope, verified not guessed:** `TrapDoorBlock` and `FenceGateBlock` have no entity-facing open method in 1.20.1 — their only writers of OPEN are `use` (players, already covered) and `neighborChanged` (redstone, already covered by `LevelMixin`). There is no AI bypass for them and no mixin was added; a speculative one would be dead code plus an injection-failure risk under `defaultRequire: 1`.

**Known gap (pre-existing, not fixed in 1.7.2):** mobs that *break* doors, such as zombies via `BreakDoorGoal`, are not stopped by `Protect Lockables` — that protection hangs off `BlockEvent.BreakEvent`, a player-only event. "Villager-proof" should not be read as "zombie-proof".

**Verified:** `./gradlew test` (53 tests pass), `clean build` / `runData` clean, and the mixin annotation processor resolves `setOpen` to `m_153165_` in the refmap. Dev **client boots to the title screen** and a **dedicated server reaches `Done`**, both with no mixin apply error — under `injectors.defaultRequire: 1` a mis-targeted `DoorBlockMixin` would abort loading, so this is what proves the guard is live. In-game door *behavior* is in the manual matrix below and **has not been run yet**.

## Optional Itemless Lock Picking (new in 1.7.2)

Server option **Allow Itemless Lock Picking**, default `false`. When on, an empty main hand opens the normal pin minigame with no lock pick.

**How it works:** the server decides a `LockPickingMode` (`ITEM_BACKED` / `ITEMLESS`) once, at menu-open time, and writes it into the menu's extra data as a bounded byte purely so the client can draw the right tool and pick the right failure animation. It is never re-derived from the held stack on either side, so swapping a pick for air mid-session cannot flip modes. Decoding clamps an unknown value to `ITEM_BACKED`, the restrictive mode, rather than indexing the enum unchecked.

**Where the rules live:** `common/container/LockPickingPolicy` — plain booleans, no game state, unit-tested. `LockPickingContainer#canAttempt` and the interaction handler gather world state and delegate. `stillValid` and `TryPinPacket` share that one gate, so a pin can never be accepted under conditions that would have closed the screen.

**Itemless semantics:** never rolls the break chance (so no durability loss, no break event, no replacement-pick scan, and no stat helper ever sees an empty stack), never rolls Auto-Pick, ignores Complexity and every pick-side enchantment, and resets all solved pins on a wrong pin. Quiet Hand needs no special case — an empty hand yields enchantment level 0, which already means full volume. Shocking `PICK_BREAK` cannot fire; the opt-in `WRONG_PIN` trigger still can.

**Interaction precedence:** physical pick, then Master Key, matching held key, held key ring, Awareness owner, Curios key ring, then itemless, then the normal denial. A player who can simply open the lock is never pushed into the minigame. There is no clash with the sneak + empty-hand lock removal gesture: that path is only reachable when every lockable at the position is already unlocked, and itemless only runs while one is locked.

**Protocol:** `LocksNetwork.PROTOCOL_VERSION` moved `2` → `3`. Both acceptors are exact-match, so a mixed 1.7.1/1.7.2 connection is refused at the channel handshake before any menu bytes are decoded.

**Reach check (intentional deviation):** session validity now also requires normal container reach (`distanceToSqr <= 64`) and a non-spectator player, **for physical picking too**. 1.7.1 let a player keep the minigame open from any distance through a wall. This is the only intentional change to physical-pick behavior in the release.

**Verified:** 16 pure-logic tests over mode selection, session validity, pin outcomes and the wire codec. A dedicated-server boot generates `world/serverconfig/locks-server.toml` containing `"Allow Itemless Lock Picking" = false` with its full comment block, and loads no client-only class. All in-game *behavior* is manual and **has not been run yet**.

## Key Pairing (new in 1.7.2)

**Root cause of the reported failure:** locks and keys get their `Id` during a server-side inventory tick, and the recipe required that tag to already be present. A lock that went creative menu → cursor → crafting grid never ticked in an inventory, so it had no `Id` and the recipe silently refused it. `LockingItem#onCraftedBy` now stamps the id at craft time, `inventoryTick` remains the backstop for `/give`, loot and third-party paths, and `KeyRecipe#assemble` assigns one server-side if it is still missing.

**Correction to the 1.7.2 spec:** the spec attributed the failure to `canCraftInDimensions` returning `x >= 3 && y >= 3`, blocking the 2×2 grid. That is wrong. `CustomRecipe#isSpecial()` returns `true`, so `locks:crafting_key` never enters the recipe book, and `RecipeManager#getRecipeFor` filters purely on `matches(...)` — nothing in vanilla consults `canCraftInDimensions` for this recipe. **Pairing in the 2×2 grid already worked in 1.7.1.** The bound was corrected to `x * y >= 2` for honesty, but it changes no behavior and must not be advertised as a fix.

**Source validation:** `common/recipe/KeyPairing` classifies each slot as EMPTY / BLANK / SOURCE / INVALID. Exclusions (Master Key, Key Ring, lock picks) are checked *before* the tag lookup, so a datapack adding the master key to `locks:keys` cannot turn it into a pairing source. The pure `isValidLayout` rule — exactly one source, exactly one blank, nothing else — is unit-tested.

**Security model unchanged:** you pair from an unplaced lock item or an existing paired key. A blank still cannot copy a placed lock; right-clicking one with a blank shows an instructional message and mutates nothing. The Key Blank tooltip never reveals an ID and stays correct with `Hide Lock ID` enabled.

**Implementation note:** `KeyBlankItem` extends `Item`, deliberately **not** `LockingItem`. `LockingItem` forces `stacksTo(1)` and stamps an `Id` every inventory tick — either would break blanks, and the stack-size change would silently truncate the stacks of blanks in existing worlds.

**Verified:** 9 pure-logic layout tests. All in-game crafting behavior is manual and **has not been run yet**.

## 1.7.2 Manual QA Script (NOT YET RUN)

None of the following has been executed. Run before release and tick individually.

**Awareness owner (the 1.7.2 lockout fix)**
- [ ] Awareness lock on a chest, empty hand, crosshair on the chest centre → one click opens the chest, and the lock still reads locked *(the reported bug)*
- [ ] Same holding a torch, not sneaking → chest opens, torch not placed
- [ ] Sneak + right-click the locked lock with an empty hand → lock opens, chest does **not** open
- [ ] Sneak + empty hand on the now-unlocked lock → lock drops as an item; place it back → locked again
- [ ] Sneak + torch beside your own locked chest → chest is not unlocked
- [ ] Break your own Awareness-locked chest in survival → allowed
- [ ] Door: one click opens it; sneak + empty hand unlocks it; villagers still cannot open it while it is locked and closed
- [ ] Your lock **plus a stranger's locked lock** on one chest → your click does not open it, and sneaking opens only yours
- [ ] Second player without Awareness → rattle, shock and minigame all unchanged
- [ ] `Enable Awareness = false` → owner treated as a stranger throughout
- [ ] Worn Curios key ring + sneak + empty hand on an unlocked lock → the lock is removed, not re-locked

**Doors**
- [ ] Villager cannot open a locked oak door; place a villager, a POI and a locked door between them and let it run — the door never reaches `OPEN=true`, no sound spam, tick time stable
- [ ] The same villager opens the door once the lock is unlocked
- [ ] A villager may still *close* a locked open door
- [ ] Re-locking an open door closes it; placing a lock on an open door closes it
- [ ] Double doors and locks spanning both halves are protected on every leaf
- [ ] Redstone still cannot open a locked door, and works again after unlock
- [ ] An ordinary unprotected door behaves exactly as vanilla
- [ ] A modded `DoorBlock` subclass using `setOpen` is protected
- [ ] Trapdoors and fence gates behave as vanilla (no regression from the door work)

**Itemless picking**
- [x] Generated server config contains `Allow Itemless Lock Picking = false` (verified on a fresh dedicated-server boot)
- [ ] An existing 1.7.1 world whose config lacks the key keeps physical-pick-only behavior
- [ ] Empty main hand opens the minigame only when enabled; an arbitrary held item never does
- [ ] A matching Curios ring, key ring, held key, Master Key and Awareness ownership all still take precedence over itemless
- [ ] No item is consumed, damaged, created or moved by an itemless attempt
- [ ] Maximum Complexity cannot block itemless access
- [ ] A wrong itemless pin resets progress with the **intact** tool animation, not the split-pick break
- [ ] Completion unlocks once and resolves loot once, **and the unlock sound is audible to the picker** (`removed()` became server-only; the sound must be broadcast with a null player or the picker is excluded)
- [ ] Closing the screen early leaves the lock locked
- [ ] Itemless failure never fires Shocking `PICK_BREAK`; it does fire `WRONG_PIN` when that trigger is enabled
- [ ] Walking out of reach, changing dimension, going spectator, filling the recorded hand, removing the lock, or toggling the config mid-session all invalidate the session
- [ ] A dedicated server rejects malformed or stale pin packets without crashing or mutating state
- [ ] A 1.7.1 client is refused by a 1.7.2 server with a clear protocol message, and vice versa

**Physical-pick regression**
- [ ] Every built-in and data-driven pick opens its normal GUI texture
- [ ] Complexity and Attunement thresholds unchanged; Sturdy, Finesse and Last Catch break maths unchanged
- [ ] Quiet Hand and Grounded unchanged; Auto-Pick stays physical-only at its old probability
- [ ] Broken-pick replacement still selects a valid inventory pick
- [ ] Netherite durability and the unbreakable config still correct
- [ ] Lock-picking GUI still renders correctly with ImmediatelyFast

**Key pairing**
- [ ] Lock + Key Blank pairs in the 2×2 inventory grid and in a crafting table
- [ ] The source lock returns unchanged with all NBT and enchantments; the key ID matches
- [ ] A paired key + blank duplicates correctly and preserves the source key
- [ ] Exactly one blank is consumed per output, including shift-crafting a stack
- [ ] Two blanks, two sources, and any extra unrelated item are all rejected
- [ ] An unrelated mod item carrying an `Id` NBT field is rejected; Master Key, Key Ring and lock picks cannot act as the source
- [ ] A lock dragged straight from the creative menu into the grid pairs on the first try, and source and output end with the same server ID
- [ ] `Hide Lock ID = true` hides the numeric ID but keeps the Key Blank instructions
- [ ] Right-clicking a placed lock with a blank shows the pairing hint and alters neither the stack nor the lock

**Persistence and compatibility**
- [ ] Locks and keys created in 1.7.1 still match in 1.7.2
- [ ] Lock state persists across chunk unload, restart, and adjacent-chunk load order
- [ ] C2ME smoke test: no hang, no off-thread mutation, no new blocking chunk fetch in door or picking validation
- [ ] Carry On retains lock state and door protection after a move
- [ ] Hopper, furnace, chest capability, piston, explosion and break protections unchanged
- [ ] Clean boot with Curios absent, and a clean build and boot with the Carry On jar absent

**JAR inspection** — all verified on the 1.7.2 build
- [x] Filename and embedded `mods.toml` both report 1.7.2
- [x] `locks.refmap.json` present; packaged `locks.mixins.json` lists `DoorBlockMixin` (15 common + 1 client)
- [x] All new translation keys present; `data/locks/recipes/key.json` still points at `locks:crafting_key`
- [x] No client-only class loaded by the dedicated server (server log clean of `NoClassDefFoundError` / `net.minecraft.client`; the only client references in common code remain the pre-existing ones inside `@OnlyIn(Dist.CLIENT)` methods)

## Native Steel Fallback (new in 1.7.1)

Locks ships its own steel material (`steel_ingot`, `steel_nugget`, `steel_ore`, `deepslate_steel_ore`) and merges it into the standard `forge:ingots/steel` / `forge:nuggets/steel` / `forge:ores/steel` tags, but **prefers a modpack's own steel** when one exists.

**How it works:** one detection service — `common/steel/NativeSteelPolicy` — inspects the *members* of the Forge steel tags, filters out Locks' own IDs (so its ingot never masks a real external provider), and decides per material form whether the native version is active. A custom recipe condition (`locks:steel`, registered via `CraftingHelper` in `FMLCommonSetupEvent`) gates the native-producing recipes; a custom placement modifier (`locks:steel_ore`) gates ore generation from a cached, thread-safe policy snapshot refreshed on `TagsUpdatedEvent`. The native blocks/items are **always registered** (existing worlds/stacks stay valid) — only acquisition is toggled. Server config `Steel Material Mode` (`AUTO` / `FORCE_NATIVE` / `EXTERNAL_ONLY`) overrides the automatic behavior.

**Detection & fallback rules (AUTO):** native ingot ⇔ no foreign ingot; native nugget ⇔ no foreign nugget; native ore gen ⇔ no foreign ore **and** native ingot active. Each form is independent (e.g. a mod with ingots but no nuggets still gets Locks' nugget).

**Verified:** `./gradlew test` (9 new pure-logic policy tests + 16 existing, all pass), `compileJava` / `build` / `runData` clean, JAR contains all blockstates/models/textures/loot/tags/recipes/features/biome-modifier/lang. Dedicated-server boot **alone** logs `ingot=true, nugget=true, oreGeneration=true` with no foreign steel; **with a datapack adding vanilla iron to the three steel tags** it correctly reports the foreign IDs and flips all native fallbacks off — no recipe/worldgen errors either way. Remaining in-game checks (visual ore render/gen, live mode-switch in JEI) are in the Still Needs Testing list below.

## Lock Pick Enchantments (new in 1.7.0)

Five lock-pick-side enchantments in a new `LOCK_PICK` enchantment category, complementing the seven lock-side enchantments and providing counterplay ("locks define resistance, lock picks define technique"). Each is config-gated (enable toggle + tunable values under the `Enchantments` → `Lock Pick` section) and, when disabled, restores the exact prior behavior.

- **Finesse** (RARE, max III) — reduces break chance by boosting effective pick strength in the break roll only (+15%/level default). Never affects Complexity/`canPick`; keeps ≥5% break chance so a pick can't become unbreakable via Finesse. Counters Sturdy. Incompatible with Last Catch.
- **Attunement** (VERY_RARE, max II) — increases effective pick strength against Complex locks inside `canPick` only (+0.10/level). Counters Complexity.
- **Grounded** (RARE, max III) — reduces Shocking-lock damage while a pick is held in either hand (−20%/level, max level across both hands), floored at 0. Counters Shocking.
- **Quiet Hand** (UNCOMMON, max I) — lowers the wrong-pin sound volume (1.0 → 0.25 default); correct-pin sound unchanged.
- **Last Catch** (VERY_RARE, max I) — ~20% chance to cancel a break that would otherwise occur. Incompatible with Finesse.

**Data-driven enchantability:** lock picks are now enchantable at the enchanting table; per-pick enchantability comes from an optional `enchantment_value` field in `data/locks/lockpick_types/*.json` (wood 5, copper 7, iron 10, steel 12, gold 22, diamond 10, netherite 15). Picks with no entry default to 0 (not table-enchantable) and are unaffected.

Verified in the headless build: `./gradlew compileJava`/`build` clean, `runData` boots (registries + config spec build) without error, all JSON valid. In-game behavior is in the Still Needs Testing list below.

## Carry On Compatibility (new in 1.6.4)

Locks are moved together with the block when it is picked up and placed with [Carry On](https://www.curseforge.com/minecraft/mc-mods/carry-on). Server-authoritative; optional (only active when `carryon` is installed) and gated by an `IMixinConfigPlugin`.

**How it works:** three thin mixins into Carry On (`PickupHandler`, `CarryOnData`, `PlacementHandler`) delegate to `common/compat/CarryOnCompat` + `CarriedLockTransfer`. On pickup the intersecting `Lockable`s are serialized into the block entity's Forge persistent data (which rides inside Carry On's own carried `"tile"` NBT) and removed from the world; on placement they are offset to the new position and re-registered via `LockableHandler.add`, preserving lock id, `Lock` combo, locked state, enchantments, Awareness owner, and key/keyring/masterkey compatibility. No new storage — the canonical store stays the per-chunk `LockableStorage` + per-level `LockableHandler`.

**Config:** `[Compatibility.CarryOn]` in the server config — master toggle, allow/deny carrying locked blocks, optional authorization requirement (default off: anyone can carry, the lock just moves), deny partial multi-block pickup (default on), and transfer logging.

**Building:** requires `libs/carryon-forge-1.20.1-2.1.2.7.jar` (gitignored, like `respawningstructures`) on the compile classpath for the mixins. If the jar is absent the build automatically skips the compat (sources/config/mixin wiring excluded) and the mod builds normally.

Manual QA (needs a world with Locks + Carry On; not runnable in the headless build env):

- [ ] Lock a single chest → Carry On it → place elsewhere → lock is visible, locked, pickable, key-compatible; no ghost lock at old pos; no duplicate at new pos
- [ ] Unlocked locked-chest carried and placed, then re-locked with the same key
- [ ] With `Require Authorization` on: no matching key/ring/masterkey/owner in inventory → pickup denied, lock intact
- [ ] Awareness lock → only the owner can carry (auth on)
- [ ] Key ring and Curios key ring authorize (auth on)
- [ ] Carry across chunk borders / dimensions → all affected chunks save
- [ ] Server restart after moving → lock persists
- [ ] Death/drop while carrying (`placeCarried`) → lock restored at drop pos
- [ ] Locked double chest → pickup denied safely (partial multi-block), original lock intact
- [ ] After move: hopper extraction, redstone/open interaction, explosion resistance, break protection all honor the restored lock
- [ ] With Carry On **not** installed → existing Locks behavior unchanged (mixins inert)
- [ ] With C2ME installed → no blocking chunk fetch regressions during carry/place

## Resolved Compatibility Issues

- **C2ME hang — world generation freezes (re-entrant blocking chunk fetch)** *(fixed in 1.6.4)*: A silent hang (no crash report, no exception) during initial world load. C2ME's `beforeAwaitChunk` redirect makes the spawn-chunk-blocked Server thread drain the chunk executor (`managedBlock`), which fires `ChunkEvent.Load` re-entrantly on that same thread; `onChunkLoad` ran inline (already "on the server thread") and called a **blocking** `Level#getChunk(x, z)` that re-parked on the chunk future the thread was mid-completing — a permanent self-deadlock. Proven with live `jstack` dumps on the minimal C2ME + locks repro. Fixed by using the live `LevelChunk` the event already supplies (the re-fetch was redundant) with only a non-blocking `hasChunk` guard; hardening `LocksThreadUtil` (no-blocking-fetch contract + safe null-server degrade); and replacing every `hasChunk`-guarded blocking `getChunk`/`getChunkAt` in `LockableHandler` with the non-blocking `getChunkNow`. Also: border-spanning generated locks on already-finished `ImposterProtoChunk` neighbours are now written through to the wrapped chunk's storage instead of an orphaned (never-drained) list, and per-chunk `LockableStorage` map access is now synchronized (`snapshot()`).

- **C2ME crash — `ArrayIndexOutOfBoundsException` reading the handler map off-thread** *(fixed in 1.6.3)*: The 1.6.2 fix made all *writes* to `LockableHandler.lockables` main-thread-only, but `StructureTemplateMixin#fillFromWorld` still *read* the map from a C2ME worker thread via `new ArrayList<>(handler.getLoaded().values())`. Copying a fastutil open-addressing map while the main thread rehashes it reads a torn backing array — the snapshot itself throws `Index -1`. Fixed by guarding every structural mutation of the map with an internal monitor and adding `LockableHandler.snapshotLoaded()` (a synchronized copy) for the cross-thread readers (`fillFromWorld`, Respawning Structures compat). Also synchronized the neighbour-`ProtoChunk` lock-list writes from `LockChestsFeature` (border-spanning generated locks on worldgen worker threads), drained under the list monitor in `LevelChunkMixin`.

- **Ghost-locked door — blocked with no visible/pickable lock** *(fixed in 1.6.3)*: Door-blocking reads the per-chunk `LockableStorage` (`getInChunk`), while rendering and the pick minigame read the world-global `LockableHandler` index / client mirror. A divergence (e.g. a missed sync under async chunk loading) left a door blocked authoritatively while its lock was invisible and unpickable; force-loading didn't help because the chunk was already loaded. Fixed with a server-side self-healing reconcile at interaction time: the chunk's storage is re-registered into the world index (idempotent, makes the lock canonical and observed so its state persists/syncs), and a blocking lock is pushed to the interacting player so a desynced client re-renders it. The open-screen packet now carries the full lockable so the pick minigame can't dead-end with "Lockable not found" on a sync race; pin order remains server-authoritative.

- **Resurrected / duplicate locks after chunk reload or structure respawn** *(fixed in 1.6.3)*: `LockableHandler.remove` skipped a multi-chunk lockable's chunks that were unloaded, leaving a stale on-disk copy that `registerChunkStorage` resurrected as canonical on reload. On the server `remove` now force-loads every chunk the lockable spans (the chunks already exist on disk) and clears the lock from all of them; the client path stays best-effort and never force-loads.

- **C2ME crash — `ArrayIndexOutOfBoundsException` in `LockableStorage.deserializeNBT`** *(fixed in 1.6.2)*: The 1.6.0 fix funneled the `LevelChunk` constructor and structure-template mixins onto the main server thread, but **missed the chunk capability deserialization path**. Forge reads each chunk's `LockableStorage` NBT inside `ChunkSerializer.read`, which C2ME runs on parallel worker threads, and `deserializeNBT` was mutating the world-global non-thread-safe `LockableHandler.lockables` map (and registering observers) directly off-thread — so concurrent chunk loads corrupted the shared map's backing array (`Index -1 out of bounds for length 33`, surfacing as `Couldn't load chunk [x, z]`). Fixed by making `LockableStorage.deserializeNBT` pure chunk-local NBT parsing (no handler, observer, packet, or level access) and moving world-handler registration to the main thread via `LockableHandler.registerChunkStorage`, driven by `ChunkEvent.Load` (and routed through `LocksThreadUtil` like the other paths). Chunk-unload bookkeeping is likewise deferred to the main thread. Defensive parsing now skips a malformed lockable entry (with a warning) instead of failing the whole chunk load.

- **Locks disappear after chunk reload / server restart** *(fixed in 1.6.2)*: Two causes. (1) The lockable id counter lives on the `LockableHandler`, a **Level capability** that Forge does not persist, so it reset to `0` every restart and newly placed locks reused ids of already-saved (unloaded) locks; on reload the by-id merge made the old lock adopt the new lock's object/position and vanish. Now persisted per-level via `LocksSavedData` and advanced to the max id seen on chunk registration, with an early main-thread bootstrap on `LevelEvent.Load`. (2) Lock state changes only synced to clients without marking all occupied chunks unsaved; state now dirties every loaded chunk the lockable occupies. Chunk-border lockables also now resolve to a single canonical instance regardless of chunk load/unload order, and the client `AddLockableToChunkPacket` handler no longer force-loads chunks.

- **C2ME crash — `ArrayIndexOutOfBoundsException` in `LevelChunk`** *(fixed in 1.6.0, issue #10)*: C2ME (Concurrent Chunk Management Engine) runs chunk loading/generation on parallel worker threads. `LevelChunkMixin` was mutating the world-global `LockableHandler.lockables` map (a non-thread-safe `Int2ObjectLinkedOpenHashMap`), registering observers, and sending packets directly from the `LevelChunk` constructor — so several chunks loading at once corrupted the shared map's backing array, surfacing as an `ArrayIndexOutOfBoundsException`. Fixed by funneling all handler mutation and packet sync onto the main server thread via `LocksThreadUtil.runOnServerThread` (inline when already on the server thread, deferred to the next tick otherwise). The same deferral was applied to `StructureTemplateMixin#placeInWorld`; `ChunkMapMixin` and `StructureTemplateMixin#fillFromWorld` now snapshot lockable collections before iterating; and `LootValueCalculator`'s cache is now a `ConcurrentHashMap`. Behavior without C2ME is unchanged. A one-time INFO log notes when the off-thread deferral path first activates.

## Resolved (Inherited from 1.16.5)

- **Lock reshuffling doesn't persist** *(fixed)*: The original mod regenerated lock combinations from the lock ID seed on every load. If a combination was ever reshuffled at runtime, the change would be lost. Fixed by storing the `combo` byte array directly in NBT (`Lock.toNbt`/`fromNbt`) and syncing it over the network (`Lock.toBuf`/`fromBuf`). Backward compatible — locks saved without a `Combo` tag fall back to seed-based generation.

- **Performance: new quaternion every frame** *(fixed)*: The original code allocated new `Quaternionf` objects on every render frame in 3 locations (`Sprite.draw`, `LocksClientForgeEvents.renderLocks`, `LocksClientUtil.worldToScreen`). Fixed by caching static `Quaternionf` fields and using JOML's in-place mutation methods (`rotationX`/`rotationY`/`rotationZ`).

- **Loot injection behavior change** *(non-issue)*: The original mod merged loot pool entries directly into vanilla loot tables via mixin + GSON deserialization. The 1.20.1 port uses `LootTableLoadEvent` to add pools from inject JSON files. Investigation confirmed this produces identical gameplay results — pool names don't conflict and probability distributions are the same.

- **JOML math migration** *(verified correct)*: All active quaternion/matrix operations in `LocksClientUtil.worldToScreen`, `LocksClientForgeEvents.renderLocks`, and `Sprite.draw` correctly use 1.20.1 JOML patterns. The `vec.transform(quat)` → `quat.transform(vec)` change was applied correctly throughout.

## Resolved During Build

- **Access Transformer SRG mapping**: AT entries updated to use SRG (intermediary) names. Fixed `PistonStructureResolver` package path.
- **CapabilityItemHandler removed**: Replaced with `ForgeCapabilities.ITEM_HANDLER` across 6 files.
- **Item.is(TagKey) API change**: Tag checking moved from `Item` to `ItemStack` — all `.getItem().is(tag)` calls updated to `stack.is(tag)`.
- **IForgeMenuType.regular() removed**: Replaced with `IForgeMenuType.create()`.
- **AbstractContainerScreen.tick() now final**: `LockPickingScreen.tick()` renamed to `containerTick()`.
- **LockPickingContainer missing quickMoveStack**: Added required override returning `ItemStack.EMPTY`.
- **Options.renderDistance type change**: Now `OptionInstance<Integer>`, accessed via `.renderDistance().get()`.
- **RenderType.create() access**: Used 7-parameter overload (already AT'd by Forge) instead of 5-parameter package-private version.
- **Random vs RandomSource**: `LocksConfig` methods updated to accept `RandomSource` instead of `java.util.Random`.
- **getPlayers() returns List**: Added `.stream()` call in `LocksPacketDistributors`.
- **ModConfigEvent promoted**: Changed from inner class `ModConfig.ModConfigEvent` to top-level `ModConfigEvent`.

## Resolved During Runtime Testing

- **LevelMixin `hasNeighborSignal` failure**: In 1.20.1, `hasNeighborSignal` moved from `Level` to the `SignalGetter` interface as a default method. Mixin cannot `@Inject` or `@Overwrite` interface default methods on the implementing class. Fixed by having `LevelMixin implements SignalGetter` and providing an `@Override` method that checks the lock state before delegating to the original signal-checking logic.
- **Registry already frozen**: Items, enchantments, menu types, features, and recipe serializers were being eagerly instantiated during class loading (via static field initializers like `new Item(...)`). In 1.20.1, `Item.<init>()` calls `createIntrusiveHolder()` which requires the registry to be unfrozen. Fixed by wrapping all instantiation in lambda suppliers: `ITEMS.register("name", () -> new Item(...))`.
- **build.gradle mixin config incomplete**: Added `config "${project.mod_id}.mixins.json"` to the `mixin {}` block and `mixin.env.remapRefMap`/`mixin.env.refMapRemappingFile` properties to run configs.

## Testing Status

### Verified
- [x] All 16 mixins apply successfully at runtime on both client and dedicated server (no MixinApplyError) — re-verified for 1.7.2
- [x] Mod loads to title screen without errors
- [x] Config files created with correct defaults
- [x] Creative tab registered

### Still Needs Testing
- [ ] Lock persistence across chunk unload/reload
- [ ] Multiplayer sync when players enter lock areas (ChunkMapMixin packet fix applied in 1.3.0)
- [ ] Structure template save/load with locks
- [ ] Modded block compatibility (blocks without BlockEntities)
- [ ] Loot table injection (verify lock picks/mechanisms appear in dungeon chests)
- [ ] Villager/wanderer trades (verify lock items in trade lists)
- [ ] World generation (verify locked chests spawn in overworld)
- [ ] Lock picking minigame UI
- [ ] Key ring container UI
- [ ] Enchantment application on locks (7 lock-side: Shocking, Sturdy, Complexity, Silent, Auto-Pick, Reinforced, Awareness)
- [ ] Silent enchantment suppresses rattle sound
- [ ] Auto-Pick enchantment bypasses minigame at correct rates (10%/20%/30%)
- [ ] Reinforced enchantment scales explosion resistance
- [ ] Silent/Shocking incompatibility enforced by enchanting table
- [ ] Auto-Pick/Complexity incompatibility enforced by enchanting table
- [ ] Awareness enchantment stores owner UUID on lock placement
- [ ] Awareness lock auto-unlocks for owner (toggle behavior)
- [ ] Awareness lock rattles for non-owner players
- [ ] Overlapping Awareness locks from different owners work independently
- [ ] Awareness config toggle disables the enchantment
- [ ] Lock picks enchantable at the enchanting table; `enchantment_value` read from `lockpick_types/*.json` (netherite still 15)
- [ ] Finesse lowers observed break chance (I/II/III); never lets a pick bypass Complexity (`canPick` unaffected); break chance never drops to 0
- [ ] Attunement lets a weak pick cross a Complexity threshold (I >0.25, II >0.50); disabling it restores the failure
- [ ] Grounded reduces Shocking damage per level (−20/−40/−60%), from either hand, taking the max level; disabling restores full damage
- [ ] Quiet Hand reduces wrong-pin volume (1.0 → 0.25) only when present; correct-pin sound unchanged
- [ ] Last Catch occasionally saves a pick (~20%); anvil/table rejects combining it with Finesse
- [ ] Netherite unbreakable config still wins over Finesse/Last Catch; Grounded/Quiet Hand still function
- [ ] Each lock-pick enchantment's config toggle disables it (hidden from table/trades/loot) and its tunable values take effect
- [ ] Netherite lock has 14 pins in lock picking screen
- [ ] Netherite lock pick has 0.95 strength in tooltip
- [ ] Netherite lock pick rarely breaks (high strength)
- [ ] Netherite Lockpick Unbreakable config prevents breaking entirely
- [ ] Netherite lock and lock pick survive in lava (fire-resistant)
- [ ] Netherite smithing recipes work at smithing table
- [ ] Netherite items appear in creative tab after diamond
- [ ] Netherite lock pick sold by level 5 toolsmith
- [ ] Netherite lock sold by wandering trader (rare, enchanted)
- [ ] Async loot precompute doesn't block world loading
- [ ] Lockpicking GUI renders correctly with ImmediatelyFast installed
