package za.co.neroland.nerosecurity.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/** The visual refresh cadence and the two derived device facts. */
class VisualStateTest {

    @Test
    void everyDeviceRefreshesExactlyOncePerPeriod() {
        long[] keys = {BlockPos.ZERO.asLong(), new BlockPos(12, 64, -3).asLong(), new BlockPos(-30000, -60, 29999).asLong(),
                Long.MIN_VALUE, Long.MAX_VALUE};
        for (long key : keys) {
            int due = 0;
            for (long t = 1000; t < 1000 + VisualState.REFRESH_TICKS; t++) {
                if (VisualState.refreshDue(t, key)) {
                    due++;
                }
            }
            assertEquals(1, due, "one refresh per period for " + key);
        }
    }

    @Test
    void neighboursAreSpreadAcrossTicks() {
        Set<Long> phases = new HashSet<>();
        for (int x = 0; x < 64; x++) {
            long key = new BlockPos(x, 64, 0).asLong();
            for (long t = 0; t < VisualState.REFRESH_TICKS; t++) {
                if (VisualState.refreshDue(t, key)) {
                    phases.add(t);
                }
            }
        }
        assertTrue(phases.size() > 8, "a row of devices does not all refresh on one tick");
    }

    @Test
    void recordingAndLockdownNeedALink() {
        assertTrue(VisualState.recording(true, true));
        assertFalse(VisualState.recording(false, true), "an unlinked device never claims to record");
        assertFalse(VisualState.recording(true, false), "audit log off: nothing is recorded");
        assertTrue(VisualState.lockdownShown(true, true, true));
        assertFalse(VisualState.lockdownShown(false, true, true));
        assertFalse(VisualState.lockdownShown(true, false, true), "lockdown disabled on the server: not shown");
        assertFalse(VisualState.lockdownShown(true, true, false));
    }
}
