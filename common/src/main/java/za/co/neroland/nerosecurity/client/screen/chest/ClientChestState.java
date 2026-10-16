package za.co.neroland.nerosecurity.client.screen.chest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestSnapshot;

/**
 * The last secure chest settings snapshot the server sent, for the open settings screen to draw.
 * Display only; nothing on the client decides access from it. Cleared when the screen closes and when
 * the client leaves a world or server.
 *
 * <p>Plain data with no client-only imports, so the payload declaration can reference it from common
 * code. Written and read on the client thread.
 */
public final class ClientChestState {

    private static @Nullable ChestSnapshot current;
    private static long version;

    private ClientChestState() {
    }

    public static void accept(ChestSnapshot snapshot) {
        current = snapshot;
        version++;
    }

    public static @Nullable ChestSnapshot current() {
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
