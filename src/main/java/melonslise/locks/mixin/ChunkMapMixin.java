package melonslise.locks.mixin;

import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksNetwork;
import melonslise.locks.common.network.toclient.AddLockableToChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

@Mixin(ChunkMap.class)
public class ChunkMapMixin
{
	@Inject(at = @At("TAIL"), method = "playerLoadedChunk(Lnet/minecraft/server/level/ServerPlayer;Lorg/apache/commons/lang3/mutable/MutableObject;Lnet/minecraft/world/level/chunk/LevelChunk;)V")
	private void playerLoadedChunk(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packetHolder, LevelChunk ch, CallbackInfo ci)
	{
		ch.getCapability(LocksCapabilities.LOCKABLE_STORAGE).ifPresent(st ->
			st.get().values().forEach(lkb -> LocksNetwork.MAIN.send(PacketDistributor.PLAYER.with(() -> player), new AddLockableToChunkPacket(lkb, ch))));
	}
}
