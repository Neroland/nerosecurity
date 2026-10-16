package za.co.neroland.nerosecurity.alarm;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/**
 * One alarm firing after coalescing: the type it is announced as, its severity, how many further events
 * were folded into it, and every type seen (so an alarm subscribed to any of them reacts). Carries no
 * actor and no position: nothing in a firing can name a player.
 *
 * @param type     the leading event's type, or for a summary the most severe type in the window
 * @param severity the highest severity in the window
 * @param extra    events folded in after the leading one (0 for a leading-edge firing)
 * @param types    every type in the window, never empty, always containing {@code type}
 * @param severities the highest severity seen per type in the window (a type missing from the map counts
 *                   at its default severity; {@code type} counts at least at {@code severity})
 */
public record AlarmFiring(SecurityEventType type, SecuritySeverity severity, int extra, Set<SecurityEventType> types,
        Map<SecurityEventType, SecuritySeverity> severities) {

    public AlarmFiring {
        if (type == null || severity == null) {
            throw new IllegalArgumentException("type and severity are required");
        }
        EnumSet<SecurityEventType> copy = EnumSet.of(type);
        if (types != null) {
            copy.addAll(types);
        }
        EnumMap<SecurityEventType, SecuritySeverity> per = new EnumMap<>(SecurityEventType.class);
        for (SecurityEventType t : copy) {
            SecuritySeverity seen = severities == null ? null : severities.get(t);
            per.put(t, seen != null ? seen : t.defaultSeverity);
        }
        per.merge(type, severity, AlarmFiring::max);
        types = Set.copyOf(copy);
        severities = Map.copyOf(per);
        extra = Math.max(0, extra);
    }

    /** A firing whose per-type severities are the types' defaults (and {@code severity} for {@code type}). */
    public AlarmFiring(SecurityEventType type, SecuritySeverity severity, int extra, Set<SecurityEventType> types) {
        this(type, severity, extra, types, Map.of());
    }

    /** A leading-edge firing for a single event. */
    public static AlarmFiring single(SecurityEventType type, SecuritySeverity severity) {
        return new AlarmFiring(type, severity, 0, Set.of(type));
    }

    /** Whether an alarm subscribed to {@code subscribed} reacts to this firing. */
    public boolean matches(Collection<SecurityEventType> subscribed) {
        return forSubscriptions(subscribed) != null;
    }

    /** The highest severity seen for {@code t} in this firing, or null when {@code t} is not in it. */
    public @Nullable SecuritySeverity severityOf(SecurityEventType t) {
        return t == null ? null : severities.get(t);
    }

    /**
     * This firing as one alarm sees it: only the types in {@code subscribed}, with the severity and the
     * announced type computed over those types alone. An alarm must never act (lock down in particular)
     * on the severity of an event it was not configured to react to. Null when nothing matches.
     *
     * <p>The announced type is the most severe matched type; on a tie the window's own type wins when it is
     * matched, otherwise the first in declaration order. {@code extra} is the window's count, unchanged.
     */
    public @Nullable AlarmFiring forSubscriptions(Collection<SecurityEventType> subscribed) {
        if (subscribed == null || subscribed.isEmpty()) {
            return null;
        }
        EnumSet<SecurityEventType> matched = EnumSet.noneOf(SecurityEventType.class);
        for (SecurityEventType t : types) {
            if (subscribed.contains(t)) {
                matched.add(t);
            }
        }
        if (matched.isEmpty()) {
            return null;
        }
        SecuritySeverity best = null;
        for (SecurityEventType t : matched) {
            best = best == null ? severities.get(t) : max(best, severities.get(t));
        }
        SecurityEventType lead = null;
        if (matched.contains(type) && severities.get(type) == best) {
            lead = type;
        } else {
            for (SecurityEventType t : matched) {
                if (severities.get(t) == best) {
                    lead = t;
                    break;
                }
            }
        }
        EnumMap<SecurityEventType, SecuritySeverity> per = new EnumMap<>(SecurityEventType.class);
        for (SecurityEventType t : matched) {
            per.put(t, severities.get(t));
        }
        return new AlarmFiring(lead, best, extra, matched, per);
    }

    private static SecuritySeverity max(SecuritySeverity a, SecuritySeverity b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }
}
