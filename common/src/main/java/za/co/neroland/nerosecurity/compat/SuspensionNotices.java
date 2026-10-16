package za.co.neroland.nerosecurity.compat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;

/**
 * Publishes a binding's {@code *_SUSPENDED} event at most once per (installation, binding) per server session, the
 * first time a source sees the suspension. Transient: nothing is persisted, the event carries no actor and no
 * organisation id, and {@link #clear()} (server stop) starts a new session.
 */
public final class SuspensionNotices {

    private final SecurityEventType type;
    private final LongSupplier clock;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public SuspensionNotices(SecurityEventType type, LongSupplier clock) {
        this.type = type;
        this.clock = clock;
    }

    /** Publishes once for this installation and binding key; later calls in the session do nothing. */
    public void suspended(UUID installation, String bindingKey) {
        if (installation == null || !reported.add(installation + "|" + bindingKey)) {
            return;
        }
        try {
            SecurityEventBus.publish(SecurityEvent.of(type, installation, null, null, clock.getAsLong()));
        } catch (RuntimeException ignored) {
            // An event is a notice, never part of the access decision.
        }
    }

    /** Forgets every notice (a new server session). */
    public void clear() {
        reported.clear();
    }
}
