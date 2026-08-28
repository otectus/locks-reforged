package melonslise.locks.common.network.toserver;

import java.util.function.Supplier;

import melonslise.locks.common.container.LockPickingContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class TryPinPacket
{
	private final byte pin;

	public TryPinPacket(byte pin)
	{
		this.pin = pin;
	}

	public static TryPinPacket decode(FriendlyByteBuf buf)
	{
		return new TryPinPacket(buf.readByte());
	}

	public static void encode(TryPinPacket pkt, FriendlyByteBuf buf)
	{
		buf.writeByte(pkt.pin);
	}

	public static void handle(TryPinPacket pkt, Supplier<NetworkEvent.Context> ctx)
	{
		// Use runnable, lambda causes issues with class loading
		ctx.get().enqueueWork(new Runnable()
		{
			@Override
			public void run()
			{
				ServerPlayer sender = ctx.get().getSender();
				if(sender == null)
					return;
				// instanceof, not getType(): AbstractContainerMenu#getType throws for menus built with a null
				// type, and InventoryMenu — every player's default containerMenu — is one of those. Asking a
				// player with no Locks screen open would have thrown.
				if(!(sender.containerMenu instanceof LockPickingContainer lpc))
					return;
				// The client sends nothing but the pin. Bounds, lock state, mode, reach, config and item
				// requirements are all re-decided here.
				if(pkt.pin < 0 || pkt.pin >= lpc.lockable.lock.getLength())
					return;
				if(!lpc.canAttempt(sender))
					return;
				lpc.tryPin(pkt.pin);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
