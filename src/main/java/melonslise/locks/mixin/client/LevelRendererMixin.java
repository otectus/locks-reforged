package melonslise.locks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import melonslise.locks.client.event.LocksClientForgeEvents;
import melonslise.locks.client.util.LocksClientUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin
{
	// Before first checkPoseStack call
	@Inject(at = @At(value = "INVOKE", target = "net/minecraft/client/renderer/LevelRenderer.checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 0), method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V")
	private void renderLevel(PoseStack mtx, float pt, long nanoTime, boolean renderOutline, Camera cam, GameRenderer gr, LightTexture lightTex, Matrix4f proj, CallbackInfo ci)
	{
		LocksClientForgeEvents.renderLocks(mtx, Minecraft.getInstance().renderBuffers().bufferSource(), LocksClientUtil.getFrustum(mtx, proj), pt);
	}
}
