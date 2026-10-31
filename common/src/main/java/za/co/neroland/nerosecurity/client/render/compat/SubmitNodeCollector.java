package za.co.neroland.nerosecurity.client.render.compat;

// Minecraft before 1.21.9 renders block entities immediately (render(..) into a MultiBufferSource) instead of
// through render states and a submit collector. Below 1.21.9 the build points the vanilla imports at these
// look-alikes (see legacyRenames), so the renderers stay one copy; the shim BlockEntityRenderer runs the
// extract + submit pair inside the old render call. Empty on 1.21.9 and later.
//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class SubmitNodeCollector {

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public SubmitNodeCollector order(int order) {
        return this;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), buffers.getBuffer(renderType));
    }

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
*///?}
