package za.co.neroland.nerosecurity.client.screen.alarm;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSnapshot;

/**
 * The last alarm settings snapshot the server sent, for the open settings screen to draw. Display only;
 * nothing on the client decides anything from it. Cleared when the screen closes and when the client
 * leaves a world or server.
 *
 * <p>Plain data with no client-only imports, so the payload declaration can reference it from common
 * code. Written and read on the client thread.
 */
public final class ClientAlarmState {

    private static @Nullable AlarmSnapshot current;
    private static long version;

    private ClientAlarmState() {
    }

    public static void accept(AlarmSnapshot snapshot) {
        current = snapshot;
        version++;
    }

    public static @Nullable AlarmSnapshot current() {
        return current;
    }

    /** Increments on every snapshot, so the screen can tell a fresh answer arrived. */
    public static long version() {
        return version;
    }

    public static void clear() {
        current = null;
        version++;
    }
}
