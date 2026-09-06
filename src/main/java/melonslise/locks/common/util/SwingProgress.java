package melonslise.locks.common.util;

/**
 * The swing animation's progress, as a plain number. Pure Java on purpose: the renderer used to divide the
 * interpolated tick count by {@code maxSwingTicks} directly, and a lock that has never swung has every one of
 * those counters at 0 — so an idle lock computed 0/0, produced NaN, and fed a non-finite rotation into the
 * pose stack.
 */
public final class SwingProgress
{
	private SwingProgress() {}

	/**
	 * Interpolated progress through a swing, in {@code [0,1]}. Returns exactly {@code 0f} when there is no
	 * swing to interpolate ({@code maxTicks <= 0}), i.e. an idle lock sits still rather than animating.
	 */
	public static float normalized(int oldTicks, int currTicks, int maxTicks, float partialTick)
	{
		if(maxTicks <= 0)
			return 0f;
		float from = maxTicks - oldTicks;
		float to = maxTicks - currTicks;
		float lerped = from + (to - from) * partialTick;
		float progress = lerped / maxTicks;
		if(Float.isNaN(progress))
			return 0f;
		return progress < 0f ? 0f : progress > 1f ? 1f : progress;
	}
}
