package melonslise.locks.common.network.toclient;

import java.util.function.Supplier;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.util.Lockable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkEvent;

public class AddLockableToChunkPacket
{
	private final Lockable lockable;
	private final int x, z;

	public AddLockableToChunkPacket(Lockable lkb, int x, int z)
	{
		this.lockable = lkb;
		this.x = x;
		this.z = z;
	}

	public AddLockableToChunkPacket(Lockable lkb, ChunkPos pos)
	{
		this(lkb, pos.x, pos.z);
	}

	public AddLockableToChunkPacket(Lockable lkb, LevelChunk ch)
	{
		this(lkb, ch.getPos());
	}

	public static AddLockableToChunkPacket decode(FriendlyByteBuf buf)
	{
		return new AddLockableToChunkPacket(Lockable.fromBuf(buf), buf.readInt(), buf.readInt());
	}

	public static void encode(AddLockableToChunkPacket pkt, FriendlyByteBuf buf)
	{
		Lockable.toBuf(buf, pkt.lockable);
		buf.writeInt(pkt.x);
		buf.writeInt(pkt.z);
	}

	public static void handle(AddLockableToChunkPacket pkt, Supplier<NetworkEvent.Context> ctx)
	{
		// Use runnable, lambda causes issues with class loading
		ctx.get().enqueueWork(new Runnable()
		{
			@Override
			public void run()
			{
				Minecraft mc = Minecraft.getInstance();
				if(mc.level == null)
					return;
				ILockableHandler handler = mc.level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
				if(handler == null)
					return;
				// The world handler is the client's authoritative render source, so always register here.
				Int2ObjectMap<Lockable> lkbs = handler.getLoaded();
				Lockable existing = lkbs.get(pkt.lockable.id);
				final Lockable lkb;
				if(existing == lkbs.defaultReturnValue())
				{
					lkb = pkt.lockable;
					lkb.addObserver(handler);
					lkbs.put(lkb.id, lkb);
				}
				else
					lkb = existing;
				// Best-effort: add to the chunk's storage only if it is already loaded. NEVER force-load the
				// chunk (mc.level.getChunk(x, z) would create one, corrupting the client chunk lifecycle).
				ChunkAccess ca = mc.level.getChunk(pkt.x, pkt.z, ChunkStatus.FULL, false);
				if(ca instanceof LevelChunk ch)
					ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).ifPresent(st -> st.add(lkb));
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
