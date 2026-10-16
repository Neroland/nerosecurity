package za.co.neroland.nerosecurity.block.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Classification;

/** The camera's pure decisions: member-logging rule, cooldown, armed, range and the staggered cadence. */
class CameraRulesTest {

    @Test
    void permittedMeansInvisibleUnlessMembersAreLogged() {
        assertEquals(SecurityEventType.MOTION_DETECTED, CameraRules.eventFor(Classification.INTRUDER, false));
        assertEquals(SecurityEventType.MOTION_DETECTED, CameraRules.eventFor(Classification.INTRUDER, true));
        assertNull(CameraRules.eventFor(Classification.PERMITTED, false), "members are not logged by default");
        assertEquals(SecurityEventType.CAMERA_MEMBER_SEEN, CameraRules.eventFor(Classification.PERMITTED, true));
        assertNull(CameraRules.eventFor(Classification.IGNORE, false));
        assertNull(CameraRules.eventFor(Classification.IGNORE, true));
        assertNull(CameraRules.eventFor(null, true));
    }

    @Test
    void oneEventPerEntityPerCooldown() {
        assertTrue(CameraRules.cooldownElapsed(null, 1000L, 200), "never seen");
        assertFalse(CameraRules.cooldownElapsed(1000L, 1000L, 200), "same tick");
        assertFalse(CameraRules.cooldownElapsed(1000L, 1199L, 200), "one tick short");
        assertTrue(CameraRules.cooldownElapsed(1000L, 1200L, 200), "exactly elapsed");
        assertTrue(CameraRules.cooldownElapsed(1000L, 5000L, 200));
    }

    @Test
    void failsDarkWhenUnlinkedUnpoweredOrDisabled() {
        assertTrue(CameraRules.armed(true, true, 2, 2));
        assertFalse(CameraRules.armed(false, true, 4000, 2), "unlinked");
        assertFalse(CameraRules.armed(true, false, 4000, 2), "cameras disabled");
        assertFalse(CameraRules.armed(true, true, 1, 2), "less than one tick's draw");
        assertTrue(CameraRules.armed(true, true, 0, 0), "free cameras need no buffer");
    }

    @Test
    void rangeModulesAddFourUpToTheCap() {
        assertEquals(16, CameraRules.range(16, 32, 0));
        assertEquals(20, CameraRules.range(16, 32, 1));
        assertEquals(32, CameraRules.range(16, 32, 4));
        assertEquals(32, CameraRules.range(16, 32, 64), "hard cap after modules");
        assertEquals(8, CameraRules.range(16, 8, 0), "cap below base");
        assertEquals(16, CameraRules.range(16, 32, -3), "negative module count ignored");
    }

    @Test
    void scansAreStaggeredOncePerInterval() {
        int interval = 10;
        for (long salt : new long[] {0L, 3L, -7L, Long.MAX_VALUE / 3}) {
            int due = 0;
            for (long t = 1000; t < 1000 + interval; t++) {
                if (CameraRules.scanDue(t, salt, interval)) {
                    due++;
                }
            }
            assertEquals(1, due, "exactly one scan per interval for salt " + salt);
        }
        boolean differ = false;
        for (long t = 0; t < interval; t++) {
            differ |= CameraRules.scanDue(t, 0L, interval) != CameraRules.scanDue(t, 3L, interval);
        }
        assertTrue(differ, "neighbouring cameras do not scan on the same tick");
    }
}
