package za.co.neroland.nerosecurity.block.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The camera's synced look is coarse and rate-limited, and its per-scan classification is bounded. Plain JVM. */
class CameraLookAndBudgetTest {

    @Test
    void lookIsQuantisedToFifteenDegreeSteps() {
        assertEquals(15.0F, CameraRules.LOOK_STEP_DEGREES);
        assertEquals(0.0F, CameraRules.quantiseAngle(7.4F));
        assertEquals(15.0F, CameraRules.quantiseAngle(7.6F));
        assertEquals(-45.0F, CameraRules.quantiseAngle(-44.0F));
        assertEquals(180.0F, CameraRules.quantiseAngle(179.0F));
        assertEquals(180.0F, CameraRules.quantiseAngle(-179.0F), "wrapped into (-180, 180]");
        assertEquals(0.0F, CameraRules.quantiseAngle(Float.NaN));
        for (float a = -720.0F; a <= 720.0F; a += 0.37F) {
            float q = CameraRules.quantiseAngle(a);
            assertEquals(0.0F, Math.abs(q % 15.0F), "step of " + a);
            assertTrue(q > -180.0F && q <= 180.0F, "range of " + a);
        }
    }

    @Test
    void lookSyncsAtMostEveryTwoSecondsAndDecays() {
        assertEquals(40, CameraRules.LOOK_SYNC_MIN_TICKS);
        assertTrue(CameraRules.lookSyncDue(Long.MIN_VALUE, 5L), "the first sync goes out");
        assertFalse(CameraRules.lookSyncDue(100L, 139L));
        assertTrue(CameraRules.lookSyncDue(100L, 140L));
        assertTrue(CameraRules.lookSyncDue(100L, 50L), "a clock that steps back never blocks a sync");
        assertFalse(CameraRules.lookExpired(100L, 100L + CameraRules.LOOK_HOLD_TICKS - 1));
        assertTrue(CameraRules.lookExpired(100L, 100L + CameraRules.LOOK_HOLD_TICKS), "decays back to the pan");
    }

    @Test
    void classificationIsCappedAndTheWindowRotates() {
        assertEquals(32, CameraRules.MAX_CLASSIFIED_PER_SCAN);
        List<Integer> few = List.of(1, 2, 3);
        assertEquals(few, CameraRules.classifyWindow(few, 7, 32), "a small crowd is classified whole");

        List<Integer> crowd = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            crowd.add(i);
        }
        Set<Integer> covered = new HashSet<>();
        int offset = 0;
        for (int scan = 0; scan < 7; scan++) {
            List<Integer> window = CameraRules.classifyWindow(crowd, offset, CameraRules.MAX_CLASSIFIED_PER_SCAN);
            assertEquals(CameraRules.MAX_CLASSIFIED_PER_SCAN, window.size(), "at most 32 resolver calls per scan");
            covered.addAll(window);
            offset += window.size();
        }
        assertEquals(200, covered.size(), "the rotating window reaches every candidate over a few scans");
        assertEquals(List.of(199, 0), CameraRules.classifyWindow(crowd, 199, 2), "wraps around");
        assertTrue(CameraRules.classifyWindow(List.of(), 3, 32).isEmpty());
    }
}
