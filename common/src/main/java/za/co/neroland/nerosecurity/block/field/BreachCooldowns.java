package za.co.neroland.nerosecurity.block.field;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Rate limit for {@code FIELD_BREACH_ATTEMPT}: at most one event per attacker per emitter per cooldown. Explosions
 * (no attacker) share one slot. Transient (never saved), bounded at {@value #MAX_ENTRIES} attackers: expired
 * entries are pruned when the map is full and on every sync pass; when it is still full the event is skipped
 * (the load is still added, so the field still fails under a crowd). Pure; tested without a level.
 */
public final class BreachCooldowns {

    /** Most attackers remembered per emitter. */
    public static final int MAX_ENTRIES = 64;

    private final Map<UUID, Long> lastAt = new HashMap<>();
    private long explosionAt = Long.MIN_VALUE;

    /**
     * Whether an event may be published for {@code actor} now; records it when so.
     *
     * @param actor    the attacker, or null for an explosion or an unknown shooter
     * @param now      game time
     * @param cooldown ticks between two events for one attacker
     */
    public boolean tryFire(@Nullable UUID actor, long now, int cooldown) {
        if (actor == null) {
            if (!expired(explosionAt, now, cooldown)) {
                return false;
            }
            explosionAt = now;
            return true;
        }
        Long at = lastAt.get(actor);
        if (at != null && !expired(at, now, cooldown)) {
            return false;
        }
        if (at == null && lastAt.size() >= MAX_ENTRIES) {
            prune(now, cooldown);
            if (lastAt.size() >= MAX_ENTRIES) {
                return false;
            }
        }
        lastAt.put(actor, now);
        return true;
    }

    /** Drops every entry whose cooldown is over (or whose clock went backwards). */
    public void prune(long now, int cooldown) {
        lastAt.values().removeIf(at -> expired(at, now, cooldown));
        if (expired(explosionAt, now, cooldown)) {
            explosionAt = Long.MIN_VALUE;
        }
    }

    public void clear() {
        lastAt.clear();
        explosionAt = Long.MIN_VALUE;
    }

    public int size() {
        return lastAt.size();
    }

    private static boolean expired(long at, long now, int cooldown) {
        return at == Long.MIN_VALUE || now < at || now - at >= Math.max(0, cooldown);
    }
}
