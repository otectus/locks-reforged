package melonslise.locks.common.util;

import javax.annotation.Nullable;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksDamageSources;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LocksSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Central logic for the Shocking enchantment's damage and theft punishments. All Shocking damage flows
 * through here so the formula lives in one place and is fully driven by {@link LocksServerConfig}.
 *
 * <p>With shipped defaults (Base 0, Per Level 1.5, Max 1024, Requires Enchantment true, Cooldown 0, and
 * only the pick-break trigger enabled) this reproduces the original behavior exactly: a lock with
 * Shocking level {@code n} deals {@code n * 1.5} damage when a pick breaks.
 */
public final class ShockingHelper
{
	public enum Trigger
	{
		PICK_BREAK, WRONG_PIN, UNAUTHORIZED_INTERACTION, BLOCK_BREAK_ATTEMPT
	}

	private static final String COOLDOWN_KEY = Locks.ID + ":LastShock";

	private ShockingHelper() {}

	/** Whether the given trigger is allowed to shock (also gated by the master Enable Shocking switch). */
	public static boolean isTriggerEnabled(Trigger trigger)
	{
		if(!LocksServerConfig.ENABLE_SHOCKING.get())
			return false;
		return switch(trigger)
		{
			case PICK_BREAK -> LocksServerConfig.SHOCKING_ON_PICK_BREAK.get();
			case WRONG_PIN -> LocksServerConfig.SHOCKING_ON_WRONG_PIN.get();
			case UNAUTHORIZED_INTERACTION -> LocksServerConfig.SHOCKING_ON_UNAUTHORIZED_INTERACTION.get();
			case BLOCK_BREAK_ATTEMPT -> LocksServerConfig.SHOCKING_ON_BLOCK_BREAK_ATTEMPT.get();
		};
	}

	/**
	 * The effective Shocking level for a lock stack: 0 when Shocking is disabled, otherwise the enchantment
	 * level. When 'Shocking Requires Enchantment' is false, unenchanted locks shock as level 1.
	 */
	public static int getLevel(ItemStack lockStack)
	{
		if(!LocksServerConfig.ENABLE_SHOCKING.get())
			return 0;
		int level = EnchantmentHelper.getItemEnchantmentLevel(LocksEnchantments.SHOCKING.get(), lockStack);
		if(level <= 0 && !LocksServerConfig.SHOCKING_REQUIRES_ENCHANTMENT.get())
			level = 1;
		return level;
	}

	/** Damage for a given Shocking level: clamp(Base + level * Per Level, 0, Max). */
	public static float computeDamage(int level)
	{
		if(level <= 0)
			return 0f;
		double damage = LocksServerConfig.SHOCKING_DAMAGE_BASE.get() + level * LocksServerConfig.SHOCKING_DAMAGE_PER_LEVEL.get();
		damage = Math.max(0.0d, Math.min(damage, LocksServerConfig.SHOCKING_MAX_DAMAGE.get()));
		return (float) damage;
	}

	/**
	 * Applies Shocking damage for {@code trigger} if enabled, the lock qualifies, and the player is off cooldown.
	 * Server-side only. {@code soundPos} is where the zap sound plays (falls back to the player's position).
	 * Returns true if a shock was actually dealt.
	 */
	public static boolean tryShock(Player player, ItemStack lockStack, @Nullable Vec3 soundPos, Trigger trigger)
	{
		if(player.level().isClientSide)
			return false;
		if(!isTriggerEnabled(trigger))
			return false;
		int level = getLevel(lockStack);
		if(level <= 0)
			return false;
		float damage = computeDamage(level);
		if(damage <= 0f)
			return false;

		int cooldown = LocksServerConfig.SHOCKING_COOLDOWN_TICKS.get();
		if(cooldown > 0)
		{
			long now = player.level().getGameTime();
			long last = player.getPersistentData().getLong(COOLDOWN_KEY);
			if(last != 0L && now - last < cooldown)
				return false;
			player.getPersistentData().putLong(COOLDOWN_KEY, now);
		}

		player.hurt(LocksDamageSources.shock(player.level()), damage);
		Vec3 pos = soundPos != null ? soundPos : player.position();
		player.level().playSound(null, pos.x, pos.y, pos.z, LocksSoundEvents.SHOCK.get(), SoundSource.BLOCKS, 1f, 1f);
		return true;
	}
}
