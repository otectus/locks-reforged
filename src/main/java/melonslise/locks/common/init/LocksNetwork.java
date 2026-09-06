package melonslise.locks.common.init;

import melonslise.locks.Locks;
import melonslise.locks.common.network.toclient.AddLockablePacket;
import melonslise.locks.common.network.toclient.AddLockableToChunkPacket;
import melonslise.locks.common.network.toclient.RemoveLockablePacket;
import melonslise.locks.common.network.toclient.TryPinResultPacket;
import melonslise.locks.common.network.toclient.UpdateLockablePacket;
import melonslise.locks.common.network.toserver.TryPinPacket;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class LocksNetwork
{
	// Bumped for 1.7.5: the pin packets gained the picking-session fields (container id, request sequence, and
	// the authoritative progress echoed back). Both acceptors are exact-match, so a mixed 1.7.4/1.7.5 connection
	// is refused at the channel handshake instead of decoding bad state.
	private static final String PROTOCOL_VERSION = "4";
	public static final SimpleChannel MAIN = NetworkRegistry.newSimpleChannel(new ResourceLocation(Locks.ID, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

	private LocksNetwork() {}

	public static void register()
	{
		MAIN.registerMessage(0, AddLockablePacket.class, AddLockablePacket::encode, AddLockablePacket::decode, AddLockablePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		MAIN.registerMessage(1, AddLockableToChunkPacket.class, AddLockableToChunkPacket::encode, AddLockableToChunkPacket::decode, AddLockableToChunkPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		MAIN.registerMessage(2, RemoveLockablePacket.class, RemoveLockablePacket::encode, RemoveLockablePacket::decode, RemoveLockablePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		MAIN.registerMessage(3, UpdateLockablePacket.class, UpdateLockablePacket::encode, UpdateLockablePacket::decode, UpdateLockablePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		MAIN.registerMessage(4, TryPinPacket.class, TryPinPacket::encode, TryPinPacket::decode, TryPinPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
		MAIN.registerMessage(5, TryPinResultPacket.class, TryPinResultPacket::encode, TryPinResultPacket::decode, TryPinResultPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	}
}
