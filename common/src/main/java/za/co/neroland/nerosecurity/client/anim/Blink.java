package za.co.neroland.nerosecurity.client.anim;

/**
 * The four-blink grammar every NeroSecurity device shares. A player learns four patterns:
 * <ul>
 *   <li><b>steady</b>: linked and healthy (no method: just draw it);</li>
 *   <li><b>recording</b>: 1 Hz, 15 ticks on / 5 off;</li>
 *   <li><b>warning</b> (unpowered, unpaired, brownout): 2 Hz, 5 on / 5 off;</li>
 *   <li><b>lockdown</b>: 4 Hz strobe, 3 on / 2 off in a 5-tick period.</li>
 * </ul>
 * Plus the deny flash (8 Hz for the 10 ticks after a refusal), driven by {@code ClientDeviceFeedback}.
 * Pure functions of game time; negative times are handled.
 */
public final class Blink {

    public static final int RECORDING_PERIOD = 20;
    public static final int RECORDING_ON = 15;
    public static final int WARNING_PERIOD = 10;
    public static final int WARNING_ON = 5;
    public static final int LOCKDOWN_PERIOD = 5;
    public static final int LOCKDOWN_ON = 3;
    /** Deny flash period in ticks (8 Hz). */
    public static final float DENY_PERIOD = 2.5f;

    private Blink() {
    }

    public static boolean recording(long gameTime) {
        return Math.floorMod(gameTime, RECORDING_PERIOD) < RECORDING_ON;
    }

    public static boolean warning(long gameTime) {
        return Math.floorMod(gameTime, WARNING_PERIOD) < WARNING_ON;
    }

    public static boolean lockdown(long gameTime) {
        return Math.floorMod(gameTime, LOCKDOWN_PERIOD) < LOCKDOWN_ON;
    }

    /** The 8 Hz deny flash phase at {@code gameTime + partial}: on for the first half of each 2.5-tick period. */
    public static boolean deny(long gameTime, float partial) {
        double t = gameTime + (double) partial;
        double phase = t - Math.floor(t / DENY_PERIOD) * DENY_PERIOD;
        return phase < DENY_PERIOD / 2.0;
    }
}
