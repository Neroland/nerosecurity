package za.co.neroland.nerosecurity.client.render.laser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlock;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;
import za.co.neroland.nerosecurity.client.render.SensorFieldRenderers;

/**
 * The laser grid node's beam half and trip ring. Each node draws lens → midpoint only (two crossed core quads in
 * {@code BEAM}, two crossed glow quads in {@code BEAM_GLOW} with a slow 40-tick pulse, UV-scrolled toward the
 * partner), full-bright. For the 40 ticks after a trip the core strobes white at 4 Hz and a {@code LOCK} ring
 * frames the lens. Nothing is drawn unless the node is armed and paired.
 *
 * <p>Culling: the box is this node's half-beam only (lens → midpoint, inflated 0.3), view distance
 * {@value #VIEW_DISTANCE}. No off-screen rendering.
 */
public class LaserBeamRenderer implements BlockEntityRenderer<LaserGridNodeBlockEntity, LaserBeamRenderState> {

    public static final int VIEW_DISTANCE = 64;
    /** Core and glow half-widths, block units. */
    public static final float CORE_HALF = 1f / 16f;
    public static final float GLOW_HALF = 3f / 16f;
    /** Vertices: core 16 + glow 16; the trip ring adds 4 quads (32). */
    public static final int BEAM_VERTICES = 4 * SecurityGeometry.FACE_VERTICES;
    public static final int RING_VERTICES = 4 * SecurityGeometry.FACE_VERTICES;

    static final Identifier BEAM_TEX = SecurityGeometry.blockTexture("laser_beam");
    /** Plain white texel source for colour-tinted emissives (the white strobe and the ring). */
    static final Identifier WHITE_TEX = SecurityGeometry.blockTexture("security_led");

    private static final float RING_Z = 1.9f;
    private static final float[] NO_PIVOT = {8f, 8f, 8f};

    @Override
    public LaserBeamRenderState createRenderState() {
        return new LaserBeamRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(LaserGridNodeBlockEntity be) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(LaserGridNodeBlock.FACING) ? state.getValue(LaserGridNodeBlock.FACING) : null;
        return LaserBeamMath.box(be.getBlockPos(), facing, be.beamPartner());
    }

    @Override
    public boolean shouldRender(LaserGridNodeBlockEntity be, Vec3 cameraPos) {
        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
            return true;
        }
        BerStats.culled();
        return false;
    }

    @Override
    public void extractRenderState(LaserGridNodeBlockEntity be, LaserBeamRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        long now = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        s.active = false;
        BlockState state = be.getBlockState();
        BlockPos partner = be.beamPartner();
        boolean armed = state.hasProperty(LaserGridNodeBlock.ARMED) && state.getValue(LaserGridNodeBlock.ARMED);
        if (!armed || partner == null || !state.hasProperty(LaserGridNodeBlock.FACING)) {
            BerStats.culled();
            BerStats.stop(t0);
            return;
        }
        boolean animate = ClientAnim.animationsEnabled();
        BlockPos pos = be.getBlockPos();
        s.facing = state.getValue(LaserGridNodeBlock.FACING);
        float[] seg = LaserBeamMath.halfSegment(s.facing, partner.getX() - pos.getX(), partner.getY() - pos.getY(),
                partner.getZ() - pos.getZ());
        s.fromX = seg[0];
        s.fromY = seg[1];
        s.fromZ = seg[2];
        s.toX = seg[3];
        s.toY = seg[4];
        s.toZ = seg[5];
        s.pulse = LaserBeamMath.pulse(now, partialTick, animate);
        s.uStart = LaserBeamMath.uStart(now, partialTick, animate);
        long lastTrip = be.lastTripGameTime();
        s.trip = LaserBeamMath.tripping(lastTrip, now, partialTick);
        s.white = LaserBeamMath.tripWhite(lastTrip, now, partialTick, animate);
        s.active = true;
        BerStats.stop(t0);
    }

    @Override
    public void submit(LaserBeamRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (!s.active) {
            return;
        }
        long t0 = BerStats.start();
        BerStats.submitted();
        final float ax = s.fromX;
        final float ay = s.fromY;
        final float az = s.fromZ;
        final float bx = s.toX;
        final float by = s.toY;
        final float bz = s.toZ;
        final float u = s.uStart;
        final int light = SecurityColours.FULL_BRIGHT;
        final int glow = SecurityColours.argb(SecurityColours.BEAM_GLOW, 0.25f + 0.15f * s.pulse);
        final boolean white = s.white;
        collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(BEAM_TEX), (pose, c) -> {
            SecurityGeometry.quadBetween(c, pose, ax, ay, az, bx, by, bz, GLOW_HALF, u, 0.5f, glow, light);
            if (!white) {
                SecurityGeometry.quadBetween(c, pose, ax, ay, az, bx, by, bz, CORE_HALF, u, 0.5f,
                        SecurityColours.argb(SecurityColours.BEAM, 0.9f), light);
            }
        });
        if (white) {
            collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(WHITE_TEX),
                    (pose, c) -> SecurityGeometry.quadBetween(c, pose, ax, ay, az, bx, by, bz, CORE_HALF, 0.4f, 0.2f,
                            0xFFFFFFFF, light));
        }
        int vertices = BEAM_VERTICES;
        if (s.trip) {
            final Direction facing = s.facing;
            final int ring = SecurityColours.argb(SecurityColours.LOCK, 255);
            collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(WHITE_TEX),
                    (pose, c) -> drawRing(c, pose, facing, ring, light));
            vertices += RING_VERTICES;
        }
        BerStats.vertices(vertices);
        BerStats.stop(t0);
    }

    /** Four thin quads framing the lens, [3.5,3.5]→[12.5,12.5] at z = 1.9 px (authored north). */
    private static void drawRing(VertexConsumer c, PoseStack.Pose pose, Direction facing,
            int argb, int light) {
        SensorFieldRenderers.Frame.quadZ(c, pose, facing, 3.5f, 11.5f, 12.5f, 12.5f, RING_Z, NO_PIVOT, 0f, 0f, 0f,
                0.4f, 0.4f, 0.6f, 0.6f, argb, light);
        SensorFieldRenderers.Frame.quadZ(c, pose, facing, 3.5f, 3.5f, 12.5f, 4.5f, RING_Z, NO_PIVOT, 0f, 0f, 0f,
                0.4f, 0.4f, 0.6f, 0.6f, argb, light);
        SensorFieldRenderers.Frame.quadZ(c, pose, facing, 3.5f, 4.5f, 4.5f, 11.5f, RING_Z, NO_PIVOT, 0f, 0f, 0f,
                0.4f, 0.4f, 0.6f, 0.6f, argb, light);
        SensorFieldRenderers.Frame.quadZ(c, pose, facing, 11.5f, 4.5f, 12.5f, 11.5f, RING_Z, NO_PIVOT, 0f, 0f, 0f,
                0.4f, 0.4f, 0.6f, 0.6f, argb, light);
    }
}
