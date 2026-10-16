package za.co.neroland.nerosecurity.client.screen.monitor;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot;

/**
 * The last monitor snapshot the server sent, for the open monitor screen to draw, plus when it arrived
 * (so the "minutes ago" text keeps ageing between refreshes). Display only. Cleared when the screen
 * closes and when the client leaves a world or server.
 *
 * <p>Plain data with no client-only imports, so the payload declaration can reference it from common
 * code. Written and read on the client thread.
 */
public final class ClientMonitorState {

    private static @Nullable MonitorSnapshot current;
    private static long receivedAt;
    private static long version;

    private ClientMonitorState() {
    }

    public static void accept(MonitorSnapshot snapshot) {
        current = snapshot;
        receivedAt = System.currentTimeMillis();
        version++;
    }

    public static @Nullable MonitorSnapshot current() {
        return current;
    }

    /** Epoch ms the current snapshot arrived (client clock). */
    public static long receivedAt() {
        return receivedAt;
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
