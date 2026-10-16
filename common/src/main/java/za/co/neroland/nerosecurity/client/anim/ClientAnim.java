package za.co.neroland.nerosecurity.client.anim;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;

/**
 * Client-side timeline for a block entity animation anchored on a synced snapshot (the door's
 * {@code OpenTicks / Opening / SyncTime / Duration}). FPS-independent; needs no per-tick packets.
 *
 * <pre>raw = clamp01((syncedTicks + direction * (clientGameTime - syncedGameTime + partial)) / duration)</pre>
 *
 * When {@code renderAnimationsEnabled} is off the integer snapshot {@code syncedTicks / duration} is returned (still frame).
 */
public final class ClientAnim {

    private ClientAnim() {
    }

    /**
     * Linear progress 0..1 (apply an {@link Easing} curve afterwards).
     *
     * @param syncedTicks    ticks of progress in the snapshot (0..duration)
     * @param direction      +1 opening, -1 closing, 0 frozen
     * @param syncedGameTime server game time the snapshot was taken at
     * @param clientGameTime the client level's current game time
     * @param partial        partial tick 0..1
     * @param duration       full travel in ticks (&lt;= 0 → treated as already at an end)
     */
    public static float progress(int syncedTicks, int direction, long syncedGameTime, long clientGameTime,
            float partial, int duration) {
        return progress(syncedTicks, direction, syncedGameTime, clientGameTime, partial, duration, animationsEnabled());
    }

    /** Pure overload with the animation switch explicit (tests, and callers that already read it). */
    public static float progress(int syncedTicks, int direction, long syncedGameTime, long clientGameTime,
            float partial, int duration, boolean animate) {
        if (duration <= 0) {
            return direction >= 0 && syncedTicks > 0 ? 1f : (direction > 0 ? 1f : 0f);
        }
        if (!animate) {
            return Easing.clamp01((float) syncedTicks / duration);
        }
        int dir = Integer.signum(direction);
        // Elapsed is clamped to >= 0 (a client clock briefly behind the server never runs time backwards)
        // and capped so a long-stale anchor cannot overflow float precision.
        double elapsed = Math.max(0.0, Math.min((double) (clientGameTime - syncedGameTime) + partial, 1_000_000.0));
        double raw = (syncedTicks + dir * elapsed) / duration;
        return (float) Easing.clamp01(raw);
    }

    /** The client-local animation switch; true when the config is unreadable (e.g. in a pure test). */
    public static boolean animationsEnabled() {
        try {
            return NeroSecurityConfig.renderAnimationsEnabled();
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }
}
