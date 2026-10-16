package za.co.neroland.nerosecurity.link;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;

/**
 * Which security events reach the companion app, and how.
 *
 * <ul>
 *   <li><b>Notified</b> (live row, notification, alert): breaches (laser tripped; door, chest or device
 *       forced; field overloaded), warnings (motion, power lost, field collapsed) and every lockdown change
 *       (entered, cleared, auto-released, operator unlock; these bypass the notification window).</li>
 *   <li><b>Denials</b> (door, chest, keycard): a live row each; a notification only as one
 *       {@link PushKind#REPEATED_DENIED} when {@code threshold} of them land at one installation within
 *       {@code window}, once per window.</li>
 *   <li><b>Live row only</b>: lockdown confirmed, alarm triggered, alarm silenced, alarm lockdown suppressed.</li>
 *   <li><b>Never</b>: everything else, notably member sightings, openings, chest changes, log reads,
 *       purges and keycard bookkeeping.</li>
 * </ul>
 *
 * <p>The denial counter is per installation id (no player), in memory, bounded. Otherwise pure.
 */
final class LinkEventMapper {

    /** What one event becomes. {@code push} null = live row only. */
    record Mapped(SecurityEventType type, @Nullable PushKind push) {
    }

    static final Set<SecurityEventType> DENIALS = EnumSet.of(SecurityEventType.DOOR_DENIED,
            SecurityEventType.CHEST_DENIED, SecurityEventType.KEYCARD_REJECTED);

    static final Set<SecurityEventType> ROW_ONLY = EnumSet.of(SecurityEventType.LOCKDOWN_CONFIRMED,
            SecurityEventType.ALARM_TRIGGERED, SecurityEventType.ALARM_SILENCED,
            SecurityEventType.ALARM_LOCKDOWN_SUPPRESSED);

    static final int MAX_COUNTERS = 4096;

    private static final class Count {
        final long start;
        int n;
        boolean fired;

        Count(long start) {
            this.start = start;
        }
    }

    private final IntSupplier threshold;
    private final LongSupplier windowMs;
    private final Map<UUID, Count> denials = new LinkedHashMap<>();

    LinkEventMapper(IntSupplier threshold, LongSupplier windowMs) {
        this.threshold = threshold;
        this.windowMs = windowMs;
    }

    /** Whether {@code type} reaches the link bus at all (independent of counters). */
    static boolean reachesLink(SecurityEventType type) {
        return PushKind.of(type).isPresent() || DENIALS.contains(type) || ROW_ONLY.contains(type);
    }

    synchronized Optional<Mapped> map(SecurityEvent event, long now) {
        if (event == null || event.type() == null) {
            return Optional.empty();
        }
        SecurityEventType type = event.type();
        Optional<PushKind> direct = PushKind.of(type);
        if (direct.isPresent()) {
            return Optional.of(new Mapped(type, direct.get()));
        }
        if (DENIALS.contains(type)) {
            return Optional.of(new Mapped(type, countDenial(event.installationId(), now) ? PushKind.REPEATED_DENIED : null));
        }
        if (ROW_ONLY.contains(type)) {
            return Optional.of(new Mapped(type, null));
        }
        return Optional.empty();
    }

    /** True exactly once per window, on the denial that reaches the threshold. */
    private boolean countDenial(UUID installationId, long now) {
        if (installationId == null) {
            return false;
        }
        Count count = denials.get(installationId);
        if (count == null || now - count.start >= Math.max(1L, windowMs.getAsLong())) {
            if (count == null && denials.size() >= MAX_COUNTERS) {
                Iterator<UUID> oldest = denials.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            count = new Count(now);
            denials.put(installationId, count);
        }
        count.n++;
        if (!count.fired && count.n >= Math.max(1, threshold.getAsInt())) {
            count.fired = true;
            return true;
        }
        return false;
    }

    synchronized void clear() {
        denials.clear();
    }
}
