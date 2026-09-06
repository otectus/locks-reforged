package melonslise.locks.common.network.toclient;

import java.util.function.Supplier;

import melonslise.locks.client.network.ClientPinResultHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The server's verdict on one pin attempt, echoed back with the identity of the request that produced it so the
 * client can only ever apply it to that request.
 */
public class TryPinResultPacket
{
	public final int containerId, sequence, pin, progress;
	public final boolean correct, reset, terminal;

	public TryPinResultPacket(int containerId, int sequence, int pin, int progress, boolean correct, boolean reset, boolean terminal)
	{
		this.containerId = containerId;
		this.sequence = sequence;
		this.pin = pin;
		this.progress = progress;
		this.correct = correct;
		this.reset = reset;
		this.terminal = terminal;
	}

	public static TryPinResultPacket decode(FriendlyByteBuf buf)
	{
		return new TryPinResultPacket(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
	}

	public static void encode(TryPinResultPacket pkt, FriendlyByteBuf buf)
	{
		buf.writeVarInt(pkt.containerId);
		buf.writeVarInt(pkt.sequence);
		buf.writeByte(pkt.pin);
		buf.writeVarInt(pkt.progress);
		buf.writeBoolean(pkt.correct);
		buf.writeBoolean(pkt.reset);
		buf.writeBoolean(pkt.terminal);
	}

	public static void handle(TryPinResultPacket pkt, Supplier<NetworkEvent.Context> ctx)
	{
		// Use runnable, lambda causes issues with class loading
		ctx.get().enqueueWork(new Runnable()
		{
			@Override
			public void run()
			{
				// Registering the packet PLAY_TO_CLIENT keeps a server from running this, but it does not stop the
				// JVM resolving the client classes named in the method body when the method is verified. The
				// dereference therefore lives in a client-only class, reached only through DistExecutor.
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPinResultHandler.handle(pkt));
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
