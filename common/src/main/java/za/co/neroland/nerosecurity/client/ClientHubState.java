package za.co.neroland.nerosecurity.client;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.HubPayloads.HubSnapshot;

/**
 * The client's only security mirror: the last hub snapshot the server sent, for the open hub screen to
 * draw. Display only; nothing on the client decides access from it. Cleared when the client leaves a
 * world or server, so one session's data never shows in the next.
 *
 * <p>Plain data with no client-only imports, so the payload handler declaration can reference it from
 * common code. Written and read on the client thread.
 */
public final class ClientHubState {

    private static @Nullable HubSnapshot current;
    private static long version;

    private ClientHubState() {
    }

    public static void accept(HubSnapshot snapshot) {
        current = snapshot;
        version++;
    }

    public static @Nullable HubSnapshot current() {
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
