package za.co.neroland.nerosecurity.lockdown;

import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;

/**
 * The lockdown state machine's guards, as pure rules (plain-JVM tested). {@code InstallationService}
 * applies them; nothing here touches the store.
 *
 * <p>An alarm may start a lockdown only when the server allows lockdown at all and allows alarm
 * lockdown, the firing is at least the configured severity, no lockdown is already in force, and the
 * installation's cooldown since the last alarm lockdown has elapsed. A running alarm lockdown is never
 * extended. Who may enter or leave a lockdown manually is the resolver's job, not this class's.
 */
public final class LockdownRules {

    /** The outcome of an alarm asking to lock an installation down. */
    public enum Verdict {
        /** Enter an alarm lockdown now. */
        ENTER,
        /** A lockdown (of any source) is already in force; nothing changes. */
        ALREADY_ACTIVE,
        /** The cooldown since the last alarm lockdown has not elapsed. */
        COOLDOWN,
        /** The firing is below the server's minimum severity for alarm lockdown. */
        BELOW_SEVERITY,
        /** Lockdown, or alarm lockdown, is switched off on this server. */
        DISABLED
    }

    private LockdownRules() {
    }

    /**
     * Whether an alarm firing at {@code severity} may start a lockdown.
     *
     * @param current      the installation's lockdown state
     * @param lastEndedAt  epoch ms the last alarm lockdown ended (0 = never)
     * @param now          epoch ms
     * @param minimum      the lowest severity that may lock down
     * @param cooldownMs   how long after an alarm lockdown ends before another may start
     */
    public static Verdict alarmEntry(Lockdown current, long lastEndedAt, long now, SecuritySeverity severity,
            SecuritySeverity minimum, boolean lockdownEnabled, boolean alarmLockdownEnabled, long cooldownMs) {
        if (!lockdownEnabled || !alarmLockdownEnabled) {
            return Verdict.DISABLED;
        }
        if (current != null && current.active()) {
            return Verdict.ALREADY_ACTIVE;
        }
        if (severity == null || minimum == null || severity.ordinal() < minimum.ordinal()) {
            return Verdict.BELOW_SEVERITY;
        }
        if (lastEndedAt > 0L && now - lastEndedAt < Math.max(0L, cooldownMs)) {
            return Verdict.COOLDOWN;
        }
        return Verdict.ENTER;
    }

    /**
     * Whether an alarm lockdown is due to end by itself. An alarm lockdown whose release time is
     * missing (a junk save) is due at once: alarm lockdowns are always time-boxed.
     */
    public static boolean dueForAutoRelease(Lockdown lockdown, long now) {
        return lockdown != null && lockdown.isAlarm() && now >= lockdown.autoReleaseAt();
    }

    /**
     * Whether leaving {@code previous} counts as the lockdown-drill milestone: only a manual lockdown an
     * owner entered, cleared by an owner. Alarm lockdowns never count.
     */
    public static boolean countsForDrill(Lockdown previous, boolean actorIsOwner) {
        return previous != null && previous.source() == LockdownSource.MANUAL && previous.enteredByOwner()
                && actorIsOwner;
    }
}
