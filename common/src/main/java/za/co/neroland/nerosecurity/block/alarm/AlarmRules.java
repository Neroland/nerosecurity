package za.co.neroland.nerosecurity.block.alarm;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.alarm.AlarmFiring;
import za.co.neroland.nerosecurity.event.SecurityEventType;

/**
 * The alarm's pure rules: which event types an alarm can subscribe to, the defaults, when an alarm is
 * armed, how long a silence may last, and who may change the lockdown response. No world, no server:
 * the block entity and the payload handlers call these after the resolver has answered.
 */
public final class AlarmRules {

    /** Breaches: something got past, or was broken, or a force field is under attack or overloaded. */
    public static final List<SecurityEventType> BREACHES = List.of(SecurityEventType.LASER_TRIPPED,
            SecurityEventType.MOTION_DETECTED, SecurityEventType.DOOR_FORCED, SecurityEventType.CHEST_FORCED,
            SecurityEventType.DEVICE_FORCED, SecurityEventType.FIELD_OVERLOADED,
            SecurityEventType.FIELD_BREACH_ATTEMPT);
    /** Denied access: someone tried and was refused. */
    public static final List<SecurityEventType> DENIALS = List.of(SecurityEventType.DOOR_DENIED,
            SecurityEventType.CHEST_DENIED, SecurityEventType.KEYCARD_REJECTED);
    /** System: the installation itself is in trouble. */
    public static final List<SecurityEventType> SYSTEM = List.of(SecurityEventType.POWER_LOST);

    private static final Set<SecurityEventType> SUBSCRIBABLE;

    static {
        EnumSet<SecurityEventType> all = EnumSet.noneOf(SecurityEventType.class);
        all.addAll(BREACHES);
        all.addAll(DENIALS);
        all.addAll(SYSTEM);
        SUBSCRIBABLE = java.util.Collections.unmodifiableSet(all);
    }

    /** The silence the screen's button asks for, in minutes (the server caps it). */
    public static final int SILENCE_BUTTON_MINUTES = 10;

    private AlarmRules() {
    }

    /** Every type an alarm can subscribe to (and therefore the only types the dispatcher coalesces). */
    public static Set<SecurityEventType> subscribable() {
        return SUBSCRIBABLE;
    }

    public static boolean isSubscribable(SecurityEventType type) {
        return type != null && SUBSCRIBABLE.contains(type);
    }

    /** A new alarm listens to every subscribable type. */
    public static EnumSet<SecurityEventType> defaultSubscribed() {
        return EnumSet.copyOf(SUBSCRIBABLE);
    }

    /** Redstone, sound, light and chat; never lockdown (that is an owner's opt-in). */
    public static EnumSet<AlarmResponse> defaultResponses() {
        return EnumSet.of(AlarmResponse.REDSTONE, AlarmResponse.SOUND, AlarmResponse.HIGHLIGHT, AlarmResponse.CHAT);
    }

    /** {@code wanted} restricted to subscribable types (anything else a client sends is dropped). */
    public static EnumSet<SecurityEventType> sanitiseSubscribed(Collection<SecurityEventType> wanted) {
        EnumSet<SecurityEventType> out = EnumSet.noneOf(SecurityEventType.class);
        if (wanted != null) {
            for (SecurityEventType type : wanted) {
                if (isSubscribable(type)) {
                    out.add(type);
                }
            }
        }
        return out;
    }

    public static EnumSet<AlarmResponse> copyResponses(Collection<AlarmResponse> wanted) {
        EnumSet<AlarmResponse> out = EnumSet.noneOf(AlarmResponse.class);
        if (wanted != null) {
            for (AlarmResponse response : wanted) {
                if (response != null) {
                    out.add(response);
                }
            }
        }
        return out;
    }

