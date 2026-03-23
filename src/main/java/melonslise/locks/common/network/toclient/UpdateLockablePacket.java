package melonslise.locks.common.network.toclient;

import java.util.function.Supplier;

import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Lockable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class UpdateLockablePacket
{
	private final int id;
	// Expandable
	private final boolean locked;

	public UpdateLockablePacket(int id, boolean locked)
	{
		this.id = id;
		this.locked = locked;
	}

	public UpdateLockablePacket(Lockable lkb)
	{
		this(lkb.id, lkb.lock.isLocked());
	}

	public static UpdateLockablePacket decode(FriendlyByteBuf buf)
	{
		return new UpdateLockablePacket(buf.readInt(), buf.readBoolean());
	}

	public static void encode(UpdateLockablePacket pkt, FriendlyByteBuf buf)
	{
		buf.writeInt(pkt.id);
		buf.writeBoolean(pkt.locked);
	}

	public static void handle(UpdateLockablePacket pkt, Supplier<NetworkEvent.Context> ctx)
	{
		// Use runnable, lambda causes issues with class loading
		ctx.get().enqueueWork(new Runnable()
		{
			@Override
			public void run()
			{
				Minecraft.getInstance().level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).ifPresent(handler ->
				{
					Lockable lkb = handler.getLoaded().get(pkt.id);
					if(lkb != null)
						lkb.lock.setLocked(pkt.locked);
				});
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
