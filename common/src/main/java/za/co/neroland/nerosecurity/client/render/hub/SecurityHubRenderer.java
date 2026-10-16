package za.co.neroland.nerosecurity.client.render.hub;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerosecurity.block.SecurityHubBlock;
import za.co.neroland.nerosecurity.block.entity.SecurityHubBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;
import za.co.neroland.nerosecurity.client.feedback.ClientDeviceFeedback;
import za.co.neroland.nerosecurity.client.render.DoorChestHubRenderers;
import za.co.neroland.nerosecurity.client.render.door.DoorLeds;

/**
 * The Security Hub's two lit signals on its bezel (the animated screen glyphs are the static model's texture): the
 * recording LED (REC, 1 Hz while the audit log is on) and the lockdown strip across the top of the bezel (LOCK,
 * 4 Hz strobe while the block state says {@code LOCKDOWN}; it also carries a deny flash). Submits nothing when both
 * are dark.
 */
public class SecurityHubRenderer implements BlockEntityRenderer<SecurityHubBlockEntity, SecurityHubRenderState> {

    public static final int VIEW_DISTANCE = 32;

    private static final RenderType LED = SecurityGeometry.emissive(DoorChestHubRenderers.LED_TEXTURE);
    /** Proud of the bezel (which stands half a pixel off the north face). */
    private static final float FRONT_Z = -0.6f;

    @Override
    public SecurityHubRenderState createRenderState() {
        return new SecurityHubRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(SecurityHubBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRender(SecurityHubBlockEntity be, Vec3 cameraPos) {
        boolean visible = getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos);
        if (!visible) {
            BerStats.culled();
        }
        return visible;
    }

    @Override
    public void extractRenderState(SecurityHubBlockEntity be, SecurityHubRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        Level level = be.getLevel();
        long now = level == null ? 0L : level.getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(SecurityHubBlock.FACING) ? state.getValue(SecurityHubBlock.FACING)
                : Direction.NORTH;
        boolean lockdown = state.hasProperty(SecurityHubBlock.LOCKDOWN) && state.getValue(SecurityHubBlock.LOCKDOWN);
        boolean animate = ClientAnim.animationsEnabled();
        s.quarterTurns = DoorChestHubRenderers.quarterTurns(facing);
        s.recording = DoorLeds.recording(be.isRecording(), now, animate);
        BlockPos pos = be.getBlockPos();
        if (ClientDeviceFeedback.flashing(pos, now)) {
            s.strip = !animate || ClientDeviceFeedback.flashOn(pos, now, partialTick) ? SecurityColours.LOCK : DoorLeds.OFF;
        } else {
            s.strip = DoorLeds.lockdown(lockdown, now, animate);
        }
        BerStats.stop(t0);
    }

    @Override
    public void submit(SecurityHubRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        long t0 = BerStats.start();
        if (s.recording == DoorLeds.OFF && s.strip == DoorLeds.OFF) {
            BerStats.culled();
            BerStats.stop(t0);
            return;
        }
        BerStats.submitted();
        int q = s.quarterTurns;
        int rec = s.recording;
        int strip = s.strip;
        collector.order(1).submitCustomGeometry(poseStack, LED, (pose, c) -> {
            int n = 0;
            if (rec != DoorLeds.OFF) {
                n += DoorChestHubRenderers.plate(c, pose, q, 13f, 13f, 15f, 15f, FRONT_Z,
                        SecurityColours.argb(rec, 255), SecurityColours.FULL_BRIGHT);
            }
            if (strip != DoorLeds.OFF) {
                n += DoorChestHubRenderers.plate(c, pose, q, 1f, 15f, 15f, 15.5f, FRONT_Z,
                        SecurityColours.argb(strip, 255), SecurityColours.FULL_BRIGHT);
            }
            BerStats.vertices(n);
        });
        BerStats.stop(t0);
    }
}
