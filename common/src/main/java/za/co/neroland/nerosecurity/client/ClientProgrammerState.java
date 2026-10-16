package za.co.neroland.nerosecurity.client;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerSnapshot;

/**
 * The last programmer snapshot the server sent, for the open programmer screen to draw. Display only;
 * nothing on the client decides anything from it. Cleared when the screen closes and when the client leaves
 * a world or server.
 *
 * <p>Plain data with no client-only imports, so the payload declaration can reference it from common code.
 * Written and read on the client thread.
 */
public final class ClientProgrammerState {

    private static @Nullable ProgrammerSnapshot current;
    private static long version;

    private ClientProgrammerState() {
    }

    public static void accept(ProgrammerSnapshot snapshot) {
        current = snapshot;
        version++;
    }

    public static @Nullable ProgrammerSnapshot current() {
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
