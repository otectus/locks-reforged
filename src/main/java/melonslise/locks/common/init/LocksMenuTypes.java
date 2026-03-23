package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.container.KeyRingContainer;
import melonslise.locks.common.container.LockPickingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksMenuTypes
{
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Locks.ID);

	public static final RegistryObject<MenuType<LockPickingContainer>>
		LOCK_PICKING = MENU_TYPES.register("lock_picking", () -> IForgeMenuType.create(LockPickingContainer.FACTORY));

	public static final RegistryObject<MenuType<KeyRingContainer>>
		KEY_RING = MENU_TYPES.register("key_ring", () -> IForgeMenuType.create(KeyRingContainer.FACTORY));

	private LocksMenuTypes() {}

	public static void register()
	{
		MENU_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
	}
}
