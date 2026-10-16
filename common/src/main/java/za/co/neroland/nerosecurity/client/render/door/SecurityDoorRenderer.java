package za.co.neroland.nerosecurity.client.render.door;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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

import za.co.neroland.nerosecurity.block.door.AbstractSecurityDoorBlock;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;
import za.co.neroland.nerosecurity.client.feedback.ClientDeviceFeedback;
import za.co.neroland.nerosecurity.client.render.DoorChestHubRenderers;

/**
 * The Security Door (1x2) and Blast Door (2x2) renderer, on the controller block entity.
 *
 * <ul>
 *   <li><b>Panels</b> only while the block state says {@code ANIMATING} (moving, or open): the security door's two
 *       panels slide into the jambs (easeInOutCubic), the blast door's four quadrant plates withdraw into the outer
 *       frame as an iris (easeInOutQuart). A shut, still door is the static model alone.</li>
 *   <li><b>Clamp bars</b> glow {@code LOCK} while sealed (the bars themselves are in the sealed static models).</li>
 *   <li><b>LEDs</b> on the head of the frame, front and back: status (level colour steady, WARN blink when
 *       unpowered, LOCK strobe when sealed, LOCK flash on a deny) and recording (REC, 1 Hz, while logging).</li>
 * </ul>
 *
 * Motion is client-time based from the synced anchor ({@code OpenTicks / Opening / SyncTime / Duration}), so it is
 * FPS-independent and needs no per-tick packets. Extract reads only the controller's own synced fields and its own
 * block state.
 */
public class SecurityDoorRenderer implements BlockEntityRenderer<SecurityDoorBlockEntity, SecurityDoorRenderState> {

    public static final int VIEW_DISTANCE = 64;

    private static final RenderType DOOR_PANEL = SecurityGeometry.cutout(SecurityGeometry.blockTexture("security_door_panel"));
    private static final RenderType BLAST_PANEL = SecurityGeometry.cutout(SecurityGeometry.blockTexture("blast_door_panel"));
    private static final RenderType DOOR_CLAMP = SecurityGeometry.emissive(SecurityGeometry.blockTexture("security_door_clamp"));
    private static final RenderType BLAST_CLAMP = SecurityGeometry.emissive(SecurityGeometry.blockTexture("blast_door_clamp"));
    private static final RenderType LED = SecurityGeometry.emissive(DoorChestHubRenderers.LED_TEXTURE);

    /** Clamp-band offset proud of the bar faces, in pixels. */
    private static final float PROUD = 0.1f;

    private final boolean blast;

    public SecurityDoorRenderer(boolean blast) {
        this.blast = blast;
    }

    @Override
    public SecurityDoorRenderState createRenderState() {
        return new SecurityDoorRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(SecurityDoorBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        if (!blast) {
            return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        }
        BlockPos other = pos.relative(facing(be.getBlockState()).getCounterClockWise());
        return new AABB(Math.min(pos.getX(), other.getX()), pos.getY(), Math.min(pos.getZ(), other.getZ()),
                Math.max(pos.getX(), other.getX()) + 1, pos.getY() + 2, Math.max(pos.getZ(), other.getZ()) + 1);
    }

    @Override
    public boolean shouldRender(SecurityDoorBlockEntity be, Vec3 cameraPos) {
        boolean visible = getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos);
        if (!visible) {
            BerStats.culled();
        }
        return visible;
    }

