package za.co.neroland.nerosecurity.client.render.compat;

// Minecraft before 1.21.9 renders block entities immediately (render(..) into a MultiBufferSource) instead of
// through render states and a submit collector. Below 1.21.9 the build points the vanilla imports at these
// look-alikes (see legacyRenames), so the renderers stay one copy; the shim BlockEntityRenderer runs the
// extract + submit pair inside the old render call. Empty on 1.21.9 and later.
//? if >=1.21.5 && <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        extends net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {

    S createRenderState();

    default void extractRenderState(T be, S state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        state.blockPos = be.getBlockPos();
        state.blockState = be.getBlockState();
        state.blockEntityType = be.getType();
        state.lightCoords = be.getLevel() == null ? 0xF000F0 : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        state.breakProgress = breakProgress;
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState);

    private void renderLegacy(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            Vec3 cameraPos) {
        S state = createRenderState();
        extractRenderState(be, state, partialTick, cameraPos, null);
        state.lightCoords = light;
        CameraRenderState camera = new CameraRenderState();
        camera.pos = cameraPos;
        submit(state, poseStack, new SubmitNodeCollector(buffers), camera);
    }

    @Override
    default void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
            Vec3 cameraPos) {
        renderLegacy(be, partialTick, poseStack, buffers, light, cameraPos);
    }
}
*///?} else if <1.21.5 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        extends net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {

    S createRenderState();

    default void extractRenderState(T be, S state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        state.blockPos = be.getBlockPos();
        state.blockState = be.getBlockState();
        state.blockEntityType = be.getType();
        state.lightCoords = be.getLevel() == null ? 0xF000F0 : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        state.breakProgress = breakProgress;
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState);

    private void renderLegacy(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            Vec3 cameraPos) {
        S state = createRenderState();
        extractRenderState(be, state, partialTick, cameraPos, null);
        state.lightCoords = light;
        CameraRenderState camera = new CameraRenderState();
        camera.pos = cameraPos;
        submit(state, poseStack, new SubmitNodeCollector(buffers), camera);
    }

    @Override
    default void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        renderLegacy(be, partialTick, poseStack, buffers, light,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
}
*///?}
