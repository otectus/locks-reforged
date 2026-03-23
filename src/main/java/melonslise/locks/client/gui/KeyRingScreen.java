package melonslise.locks.client.gui;

import melonslise.locks.common.container.KeyRingContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KeyRingScreen extends AbstractContainerScreen<KeyRingContainer>
{
	public static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

	public KeyRingScreen(KeyRingContainer cont, Inventory inv, Component title)
	{
		super(cont, inv, title);
		this.imageHeight = 114 + cont.rows * 18;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
	{
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY)
	{
		int rows = this.getMenu().rows;
		int cornerX = (this.width - this.imageWidth) / 2;
		int cornerY = (this.height - this.imageHeight) / 2;
		guiGraphics.blit(TEXTURE, cornerX, cornerY, 0, 0, this.imageWidth, rows * 18 + 17);
		guiGraphics.blit(TEXTURE, cornerX, cornerY + rows * 18 + 17, 0, 126, this.imageWidth, 96);
	}
}
