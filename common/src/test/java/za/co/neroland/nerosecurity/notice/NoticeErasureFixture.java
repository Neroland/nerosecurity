package za.co.neroland.nerosecurity.notice;

import java.util.UUID;

import za.co.neroland.nerosecurity.data.SecurityData;

/**
 * Test-only seam so the shared erasure conformance tests (other packages) can seed and probe the recording
 * notice's transient per-player memory, store {@code "notices"}.
 */
public final class NoticeErasureFixture {

    private NoticeErasureFixture() {
    }

    /** The {@code "notices"} store as it joins the composite. */
    public static SecurityData.StoreEraser eraser() {
        return RecordingNotice.ERASER;
    }

    /** Seeds: {@code player} has had {@code installation}'s notice. */
    public static void seed(UUID player, UUID installation) {
        RecordingNotice.markNoticed(player, installation);
    }

    /** The probe. */
    public static boolean holds(UUID player) {
        return RecordingNotice.holdsAnyFor(player);
    }

    /** Forgets every player (call from {@code @AfterEach}). */
    public static void reset() {
        RecordingNotice.resetForTests();
    }
}
