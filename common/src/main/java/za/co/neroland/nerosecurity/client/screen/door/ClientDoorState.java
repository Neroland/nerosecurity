package za.co.neroland.nerosecurity.client.screen.door;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorSnapshot;

/**
 * The last door snapshot the server sent, for the open door settings screen to draw. Display only;
 * nothing on the client decides access from it. Cleared when the screen closes and when the client
 * leaves a world or server.
 *
 * <p>Plain data with no client-only imports, so the payload declaration can reference it from common
 * code. Written and read on the client thread.
 */
public final class ClientDoorState {

    private static @Nullable DoorSnapshot current;
    private static long version;

    private ClientDoorState() {
    }

    public static void accept(DoorSnapshot snapshot) {
        current = snapshot;
        version++;
    }

    public static @Nullable DoorSnapshot current() {
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
