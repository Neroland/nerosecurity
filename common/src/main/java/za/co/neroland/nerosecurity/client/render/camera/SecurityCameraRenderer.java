package za.co.neroland.nerosecurity.client.render.camera;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlock;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.Blink;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;
import za.co.neroland.nerosecurity.client.feedback.ClientDeviceFeedback;
import za.co.neroland.nerosecurity.client.render.SensorFieldRenderers;

/**
 * The security camera's moving body: housing, lens, the REC LED and its bloom, pivoting at the arm's end. The body
 * sweeps with the server's own pan function and leans toward the synced, quantised last-seen direction (10-tick
 * ease, 70-tick hold, ease back). The LED blinks the recording pattern, full-bright with a translucent bloom, only
 * while the camera is armed: an unpowered camera is visibly off. A deny flash lights it {@code LOCK}.
 *
 * <p>Culling: the block inflated 0.25 (the pan never leaves it), view distance {@value #VIEW_DISTANCE}.
 * No off-screen rendering.
 */
public class SecurityCameraRenderer
        implements BlockEntityRenderer<SecurityCameraBlockEntity, SecurityCameraRenderState> {

    public static final int VIEW_DISTANCE = 48;
    /** Body box + lens box; the LED and bloom add one quad each while lit. */
    public static final int BODY_VERTICES = 2 * SecurityGeometry.BOX_VERTICES;
    public static final int LED_VERTICES = 2 * SecurityGeometry.FACE_VERTICES;

    static final Identifier BODY_TEX = SecurityGeometry.blockTexture("security_camera_body");
    static final Identifier LENS_TEX = SecurityGeometry.blockTexture("security_camera_lens");
    static final Identifier WHITE_TEX = SecurityGeometry.blockTexture("security_led");

    /** Arm end, model pixels (authored north). */
    static final float[] PIVOT = {8f, 7.5f, 10f};

    @Override
    public SecurityCameraRenderState createRenderState() {
        return new SecurityCameraRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(SecurityCameraBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.25D);
    }

    @Override
    public boolean shouldRender(SecurityCameraBlockEntity be, Vec3 cameraPos) {
        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
            return true;
        }
        BerStats.culled();
        return false;
    }

    @Override
    public void extractRenderState(SecurityCameraBlockEntity be, SecurityCameraRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        long now = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        BlockState state = be.getBlockState();
        s.valid = state.hasProperty(SecurityCameraBlock.FACING);
        if (!s.valid) {
            BerStats.culled();
            BerStats.stop(t0);
            return;
        }
        boolean animate = ClientAnim.animationsEnabled();
        boolean armed = state.hasProperty(SecurityCameraBlock.ARMED) && state.getValue(SecurityCameraBlock.ARMED);
        s.facing = state.getValue(SecurityCameraBlock.FACING);
        float[] pose = CameraTilt.pose(s.facing, now, partialTick, be.sweepTicks(), armed, be.hasLook(),
                be.lookSeenAt(), be.lastYaw(), be.lastPitch(), animate);
        s.xDeg = pose[0];
        s.yDeg = pose[1];
        s.zDeg = pose[2];
        if (ClientDeviceFeedback.flashOn(be.getBlockPos(), now, partialTick)) {
            s.ledOn = true;
            s.ledColour = SecurityColours.LOCK;
        } else {
            s.ledOn = armed && (!animate || Blink.recording(now));
            s.ledColour = SecurityColours.REC;
        }
        BerStats.stop(t0);
    }

    @Override
    public void submit(SecurityCameraRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (!s.valid) {
            return;
        }
        long t0 = BerStats.start();
        BerStats.submitted();
        final Direction f = s.facing;
        final float xd = s.xDeg;
        final float yd = s.yDeg;
        final float zd = s.zDeg;
        final int light = s.lightCoords;
        collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.cutout(BODY_TEX),
                (pose, c) -> SensorFieldRenderers.Frame.box(c, pose, f, 4f, 4f, 2f, 12f, 11f, 10f, PIVOT, xd, yd, zd,
                        1f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, light));
        collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.cutout(LENS_TEX),
                (pose, c) -> SensorFieldRenderers.Frame.box(c, pose, f, 6f, 5f, 1f, 10f, 9f, 2f, PIVOT, xd, yd, zd,
                        1f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, light));
        int vertices = BODY_VERTICES;
        if (s.ledOn) {
            final int led = SecurityColours.argb(s.ledColour, 255);
            final int bloom = SecurityColours.argb(s.ledColour, 0.35f);
            final int bright = SecurityColours.FULL_BRIGHT;
            collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(WHITE_TEX), (pose, c) -> {
                // LED on the body's front face, the bloom just in front of it and larger.
                SensorFieldRenderers.Frame.quadZ(c, pose, f, 10f, 9.5f, 12f, 11f, 1.9f, PIVOT, xd, yd, zd,
                        0.4f, 0.4f, 0.6f, 0.6f, led, bright);
                SensorFieldRenderers.Frame.quadZ(c, pose, f, 9f, 8.5f, 13f, 12f, 1.6f, PIVOT, xd, yd, zd,
                        0.4f, 0.4f, 0.6f, 0.6f, bloom, bright);
            });
            vertices += LED_VERTICES;
        }
        BerStats.vertices(vertices);
        BerStats.stop(t0);
    }
}
