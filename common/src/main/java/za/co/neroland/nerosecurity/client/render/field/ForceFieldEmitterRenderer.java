package za.co.neroland.nerosecurity.client.render.field;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.client.BerStats;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.anim.Blink;
import za.co.neroland.nerosecurity.client.anim.ClientAnim;
import za.co.neroland.nerosecurity.client.feedback.ClientDeviceFeedback;
import za.co.neroland.nerosecurity.client.render.SensorFieldRenderers;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.sound.SecuritySounds;

/**
 * The force field emitter's core: a cube spinning about the facing axis at a speed chosen by the synced
 * {@code DrawBucket}, and a 45°/45° gyro copy at 80 % turning back at half speed. Tinted {@code FIELD}, {@code LOCK}
 * in lockdown, blinking {@code WARN} in brownout, dark when idle or overloaded (overloaded also blinks a {@code LOCK}
 * ring LED). A deny (the player was refused at this emitter) flashes the core {@code LOCK} at 8 Hz for the 10 ticks of
 * {@link ClientDeviceFeedback}, over every state; steady {@code LOCK} with animations off. Also plays the quiet client-side field hum, at most once per {@value #HUM_INTERVAL_TICKS} ticks per
 * emitter, only while projecting and within {@value #HUM_RANGE} blocks of the camera.
 *
 * <p>Extract reads only the emitter's own synced fields, its block state, the game time and the client-side deny
 * flash map; never the world, a store or a menu.
 *
 * <p>Culling: the block, view distance {@value #VIEW_DISTANCE}. No off-screen rendering.
 */
public class ForceFieldEmitterRenderer
        implements BlockEntityRenderer<ForceFieldEmitterBlockEntity, ForceFieldEmitterRenderState> {

    public static final int VIEW_DISTANCE = 48;
    /** Core box + gyro box; the overload LED adds one quad. */
    public static final int CORE_VERTICES = 2 * SecurityGeometry.BOX_VERTICES;
    public static final int LED_VERTICES = SecurityGeometry.FACE_VERTICES;
    public static final int HUM_INTERVAL_TICKS = 80;
    public static final double HUM_RANGE = 12.0D;
    private static final int HUM_PURGE_SIZE = 64;

    static final Identifier CORE_TEX = SecurityGeometry.blockTexture("force_field_emitter_core");
    static final Identifier WHITE_TEX = SecurityGeometry.blockTexture("security_led");
    static final float[] CENTRE = {8f, 8f, 8f};

    /** Last hum per emitter position (render thread only; bounded and pruned). */
    private final Map<Long, Long> lastHum = new HashMap<>();

    @Override
    public ForceFieldEmitterRenderState createRenderState() {
        return new ForceFieldEmitterRenderState();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(ForceFieldEmitterBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRender(ForceFieldEmitterBlockEntity be, Vec3 cameraPos) {
        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
            return true;
        }
        BerStats.culled();
        return false;
    }

    @Override
    public void extractRenderState(ForceFieldEmitterBlockEntity be, ForceFieldEmitterRenderState s,
            float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        long now = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        BerStats.frame(now, partialTick);
        long t0 = BerStats.start();
        BlockState state = be.getBlockState();
        s.valid = state.hasProperty(ForceFieldEmitterBlock.FACING);
        if (!s.valid) {
            BerStats.culled();
            BerStats.stop(t0);
            return;
        }
        boolean animate = ClientAnim.animationsEnabled();
        FieldState field = be.renderState();
        s.facing = state.getValue(ForceFieldEmitterBlock.FACING);
        s.angle = EmitterCoreMath.angle(EmitterCoreMath.degreesPerSecond(field, be.renderDrawBucket()), now,
                partialTick, animate);
        int stateTint = EmitterCoreMath.tint(field, be.renderLockdown(), now, animate);
        BlockPos pos = be.getBlockPos();
        boolean flashing = ClientDeviceFeedback.flashing(pos, now);
        boolean flashOn = flashing && ClientDeviceFeedback.flashOn(pos, now, partialTick);
        s.tint = EmitterCoreMath.denyTint(stateTint, flashing, flashOn, animate);
        s.lit = flashing ? s.tint == SecurityColours.LOCK : EmitterCoreMath.lit(field, s.tint);
        s.overloadLed = field == FieldState.OVERLOADED && (!animate || Blink.warning(now));
        if (field == FieldState.PROJECTING) {
            hum(pos, now, cameraPos);
        }
        BerStats.stop(t0);
    }

    /** The quiet field hum: once per interval per emitter (staggered by position), only within range. */
    private void hum(BlockPos pos, long now, Vec3 cameraPos) {
        if (cameraPos == null || cameraPos.distanceToSqr(Vec3.atCenterOf(pos)) > HUM_RANGE * HUM_RANGE) {
            return;
        }
        long key = pos.asLong();
        Long last = lastHum.get(key);
        if (last == null) {
            // First sight: stagger so a wall of emitters does not hum in unison; the first hum is within an interval.
            lastHum.put(key, now - Math.floorMod(key, (long) HUM_INTERVAL_TICKS));
            return;
        }
        if (now >= last && now - last < HUM_INTERVAL_TICKS) {
            return;
        }
        if (lastHum.size() > HUM_PURGE_SIZE) {
            lastHum.values().removeIf(t -> now - t > 4L * HUM_INTERVAL_TICKS || t > now);
        }
        lastHum.put(key, now);
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                SecuritySounds hum = SecuritySounds.FIELD_HUM;
                level.playLocalSound(pos, hum.event(), SoundSource.BLOCKS, hum.volume(), hum.pitch(), false);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A sound is never worth a crash.
        }
    }

    @Override
    public void submit(ForceFieldEmitterRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (!s.valid) {
            return;
        }
        long t0 = BerStats.start();
        BerStats.submitted();
        final Direction f = s.facing;
        final float angle = s.angle;
        final int argb = SecurityColours.argb(s.tint, 255);
        final int light = s.lit ? SecurityColours.FULL_BRIGHT : s.lightCoords;
        collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(CORE_TEX), (pose, c) -> {
            // Core [6,6,6]→[10,10,10] about the facing axis (local Z), and the 45°/45° gyro at 80 % turning back.
            SensorFieldRenderers.Frame.box(c, pose, f, 6f, 6f, 6f, 10f, 10f, 10f, CENTRE, 0f, 0f, angle, 1f,
                    0f, 0f, 1f, 1f, argb, light);
            SensorFieldRenderers.Frame.box(c, pose, f, 6f, 6f, 6f, 10f, 10f, 10f, CENTRE, 45f, 45f, -angle * 0.5f,
                    0.8f, 0f, 0f, 1f, 1f, argb, light);
        });
        int vertices = CORE_VERTICES;
        if (s.overloadLed) {
            final int led = SecurityColours.argb(SecurityColours.LOCK, 255);
            final int bright = SecurityColours.FULL_BRIGHT;
            collector.order(1).submitCustomGeometry(poseStack, SecurityGeometry.emissive(WHITE_TEX),
                    (pose, c) -> SensorFieldRenderers.Frame.quadZ(c, pose, f, 7f, 13f, 9f, 13.5f, 5.95f, CENTRE,
                            0f, 0f, 0f, 0.4f, 0.4f, 0.6f, 0.6f, led, bright));
            vertices += LED_VERTICES;
        }
        BerStats.vertices(vertices);
        BerStats.stop(t0);
    }
}
