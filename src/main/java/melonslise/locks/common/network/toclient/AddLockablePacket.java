package melonslise.locks.common.network.toclient;

import java.util.function.Supplier;

import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Lockable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class AddLockablePacket
{
	private final Lockable lockable;

	public AddLockablePacket(Lockable lkb)
	{
		this.lockable = lkb;
	}

	public static AddLockablePacket decode(FriendlyByteBuf buf)
	{
		return new AddLockablePacket(Lockable.fromBuf(buf));
	}

	public static void encode(AddLockablePacket pkt, FriendlyByteBuf buf)
	{
		Lockable.toBuf(buf, pkt.lockable);
	}

	public static void handle(AddLockablePacket pkt, Supplier<NetworkEvent.Context> ctx)
	{
		// Use runnable, lambda causes issues with class loading
		ctx.get().enqueueWork(new Runnable()
		{
			@Override
			public void run()
			{
				if(Minecraft.getInstance().level == null)
					return;
				// Skip validation on client — server already validated volume and intersection
				Minecraft.getInstance().level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).ifPresent(handler -> handler.addDirect(pkt.lockable));
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
