package za.co.neroland.nerosecurity.client;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.menu.HubPayloads.HubSnapshot;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacySnapshot;

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
    private static @Nullable PrivacySnapshot privacy;
    private static long privacyVersion;

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

    /** The Privacy tab's last answer (counts and settings only). */
    public static void acceptPrivacy(PrivacySnapshot snapshot) {
        privacy = snapshot;
        privacyVersion++;
    }

    public static @Nullable PrivacySnapshot privacy() {
        return privacy;
    }

    /** Increments on every privacy snapshot. */
    public static long privacyVersion() {
        return privacyVersion;
    }

    public static void clear() {
        current = null;
        version++;
        privacy = null;
        privacyVersion++;
    }
}
