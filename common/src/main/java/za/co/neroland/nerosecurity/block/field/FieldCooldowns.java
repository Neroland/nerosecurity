package za.co.neroland.nerosecurity.block.field;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * Overload cooldowns that outlive the emitter that earned them. An emitter records its {@code cooldownUntil} here on
 * every overload; a freshly placed emitter at the same position adopts the later of its own and this value on its
 * first tick. Breaking an overloaded emitter and placing a new one therefore cannot cut the breach window short.
 *
 * <p>Server session only: never saved (a saved emitter keeps its own cooldown in NBT), cleared when the server stops,
 * bounded at {@value #MAX_ENTRIES} positions (expired entries are pruned when full; if it is still full, the entry
 * that expires soonest makes room). Holds block positions of the owner's own devices and game times, never a player.
 * Pure apart from {@link #session()}; tested without a level.
 */
public final class FieldCooldowns {

    /** Most emitter positions remembered at once. */
    public static final int MAX_ENTRIES = 1024;

    private static final FieldCooldowns SESSION = new FieldCooldowns();
    private static boolean hooked;

    private final Map<DevicePos, Long> until = new HashMap<>();

    /** The server's instance; the first call registers the server-stop reset. */
    public static synchronized FieldCooldowns session() {
        if (!hooked) {
            hooked = true;
            ServerHooks.onStopping(server -> SESSION.clear());
        }
        return SESSION;
    }

    /** Remembers that the emitter at {@code at} may not project again before {@code cooldownUntil}. */
    public synchronized void record(DevicePos at, long cooldownUntil, long now) {
        if (at == null || cooldownUntil <= now) {
            return;
        }
        if (!until.containsKey(at) && until.size() >= MAX_ENTRIES) {
            prune(now);
            if (until.size() >= MAX_ENTRIES) {
                evictSoonest();
            }
        }
        until.merge(at, cooldownUntil, Math::max);
    }

    /** The cooldown end remembered for {@code at}, or 0 when there is none still running at {@code now}. */
    public synchronized long adopt(DevicePos at, long now) {
        if (at == null) {
            return 0L;
        }
        Long end = until.get(at);
        if (end == null) {
            return 0L;
        }
        if (end <= now) {
            until.remove(at);
            return 0L;
        }
        return end;
    }

    /** Drops every cooldown that is over at {@code now}. */
    public synchronized void prune(long now) {
        until.values().removeIf(end -> end <= now);
    }

    public synchronized void clear() {
        until.clear();
    }

    public synchronized int size() {
        return until.size();
    }

    private void evictSoonest() {
        DevicePos soonest = null;
        long soonestEnd = Long.MAX_VALUE;
        for (Iterator<Map.Entry<DevicePos, Long>> it = until.entrySet().iterator(); it.hasNext();) {
            Map.Entry<DevicePos, Long> e = it.next();
            if (e.getValue() < soonestEnd) {
                soonestEnd = e.getValue();
                soonest = e.getKey();
            }
        }
        if (soonest != null) {
            until.remove(soonest);
        }
    }
}
