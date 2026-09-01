# Locks Reforged 1.7.2 Implementation Specification

## Villager-proof locks, optional itemless lock picking, and discoverable key pairing

**Target repository:** [otectus/locks-reforged](https://github.com/otectus/locks-reforged)  
**Audited branch:** `main`  
**Audited revision:** [`481bb54fc78ee6f68719674486acb90693131dbf`](https://github.com/otectus/locks-reforged/commit/481bb54fc78ee6f68719674486acb90693131dbf)  
**Current release at that revision:** 1.7.1  
**Target release:** 1.7.2  
**Platform:** Minecraft 1.20.1, Forge 47.2.0+, Java 17, official Mojang mappings

---

## 1. Purpose

Version 1.7.2 should address three user-facing problems:

1. A locked wooden door can still be opened by villagers or other mobs whose AI changes the door through Minecraft's entity-facing door API. The existing player and redstone protections do not guard that route.
2. Modpack authors need a server configuration option that lets players enter and complete the lock-picking minigame without possessing or consuming a lock-pick item.
3. Key pairing is functional but effectively undiscoverable. A player must currently combine a Key Blank with an **unplaced** lock in a 3×3 crafting table, even though the recipe has only two ingredients and does not appear like an ordinary recipe. The mod does not explain this workflow in-game or in its current README.

This is a focused corrective and usability release. It must preserve existing worlds, lock IDs, key compatibility, NBT, recipes, and default gameplay unless a server owner explicitly enables the new itemless-picking option.

---

## 2. Executive implementation outcome

The finished release should behave as follows:

- A locked vanilla or modded `DoorBlock` cannot be opened through `DoorBlock#setOpen`, regardless of whether the caller is a vanilla villager, another mob goal, a Brain behavior, or modded AI using the vanilla API.
- Closing a door remains permitted, and re-locking a door ensures it is physically closed.
- A new server config option, `Allow Itemless Lock Picking`, defaults to `false`.
- When that option is `true`, an empty-main-hand interaction with a locked block opens a server-authoritative itemless version of the existing minigame.
- Itemless picking never consumes or damages an item, cannot be blocked by the Complexity enchantment, and resets progress on a wrong pin so the minigame retains a real failure condition without permanently denying loot.
- Physical lock picks continue to work exactly as they did in 1.7.1, whether itemless picking is enabled or not.
- A Key Blank and an unplaced lock can be paired in either the 2×2 inventory grid or a 3×3 crafting table. The lock is returned and the output key receives the same ID.
- A paired key plus one Key Blank continues to produce a duplicate key.
- Tooltips, an action-bar hint, the README, and the CurseForge description explain the pairing workflow and clearly state that a blank key cannot copy a placed locked lock.

### Immediate answer for the existing support question

Until 1.7.2 is released, the correct 1.7.1 workflow is:

1. Keep the lock as an item; do **not** place it yet.
2. Put that lock and exactly one **Key Blank** together in a 3×3 crafting table. The arrangement is shapeless.
3. Take the resulting **Key**. The lock remains in the crafting grid and the key carries the lock's ID.
4. Place the lock. The paired key can now unlock and re-lock it.
5. To make another copy, craft the paired key with another Key Blank.

A blank key cannot currently be paired directly to a lock that is already placed in the world.

---

## 3. Verified repository baseline

The implementation plan below is based on the actual 1.7.1 code, not on assumptions about the original 1.16.5 mod.

| Area | Current implementation | Consequence |
| --- | --- | --- |
| Player lock enforcement | [`LocksForgeEvents#onRightClick`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/common/event/LocksForgeEvents.java) handles `PlayerInteractEvent.RightClickBlock` | Only player-generated Forge interaction events enter this authorization flow. Mob AI does not. |
| Redstone door enforcement | [`LevelMixin#hasNeighborSignal`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/mixin/LevelMixin.java) reports no neighbor signal for locked positions | Redstone cannot normally open a locked door, but AI door opening is a separate route. |
| Door AI guard | No `DoorBlock` or villager-AI mixin exists in either mixin manifest | AI may call the vanilla door-opening API without consulting the lock. |
| Lock-picking entry | `onRightClick` only opens the minigame when `LocksTagHelper.isLockPick(stack)` is true | Empty-hand picking is impossible. |
| Menu validity | [`LockPickingContainer#stillValid`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/common/container/LockPickingContainer.java) requires a valid physical pick in the recorded hand | Even if an empty-hand screen were opened, it would immediately become invalid. |
| Packet validation | [`TryPinPacket`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/common/network/toserver/TryPinPacket.java) revalidates the held physical pick | Itemless attempts would be rejected server-side. |
| Wrong-pin consequence | `LockPickingContainer#tryBreakPick` models physical breakage, replacement-pick scanning, pick enchantments, and Shocking's `PICK_BREAK` trigger | Itemless mode needs its own explicit outcome; it must not pretend that a real item broke. |
| Lock-picking rendering | [`LockPickingScreen#resetPick`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/client/gui/LockPickingScreen.java) reads the held item and falls back to the iron-pick GUI texture | The fallback is usable for a virtual pick, but reset/break animation needs mode awareness. |
| Key pairing | [`KeyRecipe`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/common/recipe/KeyRecipe.java) copies the `Id` NBT from one locking item to one Key Blank | The underlying pairing model is already correct. |
| Crafting-grid requirement | `KeyRecipe#canCraftInDimensions` returns `x >= 3 && y >= 3` | The two-item recipe fails in the player's 2×2 inventory grid. |
| Pairing discovery | `KEY_BLANK` is registered as a plain `Item`; current English lang, README, and CurseForge description do not explain pairing | Players have no in-game directions and cannot infer the special dynamic-NBT recipe. |
| Version/protocol | `gradle.properties` is 1.7.1; [`LocksNetwork`](https://github.com/otectus/locks-reforged/blob/481bb54fc78ee6f68719674486acb90693131dbf/src/main/java/melonslise/locks/common/init/LocksNetwork.java) uses protocol `2` | Menu extra-data changes should be paired with a protocol bump so mixed 1.7.1/1.7.2 connections fail cleanly. |

Minecraft 1.20.1 exposes `DoorBlock#setOpen(@Nullable Entity, Level, BlockState, BlockPos, boolean)`. This is the narrow, stable choke point to guard. It is preferable to targeting one villager behavior because both legacy mob goals and modern Brain-based behaviors ultimately operate on the door, and modded AI can use the same API.

---

## 4. Release invariants

Treat these as non-negotiable acceptance rules:

1. **The server owns authorization and lock state.** The client may render a mode supplied by the server but must never tell the server that an attempt is itemless or authorized.
2. **Defaults preserve 1.7.1 gameplay.** Existing configs that lack the new key must behave as if itemless picking is disabled.
3. **No registry removal.** The new option does not unregister lock picks, recipes, tags, trades, enchantments, or existing items. Removing registered content would threaten existing worlds and is not required to let players pick without items.
4. **Locked means unable to open.** A locked door must not be openable by a player without authorization, redstone, villager AI, other vanilla mob AI, or modded AI that uses `DoorBlock#setOpen`.
5. **Closing remains safe.** An entity or system may close a locked door; the mixin must block only `open == true`.
6. **No forced chunk loads from the new door check.** This repository has extensive C2ME/async-chunk hardening. The new mixin must use the existing non-forcing lock lookup and must not call `Level#getChunk`, `getChunkAt`, or any blocking chunk method.
7. **Itemless means no material gate.** Complexity, pick tier, durability, replacement-pick search, and pick-side enchantments cannot make an itemless session impossible.
8. **Physical picking is unchanged.** Break probability, enchantments, automatic replacement, Auto-Pick, sounds, and damage remain identical in item-backed sessions.
9. **Key pairing does not leak access.** A player cannot copy the ID from an already-placed locked lock merely by touching it with a blank key.
10. **All lock mutations remain observable.** Continue using `Lock#setLocked` or a central helper that calls it, so the existing observer, chunk-dirtying, persistence, and client-sync behavior remains intact.

---

## 5. Workstream A — prevent villagers and other AI from opening locked doors

### 5.1 Root cause

`LocksForgeEvents#onRightClick` is comprehensive for players, but it is subscribed to a player event. Villagers do not simulate a player's right-click. Their pathfinding/Brain logic requests a door state change through the door itself. The current `LevelMixin` only suppresses redstone power and therefore does not see that request.

Do not fix this by adding `instanceof Villager` checks to player code. Do not target only `InteractWithDoor`, because that would leave `DoorInteractGoal`, raider/open-door goals, and modded AI routes exposed.

### 5.2 Add a central `DoorBlock` guard

Create:

`src/main/java/melonslise/locks/mixin/DoorBlockMixin.java`

Target the exact 1.20.1 official-mapping descriptor:

```java
setOpen(
    @Nullable Entity source,
    Level level,
    BlockState state,
    BlockPos pos,
    boolean open
)
```

Inject at `HEAD`, cancellable. The policy is deliberately small:

```java
if (!open || level.isClientSide)
    return;

if (LocksUtil.locked(level, pos))
    ci.cancel();
```

Implementation requirements:

- Use the full descriptor in the injection annotation if necessary to avoid overload ambiguity.
- Keep the injection required under the repository's existing `defaultRequire: 1`; a mapping or signature regression should fail loudly during development instead of silently disabling security.
- Do not special-case villagers. Guard all callers of the vanilla API.
- Do not play the lock-rattle sound from this mixin. Villager AI may retry frequently, and a sound or game event on every retry would create noise and possible performance problems.
- Do not modify the lock, door, AI memory, navigation path, or entity. Cancellation alone is the authoritative protection.
- Use `LocksUtil.locked(level, pos)`, which follows the existing lockable index/storage path. Do not introduce a parallel lock lookup.

Add `DoorBlockMixin` to the common `mixins` array in:

- `src/main/resources/locks.mixins.json`
- the root `locks.mixins.json`, which currently duplicates the packaged manifest and should remain synchronized unless the project intentionally removes that duplicate in a separate cleanup

### 5.3 Enforce the closed-door invariant when a lock becomes locked

Blocking future `setOpen(..., true)` calls is not sufficient if the door was already open when the lock was placed or re-locked. Centralize the transition instead of adding more direct `lock.setLocked(true)` calls.

Recommended helper shape:

```java
LocksUtil.setLockState(Level level, Lockable lockable, boolean locked, @Nullable Entity source)
```

Behavior:

1. If `locked` is `true`, iterate the positions in `lockable.bb`.
2. For each `DoorBlock` whose `OPEN` property is currently true, call `door.setOpen(source, level, state, pos, false)`.
3. Call `lockable.lock.setLocked(locked)` even when no door is present.
4. Do all world mutation on the logical server.

The new mixin explicitly allows `open == false`, so closing after the lock state flips is safe. Calling the door's API is preferable to blindly changing the property because it preserves vanilla's two-block synchronization, sound, and game event behavior.

Route every relevant transition through the helper:

- matching held key unlock/re-lock
- held key ring unlock/re-lock
- Curios key ring unlock/re-lock
- Master Key unlock/re-lock
- Awareness owner unlock/re-lock
- physical Auto-Pick unlock
- completed physical minigame unlock
- completed itemless minigame unlock
- newly placed lock, if its `Lock` state is initially locked

The helper must not call `resolveLootTables`; keep loot resolution tied only to a real locked-to-unlocked transition in the existing call sites. It must also avoid playing the lock click, because each caller already owns the appropriate sound.

If a full transition refactor is judged too risky for 1.7.2, the minimum acceptable alternative is:

- add `DoorBlockMixin`; and
- close doors explicitly in both lock-placement paths and every re-lock branch in `LocksForgeEvents`.

The centralized helper is strongly preferred because the current handler has several independent re-lock branches and future features could otherwise forget the invariant.

### 5.4 Compatibility boundary

This fix covers:

- vanilla villagers using Brain door interaction;
- mobs using vanilla door-interaction goals;
- raiders or other entities using the standard door API;
- modded villagers/NPCs that operate a vanilla or subclassed `DoorBlock` by calling `setOpen`.

It cannot guarantee support for a modded “door” block that does not extend `DoorBlock`, overrides the behavior without calling the guarded method, or directly writes an `OPEN` property with `Level#setBlock`. Such a block needs a dedicated compatibility hook. Document that boundary rather than adding a broad `Level#setBlock` mixin, which would be invasive and could interfere with unrelated state changes.

### 5.5 Villager-fix tests

Add deterministic GameTests or an equivalent integration harness:

1. **Locked vanilla door rejects entity open**
   - Place a closed oak door.
   - Register a locked `Lockable` covering both halves.
   - Spawn a villager.
   - Call `DoorBlock#setOpen(villager, level, state, pos, true)`.
   - Assert both halves remain closed and the lock remains locked.

2. **Unlocked door still opens**
   - Set the same lock to unlocked.
   - Call the same API.
   - Assert the door opens.

3. **Locked door may close**
   - Begin with an open door and a locked lockable.
   - Call `setOpen(..., false)`.
   - Assert the door closes.

4. **Re-lock closes an open door**
   - Unlock, open, and then re-lock with a matching key through the production helper.
   - Assert the door is closed and cannot be reopened by the villager.

5. **Double door/multi-block lock**
   - Cover a paired door using the same bounding-box path as `LockItem#easyLock`.
   - Assert an entity cannot open either leaf while the lock is locked.

6. **No lock, no behavior change**
   - An ordinary unprotected door must retain vanilla behavior.

7. **Async-safety smoke test**
   - Run the door interaction with C2ME present if the project has its established compatibility environment.
   - Confirm no blocking chunk fetch or new off-thread handler mutation appears in logs or thread dumps.

An additional manual AI test should place a villager, a destination/POI, and a locked door between them. Let the world run long enough for repeated navigation attempts and verify that the door never enters `OPEN=true`, no sound spam occurs, and server tick time remains stable.

---

## 6. Workstream B — optional itemless lock picking

### 6.1 Configuration contract

Add this value to `LocksServerConfig` near other top-level gameplay options:

| Java field | TOML key | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `ALLOW_ITEMLESS_LOCK_PICKING` | `Allow Itemless Lock Picking` | Boolean | `false` | Allows a player with an empty main hand to enter and complete the lock-picking minigame without a lock-pick item. Physical lock picks remain usable. |

Suggested config comment:

```text
Allow players to pick locked blocks with an empty main hand.
Itemless attempts use the normal pin minigame but never consume an item,
bypass lock-pick strength/Complexity requirements, and reset progress on a wrong pin.
This does not remove lock-pick items, recipes, loot, trades, or enchantments.
```

Why this direction is preferable to `Require Lock Pick Item = true`:

- the default value is an intuitive opt-in `false`;
- existing generated configs gain a new false setting without inverting semantics;
- the option describes the actual behavior rather than suggesting registry removal;
- it still satisfies the request that players never *need* a physical pick.

This belongs in the server config because it changes server-authoritative access and must be identical for every connected player in a world.

### 6.2 Interaction precedence

Preserve the following order when a locked block is clicked with the main hand:

| Priority | Condition | Result |
| --- | --- | --- |
| 1 | Valid held physical lock pick | Existing physical-pick flow, including Complexity and Auto-Pick |
| 2 | Master Key | Existing universal toggle |
| 3 | Matching held key | Existing matching-ID toggle |
| 4 | Held key ring containing a match | Existing matching-key toggle |
| 5 | Awareness owner | Existing owner toggle |
| 6 | Matching Curios key ring | Existing Curios toggle |
| 7 | Empty main hand and `Allow Itemless Lock Picking = true` | Open itemless minigame |
| 8 | Anything else | Existing denial/rattle/Shocking unauthorized-interaction behavior |

This ordering matters. A player wearing a matching Curios key ring should still unlock normally rather than being forced into a minigame merely because her hand is empty.

Only an **empty main hand** initiates itemless picking. An arbitrary held item must not act as a virtual pick; doing so would hijack interactions with food, tools, blocks, and other mod items.

### 6.3 Introduce an explicit picking mode

Do not spread boolean expressions such as `config || isLockPick(stack)` throughout the menu, packet, and screen. Add a small explicit mode, for example:

```java
public enum LockPickingMode {
    ITEM_BACKED,
    ITEMLESS
}
```

The server decides the mode at menu-open time. The mode is stored on `LockPickingContainer` and serialized to the client solely so the client can render and animate correctly.

Recommended menu extra-data order:

1. `InteractionHand`
2. `LockPickingMode` as a bounded enum/byte
3. the existing full `Lockable`

Update `Provider`, `Writer`, `FACTORY`, and every constructor call together. Reject an out-of-range mode value during decoding rather than indexing an enum array unchecked.

Because the menu wire payload changes, bump `LocksNetwork.PROTOCOL_VERSION` from `2` to `3`. Although the menu bytes travel through the Forge menu-opening path rather than `TryPinPacket`, the channel version is the project's available handshake guard; bumping it prevents a 1.7.1 client and 1.7.2 server from accepting one another and then decoding incompatible state.

### 6.4 Server-side entry helper

Extract one server-side helper from the very large `onRightClick` method, conceptually:

```java
tryOpenLockPicking(ServerPlayer player, InteractionHand hand, Lockable lockable, LockPickingMode mode)
```

For `ITEM_BACKED`:

- verify the current held stack is a tagged/registered lock pick;
- apply existing `LockPickItem.canPick` Complexity logic;
- apply existing Auto-Pick behavior;
- otherwise open the menu.

For `ITEMLESS`:

- verify the config is currently enabled;
- verify the recorded hand is empty;
- bypass `LockPickItem.canPick` entirely;
- do not roll Auto-Pick, because the user selected a mode intended to play the minigame and no enchanted pick is present;
- open the menu with mode `ITEMLESS`.

Make the actual menu open server-only. The client-side interaction handler should cancel the protected block interaction and render the server-provided screen, not independently decide that the config permits access.

### 6.5 Container validity and security

Replace the current physical-only `isValidPick`/`stillValid` assumptions with mode-aware methods.

Recommended responsibilities:

```java
boolean canAttempt(ServerPlayer player)
boolean isValidPhysicalPick(ItemStack stack)
boolean isItemlessSessionStillAllowed(ServerPlayer player)
```

`ITEM_BACKED` remains valid only while:

- the lock is still locked;
- the hand contains a recognized pick;
- the pick still meets Complexity;
- the player is still close enough to the lock.

`ITEMLESS` remains valid only while:

- the lock is still locked;
- `ALLOW_ITEMLESS_LOCK_PICKING` remains true;
- the recorded hand remains empty;
- the player is not a spectator;
- the player is still close enough to the lock.

Add a distance check using the stored lock position, such as the normal container reach threshold (`distanceToSqr` no greater than 64), and verify the server's loaded handler still contains the lock ID. The current menu only checks lock state and held pick; modifying it is the right time to prevent a player from keeping the minigame open after moving away or after the lock is removed.

Do not call `LockPickItem.getOrSetStrength` or any pick-stat method on `ItemStack.EMPTY`/air. Branch on the mode before touching physical-pick code.

### 6.6 Packet validation

`TryPinPacket` should remain minimal: the client sends only the selected pin. It must not send the picking mode, config value, success result, current progress, or claimed lock ID.

On the server:

1. Confirm the sender exists.
2. Confirm the active menu is a `LockPickingContainer` by `instanceof`, not only by comparing its type and then casting.
3. Confirm `pkt.pin` is within `[0, lockLength)`.
4. Call the container's mode-aware `canAttempt(sender)`.
5. Resolve/confirm the canonical server lockable and ensure it is still locked.
6. Only then call `tryPin`.

This keeps pin order, progress, lock state, config, distance, and item requirements server-authoritative.

### 6.7 Define itemless minigame semantics

The itemless mode needs explicit rules rather than accidentally inheriting physical-item assumptions.

| Mechanic | Item-backed session | Itemless session |
| --- | --- | --- |
| Complexity eligibility | Existing pick strength + Attunement | Always eligible; no loot can be permanently gated by absent picks |
| Correct pin | Advance | Advance |
| Wrong pin | Existing break/survival roll | Reset solved-pin progress every time |
| Item damage/consumption | Existing behavior | None |
| Replacement pick search | Existing behavior | Never run |
| Finesse | Existing behavior | Ignored |
| Attunement | Existing behavior | Ignored |
| Last Catch | Existing behavior | Ignored |
| Quiet Hand | Existing behavior | Ignored; use normal fail volume |
| Grounded | Existing held-item behavior | No virtual enchantment; ordinary Shocking mitigation rules apply to actual held items only |
| Auto-Pick | Existing chance | Never roll |
| Shocking `PICK_BREAK` | Existing behavior when a pick breaks | Never trigger because no pick broke |
| Shocking `WRONG_PIN` | Existing opt-in behavior when the pick survives | May trigger on the itemless wrong pin if enabled |
| Completion | Unlock and resolve loot | Same |

Refactor `tryPin` so the wrong-pin result is not represented only as “did a physical pick break?” A clean internal outcome is:

```java
enum PinAttemptOutcome {
    CORRECT,
    WRONG_CONTINUE,
    WRONG_RESET,
    PICK_BROKE
}
```

The existing client packet may still encode the minimal booleans if the menu mode disambiguates the animation, but server logic should distinguish `WRONG_RESET` from `PICK_BROKE` so itemless failures cannot invoke item breakage, replacement scanning, or the wrong Shocking trigger.

### 6.8 Client rendering and animation

`LockPickingScreen` already falls back to `textures/gui/iron_lock_pick.png` when an item-specific GUI texture cannot be found. Use that fallback deliberately as the virtual tool texture for `ITEMLESS` rather than looking up the player's empty hand.

Separate two reset presentations:

- `PICK_BROKE`: keep the existing split-pick break animation and slide a replacement physical pick into view.
- itemless `WRONG_RESET`: lower all solved pins and withdraw/reinsert or reposition the intact virtual pick without showing broken fragments.

Extract the shared “clear solved pins” portion from the current `reset()` method so the two animations cannot drift apart.

The mode must come from the menu data written by the server. Do not consult a local server-config file to decide how a remote server's session should render.

### 6.9 Completion behavior

When every pin is solved, closing/removing the menu currently unlocks the lock in `LockPickingContainer#removed`. Preserve the result but harden the conditions:

- completion must have been reached server-side;
- the canonical lock must still exist and remain locked;
- the player must still satisfy the session validity check at the moment of completion;
- perform the transition through the new central lock-state helper;
- play the existing lock-open sound once;
- call `LocksUtil.resolveLootTables` once.

Avoid toggling with `setLocked(!isLocked())`; use the explicit target `false`. A completion path should never re-lock because state changed concurrently.

### 6.10 Itemless-picking tests

Required automated or GameTest coverage:

1. Config absent/default false + empty hand: no menu, lock stays locked, normal denial behavior.
2. Config true + empty main hand: itemless menu opens.
3. Config true + nonempty arbitrary item: no itemless menu.
4. Config true + valid physical pick: physical mode is selected and all old mechanics remain active.
5. Complexity at maximum + itemless: menu still opens and can complete.
6. Wrong itemless pin: progress resets, no inventory slot changes, no durability changes, no item-break event, no replacement scan.
7. Shocking-on-pick-break enabled: an itemless wrong pin does not invoke it.
8. Shocking-on-wrong-pin enabled: an itemless wrong pin invokes that trigger once, subject to cooldown.
9. Completion: unlocks once and resolves a loot table once.
10. Move beyond reach, remove the lock, change dimensions, fill the recorded hand, become spectator, or disable config mid-session: the menu becomes invalid and packets stop having an effect.
11. Malformed pin byte (`-1`, `length`, `127`): rejected without exception or mutation.
12. Old-client/new-server or new-client/old-server connection: rejected cleanly by protocol mismatch.
13. Physical regression matrix: wood through netherite picks, Complexity, Auto-Pick, Finesse, Attunement, Last Catch, Quiet Hand, Grounded, Shocking, replacement pick selection, and netherite-unbreakable behavior remain unchanged.

---

## 7. Workstream C — make key pairing reliable and discoverable

### 7.1 Preserve the security model

The correct model is “cut the key from an item that already knows the lock ID,” not “read any placed lock's ID with a blank key.” A placed-lock copying gesture would let any visitor manufacture a matching key and defeat the mod's primary purpose.

For 1.7.2:

- pair from an **unplaced lock item**;
- duplicate from an **existing paired key**;
- do not pair from a placed locked lock;
- do not expose hidden lock IDs when `Hide Lock ID` is enabled.

If post-placement owner key cutting is ever added later, first persist a separate placer/owner UUID for every lock and require that identity or another existing authorization method. Do not overload the Awareness owner field or silently grant copying to anyone.

### 7.2 Fix the unnecessary 3×3 restriction

Change:

```java
return x >= 3 && y >= 3;
```

to a dimension rule that accepts both vanilla crafting containers, for example:

```java
return x * y >= 2;
```

The recipe only needs two occupied slots, so the 2×2 inventory grid is sufficient. Keep the recipe shapeless.

### 7.3 Tighten source-item validation

The current recipe treats any stack containing an integer-like `Id` tag as the source. That can accidentally accept unrelated mod items that also use a generic `Id` NBT key.

Classify ingredients explicitly:

- exactly one `locks:key_blank` stack/slot;
- exactly one source for which `LocksTagHelper.isLock(stack)` or `LocksTagHelper.isKey(stack)` is true;
- no Master Key;
- no Key Ring;
- no lock pick;
- no arbitrary item that merely contains an `Id` field;
- no second source or extra ingredient.

Using `LocksTagHelper` retains support for the project's data-driven lock registrations and tagged compatibility items.

The blank stack may contain more than one item; a normal craft consumes one blank and produces one key. Shift-crafting should repeat one-for-one until blanks or output space run out. The source lock/key must remain exactly once and retain all NBT, enchantments, custom name, owner data, and other metadata.

### 7.4 Make lock ID initialization robust

`LockingItem#getOrSetId` currently assigns IDs during server-side inventory ticks. A newly crafted or command-created lock can briefly exist without `Id`, while the current recipe refuses any source lacking it.

Add small identity helpers to `LockingItem`, such as:

```java
boolean hasId(ItemStack stack)
int ensureId(ItemStack stack)
OptionalInt readId(ItemStack stack)
ItemStack copyId(ItemStack from, ItemStack to)
```

Recommended behavior:

- `onCraftedBy` ensures an ID on the logical server as soon as a lock/key stack is crafted.
- `inventoryTick` remains as a backstop for `/give`, loot, migration, and third-party creation paths.
- `KeyRecipe#matches` recognizes the source by item/type/tag, not by pre-existing NBT.
- `assemble` ensures the source ID before copying it to the output.
- `getRemainingItems` returns the now-ID-bearing source unchanged.

Be conscious that recipe preview runs on the client while the server owns the final crafted stack. Do not trust a client-generated ID. Verify in an integrated client/server test that the server's source and output key end with the same ID and that its result replaces any speculative client preview cleanly.

Do not change the persisted key from a 32-bit `Id` to a UUID in this patch. Existing locks, keys, key rings, network data, and worlds all depend on the integer format; that would be a migration release, not a 1.7.2 usability fix.

### 7.5 Add an explanatory Key Blank item

Create a lightweight `KeyBlankItem` class and register `KEY_BLANK` with it instead of plain `Item`.

Suggested tooltip lines:

```text
Craft with an unplaced lock to make its key
Craft with a paired key to make a copy
```

Use translatable components, subdued formatting, and no hard-coded English. Suggested keys:

```json
"locks.tooltip.key_blank.pair": "Craft with an unplaced lock to make its key",
"locks.tooltip.key_blank.copy": "Craft with a paired key to make a copy"
```

The tooltip should not reveal a numeric lock ID and should remain useful when `Hide Lock ID` is enabled.

### 7.6 Add a contextual placed-lock hint

In the unauthorized branch of `onRightClick`, detect a held Key Blank before the generic rattle message. Leave the lock untouched and show a server-authored action-bar message such as:

```text
Pair this blank with the lock item in a crafting grid before placing the lock
```

Suggested translation key:

```json
"locks.status.key_blank_pairing": "Pair this blank with the lock item in a crafting grid before placing the lock"
```

The interaction may still swing/rattle according to existing Silent behavior, but the pairing explanation should replace the generic “This block is locked” feedback for this specific mistake. Rate-limit or rely on one-message-per-click behavior; do not send it every tick.

### 7.7 Documentation additions

Add a “Keys and Key Pairing” section to both `README.md` and `CURSEFORGE_DESCRIPTION.md`:

```markdown
### Pairing a key

1. Craft a Key Blank.
2. Before placing the lock, combine one Key Blank and the lock in any crafting grid.
3. The recipe returns the lock and creates a Key with the same lock ID.
4. Place the lock and use that Key to unlock or re-lock it.
5. To duplicate a Key, combine the paired Key with another Key Blank.

The recipe is shapeless. Blank keys cannot copy a lock that is already placed.
```

Also mention key rings and the Master Key immediately afterward so the complete access workflow is in one place.

Do not attempt to replace the dynamic recipe with an ordinary static JSON recipe: the output must copy NBT from the source, which is why `locks:crafting_key` is a custom recipe serializer. Optional JEI/REI display support may be added later as a dedicated compatibility feature, but tooltips and first-party documentation are required for 1.7.2 regardless of whether a recipe viewer is installed.

### 7.8 Key-pairing tests

Required coverage:

1. Unplaced lock + one blank in a 2×2 grid matches.
2. The same inputs in a 3×3 grid match.
3. Output is one `locks:key` with the same `Id` as the source lock.
4. Source lock remains exactly once, with all NBT and enchantments preserved.
5. Existing paired key + blank creates a second key with the same ID and preserves the source key.
6. A stack of blanks consumes exactly one per craft; shift-crafting is one-for-one.
7. Two blanks without a source fail.
8. Two source items fail.
9. Extra unrelated item fails.
10. An unrelated mod item with an `Id` NBT field fails.
11. Master Key, Key Ring, and lock pick cannot act as the source.
12. A source missing `Id` receives one server-side and returns a key with that exact same ID.
13. `Hide Lock ID = true` hides numeric IDs but leaves explanatory tooltips intact.
14. Holding a blank against a placed lock shows the pairing hint and does not alter either stack or lock state.

---

## 8. Recommended code organization

The existing `onRightClick` method spans hundreds of lines and repeats lock transitions across held keys, rings, Curios, Awareness, and Master Key paths. A narrow refactor will make this patch safer.

Recommended helpers:

```text
findIntersectingLockables(level, pos)
findFirstLocked(lockables)
tryHeldAuthorization(...)
tryAwarenessAuthorization(...)
tryCuriosAuthorization(...)
tryStartPhysicalPicking(...)
tryStartItemlessPicking(...)
setLockState(level, lockable, targetState, source)
denyAndRattle(...)
```

Constraints on the refactor:

- Preserve the main-hand-only rule and offhand block denial.
- Preserve the self-healing chunk-storage/world-index reconciliation added in 1.6.3.
- Preserve the targeted `AddLockableToChunkPacket` repair sent to an interacting player.
- Preserve exact key, ring, Curios, Awareness, sound, swing, cancellation, and loot-resolution behavior.
- Avoid a broad rewrite of lock storage, observer synchronization, rendering, or world generation.
- Do not introduce a second authorization model solely for doors; door enforcement should consume the same `locked` truth as every other protection.

---

## 9. File-by-file implementation map

| File | Required 1.7.2 change |
| --- | --- |
| `gradle.properties` | Set `mod_version=1.7.2`. |
| `src/main/java/melonslise/locks/mixin/DoorBlockMixin.java` | New server-authoritative guard for `DoorBlock#setOpen(..., true)`. |
| `src/main/resources/locks.mixins.json` | Register `DoorBlockMixin`. |
| `locks.mixins.json` | Keep the duplicate development manifest synchronized, or deliberately remove/consolidate it after verifying the build. |
| `LocksServerConfig.java` | Add `ALLOW_ITEMLESS_LOCK_PICKING`, default false, with detailed comment. |
| `LocksForgeEvents.java` | Add itemless entry after all existing authorization paths; add Key Blank hint; route lock transitions through helper; retain sync/self-heal behavior. |
| `LocksUtil.java` or a new focused service | Add explicit lock-state transition/door-closing helper without blocking chunk access. |
| `LockPickingContainer.java` | Store mode, validate itemless sessions, implement mode-specific wrong-pin behavior, reach/canonical-lock checks, and explicit unlock completion. |
| `TryPinPacket.java` | Use mode-aware server validation and safe `instanceof` checks. |
| `LockPickingScreen.java` | Render the virtual pick deterministically and distinguish intact itemless reset from physical break animation. |
| `LocksNetwork.java` | Bump protocol `2` → `3`. |
| `KeyRecipe.java` | Accept 2×2 grids, validate real lock/key sources, initialize/copy ID safely, preserve the source. |
| `LockingItem.java` | Add identity helpers and immediate crafted-stack initialization while retaining inventory-tick backstop. |
| `KeyBlankItem.java` | New tooltip-bearing item class. |
| `LocksItems.java` | Register `KEY_BLANK` using `KeyBlankItem`. |
| `assets/locks/lang/en_us.json` | Add itemless/pairing tooltip and status translations; keep existing keys. |
| `README.md` | Update version/build output, config list, itemless behavior, and complete key-pairing instructions. |
| `CURSEFORGE_DESCRIPTION.md` | Add concise player-facing instructions and config description. |
| `CHANGELOG.md` | Add a 1.7.2 entry describing root causes, defaults, behavior, and compatibility. |
| `KNOWN_ISSUES.md` | Record the fixes and exact verification status; do not mark manual tests complete unless actually run. |
| `CLAUDE.md` | Update version and mixin count after adding `DoorBlockMixin`; mention new GameTest command if added. |
| `META-INF/mods.toml` | No version edit is needed because Gradle expands it; consider correcting `issueTrackerURL` to this maintained repository as release hygiene. |
| `src/test/...` and/or GameTest source | Add policy, recipe, packet-boundary, transition, and door regression tests. |

---

## 10. Suggested implementation sequence

### Phase 1 — establish regression fixtures

1. Add tests that demonstrate the current villager/entity door bypass.
2. Add recipe tests demonstrating the current 2×2 rejection and 3×3 success.
3. Add a small picking-mode policy test surface so physical and itemless outcomes can be exercised without depending entirely on the GUI.

The tests should fail for the expected reason before production code changes.

### Phase 2 — door security

1. Add `DoorBlockMixin` and both manifest entries.
2. Add the central lock-state/close-door helper.
3. Route every lock/re-lock transition through it.
4. Run door GameTests and manual vanilla villager AI verification.
5. Smoke-test redstone, player keys, trapdoors, gates, and ordinary unlocked doors for regressions.

### Phase 3 — itemless minigame

1. Add the default-false server config.
2. Introduce `LockPickingMode` and serialize it with the menu.
3. Add the interaction entry point at the correct precedence.
4. Refactor container validity and `TryPinPacket` validation.
5. Implement itemless wrong-pin reset and completion.
6. Add mode-aware client rendering/animation.
7. Bump protocol to `3`.
8. Run the physical-pick regression matrix before considering the feature complete.

### Phase 4 — key-pairing UX

1. Update the dynamic recipe for 2×2 crafting and strict source recognition.
2. Harden ID initialization and source preservation.
3. Add `KeyBlankItem` tooltips and the placed-lock hint.
4. Add README and CurseForge instructions.
5. Test ordinary craft, shift-craft, key duplication, fresh no-ID sources, and unrelated `Id`-bearing mod items.

### Phase 5 — release verification

1. Update versioned documentation and changelog.
2. Run automated build/test/data checks.
3. Boot client and dedicated server.
4. Run single-player and multiplayer manual matrices.
5. Inspect the built JAR for correct version, refmap, both new/updated mixin entries, lang, and recipes.

---

## 11. Full QA matrix

### 11.1 Door and entity behavior

- [ ] Vanilla villager cannot open a locked oak door.
- [ ] Villager can open the same door after the lock is unlocked.
- [ ] Villager can close a locked open door.
- [ ] Re-locking an open door closes it.
- [ ] Redstone still cannot open a locked door.
- [ ] Redstone works after unlock.
- [ ] Player without authorization still receives normal denial.
- [ ] Matching key, key ring, Curios ring, Master Key, and Awareness behavior remain correct.
- [ ] Double doors and locks spanning both halves remain protected.
- [ ] No repeated rattle/sound/event spam from AI retries.
- [ ] A modded `DoorBlock` subclass using `setOpen` is protected.
- [ ] A normal unlocked/unprotected door has unchanged vanilla behavior.

### 11.2 Itemless configuration

- [ ] Generated server config contains `Allow Itemless Lock Picking = false`.
- [ ] Existing worlds without the key retain physical-pick-only behavior.
- [ ] Enabling it on a server applies to every player.
- [ ] Empty main hand opens the minigame only when enabled.
- [ ] Nonempty unrelated hand never opens itemless mode.
- [ ] A matching Curios ring takes precedence over itemless picking.
- [ ] No item is consumed, damaged, created, or moved by itemless attempts.
- [ ] Maximum Complexity cannot block itemless access.
- [ ] Wrong pin resets progress with an intact virtual-tool animation.
- [ ] Correct sequence unlocks and resolves loot once.
- [ ] Itemless failure never fires `PICK_BREAK` Shocking.
- [ ] Itemless failure may fire configured `WRONG_PIN` Shocking.
- [ ] Closing the screen early leaves the lock locked.
- [ ] Distance, dimension, spectator, hand-state, lock-removal, and live-config changes invalidate the session.
- [ ] Dedicated server rejects malformed/stale packets without crash or mutation.

### 11.3 Physical-pick regression

- [ ] Every built-in and data-driven pick still opens its normal GUI texture.
- [ ] Complexity and Attunement thresholds are unchanged.
- [ ] Sturdy, Finesse, and Last Catch break calculations are unchanged.
- [ ] Quiet Hand and Grounded are unchanged.
- [ ] Auto-Pick remains physical-pick-only and keeps its old probability.
- [ ] Broken pick replacement still selects a valid inventory pick.
- [ ] Netherite durability and unbreakable config remain correct.
- [ ] ImmediatelyFast/other known GUI optimization compatibility remains intact.

### 11.4 Key pairing

- [ ] Lock + Key Blank works in inventory 2×2.
- [ ] Lock + Key Blank works in table 3×3.
- [ ] The source lock is returned unchanged and the key ID matches.
- [ ] Paired key + Key Blank duplicates correctly.
- [ ] One blank is consumed per output, including shift-craft.
- [ ] Extra inputs and multiple sources are rejected.
- [ ] Unrelated `Id` NBT items are rejected.
- [ ] Fresh source without ID ends with one stable server ID shared by source and output.
- [ ] Tooltips explain both pair and copy workflows.
- [ ] Placed-lock blank-key click explains pre-placement pairing without copying or mutating access.
- [ ] Hidden-ID mode does not remove the instructional text or reveal the ID.

### 11.5 Persistence and compatibility

- [ ] Locks and keys created in 1.7.1 still match in 1.7.2.
- [ ] Locks created in 1.7.2 persist locked/unlocked state across chunk unload, restart, and adjacent-chunk load order.
- [ ] No new `getChunk`/`getChunkAt` call exists in door or picking validation paths.
- [ ] C2ME smoke test shows no hang, map corruption, or off-thread mutation.
- [ ] Carry On retains lock state and door protection after move.
- [ ] Hopper, furnace, chest capability, piston, explosion, and break protections are unchanged.
- [ ] Curios absent: clean boot and normal held-key behavior.
- [ ] Carry On optional JAR absent: clean build and boot.
- [ ] Client/server 1.7.2 connects; mixed 1.7.1/1.7.2 is rejected clearly.

---

## 12. Build and verification commands

Use JDK 17 and the repository's Gradle wrapper.

```bash
./gradlew clean test
./gradlew compileJava
./gradlew build
./gradlew runData
```

If GameTests are added as a Forge run configuration, also run the corresponding GameTest server task and document its exact name in `CLAUDE.md`.

Then perform:

```bash
./gradlew runClient
./gradlew runServer
```

The final JAR inspection should confirm:

- filename and embedded `mods.toml` version both report 1.7.2;
- `locks.refmap.json` is present;
- the packaged `locks.mixins.json` includes `DoorBlockMixin`;
- all new translation keys are present;
- `data/locks/recipes/key.json` still points to `locks:crafting_key`;
- no client-only class is loaded by the dedicated server;
- protocol version is `3` on both sides.

Do not dismiss the familiar development refmap warning as a production failure; the repository already documents that warning as cosmetic in dev. Do treat any failed `DoorBlockMixin` injection as release-blocking.

---

## 13. Proposed 1.7.2 changelog content

```markdown
## 1.7.2

### Locked doors now stop villagers and other door-opening AI

- Fixed villagers being able to open doors protected by a locked lock. Locks now guard Minecraft's central entity door-opening path, covering vanilla villagers, other door-opening mobs, and modded AI that uses the vanilla `DoorBlock` API.
- Re-locking an open door now closes it, so a door cannot remain physically open while its lock reports “locked.” Closing a locked door is still allowed.

### Optional itemless lock picking

- Added the server option **Allow Itemless Lock Picking** (default: off). When enabled, right-click a locked block with an empty main hand to play the normal pin minigame without carrying or consuming a lock pick.
- Itemless attempts always remain possible regardless of Complexity, reset progress on a wrong pin, and never trigger pick-break effects. Physical lock picks and their tiers, durability, Auto-Pick, and enchantments are unchanged.

### Clearer and easier key pairing

- A lock and one Key Blank can now be paired in either the 2×2 inventory grid or a crafting table. The lock is returned and the crafted Key receives its ID.
- A paired Key can still be combined with another Key Blank to make a copy.
- Added Key Blank tooltips, an in-world hint, and full pairing instructions. Keys are paired from an unplaced lock; a blank key cannot copy a lock that is already placed.
```

---

## 14. Definition of done

The update is complete only when all of the following are true:

- The villager bypass has a reproducible pre-fix test and a passing post-fix test at the central `DoorBlock#setOpen` boundary.
- A physically open door is closed whenever its lock transitions to locked.
- The itemless option exists in the server config, defaults false, and is enforced entirely by the server.
- Itemless sessions can complete every lock without item consumption or Complexity lockout, while wrong pins still have a meaningful reset consequence.
- The physical-pick path passes its full regression matrix.
- Key pairing works in 2×2 and 3×3 grids, safely preserves/copies IDs, and rejects unrelated `Id`-bearing items.
- Players can learn the pairing workflow from the item and first-party documentation without searching externally.
- Existing 1.7.1 worlds, locks, keys, key rings, NBT, and configs remain compatible.
- Unit tests, GameTests, build, data generation, client boot, dedicated-server boot, and relevant manual multiplayer checks pass.
- The built artifact reports 1.7.2 internally and contains the updated required mixin manifest.

The critical design theme is simple: enforce “locked” at the server's real mutation boundary, represent itemless picking as an explicit server-decided mode, and teach the existing secure key workflow instead of weakening it for convenience.
