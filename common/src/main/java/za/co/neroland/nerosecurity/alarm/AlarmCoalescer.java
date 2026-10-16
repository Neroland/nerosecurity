package za.co.neroland.nerosecurity.alarm;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/**
 * Storm control for alarms: one coalescing window per installation. The first event fires at once
 * (leading edge) and opens a window; events inside the window only count; when the window has run its
 * period, {@link #drain} emits one summary ("+N more") if anything was folded in, and closes it.
 *
 * <p>A CRITICAL inside a WARN window does not bypass the window (storm control wins); the summary
 * carries the window's highest severity (for chat) and the highest severity per type, so each alarm acts
 * only on the types it subscribes to: a laser trip inside a motion window still reaches a laser-subscribed
 * lockdown response at drain time, and never one that does not listen to lasers.
 *
 * <p><b>Bounded by construction.</b> At most {@code maxWindows} windows exist; expired windows are pruned
 * on every drain and whenever the table is full; summaries owed by windows that expired between drains
 * are kept in a list bounded by the same cap. A clock that steps backwards closes the window rather than
 * stretching it, so no installation's alarm can be stuck open. Pure: no server, injectable clock values.
 */
public final class AlarmCoalescer {

    /** Default cap on concurrently open windows (one per installation). */
    public static final int DEFAULT_MAX_WINDOWS = 4096;

    /** A firing owed to one installation. */
    public record Emit(UUID installationId, AlarmFiring firing) {
    }

    private static final class Window {
        final long openedAt;
        final SecurityEventType firstType;
        SecuritySeverity maxSeverity;
        SecurityEventType maxType;
        int extra;
        final EnumSet<SecurityEventType> types;
        /** Highest severity per type, so each alarm can judge only the types it subscribes to. */
        final EnumMap<SecurityEventType, SecuritySeverity> severities = new EnumMap<>(SecurityEventType.class);

        Window(long openedAt, SecurityEventType type, SecuritySeverity severity) {
            this.openedAt = openedAt;
            this.firstType = type;
            this.maxSeverity = severity;
            this.maxType = type;
            this.types = EnumSet.of(type);
            this.severities.put(type, severity);
        }

        void see(SecurityEventType type, SecuritySeverity severity) {
            types.add(type);
            severities.merge(type, severity, (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }

        AlarmFiring summary() {
            return new AlarmFiring(maxType, maxSeverity, extra, types, severities);
        }
    }

    private final LongSupplier windowMs;
    private final int maxWindows;
    private final Map<UUID, Window> windows = new HashMap<>();
    private final List<Emit> overdue = new ArrayList<>();

    /** @param windowMs the window length in ms, read on every call so a config change applies at once */
    public AlarmCoalescer(LongSupplier windowMs, int maxWindows) {
        this.windowMs = windowMs;
        this.maxWindows = Math.max(1, maxWindows);
    }

    private long period() {
        long ms;
        try {
            ms = windowMs.getAsLong();
        } catch (RuntimeException | LinkageError e) {
            ms = 10_000L;
        }
        return Math.max(1L, ms);
    }

    private static boolean open(Window w, long now, long period) {
        return now >= w.openedAt && now - w.openedAt < period;
    }

    /**
     * One event. Returns the leading-edge firing when no window is open for {@code installationId}, or
     * null when the event was folded into the open window (or dropped because the table is full).
     */
    public synchronized @Nullable AlarmFiring offer(UUID installationId, SecurityEventType type,
            SecuritySeverity severity, long now) {
        if (installationId == null || type == null || severity == null) {
            return null;
        }
        long period = period();
        Window w = windows.get(installationId);
        if (w != null) {
            if (open(w, now, period)) {
                if (w.extra < Integer.MAX_VALUE) {
                    w.extra++;
                }
                w.see(type, severity);
                if (severity.compareTo(w.maxSeverity) > 0) {
                    w.maxSeverity = severity;
                    w.maxType = type;
                }
                return null;
            }
            close(installationId, w);
        } else if (windows.size() >= maxWindows) {
            pruneExpired(now, period);
            if (windows.size() >= maxWindows) {
                return null; // storm control: the table is full of live windows
            }
        }
        windows.put(installationId, new Window(now, type, severity));
        return AlarmFiring.single(type, severity);
    }

    /** Closes every window past its period: one summary each where events were folded in. */
    public synchronized List<Emit> drain(long now) {
        pruneExpired(now, period());
        if (overdue.isEmpty()) {
            return List.of();
        }
        List<Emit> out = List.copyOf(overdue);
        overdue.clear();
        return out;
    }

    private void pruneExpired(long now, long period) {
        Iterator<Map.Entry<UUID, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Window> entry = it.next();
            if (!open(entry.getValue(), now, period)) {
                owe(entry.getKey(), entry.getValue());
                it.remove();
            }
        }
    }

    private void close(UUID id, Window w) {
        owe(id, w);
        windows.remove(id);
    }

    private void owe(UUID id, Window w) {
        if (w.extra > 0 && overdue.size() < maxWindows) {
            overdue.add(new Emit(id, w.summary()));
        }
    }

    /** Open windows right now (tests and diagnostics). */
    public synchronized int openWindows() {
        return windows.size();
    }

    /** The type that opened the window for {@code id}, or null (tests). */
    synchronized @Nullable SecurityEventType firstType(UUID id) {
        Window w = windows.get(id);
        return w == null ? null : w.firstType;
    }

    /** Forgets everything (server stop). */
    public synchronized void clear() {
        windows.clear();
        overdue.clear();
    }
}
