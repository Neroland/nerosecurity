package za.co.neroland.nerosecurity.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/** Storm control: leading edge, one summary per window, per-installation windows, bounded tables. Plain JVM. */
class AlarmCoalescerTest {

    private static final long WINDOW = 10_000L;
    private static final long T0 = 1_000_000L;

    private static AlarmCoalescer coalescer() {
        return new AlarmCoalescer(() -> WINDOW, AlarmCoalescer.DEFAULT_MAX_WINDOWS);
    }

    /** 17. Five events in three seconds: one immediate emit, one summary with extra 4 after the window. */
    @Test
    void leadingEdgeThenOneSummary() {
        AlarmCoalescer c = coalescer();
        UUID id = UUID.randomUUID();
        AlarmFiring first = c.offer(id, SecurityEventType.MOTION_DETECTED, SecuritySeverity.WARN, T0);
        assertNotNull(first);
        assertEquals(0, first.extra());
        assertEquals(SecurityEventType.MOTION_DETECTED, first.type());
        for (int i = 1; i <= 4; i++) {
            assertNull(c.offer(id, SecurityEventType.MOTION_DETECTED, SecuritySeverity.WARN, T0 + i * 750L));
        }
        assertTrue(c.drain(T0 + 5_000L).isEmpty(), "nothing between the leading edge and the window end");
        assertTrue(c.drain(T0 + WINDOW - 1).isEmpty());
        List<AlarmCoalescer.Emit> summary = c.drain(T0 + WINDOW);
        assertEquals(1, summary.size());
        assertEquals(id, summary.get(0).installationId());
        assertEquals(4, summary.get(0).firing().extra());
        assertTrue(c.drain(T0 + WINDOW + 1).isEmpty(), "a window yields one summary only");
        assertEquals(0, c.openWindows());
        assertNotNull(c.offer(id, SecurityEventType.MOTION_DETECTED, SecuritySeverity.WARN, T0 + WINDOW + 2),
                "after the window closes the next event is a new leading edge");
    }

    /** 18. WARN opens, CRITICAL inside: no bypass, and the summary carries CRITICAL and its type. */
    @Test
    void severityEscalatesWithinWindow() {
        AlarmCoalescer c = coalescer();
        UUID id = UUID.randomUUID();
        assertNotNull(c.offer(id, SecurityEventType.MOTION_DETECTED, SecuritySeverity.WARN, T0));
        assertNull(c.offer(id, SecurityEventType.LASER_TRIPPED, SecuritySeverity.CRITICAL, T0 + 1_000L),
                "a critical inside a warn window does not bypass storm control");
        AlarmFiring summary = c.drain(T0 + WINDOW).get(0).firing();
        assertEquals(SecuritySeverity.CRITICAL, summary.severity());
        assertEquals(SecurityEventType.LASER_TRIPPED, summary.type());
        assertEquals(Set.of(SecurityEventType.MOTION_DETECTED, SecurityEventType.LASER_TRIPPED), summary.types());
        assertTrue(summary.matches(Set.of(SecurityEventType.LASER_TRIPPED)),
                "an alarm subscribed only to lasers still reacts to the summary");
        assertTrue(summary.matches(Set.of(SecurityEventType.MOTION_DETECTED)));
    }

    /** 19. Windows are per installation. */
    @Test
    void windowsAreIndependentPerInstallation() {
        AlarmCoalescer c = coalescer();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertNotNull(c.offer(a, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0));
        assertNotNull(c.offer(b, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 + 1),
                "another installation's window does not swallow this one's leading edge");
        assertNull(c.offer(a, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 + 2));
        List<AlarmCoalescer.Emit> out = c.drain(T0 + WINDOW + 5);
        assertEquals(1, out.size(), "only the window that folded an event in summarises");
        assertEquals(a, out.get(0).installationId());
    }

    @Test
    void lateEventAfterAnUndrainedWindowStillOwesTheSummary() {
        AlarmCoalescer c = coalescer();
        UUID id = UUID.randomUUID();
        c.offer(id, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0);
        c.offer(id, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 + 1);
        assertNotNull(c.offer(id, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 + WINDOW + 1));
        List<AlarmCoalescer.Emit> out = c.drain(T0 + WINDOW + 2);
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).firing().extra());
    }

    @Test
    void clockSteppingBackwardsClosesTheWindow() {
        AlarmCoalescer c = coalescer();
        UUID id = UUID.randomUUID();
        c.offer(id, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0);
        assertNotNull(c.offer(id, SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 - 60_000L),
                "a clock that went backwards must not keep the alarm folded forever");
    }

    @Test
    void tablesAreBounded() {
        AlarmCoalescer c = new AlarmCoalescer(() -> WINDOW, 3);
        for (int i = 0; i < 3; i++) {
            assertNotNull(c.offer(UUID.randomUUID(), SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0));
        }
        assertNull(c.offer(UUID.randomUUID(), SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 + 1),
                "a full table of live windows drops new installations (storm control)");
        assertEquals(3, c.openWindows());
        assertNotNull(c.offer(UUID.randomUUID(), SecurityEventType.DOOR_DENIED, SecuritySeverity.WARN, T0 + WINDOW),
                "expired windows are pruned when the table is full");
        assertEquals(1, c.openWindows());
    }
}
