package za.co.neroland.nerosecurity.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Delivery, filtering, unsubscription and listener isolation on the security event bus. */
class SecurityEventBusTest {

    @AfterEach
    void reset() {
        SecurityEventBus.reset();
    }

    private static SecurityEvent event(UUID installation) {
        return SecurityEvent.of(SecurityEventType.LOCKDOWN_ENTERED, installation, null, null, 0L);
    }

    @Test
    void aThrowingListenerDoesNotStopTheNext() {
        List<SecurityEvent> seen = new ArrayList<>();
        SecurityEventBus.subscribe(e -> {
            throw new IllegalStateException("deliberate test failure, safe to ignore");
        });
        SecurityEventBus.subscribe(seen::add);
        SecurityEvent sent = event(UUID.randomUUID());
        assertDoesNotThrow(() -> SecurityEventBus.publish(sent));
        assertEquals(List.of(sent), seen);
        // The failing listener is never detached: a second publish still reaches both.
        SecurityEventBus.publish(sent);
        assertEquals(2, seen.size());
    }

    @Test
    void perInstallationFilter() {
        UUID mine = UUID.randomUUID();
        List<SecurityEvent> seen = new ArrayList<>();
        SecurityEventBus.subscribe(mine, seen::add);
        SecurityEventBus.publish(event(UUID.randomUUID()));
        SecurityEventBus.publish(event(mine));
        assertEquals(1, seen.size());
        assertEquals(mine, seen.get(0).installationId());
    }

    @Test
    void closingASubscriptionStopsDelivery() {
        List<SecurityEvent> seen = new ArrayList<>();
        SecurityEventBus.Subscription subscription = SecurityEventBus.subscribe(seen::add);
        SecurityEventBus.publish(event(UUID.randomUUID()));
        subscription.close();
        SecurityEventBus.publish(event(UUID.randomUUID()));
        assertEquals(1, seen.size());
    }

    @Test
    void publishWithNoListenersIsFine() {
        assertDoesNotThrow(() -> SecurityEventBus.publish(event(UUID.randomUUID())));
    }

    @Test
    void severityDefaultsToTheType() {
        assertEquals(SecuritySeverity.WARN, event(UUID.randomUUID()).severity());
        assertEquals(SecuritySeverity.INFO,
                SecurityEvent.of(SecurityEventType.MEMBER_ADDED, UUID.randomUUID(), null, null, 0L).severity());
    }
}
