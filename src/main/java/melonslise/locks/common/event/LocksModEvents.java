package melonslise.locks.common.event;

import melonslise.locks.Locks;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.capability.ISelection;
import melonslise.locks.common.compat.CuriosHelper;
import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LockTypeRegistry;
import melonslise.locks.common.init.LocksNetwork;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Locks.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LocksModEvents
{
	private LocksModEvents() {}

	@SubscribeEvent
	public static void onSetup(FMLCommonSetupEvent e)
	{
		LocksNetwork.register();
		e.enqueueWork(CuriosHelper::init);
	}

	@SubscribeEvent
	public static void onRegisterCaps(RegisterCapabilitiesEvent event)
	{
		event.register(ILockableHandler.class);
		event.register(ILockableStorage.class);
		event.register(ISelection.class);
	}

	@SubscribeEvent
	public static void onConfigLoad(ModConfigEvent e)
	{
		if(e.getConfig().getSpec() == LocksConfig.SPEC)
		{
			LocksConfig.init();
			if (LockTypeRegistry.isLoaded())
				LockTypeRegistry.resetStats();
		}
		if(e.getConfig().getSpec() == LocksServerConfig.SPEC)
			LocksServerConfig.init();
	}
}
