package melonslise.locks.common.network.toclient;

import java.util.function.Supplier;

import melonslise.locks.common.container.LockPickingContainer;
import melonslise.locks.common.init.LocksMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class TryPinResultPacket
{
	private final boolean correct, reset;

	public TryPinResultPacket(boolean correct, boolean reset)
	{
		this.correct = correct;
		this.reset = reset;
	}

	public static TryPinResultPacket decode(FriendlyByteBuf buf)
	{
		return new TryPinResultPacket(buf.readBoolean(), buf.readBoolean());
	}

	public static void encode(TryPinResultPacket pkt, FriendlyByteBuf buf)
	{
		buf.writeBoolean(pkt.correct);
		buf.writeBoolean(pkt.reset);
	}

	public static void handle(TryPinResultPacket pkt, Supplier<NetworkEvent.Context> ctx)
	{
		// Use runnable, lambda causes issues with class loading
		ctx.get().enqueueWork(new Runnable()
		{
			@Override
			public void run()
			{
				AbstractContainerMenu container = Minecraft.getInstance().player.containerMenu;
				if(container.getType() == LocksMenuTypes.LOCK_PICKING.get())
					((LockPickingContainer) container).handlePin(pkt.correct, pkt.reset);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
