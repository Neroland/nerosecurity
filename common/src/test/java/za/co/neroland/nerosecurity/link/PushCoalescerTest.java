package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;

class PushCoalescerTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID inst = UUID.randomUUID();
    private final UUID inst2 = UUID.randomUUID();

    private static PushCoalescer coalescer() {
        return new PushCoalescer(() -> 60_000L, PushCoalescer.DEFAULT_MAX_WINDOWS);
    }

    @Test
    void leadingEdgeThenHoldsThenOneSummary() {
        PushCoalescer c = coalescer();
        PushCoalescer.Offer first = c.offer(owner, inst, PushKind.MOTION_DETECTED, LinkAlert.Severity.WARN, 0L);
        assertTrue(first.emitted());
        assertEquals(0, first.now().extra());
        assertFalse(c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 1_000L).emitted());
        assertFalse(c.offer(owner, inst, PushKind.POWER_LOST, LinkAlert.Severity.WARN, 2_000L).emitted());
        assertTrue(c.drain(59_000L).isEmpty(), "window still open");
        List<PushCoalescer.Emit> due = c.drain(60_000L);
        assertEquals(1, due.size());
        PushCoalescer.Emit summary = due.get(0);
        assertEquals(2, summary.extra());
        assertEquals(PushKind.MOTION_DETECTED, summary.kind(), "the window's first kind");
        assertEquals(LinkAlert.Severity.CRITICAL, summary.severity(), "the highest severity counted");
        assertEquals(0, c.size());
        assertTrue(c.offer(owner, inst, PushKind.MOTION_DETECTED, LinkAlert.Severity.WARN, 61_000L).emitted());
    }

    @Test
    void aQuietWindowDrainsToNothing() {
        PushCoalescer c = coalescer();
        c.offer(owner, inst, PushKind.DOOR_FORCED, LinkAlert.Severity.CRITICAL, 0L);
        assertTrue(c.drain(120_000L).isEmpty());
        assertEquals(0, c.size());
    }

    @Test
    void lockdownBypassesAnOpenWindowAndLeavesItAlone() {
        PushCoalescer c = coalescer();
        c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L);
        PushCoalescer.Offer lockdown = c.offer(owner, inst, PushKind.LOCKDOWN_ENTERED, LinkAlert.Severity.CRITICAL, 500L);
        assertTrue(lockdown.emitted());
        assertTrue(c.drain(60_000L).isEmpty(), "the bypass was not counted into the window");
    }

    @Test
    void windowsArePerRecipientAndInstallation() {
        PushCoalescer c = coalescer();
        assertTrue(c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L).emitted());
        assertTrue(c.offer(other, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L).emitted());
        assertTrue(c.offer(owner, inst2, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L).emitted());
        assertFalse(c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 1L).emitted());
    }

    @Test
    void anExpiredWindowWithPendingCountIsFlushedOnTheNextOffer() {
        PushCoalescer c = coalescer();
        c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L);
        c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 10L);
        PushCoalescer.Offer next = c.offer(owner, inst, PushKind.POWER_LOST, LinkAlert.Severity.WARN, 70_000L);
        assertTrue(next.emitted());
        assertNotNull(next.flushed());
        assertEquals(1, next.flushed().extra());
    }

    @Test
    void boundedByEvictingTheOldest() {
        PushCoalescer c = new PushCoalescer(() -> 60_000L, 3);
        for (int i = 0; i < 5; i++) {
            c.offer(UUID.randomUUID(), inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, i);
        }
        assertEquals(3, c.size());
    }

    @Test
    void forgetDropsOnlyThatRecipient() {
        PushCoalescer c = coalescer();
        c.offer(owner, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L);
        c.offer(other, inst, PushKind.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, 0L);
        c.forget(owner);
        assertFalse(c.holdsAnyFor(owner));
        assertTrue(c.holdsAnyFor(other));
    }
}
