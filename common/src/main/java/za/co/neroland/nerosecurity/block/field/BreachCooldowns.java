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
    /** Fewest ticks between two melee hits by one attacker that each add load (5 per second, above any real swing rate). */
    public static final int MELEE_SPACING_TICKS = 4;

    private final Map<UUID, Long> lastAt = new HashMap<>();
    private final Map<UUID, Long> lastMeleeAt = new HashMap<>();
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

    /**
     * Whether a melee hit by {@code actor} at {@code now} may add load; records it when so. At most one hit per
     * {@value #MELEE_SPACING_TICKS} ticks per attacker counts, so the meter measures swings, not packets (a modified
     * client or an autoclicker sends one attack per packet). Bounded like the event slots: when the map is full of
     * attackers seen within the spacing, the hit adds nothing. A null actor (no player) is never spaced.
     */
    public boolean tryMelee(@Nullable UUID actor, long now) {
        if (actor == null) {
            return true;
        }
        Long at = lastMeleeAt.get(actor);
        if (at != null && !expired(at, now, MELEE_SPACING_TICKS)) {
            return false;
        }
        if (at == null && lastMeleeAt.size() >= MAX_ENTRIES) {
            lastMeleeAt.values().removeIf(t -> expired(t, now, MELEE_SPACING_TICKS));
            if (lastMeleeAt.size() >= MAX_ENTRIES) {
                return false;
            }
        }
        lastMeleeAt.put(actor, now);
        return true;
    }

    /** Drops every entry whose cooldown is over (or whose clock went backwards). */
    public void prune(long now, int cooldown) {
        lastAt.values().removeIf(at -> expired(at, now, cooldown));
        lastMeleeAt.values().removeIf(at -> expired(at, now, MELEE_SPACING_TICKS));
        if (expired(explosionAt, now, cooldown)) {
            explosionAt = Long.MIN_VALUE;
        }
    }

    public void clear() {
        lastAt.clear();
        lastMeleeAt.clear();
        explosionAt = Long.MIN_VALUE;
    }

    public int size() {
        return lastAt.size();
    }

    /** Attackers whose last melee hit is remembered. */
    public int meleeSize() {
        return lastMeleeAt.size();
    }

    private static boolean expired(long at, long now, int cooldown) {
        return at == Long.MIN_VALUE || now < at || now - at >= Math.max(0, cooldown);
    }
}
