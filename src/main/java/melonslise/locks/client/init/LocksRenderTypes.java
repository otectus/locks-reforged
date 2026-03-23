package melonslise.locks.client.init;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import melonslise.locks.Locks;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LocksRenderTypes extends RenderType
{
	// FIXME this still has depth for some reason. As suggested we could try to create a custom DepthTestState which clears GL_DEPTH_BUFFER_BIT on setup, but thats not possible without AT or reflect...
	public static final RenderType OVERLAY_LINES = RenderType.create(Locks.ID + ".overlay_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false, RenderType.CompositeState.builder()
		.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
		.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
		.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
		.setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
		.setWriteMaskState(RenderStateShard.COLOR_WRITE)
		.setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
		.createCompositeState(false));

	private LocksRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufSize, boolean affectsCrumbling, boolean sorting, Runnable setup, Runnable clear)
	{
		super(name, format, mode, bufSize, affectsCrumbling, sorting, setup, clear);
	}
}
