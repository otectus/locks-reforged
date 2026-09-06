package melonslise.locks.client.network;

import melonslise.locks.common.container.LockPickingContainer;
import melonslise.locks.common.network.toclient.TryPinResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client half of {@link TryPinResultPacket}. Kept in its own client-only class so the packet class — which
 * is loaded on a dedicated server too — never names Minecraft's client types in a method the verifier resolves.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPinResultHandler
{
	private ClientPinResultHandler() {}

	public static void handle(TryPinResultPacket pkt)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null)
			return;
		AbstractContainerMenu container = mc.player.containerMenu;
		// instanceof, not getType(): getType throws for menus built with a null type, InventoryMenu included.
		if(!(container instanceof LockPickingContainer lpc) || lpc.containerId != pkt.containerId)
			return;
		lpc.handlePin(pkt.sequence, pkt.pin, pkt.progress, pkt.correct, pkt.reset, pkt.terminal);
	}
}