    @Override
    public void extractRenderState(SecurityDoorBlockEntity be, SecurityDoorRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        Level level = be.getLevel();
        long now = level == null ? 0L : level.getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        BlockState state = be.getBlockState();
        Direction facing = facing(state);
        boolean animate = ClientAnim.animationsEnabled();

        s.blast = blast;
        s.quarterTurns = DoorChestHubRenderers.quarterTurns(facing);
        int direction = be.opening() ? 1 : -1;
        float raw = ClientAnim.progress(be.openTicks(), direction, be.changedAt(), now, partialTick,
                be.openDuration(), animate);
        s.progress = DoorPanelMath.eased(raw, blast);
        boolean animating = state.hasProperty(AbstractSecurityDoorBlock.ANIMATING)
                && state.getValue(AbstractSecurityDoorBlock.ANIMATING);
        s.panels = animating && DoorPanelMath.panelVertices(blast, s.progress) > 0;
        s.sealed = be.renderSealed();

        BlockPos pos = be.getBlockPos();
        boolean flashing = false;
        boolean flashOn = false;
        for (BlockPos part : parts(pos, facing)) {
            if (ClientDeviceFeedback.flashing(part, now)) {
                flashing = true;
                flashOn |= ClientDeviceFeedback.flashOn(part, now, partialTick);
            }
        }
        s.status = DoorLeds.status(SecurityColours.levelColour(be.renderMinLevel()), s.sealed, be.renderPowerLow(),
                flashing, flashOn, now, animate);
        s.recording = DoorLeds.recording(be.renderRecording(), now, animate);
        BerStats.stop(t0);
    }

    @Override
    public void submit(SecurityDoorRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        long t0 = BerStats.start();
        if (!s.panels && !s.sealed && s.status == DoorLeds.OFF && s.recording == DoorLeds.OFF) {
            BerStats.culled();
            BerStats.stop(t0);
            return;
        }
        BerStats.submitted();
        int q = s.quarterTurns;
        int light = s.lightCoords;
        if (s.panels) {
            float p = s.progress;
            collector.order(1).submitCustomGeometry(poseStack, s.blast ? BLAST_PANEL : DOOR_PANEL,
                    (pose, c) -> BerStats.vertices(s.blast ? irisPanels(c, pose, q, p, light)
                            : slidingPanels(c, pose, q, p, light)));
        }
        if (s.sealed) {
            int lock = SecurityColours.argb(SecurityColours.LOCK, 255);
            collector.order(1).submitCustomGeometry(poseStack, s.blast ? BLAST_CLAMP : DOOR_CLAMP,
                    (pose, c) -> BerStats.vertices(s.blast ? blastClamps(c, pose, q, lock) : doorClamps(c, pose, q, lock)));
        }
        if (s.status != DoorLeds.OFF || s.recording != DoorLeds.OFF) {
            int status = s.status;
            int rec = s.recording;
            boolean isBlast = s.blast;
            collector.order(1).submitCustomGeometry(poseStack, LED,
                    (pose, c) -> BerStats.vertices(leds(c, pose, q, isBlast, status, rec)));
        }
        BerStats.stop(t0);
    }

    // ---- geometry ----

    /** Security door: the two panels at progress {@code p}, over both blocks (y 0..32), z 6..10. */
    static int slidingPanels(VertexConsumer c, PoseStack.Pose pose, int q, float p, int light) {
        float[] x = DoorPanelMath.securityPanels(p);
        float[] u = DoorPanelMath.securityPanelUv(p);
        int white = 0xFFFFFFFF;
        int n = 0;
        if (DoorPanelMath.drawn(x[1] - x[0])) {
            n += DoorChestHubRenderers.box(c, pose, q, x[0], 0f, 6f, x[1], 32f, 10f, u[0], 0f, u[1], 16f, white, light);
        }
        if (DoorPanelMath.drawn(x[3] - x[2])) {
            n += DoorChestHubRenderers.box(c, pose, q, x[2], 0f, 6f, x[3], 32f, 10f, u[2], 0f, u[3], 16f, white, light);
        }
        return n;
    }

