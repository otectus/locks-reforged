package melonslise.locks.client.init;

import melonslise.locks.client.gui.KeyRingScreen;
import melonslise.locks.client.gui.LockPickingScreen;
import melonslise.locks.common.init.LocksMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LocksScreens
{
	private LocksScreens() {}

	public static void register()
	{
		MenuScreens.register(LocksMenuTypes.LOCK_PICKING.get(), LockPickingScreen::new);
		MenuScreens.register(LocksMenuTypes.KEY_RING.get(), KeyRingScreen::new);
	}
}
