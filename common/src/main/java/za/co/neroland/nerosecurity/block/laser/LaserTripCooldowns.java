package za.co.neroland.nerosecurity.block.laser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-entity trip cooldowns for one laser grid node: one {@code LASER_TRIPPED} per entity per
 * {@code laserTripCooldownTicks}, so someone standing in the beam cannot flood the alarm or the log.
 *
 * <p>Keys are <b>entity</b> UUIDs (a mob has no subject), values the game time of the last trip.
 * Transient: never saved, never logged, cleared on unload. Bounded: above {@value #PRUNE_ABOVE} entries
 * the expired ones are dropped, and if the map is still at {@value #HARD_CAP} the oldest entry is evicted,
 * so the map never exceeds {@value #HARD_CAP} entries whatever the beam sees.
 */
public final class LaserTripCooldowns {

    /** Prune expired entries once the map grows past this. */
    public static final int PRUNE_ABOVE = 64;
    /** The map never holds more than this. */
    public static final int HARD_CAP = 256;

    private final Map<UUID, Long> lastTrip = new HashMap<>();

    /** Whether {@code entity} may trip again at {@code now} with a cooldown of {@code cooldownTicks}. */
    public boolean due(UUID entity, long now, int cooldownTicks) {
        if (entity == null) {
            return false;
        }
        Long last = lastTrip.get(entity);
        return last == null || now - last >= Math.max(1, cooldownTicks);
    }

    /**
     * Records a trip if one is due and reports whether it was. Pruning happens here, so the map stays
     * bounded however many distinct entities cross the beam.
     */
    public boolean tryTrip(UUID entity, long now, int cooldownTicks) {
        if (!due(entity, now, cooldownTicks)) {
            return false;
        }
        int cooldown = Math.max(1, cooldownTicks);
        if (lastTrip.size() >= PRUNE_ABOVE && !lastTrip.containsKey(entity)) {
            lastTrip.values().removeIf(at -> now - at >= cooldown);
        }
        if (lastTrip.size() >= HARD_CAP && !lastTrip.containsKey(entity)) {
            UUID oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<UUID, Long> e : lastTrip.entrySet()) {
                if (e.getValue() < oldestAt) {
                    oldestAt = e.getValue();
                    oldest = e.getKey();
                }
            }
            if (oldest != null) {
                lastTrip.remove(oldest);
            }
        }
        lastTrip.put(entity, now);
        return true;
    }

    public int size() {
        return lastTrip.size();
    }

    public void clear() {
        lastTrip.clear();
    }
}
