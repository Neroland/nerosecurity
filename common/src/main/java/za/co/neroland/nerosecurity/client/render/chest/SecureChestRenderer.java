package za.co.neroland.nerosecurity.client.render.chest;

import java.util.Map;
import java.util.WeakHashMap;

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

import za.co.neroland.nerosecurity.block.chest.SecureChestBlock;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;
import za.co.neroland.nerosecurity.client.feedback.ClientDeviceFeedback;
import za.co.neroland.nerosecurity.client.render.DoorChestHubRenderers;
import za.co.neroland.nerosecurity.client.render.door.DoorLeds;

/**
 * The Secure Chest (the block model has no elements; this renderer is the chest): body, a lid hinged at the back
 * that swings open with easeOutCubic while anyone has the chest open, a lock plate on the lid tinted the chest's
 * minimum-level colour ({@code OFF} grey while unlinked), and the LED on the body: the {@code LOCK} strobe in
 * lockdown, else the recording blink (REC, 1 Hz); dark while unlinked. A deny flashes the plate and the LED
 * {@code LOCK} at 8 Hz for 10 ticks. With animations off every blink is a steady colour ({@link ChestLights}).
 *
 * <p>Extract reads only the chest's own synced fields ({@code isOpenForRender}, {@code renderMinLevel},
 * {@code renderRecording}, {@code renderLinked}, {@code renderLockdown}), its own block state, the game time and the
 * client-side deny flash map. The lid anchor lives in this renderer, weakly keyed by the
 * block entity, so the chest itself carries no client animation state.
 */
public class SecureChestRenderer implements BlockEntityRenderer<SecureChestBlockEntity, SecureChestRenderState> {

    public static final int VIEW_DISTANCE = 48;

    private static final RenderType BODY = SecurityGeometry.cutout(SecurityGeometry.blockTexture("secure_chest_body"));
    private static final RenderType LID = SecurityGeometry.cutout(SecurityGeometry.blockTexture("secure_chest_lid"));
    private static final RenderType PLATE = SecurityGeometry.cutout(SecurityGeometry.blockTexture("secure_chest_plate"));
    private static final RenderType LED = SecurityGeometry.emissive(DoorChestHubRenderers.LED_TEXTURE);

    private final Map<SecureChestBlockEntity, LidMath.Anchor> anchors = new WeakHashMap<>();

    @Override
    public SecureChestRenderState createRenderState() {
        return new SecureChestRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(SecureChestBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRender(SecureChestBlockEntity be, Vec3 cameraPos) {
        boolean visible = getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos);
        if (!visible) {
            BerStats.culled();
        }
        return visible;
    }

    @Override
    public void extractRenderState(SecureChestBlockEntity be, SecureChestRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        Level level = be.getLevel();
        long now = level == null ? 0L : level.getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(SecureChestBlock.FACING) ? state.getValue(SecureChestBlock.FACING)
                : Direction.NORTH;
        boolean animate = ClientAnim.animationsEnabled();
        s.quarterTurns = DoorChestHubRenderers.quarterTurns(facing);

        boolean open = be.isOpenForRender();
        double time = now + (double) partialTick;
        LidMath.Anchor anchor = anchors.computeIfAbsent(be, k -> new LidMath.Anchor(open, time));
        float progress = anchor.at(open, time);
        s.lidDegrees = LidMath.angle(animate ? progress : (open ? 1f : 0f));

        BlockPos pos = be.getBlockPos();
        boolean flashing = ClientDeviceFeedback.flashing(pos, now);
        boolean flashOn = flashing && (!animate || ClientDeviceFeedback.flashOn(pos, now, partialTick));
        boolean linked = be.renderLinked();
        s.plate = ChestLights.plate(linked, SecurityColours.levelColour(be.renderMinLevel()), flashOn);
        s.led = ChestLights.led(linked, be.renderLockdown(), be.renderRecording(), flashing, flashOn, now, animate);
        BerStats.stop(t0);
    }

    @Override
    public void submit(SecureChestRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        long t0 = BerStats.start();
        BerStats.submitted();
        int q = s.quarterTurns;
        int light = s.lightCoords;
        float lid = s.lidDegrees;
        int plate = SecurityColours.argb(s.plate, 255);
        collector.order(1).submitCustomGeometry(poseStack, BODY,
                (pose, c) -> BerStats.vertices(DoorChestHubRenderers.box(c, pose, q, 1f, 0f, 1f, 15f, 10f, 15f,
                        0f, 0f, 16f, 16f, 0xFFFFFFFF, light)));
        collector.order(1).submitCustomGeometry(poseStack, LID,
                (pose, c) -> BerStats.vertices(hinged(c, pose, q, lid, 8f, 12f, 8f, 7f, 2f, 7f, 0xFFFFFFFF, light)));
        collector.order(1).submitCustomGeometry(poseStack, PLATE,
                (pose, c) -> BerStats.vertices(hinged(c, pose, q, lid, 8f, 10f, 0.25f, 2f, 3f, 0.75f, plate, light)));
        if (s.led != DoorLeds.OFF) {
            int led = SecurityColours.argb(s.led, 255);
            collector.order(1).submitCustomGeometry(poseStack, LED,
                    (pose, c) -> BerStats.vertices(DoorChestHubRenderers.plate(c, pose, q, 13f, 8f, 14f, 9f, 0.45f,
                            led, SecurityColours.FULL_BRIGHT)));
        }
        BerStats.stop(t0);
    }

    /**
     * A box authored facing north (centre and half extents in model pixels) that swings with the lid: rotated
     * {@code degrees} about the hinge line, then turned to the chest's facing. Full UV.
     */
    static int hinged(VertexConsumer c, PoseStack.Pose pose, int q, float degrees, float cx, float cy, float cz,
            float hx, float hy, float hz, int argb, int light) {
        float[] centre = LidMath.hingedCentre(cx, cy, cz, degrees);
        float[] turned = DoorChestHubRenderers.turn(centre[0], centre[2], q);
        float px = SecurityGeometry.PX;
        return SecurityGeometry.cubeRotated(c, pose, turned[0] * px, centre[1] * px, turned[1] * px,
                hx * px, hy * px, hz * px, degrees, -90f * q, 0f, argb, light);
    }
}
