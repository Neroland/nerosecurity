package za.co.neroland.nerosecurity.client.render.camera;

import net.minecraft.core.Direction;

import za.co.neroland.nerosecurity.block.camera.CameraGeometry;
import za.co.neroland.nerosecurity.client.anim.Easing;
import za.co.neroland.nerosecurity.client.render.SensorFieldRenderers;

/**
 * The camera body's pose as pure functions. The sweep is the server's own {@link CameraGeometry#panDegrees} (so
 * what the camera appears to watch is what it senses); a synced, already quantised look (two angles, never a
 * target) pulls the body toward it: eased in over {@value #LERP_TICKS} ticks, held until {@value #HOLD_UNTIL}
 * ticks after it was sent, eased back over {@value #LERP_TICKS}. Unit-tested.
 *
 * <p>Poses are {@code {xDeg, yDeg, zDeg}} for {@code SecurityGeometry.rotate} in the authored (north) frame about
 * the arm pivot: wall cameras pan about local Y and tilt about local X; ceiling and floor cameras spin their housing
 * about the vertical (local Z), exactly as {@link CameraGeometry#viewDirection} leaves their axis.
 */
public final class CameraTilt {

    public static final int LERP_TICKS = 10;
    public static final int HOLD_UNTIL = 70;
    public static final int END = HOLD_UNTIL + LERP_TICKS;
    public static final float MAX_PITCH = 35f;
    public static final float MAX_WALL_YAW = 80f;
    public static final float MAX_VERTICAL_TILT = 35f;

    private CameraTilt() {
    }

    /** Ticks since the look was sent, or −1 when there is none. */
    public static double elapsed(boolean hasLook, long seenAt, long gameTime, float partial) {
        if (!hasLook || seenAt == Long.MIN_VALUE) {
            return -1.0D;
        }
        return (double) (gameTime - seenAt) + partial;
    }

    /** How far the body is pulled toward the look, 0..1. Still frames step 1 → 0 at {@value #HOLD_UNTIL}. */
    public static float weight(double elapsed, boolean animate) {
        if (!(elapsed >= 0.0D) || elapsed >= END) {
            return 0f;
        }
        if (!animate) {
            return elapsed < HOLD_UNTIL ? 1f : 0f;
        }
        if (elapsed < LERP_TICKS) {
            return Easing.easeInOutCubic((float) (elapsed / LERP_TICKS));
        }
        if (elapsed < HOLD_UNTIL) {
            return 1f;
        }
        return 1f - Easing.easeInOutCubic((float) ((elapsed - HOLD_UNTIL) / LERP_TICKS));
    }

    /** The shared pan, interpolated between whole ticks (equal to the server's value on every whole tick). */
    public static float pan(long gameTime, float partial, int sweepTicks) {
        double a = CameraGeometry.panDegrees(gameTime, sweepTicks);
        double b = CameraGeometry.panDegrees(gameTime + 1, sweepTicks);
        return (float) (a + (b - a) * partial);
    }

    /** The sweep pose for a pan angle (clockwise from above positive, as the server). */
    public static float[] sweepPose(Direction facing, float pan) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return new float[] {0f, 0f, pan};
        }
        return new float[] {0f, -pan, 0f};
    }

    /** The pose aiming the lens along a world yaw/pitch (Minecraft convention), clamped to what the mount allows. */
    public static float[] lookPose(Direction facing, float yaw, float pitch) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        float dx = (float) (-Math.sin(yr) * Math.cos(pr));
        float dy = (float) (-Math.sin(pr));
        float dz = (float) (Math.cos(yr) * Math.cos(pr));
        float[] l = SensorFieldRenderers.Frame.dirToLocal(facing, dx, dy, dz);
        float a = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, l[1]))));
        float b = (float) Math.toDegrees(Math.atan2(-l[0], -l[2]));
        float maxYaw = facing.getAxis() == Direction.Axis.Y ? MAX_VERTICAL_TILT : MAX_WALL_YAW;
        return new float[] {clamp(a, MAX_PITCH), clamp(b, maxYaw), 0f};
    }

    /**
     * The body pose this frame. An unarmed camera is parked (pose 0) and ignores any look; with animations off it
     * does not sweep, and a held look is shown as a still frame.
     */
    public static float[] pose(Direction facing, long gameTime, float partial, int sweepTicks, boolean armed,
            boolean hasLook, long seenAt, float yaw, float pitch, boolean animate) {
        if (!armed) {
            return new float[] {0f, 0f, 0f};
        }
        float[] sweep = sweepPose(facing, animate ? pan(gameTime, partial, sweepTicks) : 0f);
        float k = weight(elapsed(hasLook, seenAt, gameTime, partial), animate);
        if (k <= 0f) {
            return sweep;
        }
        float[] look = lookPose(facing, yaw, pitch);
        return new float[] {
            Easing.lerp(sweep[0], look[0], k), Easing.lerp(sweep[1], look[1], k), Easing.lerp(sweep[2], look[2], k)};
    }

    private static float clamp(float v, float max) {
        return v < -max ? -max : (v > max ? max : v);
    }
}
