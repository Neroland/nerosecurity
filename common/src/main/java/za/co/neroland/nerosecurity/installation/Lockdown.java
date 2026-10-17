package za.co.neroland.nerosecurity.installation;

/**
 * An installation's lockdown state.
 *
 * @param active         whether lockdown is in force
 * @param since          epoch ms it was entered (0 when never)
 * @param enteredByOwner whether an owner entered it; a boolean rather than "who", so no identity is
 *                       stored. Used only for the lockdown-drill milestone and the UI.
 * @param source         what started it; an alarm lockdown is time-boxed
 * @param autoReleaseAt  epoch ms an alarm lockdown ends by itself; 0 for a manual or inactive one. Set
 *                       once on entry and never extended.
 */
public record Lockdown(boolean active, long since, boolean enteredByOwner, LockdownSource source, long autoReleaseAt) {

    public static final Lockdown NONE = new Lockdown(false, 0L, false, LockdownSource.MANUAL, 0L);

    public Lockdown {
        if (source == null) {
            source = LockdownSource.MANUAL;
        }
        if (!active || source != LockdownSource.ALARM) {
            autoReleaseAt = 0L;
        }
    }

    /** A manual lockdown (the original shape, before alarm lockdowns). */
    public Lockdown(boolean active, long since, boolean enteredByOwner) {
        this(active, since, enteredByOwner, LockdownSource.MANUAL, 0L);
    }

    /** A lockdown entered at the hub or by command. */
    public static Lockdown manual(long now, boolean byOwner) {
        return new Lockdown(true, now, byOwner, LockdownSource.MANUAL, 0L);
    }

    /** A lockdown started by an alarm; it ends by itself at {@code autoReleaseAt}. */
    public static Lockdown alarm(long now, long autoReleaseAt) {
        return new Lockdown(true, now, false, LockdownSource.ALARM, autoReleaseAt);
    }

    /** An owner confirmed an alarm lockdown: it becomes manual and no longer clears itself. */
    public Lockdown confirmed() {
        return confirmed(true);
    }

    /** {@link #confirmed()} with an explicit "entered by an owner" flag (false for an operator). */
    public Lockdown confirmed(boolean byOwner) {
        return new Lockdown(true, since, byOwner, LockdownSource.MANUAL, 0L);
    }

    public Lockdown withEnteredByOwner(boolean byOwner) {
        return new Lockdown(active, since, byOwner, source, autoReleaseAt);
    }

    /** Whether this is an active, time-boxed alarm lockdown. */
    public boolean isAlarm() {
        return active && source == LockdownSource.ALARM;
    }
}
