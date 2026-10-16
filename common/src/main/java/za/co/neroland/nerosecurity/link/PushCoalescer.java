package za.co.neroland.nerosecurity.link;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;

/**
 * Storm control for phone notifications: at most one notification per (recipient, installation) per
 * cooldown, leading edge first. Later events inside the window are counted ({@code extra}) and the highest
 * severity kept; when the window closes with {@code extra > 0}, {@link #drain} hands back one summary.
 * Lockdown kinds bypass the window (they are sent at once and leave it untouched).
 *
 * <p>Pure and in memory only: an injectable cooldown, the caller's clock, bounded at {@link #maxWindows}
 * (the oldest window is evicted). Keyed by recipient UUID, so erasure forgets a recipient's windows.
 */
final class PushCoalescer {

    static final int DEFAULT_MAX_WINDOWS = 4096;

    /** One notification to send now. {@code kind} is the window's first kind. */
    record Emit(UUID recipient, UUID installationId, PushKind kind, LinkAlert.Severity severity, int extra) {
    }

    /**
     * What an offer decided: {@code now} is non-null when this event is notified at once (extra 0);
     * {@code flushed} carries a previous, already expired window's summary when one was still pending.
     */
    record Offer(@Nullable Emit now, @Nullable Emit flushed) {

        boolean emitted() {
            return now != null;
        }
    }

    private record Key(UUID recipient, UUID installationId) {
    }

    private static final class Window {
        final long start;
        final PushKind firstKind;
        LinkAlert.Severity maxSeverity;
        int extra;

        Window(long start, PushKind firstKind, LinkAlert.Severity severity) {
            this.start = start;
            this.firstKind = firstKind;
            this.maxSeverity = severity;
        }
    }

    private final LongSupplier cooldownMs;
    private final int maxWindows;
    private final Map<Key, Window> windows = new LinkedHashMap<>();

    PushCoalescer(LongSupplier cooldownMs, int maxWindows) {
        this.cooldownMs = cooldownMs;
        this.maxWindows = Math.max(1, maxWindows);
    }

    synchronized Offer offer(UUID recipient, UUID installationId, PushKind kind, LinkAlert.Severity severity,
            long now) {
        if (kind.bypassesCooldown()) {
            return new Offer(new Emit(recipient, installationId, kind, severity, 0), null);
        }
        Key key = new Key(recipient, installationId);
        Window open = windows.get(key);
        Emit flushed = null;
        if (open != null && now - open.start >= cooldown()) {
            windows.remove(key);
            if (open.extra > 0) {
                flushed = new Emit(recipient, installationId, open.firstKind, open.maxSeverity, open.extra);
            }
            open = null;
        }
        if (open == null) {
            evictIfFull();
            windows.put(key, new Window(now, kind, severity));
            return new Offer(new Emit(recipient, installationId, kind, severity, 0), flushed);
        }
        open.extra++;
        if (severity.ordinal() > open.maxSeverity.ordinal()) {
            open.maxSeverity = severity;
        }
        return new Offer(null, null);
    }

    /** Summaries for every window older than the cooldown that counted further events; closes them all. */
    synchronized List<Emit> drain(long now) {
        List<Emit> out = new ArrayList<>();
        long cooldown = cooldown();
        for (Iterator<Map.Entry<Key, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Window> e = it.next();
            Window w = e.getValue();
            if (now - w.start >= cooldown) {
                it.remove();
                if (w.extra > 0) {
                    out.add(new Emit(e.getKey().recipient(), e.getKey().installationId(), w.firstKind, w.maxSeverity,
                            w.extra));
                }
            }
        }
        return out;
    }

    synchronized int size() {
        return windows.size();
    }

    synchronized void forget(UUID recipient) {
        windows.keySet().removeIf(key -> key.recipient().equals(recipient));
    }

    synchronized boolean holdsAnyFor(UUID recipient) {
        for (Key key : windows.keySet()) {
            if (key.recipient().equals(recipient)) {
                return true;
            }
        }
        return false;
    }

    synchronized void clear() {
        windows.clear();
    }

    private long cooldown() {
        return Math.max(0L, cooldownMs.getAsLong());
    }

    private void evictIfFull() {
        while (windows.size() >= maxWindows) {
            Iterator<Key> oldest = windows.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }
}
