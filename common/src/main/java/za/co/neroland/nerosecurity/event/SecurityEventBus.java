package za.co.neroland.nerosecurity.event;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * NeroSecurity's in-process event bus. Producers publish on the server thread; delivery is synchronous.
 * It is not Core's link event bus and nothing published here leaves the process.
 *
 * <p><b>Listener isolation.</b> Every listener call is guarded: a listener that throws is logged
 * (listener class, event type and exception class only, once per listener class per session) and the
 * next listener still runs. A listener is never detached automatically, so an alarm that failed once
 * keeps receiving events.
 */
public final class SecurityEventBus {

    /** Receives events. */
    @FunctionalInterface
    public interface Listener {
        void on(SecurityEvent event);
    }

    /** Closing a subscription stops delivery to its listener. */
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private record Entry(@Nullable UUID filter, Listener listener) {
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private SecurityEventBus() {
    }

    /** Every event, for every installation. */
    public static Subscription subscribe(Listener listener) {
        return add(new Entry(null, listener));
    }

    /** Events for one installation only. */
    public static Subscription subscribe(UUID installationId, Listener listener) {
        return add(new Entry(installationId, listener));
    }

    private static Subscription add(Entry entry) {
        if (entry.listener() == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        ENTRIES.add(entry);
        return () -> ENTRIES.remove(entry);
    }

    /** Delivers {@code event} to every matching listener, synchronously. */
    public static void publish(SecurityEvent event) {
        if (event == null) {
            return;
        }
        for (Entry entry : ENTRIES) {
            if (entry.filter() != null && !entry.filter().equals(event.installationId())) {
                continue;
            }
            try {
                entry.listener().on(event);
            } catch (RuntimeException | LinkageError e) {
                String listenerClass = entry.listener().getClass().getName();
                if (WARNED.add(listenerClass)) {
                    NeroSecurityCommon.LOGGER.warn(
                            "[NeroSecurity] security event listener {} failed on {} ({}); other listeners still ran.",
                            listenerClass, event.type(), e.getClass().getName());
                }
            }
        }
    }

    /** Tests only. */
    static void reset() {
        ENTRIES.clear();
        WARNED.clear();
    }
}