    /** Blast door: the four quadrant plates of the iris at progress {@code p}, z 5..11. */
    static int irisPanels(VertexConsumer c, PoseStack.Pose pose, int q, float p, int light) {
        if (!DoorPanelMath.drawn(DoorPanelMath.irisLength(p))) {
            return 0;
        }
        int white = 0xFFFFFFFF;
        int n = 0;
        for (int i = 0; i < 4; i++) {
            float[] r = DoorPanelMath.irisQuadrant((i & 1) != 0, (i & 2) != 0, p);
            n += DoorChestHubRenderers.box(c, pose, q, r[0], r[1], 5f, r[2], r[3], 11f, r[4], r[5], r[6], r[7],
                    white, light);
        }
        return n;
    }

    /** Security door: glowing bands on the four clamp bars (two per block), front and back faces. */
    static int doorClamps(VertexConsumer c, PoseStack.Pose pose, int q, int argb) {
        int n = 0;
        float[] bars = {3f, 11f, 19f, 27f};
        for (float y0 : bars) {
            n += DoorChestHubRenderers.plate(c, pose, q, 2f, y0, 14f, y0 + 2f, 5f - PROUD, argb, SecurityColours.FULL_BRIGHT);
            n += DoorChestHubRenderers.plate(c, pose, q, 2f, y0, 14f, y0 + 2f, 11f + PROUD, argb, SecurityColours.FULL_BRIGHT);
        }
        return n;
    }

    /** Blast door: one band per quadrant, from the outer frame bar to the vertical seam at mid-height. */
    static int blastClamps(VertexConsumer c, PoseStack.Pose pose, int q, int argb) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            boolean right = (i & 1) != 0;
            boolean top = (i & 2) != 0;
            float x0 = right ? -16f + DoorPanelMath.FRAME : 0f;
            float x1 = right ? 0f : 16f - DoorPanelMath.FRAME;
            float y0 = top ? 23f : 7f;
            n += DoorChestHubRenderers.plate(c, pose, q, x0, y0, x1, y0 + 2f, 4f - PROUD, argb, SecurityColours.FULL_BRIGHT);
            n += DoorChestHubRenderers.plate(c, pose, q, x0, y0, x1, y0 + 2f, 12f + PROUD, argb, SecurityColours.FULL_BRIGHT);
        }
        return n;
    }

    /**
     * Status and recording LEDs on the head of the frame, front (z just proud of the frame face) and back (the same
     * spot seen from behind: the geometry turned a half turn). Security door: on the lintel; blast door: on the top
     * frame bar of the controller column.
     */
    static int leds(VertexConsumer c, PoseStack.Pose pose, int q, boolean blast, int status, int rec) {
        float y0 = blast ? 30.5f : 30.2f;
        float sx = blast ? 10f : 6f;
        float rx = blast ? 12f : 9f;
        float z = -PROUD;
        int n = 0;
        for (int side = 0; side < 2; side++) {
            int turns = q + 2 * side;
            if (status != DoorLeds.OFF) {
                n += DoorChestHubRenderers.plate(c, pose, turns, sx, y0, sx + 1f, y0 + 1f, z,
                        SecurityColours.argb(status, 255), SecurityColours.FULL_BRIGHT);
            }
            if (rec != DoorLeds.OFF) {
                n += DoorChestHubRenderers.plate(c, pose, turns, rx, y0, rx + 1f, y0 + 1f, z,
                        SecurityColours.argb(rec, 255), SecurityColours.FULL_BRIGHT);
            }
        }
        return n;
    }

    // ---- own-state helpers ----

    private static Direction facing(BlockState state) {
        return state.hasProperty(AbstractSecurityDoorBlock.FACING)
                ? state.getValue(AbstractSecurityDoorBlock.FACING) : Direction.NORTH;
    }

    /** Every part position of this door (arithmetic on the controller position; no world reads). */
    private BlockPos[] parts(BlockPos controller, Direction facing) {
        if (!blast) {
            return new BlockPos[] {controller, controller.above()};
        }
        BlockPos right = controller.relative(facing.getCounterClockWise());
        return new BlockPos[] {controller, right, controller.above(), right.above()};
    }
}
