package za.co.neroland.nerosecurity.client.render.field;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.anim.Blink;
import za.co.neroland.nerosecurity.field.FieldState;

/** The emitter core's pure maths: spin speed from the synced state and draw bucket, angle, tint. Unit-tested. */
public final class EmitterCoreMath {

    /** Core spin (°/s) by draw bucket 0..4 while projecting. */
    private static final float[] PROJECTING_DPS = {0f, 60f, 120f, 240f, 360f};
    /** A browning-out core turns slowly. */
    public static final float BROWNOUT_DPS = 30f;
    /** Every speed above is a whole number of turns in this many ticks, so the angle is continuous across the wrap. */
    static final long WRAP_TICKS = 7200L;

    private EmitterCoreMath() {
    }

    /** Degrees per second: IDLE 0, PROJECTING 0/60/120/240/360 by bucket, BROWNOUT 30, OVERLOADED 0. */
    public static float degreesPerSecond(FieldState state, int bucket) {
        if (state == FieldState.PROJECTING) {
            return PROJECTING_DPS[Math.max(0, Math.min(PROJECTING_DPS.length - 1, bucket))];
        }
        return state == FieldState.BROWNOUT ? BROWNOUT_DPS : 0f;
    }

    /** The core angle in degrees for a speed (°/s) at a game time; 0 when still. */
    public static float angle(float degreesPerSecond, long gameTime, float partial, boolean animate) {
        if (!animate || degreesPerSecond == 0f) {
            return 0f;
        }
        double t = Math.floorMod(gameTime, WRAP_TICKS) + (double) partial;
        return (float) ((degreesPerSecond / 20.0D * t) % 360.0D);
    }

    /** Tint {@code 0xRRGGBB}: FIELD projecting, LOCK projecting in lockdown, WARN blinking in brownout, else dormant. */
    public static int tint(FieldState state, boolean lockdown, long gameTime, boolean animate) {
        if (state == FieldState.PROJECTING) {
            return lockdown ? SecurityColours.LOCK : SecurityColours.FIELD;
        }
        if (state == FieldState.BROWNOUT) {
            return !animate || Blink.warning(gameTime) ? SecurityColours.WARN : SecurityColours.DORMANT;
        }
        return SecurityColours.DORMANT;
    }

    /** Whether the core reads as lit (full-bright) rather than taking the block's light. */
    public static boolean lit(FieldState state, int tint) {
        return state == FieldState.PROJECTING || (state == FieldState.BROWNOUT && tint == SecurityColours.WARN);
    }

    /**
     * The deny flash over the state tint: while {@code flashing}, {@code LOCK} in the lit 8 Hz phase and dormant-dark
     * in the other (with animations off, {@code LOCK} steady for the whole flash); otherwise {@code stateTint}.
     */
    public static int denyTint(int stateTint, boolean flashing, boolean flashOn, boolean animate) {
        if (!flashing) {
            return stateTint;
        }
        return !animate || flashOn ? SecurityColours.LOCK : SecurityColours.DORMANT;
    }
}
