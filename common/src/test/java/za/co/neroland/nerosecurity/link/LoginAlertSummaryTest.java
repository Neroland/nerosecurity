package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;

class LoginAlertSummaryTest {

    private static final long NOW = 1_792_000_000_000L;

    private static LinkAlert alert(UUID installation, PushKind kind) {
        return new LinkAlert(PushCopy.alertId(installation, kind), "nerosecurity", kind.severity(),
                "Secret text that must not be echoed", NOW - 1000L, false, 0L);
    }

    @Test
    void countsOnly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<LinkAlert> alerts = List.of(alert(a, PushKind.LASER_TRIPPED), alert(a, PushKind.DOOR_FORCED),
                alert(b, PushKind.POWER_LOST), alert(b, PushKind.MOTION_DETECTED).withAcked(),
                alert(b, PushKind.FIELD_COLLAPSED).withSnoozedUntil(NOW + 60_000L),
                new LinkAlert("nerocolonies.x", "nerocolonies", LinkAlert.Severity.INFO, "other", NOW, false, 0L));
        List<LoginAlertSummary.Counts> sent = new ArrayList<>();
        assertTrue(LoginAlertSummary.deliver(LinkSettings.defaults(), () -> alerts, NOW, sent::add));
        assertEquals(List.of(new LoginAlertSummary.Counts(3, 2)), sent,
                "unread, unsnoozed, this module only; the sink only ever receives two numbers");
        assertNull(LoginAlertSummary.counts(List.of(), NOW));
    }

    @Test
    void skippedWhenAnySwitchIsOff() {
        List<LinkAlert> alerts = List.of(alert(UUID.randomUUID(), PushKind.LASER_TRIPPED));
        List<LoginAlertSummary.Counts> sent = new ArrayList<>();
        LinkSettings d = LinkSettings.defaults();
        assertFalse(LoginAlertSummary.deliver(d.withSwitches(true, true, true, true, false), () -> alerts, NOW, sent::add));
        assertFalse(LoginAlertSummary.deliver(d.withSwitches(true, false, true, true, true), () -> alerts, NOW, sent::add));
        assertFalse(LoginAlertSummary.deliver(d.withSwitches(false, true, true, true, true), () -> alerts, NOW, sent::add));
        assertTrue(sent.isEmpty());
    }

    @Test
    void aThrowingStoreDoesNotEscape() {
        assertFalse(LoginAlertSummary.deliver(LinkSettings.defaults(), () -> {
            throw new IllegalStateException("store down");
        }, NOW, counts -> {
            throw new AssertionError("must not send");
        }));
    }

    @Test
    void pendingCountdownIsForgotten() {
        UUID p = UUID.randomUUID();
        LoginAlertSummary.schedule(p);
        assertTrue(LoginAlertSummary.holdsAnyFor(p));
        LoginAlertSummary.forget(p);
        assertFalse(LoginAlertSummary.holdsAnyFor(p));
    }
}
