package za.co.neroland.nerosecurity.block.camera;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Classification;

/**
 * The camera's decisions as pure rules (plain-JVM tested): range, armed, scan cadence, cooldown and
 * which event (if any) a classified, visible entity produces.
 */
public final class CameraRules {

    /** Blocks of range each fitted range module adds. */
    public static final int RANGE_PER_MODULE = 4;

    /** Above this many entries the cooldown map drops the expired ones before adding another. */
    public static final int COOLDOWN_PRUNE_SIZE = 64;

    /**
     * Upper bound on cone candidates classified (resolver calls) per scan. The window rotates over the
     * distance-sorted cone survivors, as the laser's does, so a crowd is still covered across scans.
     */
    public static final int MAX_CLASSIFIED_PER_SCAN = 32;

    /** The synced look direction is rounded to this many degrees (a coarse turn, not a tracker). */
    public static final float LOOK_STEP_DEGREES = 15.0F;
    /** At most one look-direction sync per this many ticks (2 s). */
    public static final int LOOK_SYNC_MIN_TICKS = 40;
    /** Without a new detection for this many ticks the camera's look decays back to the pan. */
    public static final int LOOK_HOLD_TICKS = 60;

    private CameraRules() {
    }

    /** {@code min(max, base + 4 x modules)}, never below 1. */
    public static int range(int base, int max, int rangeModules) {
        long wanted = base + RANGE_PER_MODULE * (long) Math.max(0, rangeModules);
        return (int) Math.max(1L, Math.min(max, wanted));
    }

    /**
     * Whether a camera can sense right now: linked, cameras enabled on the server, and holding at least
     * one tick's draw. Anything else leaves it dark (fail dark, never open).
     */
    public static boolean armed(boolean linked, boolean camerasEnabled, long energy, int powerPerTick) {
        return linked && camerasEnabled && energy >= Math.max(0, powerPerTick);
    }

    /**
     * The staggered cadence: a camera scans when {@code (gameTime + posSalt) mod interval == 0}, so
     * cameras placed together spread their scans over the interval instead of all scanning on one tick.
     */
    public static boolean scanDue(long gameTime, long posSalt, int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        return Math.floorMod(gameTime + posSalt, (long) interval) == 0L;
    }

    /** Whether an entity last reported at {@code lastReported} (null = never) may be reported again at {@code now}. */
    public static boolean cooldownElapsed(@Nullable Long lastReported, long now, int cooldownTicks) {
        return lastReported == null || now - lastReported >= Math.max(0, cooldownTicks);
    }

    /**
     * The rotating classification window: all of {@code sorted} when it holds at most {@code cap},
     * otherwise {@code cap} consecutive entries starting at {@code offset} (mod size), wrapping.
     */
    public static <T> List<T> classifyWindow(List<T> sorted, int offset, int cap) {
        int size = sorted.size();
        int limit = Math.max(0, Math.min(size, cap));
        if (limit == size) {
            return List.copyOf(sorted);
        }
        int start = Math.floorMod(offset, size);
        List<T> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(sorted.get((start + i) % size));
        }
        return out;
    }

    /** {@code degrees} rounded to the nearest {@link #LOOK_STEP_DEGREES} step, wrapped into (-180, 180]. */
    public static float quantiseAngle(float degrees) {
        if (!Float.isFinite(degrees)) {
            return 0.0F;
        }
        float stepped = Math.round(degrees / LOOK_STEP_DEGREES) * LOOK_STEP_DEGREES;
        float wrapped = stepped % 360.0F;
        if (wrapped > 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped <= -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped == -0.0F ? 0.0F : wrapped;
    }

    /** Whether a look sync may go out now (last one at {@code lastSyncAt}; {@code Long.MIN_VALUE} = never). */
    public static boolean lookSyncDue(long lastSyncAt, long now) {
        return lastSyncAt == Long.MIN_VALUE || now < lastSyncAt || now - lastSyncAt >= LOOK_SYNC_MIN_TICKS;
    }

    /** Whether a look last refreshed at {@code lastSeenAt} has decayed back to the pan. */
    public static boolean lookExpired(long lastSeenAt, long now) {
        return now < lastSeenAt || now - lastSeenAt >= LOOK_HOLD_TICKS;
    }

    /**
     * The event a visible, classified entity produces: an intruder raises {@code MOTION_DETECTED}; a
     * permitted player or owned animal raises {@code CAMERA_MEMBER_SEEN} only when the server opted in to
     * logging members ({@code privacyCameraLogsMembers}); everything else produces nothing (no event, no
     * cooldown entry).
     */
    public static @Nullable SecurityEventType eventFor(Classification classification, boolean logsMembers) {
        if (classification == null) {
            return null;
        }
        return switch (classification) {
            case INTRUDER -> SecurityEventType.MOTION_DETECTED;
            case PERMITTED -> logsMembers ? SecurityEventType.CAMERA_MEMBER_SEEN : null;
            case IGNORE -> null;
        };
    }
}
