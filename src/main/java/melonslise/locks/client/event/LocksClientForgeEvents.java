package melonslise.locks.client.event;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import melonslise.locks.Locks;
import melonslise.locks.client.init.LocksRenderTypes;
import melonslise.locks.client.util.LocksClientUtil;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ISelection;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksItemTags;
import melonslise.locks.common.init.LocksTagHelper;
import melonslise.locks.common.util.Lockable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = Locks.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LocksClientForgeEvents
{
	public static Lockable tooltipLockable;

	// Cached quaternions to avoid per-frame allocation during lock rendering
	private static final Quaternionf LOCK_ROT_Y = new Quaternionf();
	private static final Quaternionf LOCK_ROT_X = new Quaternionf();
	private static final Quaternionf LOCK_ROT_Z = new Quaternionf();

	private LocksClientForgeEvents() {}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent e)
	{
		Minecraft mc = Minecraft.getInstance();
		if(e.phase != TickEvent.Phase.START || mc.level == null || mc.isPaused())
			return;
		mc.level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).ifPresent(handler -> handler.getLoaded().values().forEach(lkb -> lkb.tick()));
	}

	@SubscribeEvent
	public static void onRenderWorld(RenderLevelStageEvent e)
	{
		if(e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;
		Minecraft mc = Minecraft.getInstance();
		PoseStack mtx = e.getPoseStack();
		MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

		// use mixin to avoid models disappearing  in water and when fabulous graphics are on
		// renderLocks(mtx, buf, LocksClientUtil.getFrustum(mtx, e.getProjectionMatrix()), e.getPartialTick());
		renderSelection(mtx, buf);
	}

	public static boolean holdingPick(Player player)
	{
		for(InteractionHand hand : InteractionHand.values())
			if(LocksTagHelper.isLockPick(player.getItemInHand(hand)))
				return true;
		return false;
	}

	@SubscribeEvent
	public static void onRenderOverlay(RenderGuiOverlayEvent.Pre e)
	{
		Minecraft mc = Minecraft.getInstance();
		if(e.getOverlay() != VanillaGuiOverlay.HOTBAR.type() || tooltipLockable == null)
			return;
		if(holdingPick(mc.player))
		{
			PoseStack mtx = e.getGuiGraphics().pose();
			Vector3f vec = LocksClientUtil.worldToScreen(tooltipLockable.getLockState(mc.level).pos, e.getPartialTick());
			if (vec.z < 0f)
			{
				mtx.pushPose();
				mtx.translate(vec.x, vec.y, 0f);
				List<Component> tooltipLines = tooltipLockable.stack.getTooltipLines(mc.player, mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
				filterEnchantmentLines(tooltipLockable.stack, tooltipLines);
				renderHudTooltip(mtx, Lists.transform(tooltipLines, Component::getVisualOrderText), mc.font);
				mtx.popPose();
			}
		}
		tooltipLockable = null;
	}

	private static ItemStack cachedEnchantStack;
	private static Set<String> cachedEnchantNames;

	private static List<Component> filterEnchantmentLines(ItemStack stack, List<Component> lines)
	{
		if(!LocksServerConfig.HIDE_HUD_ENCHANTMENTS.get())
			return lines;
		Set<String> enchantNames;
		if(stack == cachedEnchantStack && cachedEnchantNames != null)
		{
			enchantNames = cachedEnchantNames;
		}
		else
		{
			Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
			enchantNames = new HashSet<>();
			for(Map.Entry<Enchantment, Integer> entry : enchantments.entrySet())
				enchantNames.add(entry.getKey().getFullname(entry.getValue()).getString());
			cachedEnchantStack = stack;
			cachedEnchantNames = enchantNames;
		}
		if(enchantNames.isEmpty())
			return lines;
		lines.removeIf(line -> enchantNames.contains(line.getString()));
		return lines;
	}

	public static void renderLocks(PoseStack mtx, MultiBufferSource.BufferSource buf, Frustum ch, float pt)
	{
		Minecraft mc = Minecraft.getInstance();
		Vec3 o = LocksClientUtil.getCamera().getPosition();
		BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

		double dMin = 0d;
		Vec3 look = o.add(mc.player.getViewVector(pt));

		ILockableHandler handler = mc.level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElse(null);
		if(handler == null)
			return;
		for(Lockable lkb : handler.getLoaded().values())
		{
			Lockable.State state = lkb.getLockState(mc.level);
			if(state == null || !state.inRange(o) || !state.inView(ch))
				continue;

			double d = o.subtract(state.pos).lengthSqr();
			if(d <= 25d)
			{
				double d1 = LocksClientUtil.distanceToLineSq(state.pos, o, look);
				if(d1 <= 4d && (dMin == 0d || d1 < dMin))
				{
					tooltipLockable = lkb;
					dMin = d1;
				}
			}

			mtx.pushPose();
			// For some reason translating by negative player position and then the point coords causes jittering in very big z and x coords. Why? Thus we use 1 translation instead
			mtx.translate(state.pos.x - o.x, state.pos.y - o.y, state.pos.z - o.z);
			mtx.mulPose(LOCK_ROT_Y.rotationY((float) Math.toRadians(-state.tr.dir.toYRot() - 180f)));
			if(state.tr.face != AttachFace.WALL)
				mtx.mulPose(LOCK_ROT_X.rotationX((float) Math.toRadians(90f)));
			mtx.translate(0d, 0.1d, 0d);
			mtx.mulPose(LOCK_ROT_Z.rotationZ((float) Math.toRadians(Mth.sin(LocksClientUtil.cubicBezier1d(1f, 1f, LocksClientUtil.lerp(lkb.maxSwingTicks - lkb.oldSwingTicks, lkb.maxSwingTicks - lkb.swingTicks, pt) / lkb.maxSwingTicks) * lkb.maxSwingTicks / 5f * 3.14f) * 10f)));
			mtx.translate(0d, -0.1d, 0d);
			mtx.scale(0.5f, 0.5f, 0.5f);
			int light = LevelRenderer.getLightColor(mc.level, mut.set(state.pos.x, state.pos.y, state.pos.z));
			mc.getItemRenderer().renderStatic(lkb.stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, mtx, buf, mc.level, 0);
			mtx.popPose();
		}
		buf.endBatch();
	}

	public static void renderSelection(PoseStack mtx, MultiBufferSource.BufferSource buf)
	{
		Minecraft mc = Minecraft.getInstance();
		Vec3 o = LocksClientUtil.getCamera().getPosition();
		ISelection select = mc.player.getCapability(LocksCapabilities.SELECTION).orElse(null);
		if(select == null)
			return;
		BlockPos pos = select.get();
		if(pos == null)
			return;
		BlockPos pos1 = mc.hitResult instanceof BlockHitResult ? ((BlockHitResult) mc.hitResult).getBlockPos() : pos;
		boolean allow = Math.abs(pos.getX() - pos1.getX()) * Math.abs(pos.getY() - pos1.getY()) * Math.abs(pos.getZ() - pos1.getZ()) <= LocksServerConfig.MAX_LOCKABLE_VOLUME.get() && LocksServerConfig.canLock(mc.level, pos1);
		// Same as above
		LevelRenderer.renderLineBox(mtx, buf.getBuffer(LocksRenderTypes.OVERLAY_LINES), Math.min(pos.getX(), pos1.getX()) - o.x, Math.min(pos.getY(), pos1.getY()) - o.y, Math.min(pos.getZ(), pos1.getZ()) - o.z, Math.max(pos.getX(), pos1.getX()) + 1d - o.x, Math.max(pos.getY(), pos1.getY()) + 1d - o.y, Math.max(pos.getZ(), pos1.getZ()) + 1d - o.z, allow ? 0f : 1f, allow ? 1f : 0f, 0f, 0.5f);
		RenderSystem.disableDepthTest();
		buf.endBatch();
	}

	// Taken from Screen and modified to draw and fancy line and square and removed color recalculation
	public static void renderHudTooltip(PoseStack mtx, List<? extends FormattedCharSequence> lines, Font font)
	{
		if (lines.isEmpty())
			return;
		int width = 0;
		for (FormattedCharSequence line : lines)
		{
			int j = font.width(line);
			if (j > width)
				width = j;
		}

		int x = 36;
		int y = -36;
		int height = 8;
		if (lines.size() > 1)
			height += 2 + (lines.size() - 1) * 10;

		mtx.pushPose();

		BufferBuilder buf = Tesselator.getInstance().getBuilder();
		buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		LocksClientUtil.square(buf, mtx, 0f, 0f, 4f, 0.05f, 0f, 0.3f, 0.8f);
		LocksClientUtil.line(buf, mtx, 1f, -1f, x / 3f + 0.6f, y / 2f, 2f, 0.05f, 0f, 0.3f, 0.8f);
		LocksClientUtil.line(buf, mtx, x / 3f, y / 2f, x - 3f, y / 2f, 2f, 0.05f, 0f, 0.3f, 0.8f);
		// line(buf, last, 1f, -1f, x - 3f, y / 2f, 2f, 0.05f, 0f, 0.3f, 0.8f);
		LocksClientUtil.vGradient(buf, mtx, x - 3, y - 4, x + width + 3, y - 3, 0.0627451f, 0f, 0.0627451f, 0.9411765f, 0.0627451f, 0f, 0.0627451f, 0.9411765f);
		LocksClientUtil.vGradient(buf, mtx, x - 3, y + height + 3, x + width + 3, y + height + 4, 0.0627451f, 0f, 0.0627451f, 0.9411765f, 0.0627451f, 0f, 0.0627451f, 0.9411765f);
		LocksClientUtil.vGradient(buf, mtx, x - 3, y - 3, x + width + 3, y + height + 3, 0.0627451f, 0f, 0.0627451f, 0.9411765f, 0.0627451f, 0f, 0.0627451f, 0.9411765f);
		LocksClientUtil.vGradient(buf, mtx, x - 4, y - 3, x - 3, y + height + 3, 0.0627451f, 0f, 0.0627451f, 0.9411765f, 0.0627451f, 0f, 0.0627451f, 0.9411765f);
		LocksClientUtil.vGradient(buf, mtx, x + width + 3, y - 3, x + width + 4, y + height + 3, 0.0627451f, 0f, 0.0627451f, 0.9411765f, 0.0627451f, 0f, 0.0627451f, 0.9411765f);
		LocksClientUtil.vGradient(buf, mtx, x - 3, y - 3 + 1, x - 3 + 1, y + height + 3 - 1, 0.3137255f, 0f, 1f, 0.3137255f, 0.15686275f, 0f, 0.49803922f, 0.3137255f);
		LocksClientUtil.vGradient(buf, mtx, x + width + 2, y - 3 + 1, x + width + 3, y + height + 3 - 1, 0.3137255f, 0f, 1f, 0.3137255f, 0.15686275f, 0f, 0.49803922f, 0.3137255f);
		LocksClientUtil.vGradient(buf, mtx, x - 3, y - 3, x + width + 3, y - 3 + 1, 0.3137255f, 0f, 1f, 0.3137255f, 0.3137255f, 0f, 1f, 0.3137255f);
		LocksClientUtil.vGradient(buf, mtx, x - 3, y + height + 2, x + width + 3, y + height + 3, 0.15686275f, 0f, 0.49803922f, 0.3137255f, 0.15686275f, 0f, 0.49803922f, 0.3137255f);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
		BufferUploader.drawWithShader(buf.end());
		RenderSystem.disableBlend();
		MultiBufferSource.BufferSource buf1 = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

		Matrix4f last = mtx.last().pose();
		for (int a = 0; a < lines.size(); ++a)
		{
			FormattedCharSequence line = lines.get(a);
			if (line != null)
				font.drawInBatch(line, (float) x, (float) y, -1, true, last, buf1, Font.DisplayMode.NORMAL, 0, 15728880);
			if (a == 0)
				y += 2;
			y += 10;
		}

		buf1.endBatch();

		mtx.popPose();
	}
}
