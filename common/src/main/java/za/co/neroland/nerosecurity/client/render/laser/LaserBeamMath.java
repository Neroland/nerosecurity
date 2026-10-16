package za.co.neroland.nerosecurity.client.render.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerosecurity.client.render.SensorFieldRenderers;

/**
 * The laser beam's pure maths: where this node's half of the beam runs, its bounding box, the slow pulse, the UV
 * scroll and the trip flash. No level, no entity, no clock: game time is passed in. Unit-tested.
 *
 * <p>Each node draws only its own half, lens to midpoint, so a pair stays drawn while either node's box is in view
 * and neither renderer ever reaches outside its own half.
 */
public final class LaserBeamMath {

    /** The lens front, model pixels, authored facing north. */
    public static final float LENS_X_PX = 8f;
    public static final float LENS_Y_PX = 8f;
    public static final float LENS_Z_PX = 1f;
    /** Bounding box slack around the half-beam (covers the glow and the trip ring). */
    public static final double BOX_INFLATE = 0.3D;
    /** Pulse period in ticks. */
    public static final int PULSE_TICKS = 40;
    /** Trip flash length in ticks. */
    public static final int TRIP_TICKS = 40;
    /** Trip strobe period in ticks (4 Hz). */
    public static final float TRIP_STROBE_TICKS = 5f;
    /** Ticks per UV scroll cycle. */
    public static final int SCROLL_TICKS = 20;

    private LaserBeamMath() {
    }

    /** The lens front of a node facing {@code facing}, block-relative (0..1). */
    public static float[] lens(Direction facing) {
        return SensorFieldRenderers.Frame.toBlock(facing, LENS_X_PX / 16f, LENS_Y_PX / 16f, LENS_Z_PX / 16f);
    }

    /**
     * This node's half of the beam, block-relative to this node: {@code {fromX, fromY, fromZ, toX, toY, toZ}}
     * from its lens to the midpoint between its lens and the partner's (the partner faces the opposite way;
     * its block state is never read).
     *
     * @param dx partner X minus this node's X (likewise dy, dz)
     */
    public static float[] halfSegment(Direction facing, int dx, int dy, int dz) {
        float[] a = lens(facing);
        float[] b = lens(facing.getOpposite());
        float bx = dx + b[0];
        float by = dy + b[1];
        float bz = dz + b[2];
        return new float[] {a[0], a[1], a[2], (a[0] + bx) * 0.5f, (a[1] + by) * 0.5f, (a[2] + bz) * 0.5f};
    }

    /** World box of the drawn half (lens → midpoint, inflated by {@link #BOX_INFLATE}); the block when unpaired. */
    public static AABB box(BlockPos pos, Direction facing, BlockPos partner) {
        if (partner == null || facing == null) {
            return new AABB(pos);
        }
        float[] s = halfSegment(facing, partner.getX() - pos.getX(), partner.getY() - pos.getY(),
                partner.getZ() - pos.getZ());
        return new AABB(pos.getX() + Math.min(s[0], s[3]), pos.getY() + Math.min(s[1], s[4]),
                pos.getZ() + Math.min(s[2], s[5]), pos.getX() + Math.max(s[0], s[3]),
                pos.getY() + Math.max(s[1], s[4]), pos.getZ() + Math.max(s[2], s[5])).inflate(BOX_INFLATE);
    }

    /** {@code 0.5 + 0.5 sin(2 pi (t mod 40) / 40)}; a constant 1 when animations are off. */
    public static float pulse(long gameTime, float partial, boolean animate) {
        if (!animate) {
            return 1f;
        }
        double t = Math.floorMod(gameTime, (long) PULSE_TICKS) + (double) partial;
        return (float) (0.5D + 0.5D * Math.sin(2.0D * Math.PI * t / PULSE_TICKS));
    }

    /** UV start of the scrolling beam texture, kept inside 0..0.5 so u never leaves 0..1; 0 when still. */
    public static float uStart(long gameTime, float partial, boolean animate) {
        if (!animate) {
            return 0f;
        }
        double t = Math.floorMod(gameTime, (long) SCROLL_TICKS) + (double) partial;
        return (float) (t / SCROLL_TICKS) * 0.5f;
    }

    /** Ticks since the last trip, or a negative number when there is none in the flash window. */
    public static double tripElapsed(long lastTrip, long gameTime, float partial) {
        if (lastTrip == Long.MIN_VALUE) {
            return -1.0D;
        }
        double e = (double) (gameTime - lastTrip) + partial;
        return e >= 0.0D && e < TRIP_TICKS ? e : -1.0D;
    }

    /** Whether the trip ring is shown (the 40 ticks after {@code lastTrip}). */
    public static boolean tripping(long lastTrip, long gameTime, float partial) {
        return tripElapsed(lastTrip, gameTime, partial) >= 0.0D;
    }

    /** Whether the core is in the white half of the 4 Hz trip strobe; never when still or not tripping. */
    public static boolean tripWhite(long lastTrip, long gameTime, float partial, boolean animate) {
        double e = tripElapsed(lastTrip, gameTime, partial);
        if (e < 0.0D || !animate) {
            return false;
        }
        double phase = e - Math.floor(e / TRIP_STROBE_TICKS) * TRIP_STROBE_TICKS;
        return phase < TRIP_STROBE_TICKS / 2.0D;
    }
}
