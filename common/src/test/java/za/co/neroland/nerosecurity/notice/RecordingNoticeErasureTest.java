package za.co.neroland.nerosecurity.notice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerosecurity.data.SecurityData;

/** The recording notice's transient per-player memory is cleared by the {@code "notices"} eraser. Plain JVM. */
class RecordingNoticeErasureTest {

    @AfterEach
    void cleanup() {
        SecurityData.removeStore(RecordingNotice.ERASER);
        PlayerDataErasure.unregister(SecurityData.eraser());
        RecordingNotice.resetForTests();
    }

    @Test
    void erasureForgetsWhichNoticesThePlayerHad() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID installation = UUID.randomUUID();
        RecordingNotice.markNoticed(subject, installation);
        RecordingNotice.markNoticed(bystander, installation);
        SecurityData.addStore(RecordingNotice.ERASER);
        PlayerDataErasure.register(SecurityData.eraser());

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:notices", RecordingNotice::holdsAnyFor)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(RecordingNotice.holdsAnyFor(subject));
        assertTrue(RecordingNotice.holdsAnyFor(bystander), "erasure is per-subject");
    }
}
