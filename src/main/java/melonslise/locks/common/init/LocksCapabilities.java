package melonslise.locks.common.init;

import melonslise.locks.common.capability.CapabilityProvider;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.capability.ISelection;
import melonslise.locks.common.capability.LockableHandler;
import melonslise.locks.common.capability.LockableStorage;
import melonslise.locks.common.capability.Selection;
import melonslise.locks.common.capability.SerializableCapabilityProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.AttachCapabilitiesEvent;

public final class LocksCapabilities
{
	public static final Capability<ILockableHandler> LOCKABLE_HANDLER = CapabilityManager.get(new CapabilityToken<>(){});

	public static final Capability<ILockableStorage> LOCKABLE_STORAGE = CapabilityManager.get(new CapabilityToken<>(){});

	public static final Capability<ISelection> SELECTION = CapabilityManager.get(new CapabilityToken<>(){});

	private LocksCapabilities() {}

	public static void attachToWorld(AttachCapabilitiesEvent<Level> e)
	{
		SerializableCapabilityProvider provider = new SerializableCapabilityProvider(LOCKABLE_HANDLER, new LockableHandler(e.getObject()));
		e.addCapability(LockableHandler.ID, provider);
		e.addListener(provider::invalidate);
	}

	public static void attachToChunk(AttachCapabilitiesEvent<LevelChunk> e)
	{
		SerializableCapabilityProvider provider = new SerializableCapabilityProvider(LOCKABLE_STORAGE, new LockableStorage(e.getObject()));
		e.addCapability(LockableStorage.ID, provider);
		e.addListener(provider::invalidate);
	}

	public static void attachToEntity(AttachCapabilitiesEvent<Entity> e)
	{
		if(e.getObject() instanceof Player)
		{
			CapabilityProvider provider = new CapabilityProvider(SELECTION, new Selection());
			e.addCapability(Selection.ID, provider);
			e.addListener(provider::invalidate);
		}
	}
}