    /** Stored lower-case name of an event type. */
    public static String typeKey(SecurityEventType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    /** The subscribable type with this lower-case name, or null (unknown and non-subscribable names drop). */
    public static SecurityEventType typeByKey(String key) {
        if (key == null) {
            return null;
        }
        for (SecurityEventType type : SUBSCRIBABLE) {
            if (typeKey(type).equals(key)) {
                return type;
            }
        }
        return null;
    }

    /** Stored names to types: unknown and non-subscribable names are dropped. */
    public static EnumSet<SecurityEventType> typesFromNames(List<String> names) {
        EnumSet<SecurityEventType> out = EnumSet.noneOf(SecurityEventType.class);
        for (String name : names) {
            SecurityEventType type = typeByKey(name);
            if (type != null) {
                out.add(type);
            }
        }
        return out;
    }

    /** Stored names to responses: unknown names are dropped. */
    public static EnumSet<AlarmResponse> responsesFromNames(List<String> names) {
        EnumSet<AlarmResponse> out = EnumSet.noneOf(AlarmResponse.class);
        for (String name : names) {
            AlarmResponse.byKey(name).ifPresent(out::add);
        }
        return out;
    }

    /**
     * Whether a linked alarm reacts right now: not silenced, or a lockdown is in force (a lockdown always
     * overrides a silence, so an intruder who got an officer to silence the alarm gains nothing once the
     * base seals). An unlinked alarm never reacts.
     */
    public static boolean armed(boolean linked, long now, long silencedUntil, boolean lockdownEnabled,
            boolean lockdownActive) {
        if (!linked) {
            return false;
        }
        return now >= silencedUntil || (lockdownEnabled && lockdownActive);
    }

    /**
     * The firing as an alarm subscribed to {@code subscribed} sees it: type and severity computed over the
     * window's types that this alarm subscribes to, never over the whole installation window. This is the
     * severity the alarm's own responses (the lockdown gate in particular) act on. Null when nothing matches.
     */
    public static @Nullable AlarmFiring effective(AlarmFiring firing, Collection<SecurityEventType> subscribed) {
        return firing == null ? null : firing.forSubscriptions(subscribed);
    }

    /** Whether this alarm's own configuration asks for a lockdown; the service applies every other guard. */
    public static boolean attemptsLockdown(Set<AlarmResponse> responses) {
        return responses != null && responses.contains(AlarmResponse.LOCKDOWN);
    }

    /** A silence request clamped to {@code 1..maxMinutes}. */
    public static int clampSilenceMinutes(int requested, int maxMinutes) {
        int max = Math.max(1, maxMinutes);
        return Math.max(1, Math.min(requested, max));
    }

    /** Silenced-until for a clamped request; saturates instead of overflowing. */
    public static long silencedUntil(long now, int minutes) {
        long span = Math.max(0, minutes) * 60_000L;
        return now > Long.MAX_VALUE - span ? Long.MAX_VALUE : now + span;
    }

    /** Whole seconds of silence left (0 when not silenced). */
    public static int silencedForSeconds(long now, long silencedUntil) {
        if (silencedUntil <= now) {
            return 0;
        }
        long seconds = (silencedUntil - now + 999L) / 1000L;
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    /** The answer to a settings change that may touch the lockdown response. */
    public enum LockdownChange {
        /** The lockdown flag is not being changed. */
        UNCHANGED,
        /** It changes and the change is allowed. */
        ALLOWED,
        /** It changes and the sender is not an effective owner (or operator): refuse the whole payload. */
        OWNER_ONLY,
        /** It would be switched on while the server forbids alarm lockdowns: refuse the whole payload. */
        SERVER_DISABLED,
        /**
         * The lockdown flag stays on and something else changes, but the sender is not an effective owner
         * (or operator): refuse the whole payload. An alarm that can lock the base down is owner-only to
         * edit; officers can still silence it.
         */
        LOCKDOWN_ARMED_OWNER_ONLY
    }

    /**
     * Whether a settings change needs {@code CONFIGURE_ALARM_LOCKDOWN} at OWNER: it switches the lockdown
     * response on or off, or the alarm already has the lockdown response and anything at all changes.
     */
    public static boolean editNeedsOwner(Set<SecurityEventType> currentTypes, Set<AlarmResponse> currentResponses,
            Set<SecurityEventType> wantedTypes, Set<AlarmResponse> wantedResponses) {
        if (attemptsLockdown(currentResponses) != attemptsLockdown(wantedResponses)) {
            return true;
        }
        return attemptsLockdown(currentResponses)
                && (!same(currentTypes, wantedTypes) || !same(currentResponses, wantedResponses));
    }

    /**
     * The full answer to a settings change: {@link #lockdownChange} for the flag itself, and, while the
     * flag stays on, {@link LockdownChange#LOCKDOWN_ARMED_OWNER_ONLY} for any other edit by a non-owner.
     */
    public static LockdownChange configureChange(Set<SecurityEventType> currentTypes,
            Set<AlarmResponse> currentResponses, Set<SecurityEventType> wantedTypes, Set<AlarmResponse> wantedResponses,
            boolean ownerMayConfigureLockdown, boolean serverAllows) {
        LockdownChange flag = lockdownChange(currentResponses, wantedResponses, ownerMayConfigureLockdown, serverAllows);
        if (flag != LockdownChange.UNCHANGED) {
            return flag;
        }
        if (!ownerMayConfigureLockdown
                && editNeedsOwner(currentTypes, currentResponses, wantedTypes, wantedResponses)) {
            return LockdownChange.LOCKDOWN_ARMED_OWNER_ONLY;
        }
        return LockdownChange.UNCHANGED;
    }

    private static <T> boolean same(Set<T> a, Set<T> b) {
        Set<T> left = a == null ? Set.of() : a;
        Set<T> right = b == null ? Set.of() : b;
        return left.equals(right);
    }

    /**
     * Who may change the lockdown response. Switching it, on or off, needs {@code CONFIGURE_ALARM_LOCKDOWN}
     * at OWNER (the caller passes the resolver's answer); switching it on additionally needs the server to
     * allow alarm lockdowns at all. Switching it off is always possible for an owner, so a server that
     * later disables the feature never strands a stale flag.
     */
    public static LockdownChange lockdownChange(Set<AlarmResponse> current, Set<AlarmResponse> wanted,
            boolean ownerMayConfigureLockdown, boolean serverAllows) {
        boolean before = attemptsLockdown(current);
        boolean after = attemptsLockdown(wanted);
        if (before == after) {
            return LockdownChange.UNCHANGED;
        }
        if (!ownerMayConfigureLockdown) {
            return LockdownChange.OWNER_ONLY;
        }
        if (after && !serverAllows) {
            return LockdownChange.SERVER_DISABLED;
        }
        return LockdownChange.ALLOWED;
    }
}
