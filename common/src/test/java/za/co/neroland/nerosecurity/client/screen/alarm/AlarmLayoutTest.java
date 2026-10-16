package za.co.neroland.nerosecurity.client.screen.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.widget.Status;

/** Check boxes pack left to right and wrap by width; the lock reasons mirror the server's alarm rules. */
class AlarmLayoutTest {

    @Test
    void shortBoxesShareARowAndLongOnesWrap() {
        // widths: 50, 40, 120, 30 in a 100-px column at (10, 20)
        List<Integer> widths = List.of(50, 40, 120, 30);
        AlarmLayout.Packed<Integer> packed = AlarmLayout.pack(widths, Integer::intValue, 10, 20, 100);
        List<AlarmLayout.Placed<Integer>> placed = packed.placed();
        assertEquals(4, placed.size());
        assertEquals(new AlarmLayout.Placed<>(50, 10, 20), placed.get(0));
        assertEquals(new AlarmLayout.Placed<>(40, 10 + 50 + AlarmLayout.GAP, 20), placed.get(1));
        // 120 is wider than the column: it still gets a row of its own
        assertEquals(new AlarmLayout.Placed<>(120, 10, 20 + AlarmLayout.ROW), placed.get(2));
        assertEquals(new AlarmLayout.Placed<>(30, 10, 20 + 2 * AlarmLayout.ROW), placed.get(3));
        assertEquals(20 + 3 * AlarmLayout.ROW, packed.bottom());
    }

    @Test
    void anExactFitStaysOnTheRow() {
        List<Integer> widths = List.of(46, 46);
        AlarmLayout.Packed<Integer> packed = AlarmLayout.pack(widths, Integer::intValue, 0, 0, 46 + AlarmLayout.GAP + 46);
        assertEquals(0, packed.placed().get(1).y());
        assertEquals(AlarmLayout.ROW, packed.bottom());
    }

    @Test
    void emptyListTakesNoHeight() {
        AlarmLayout.Packed<Integer> packed = AlarmLayout.pack(List.of(), Integer::intValue, 0, 40, 100);
        assertTrue(packed.placed().isEmpty());
        assertEquals(40, packed.bottom());
    }

    @Test
    void armedOrSilencedWithMinutesRoundedUp() {
        assertEquals(new AlarmLayout.StateRow(Status.OK, "alarm.nerosecurity.armed", 0), AlarmLayout.state(0));
        assertEquals(new AlarmLayout.StateRow(Status.WARN, "alarm.nerosecurity.silenced_until", 1), AlarmLayout.state(1));
        assertEquals(new AlarmLayout.StateRow(Status.WARN, "alarm.nerosecurity.silenced_until", 1), AlarmLayout.state(60));
        assertEquals(new AlarmLayout.StateRow(Status.WARN, "alarm.nerosecurity.silenced_until", 2), AlarmLayout.state(61));
        assertEquals(7, AlarmLayout.state(7 * 60).minutesLeft());
    }

    @Test
    void lockdownBoxReasons() {
        assertEquals("alarm.nerosecurity.lockdown.server_disabled", AlarmLayout.lockdownWhy(true, false));
        assertEquals("alarm.nerosecurity.lockdown.owner_only", AlarmLayout.lockdownWhy(false, true));
        assertNull(AlarmLayout.lockdownWhy(true, true));
    }

    @Test
    void otherBoxesLockForNonOwnersOnlyWhileTheAlarmCanLockDown() {
        assertEquals("alarm.nerosecurity.lockdown.owner_only_armed", AlarmLayout.othersWhy(false, true));
        assertNull(AlarmLayout.othersWhy(true, true));
        assertNull(AlarmLayout.othersWhy(false, false));
    }

    @Test
    void silenceNeedsOfficer() {
        assertTrue(AlarmLayout.maySilence(AccessLevel.OFFICER, false));
        assertTrue(AlarmLayout.maySilence(AccessLevel.OWNER, false));
        assertTrue(AlarmLayout.maySilence(AccessLevel.MEMBER, true));
        assertFalse(AlarmLayout.maySilence(AccessLevel.MEMBER, false));
        assertFalse(AlarmLayout.maySilence(null, false));
    }
}
