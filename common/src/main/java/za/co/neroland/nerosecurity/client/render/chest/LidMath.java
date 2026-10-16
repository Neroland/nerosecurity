package za.co.neroland.nerosecurity.client.render.chest;

import za.co.neroland.nerosecurity.client.anim.Easing;

/**
 * Pure lid timeline for the Secure Chest: a linear 0..1 progress that runs at one tenth per tick toward the synced
 * open flag from the progress it had when the flag last changed, so a lid reversing mid-swing never jumps. The
 * angle is {@code 90 x easeOutCubic(progress)} degrees.
 */
public final class LidMath {

    /** Ticks for a full swing (the vanilla chest's 10). */
    public static final float SWING_TICKS = 10f;
    /** Fully open, in degrees. */
    public static final float OPEN_DEGREES = 90f;

    private LidMath() {
    }

    /** Progress {@code elapsedTicks} after an anchor at {@code anchorProgress}, heading open or shut. */
    public static float progress(float anchorProgress, boolean open, double elapsedTicks) {
        double e = Math.max(0.0, elapsedTicks);
        double p = anchorProgress + (open ? e : -e) / SWING_TICKS;
        return (float) Easing.clamp01(p);
    }

    /** Lid angle in degrees for a linear progress. */
    public static float angle(float progress) {
        return OPEN_DEGREES * Easing.easeOutCubic(progress);
    }

    /** Hinge line (model pixels, authored facing north): the back top edge of the body. */
    public static final float HINGE_Y = 10f;
    public static final float HINGE_Z = 15f;

    /**
     * The centre of a lid-attached box after the lid opens {@code degrees} (model pixels, authored facing north),
     * rotating about the X axis through the hinge line. Positive degrees lift the front (z = 0 side) up and back.
     * Same rotation sense as {@code SecurityGeometry.rotate} about X, which the box corners use.
     */
    public static float[] hingedCentre(float cx, float cy, float cz, float degrees) {
        double a = Math.toRadians(degrees);
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        double y = cy - HINGE_Y;
        double z = cz - HINGE_Z;
        return new float[] {cx, (float) (HINGE_Y + y * ca - z * sa), (float) (HINGE_Z + y * sa + z * ca)};
    }

    /** A per-chest anchor (render thread only). */
    public static final class Anchor {
        public boolean open;
        public double time;
        public float progress;

        public Anchor(boolean open, double time) {
            this.open = open;
            this.time = time;
            this.progress = open ? 1f : 0f;
        }

        /** Progress at {@code now}, re-anchoring first when the open flag changed. */
        public float at(boolean openNow, double now) {
            if (openNow != open) {
                progress = LidMath.progress(progress, open, now - time);
                open = openNow;
                time = now;
            }
            return LidMath.progress(progress, open, now - time);
        }
    }
}
