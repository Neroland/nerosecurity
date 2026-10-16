package za.co.neroland.nerosecurity.client.render.alarm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerosecurity.block.alarm.AlarmBlock;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;

/**
 * The alarm's beacon, only while {@code ACTIVE}: two opposed blades turning 18°/tick inside the dome and a
 * translucent {@code LOCK} light column rising six blocks, full-bright. Nothing is drawn while silent.
 *
 * <p>Culling: the block up to the column top, view distance {@value #VIEW_DISTANCE}. No off-screen rendering.
 */
public class AlarmBeaconRenderer implements BlockEntityRenderer<AlarmBlockEntity, AlarmBeaconRenderState> {

    public static final int VIEW_DISTANCE = 64;
    /** Two blade quads + four column sides, all double-sided. */
    public static final int VERTICES = 6 * SecurityGeometry.FACE_VERTICES;

    static final Identifier BLADE_TEX = SecurityGeometry.blockTexture("alarm_blade");
    static final Identifier WHITE_TEX = SecurityGeometry.blockTexture("security_led");

    @Override
    public AlarmBeaconRenderState createRenderState() {
        return new AlarmBeaconRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(AlarmBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0D,
                p.getY() + AlarmBeaconMath.COLUMN_Y0 + AlarmBeaconMath.COLUMN_HEIGHT, p.getZ() + 1.0D);
    }

    @Override
    public boolean shouldRender(AlarmBlockEntity be, Vec3 cameraPos) {
        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
            return true;
        }
        BerStats.culled();
        return false;
    }

    @Override
    public void extractRenderState(AlarmBlockEntity be, AlarmBeaconRenderState s, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        long now = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        BlockState state = be.getBlockState();
        s.active = state.hasProperty(AlarmBlock.ACTIVE) && state.getValue(AlarmBlock.ACTIVE);
        if (!s.active) {
            BerStats.culled();
            BerStats.stop(t0);
            return;
        }
        boolean animate = ClientAnim.animationsEnabled();
        s.angle = AlarmBeaconMath.bladeAngle(now, partialTick, animate);
        s.columnAlpha = AlarmBeaconMath.columnAlpha(now, partialTick, animate);
        BerStats.stop(t0);
    }

    @Override
    public void submit(AlarmBeaconRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (!s.active) {
            return;
        }
        long t0 = BerStats.start();
        BerStats.submitted();
        final float angle = s.angle;
        final int blade = SecurityColours.argb(SecurityColours.LOCK, 0.7f);
        final int column = SecurityColours.argb(SecurityColours.LOCK, s.columnAlpha);
        final int light = SecurityColours.FULL_BRIGHT;
        collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(BLADE_TEX), (pose, c) -> {
            drawBlade(c, pose, angle, blade, light);
            drawBlade(c, pose, angle + 180f, blade, light);
        });
        collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(WHITE_TEX),
                (pose, c) -> drawColumn(c, pose, column, light));
        BerStats.vertices(VERTICES);
        BerStats.stop(t0);
    }

    private static void drawBlade(VertexConsumer c, PoseStack.Pose pose, float degrees, int argb, int light) {
        float[] tip = AlarmBeaconMath.bladeTip(degrees);
        float hx = AlarmBeaconMath.HUB_X / 16f;
        float hz = AlarmBeaconMath.HUB_Z / 16f;
        float tx = tip[0] / 16f;
        float tz = tip[1] / 16f;
        float y0 = AlarmBeaconMath.BLADE_Y0 / 16f;
        float y1 = AlarmBeaconMath.BLADE_Y1 / 16f;
        SecurityGeometry.quad(c, pose, hx, y1, hz, tx, y1, tz, tx, y0, tz, hx, y0, hz, 0f, 0f, 1f, 1f, argb, light);
    }

    private static void drawColumn(VertexConsumer c, PoseStack.Pose pose, int argb, int light) {
        float lo = 0.5f - AlarmBeaconMath.COLUMN_HALF;
        float hi = 0.5f + AlarmBeaconMath.COLUMN_HALF;
        float y0 = AlarmBeaconMath.COLUMN_Y0;
        float y1 = y0 + AlarmBeaconMath.COLUMN_HEIGHT;
        float u0 = 0.4f;
        float u1 = 0.6f;
        SecurityGeometry.face(c, pose, 0, 0, -1, hi, y1, lo, lo, y1, lo, lo, y0, lo, hi, y0, lo, u0, u0, u1, u1, argb, light);
        SecurityGeometry.face(c, pose, 0, 0, 1, lo, y1, hi, hi, y1, hi, hi, y0, hi, lo, y0, hi, u0, u0, u1, u1, argb, light);
        SecurityGeometry.face(c, pose, -1, 0, 0, lo, y1, lo, lo, y1, hi, lo, y0, hi, lo, y0, lo, u0, u0, u1, u1, argb, light);
        SecurityGeometry.face(c, pose, 1, 0, 0, hi, y1, hi, hi, y1, lo, hi, y0, lo, hi, y0, hi, u0, u0, u1, u1, argb, light);
    }
}
