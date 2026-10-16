package za.co.neroland.nerosecurity.access;

/**
 * Everything a player can try to do against an installation, each with the minimum level a caller
 * passes to the resolver unless a device overrides it.
 */
public enum AccessAction {
    /** Use a gated device (doors, chests, the hub's read view). */
    OPEN(AccessLevel.MEMBER, false),
    EDIT_MEMBERS(AccessLevel.OFFICER, false),
    /** Link, unlink, and select a hub with the linking tool. */
    LINK_DEVICE(AccessLevel.OFFICER, false),
    /** Reading the audit log (and the keycard ledger listing). Every read is itself audited. */
    READ_LOG(AccessLevel.OFFICER, false),
    /** Entering lockdown. Leaving it needs OWNER, which the lockdown rule produces on its own. */
    LOCKDOWN_TOGGLE(AccessLevel.OFFICER, false),
    RENAME(AccessLevel.OWNER, false),
    /** Breaking the hub. */
    DISSOLVE(AccessLevel.OWNER, false),
    /** Write or wipe a card at a programmer. Copying a card passes MEMBER explicitly. */
    PROGRAM_CARD(AccessLevel.OFFICER, false),
    /** Revoke one card, or every card, from the ledger. */
    REVOKE_CARD(AccessLevel.OWNER, false),
    /** A door's or chest's settings screen. Never filtered to keycards, so an owner cannot be locked out. */
    CONFIGURE_DEVICE(AccessLevel.OFFICER, false),
    /** The installation's secure-chest automation policy. */
    SET_AUTOMATION(AccessLevel.OWNER, false),
    /** Passing a laser grid or camera without raising an event. Devices pass their own minimum. */
    SENSOR_PASS(AccessLevel.MEMBER, false),
    /** Turning an alarm's "trigger lockdown" response on or off, either way. */
    CONFIGURE_ALARM_LOCKDOWN(AccessLevel.OWNER, false),
    /** Silencing an alarm for a while (a lockdown overrides the silence). */
    SILENCE_ALARM(AccessLevel.OFFICER, false),
    /** Operators only: non-admins are always refused, whatever their level. */
    ADMIN_UNLOCK(AccessLevel.OWNER, true),
    /** Whether officers also receive the installation's alarm chat messages. */
    SET_NOTIFY_OFFICERS(AccessLevel.OWNER, false),
    /** Walking through a force field. Emitters pass their own minimum (and keycard filter). */
    PASS_FIELD(AccessLevel.MEMBER, false),
    /** Binding or unbinding a faction, colony or station integration. Always OWNER. */
    BIND_INTEGRATION(AccessLevel.OWNER, false);

    public final AccessLevel defaultMinimum;
    public final boolean adminOnly;

    AccessAction(AccessLevel defaultMinimum, boolean adminOnly) {
        this.defaultMinimum = defaultMinimum;
        this.adminOnly = adminOnly;
    }
}
