package melonslise.locks.common.init;

import java.util.stream.Stream;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

public final class LocksPacketDistributors
{
	public static final PacketDistributor<Stream<LevelChunk>> TRACKING_AREA = new PacketDistributor<>((pd, s) ->
		pkt ->
		{
			// Convert each chunk to a stream of tracking players
			// Merge all streams into one
			// Remove duplicate players
			// Send packet
			s.get()
				.flatMap(chunk -> ((ServerChunkCache) chunk.getLevel().getChunkSource()).chunkMap.getPlayers(chunk.getPos(), false).stream())
				.distinct()
				.forEach(p -> p.connection.send(pkt));
		}, NetworkDirection.PLAY_TO_CLIENT);

	private LocksPacketDistributors() {}
}
