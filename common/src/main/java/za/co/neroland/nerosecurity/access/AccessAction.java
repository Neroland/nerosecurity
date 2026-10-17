package za.co.neroland.nerosecurity.access;

/**
 * Everything a player can try to do against an installation, each with the minimum level a caller
 * passes to the resolver unless a device overrides it, and whether a keycard may grant it.
 *
 * <p>Keycards grant only physical passage: {@link #OPEN}, {@link #SENSOR_PASS} and {@link #PASS_FIELD}
 * ({@link #keycardMayGrant}). Every other action takes grants from stored membership and the
 * owner-chosen integration bindings only, so a card opens doors, chests, fields and sensors but never
 * manages the installation. Denies, lockdown and the operator override apply to every action alike.
 *
 * <p>Appended actions go at the end: the declaration order is part of the enum's identity and must
 * stay stable.
 */
public enum AccessAction {
    /**
     * Physically using a gated device: opening a door or a secure chest, and the "would this player be
     * refused here" check behind a forced break. Keycards grant it, so an OWNER card still opens a door
     * during lockdown. The hub screen is {@link #OPEN_HUB}, not this.
     */
    OPEN(AccessLevel.MEMBER, false, true),
    EDIT_MEMBERS(AccessLevel.OFFICER, false, false),
    /** Link, unlink, and select a hub with the linking tool. */
    LINK_DEVICE(AccessLevel.OFFICER, false, false),
    /** Reading the audit log (and the keycard ledger listing). Every read is itself audited. */
    READ_LOG(AccessLevel.OFFICER, false, false),
    /** Entering lockdown. Leaving it needs OWNER, which the lockdown rule produces on its own. */
    LOCKDOWN_TOGGLE(AccessLevel.OFFICER, false, false),
    RENAME(AccessLevel.OWNER, false, false),
    /** Breaking the hub. */
    DISSOLVE(AccessLevel.OWNER, false, false),
    /** Write or wipe a card at a programmer. Copying a card passes MEMBER explicitly. */
    PROGRAM_CARD(AccessLevel.OFFICER, false, false),
    /** Revoke one card, or every card, from the ledger. */
    REVOKE_CARD(AccessLevel.OWNER, false, false),
    /** A device's settings screen. Never filtered to keycards, so an owner cannot be locked out. */
    CONFIGURE_DEVICE(AccessLevel.OFFICER, false, false),
    /** The installation's secure-chest automation policy. */
    SET_AUTOMATION(AccessLevel.OWNER, false, false),
    /** Passing a laser grid or camera without raising an event. Devices pass their own minimum. */
    SENSOR_PASS(AccessLevel.MEMBER, false, true),
    /** Turning an alarm's "trigger lockdown" response on or off, either way. */
    CONFIGURE_ALARM_LOCKDOWN(AccessLevel.OWNER, false, false),
    /** Silencing an alarm for a while (a lockdown overrides the silence). */
    SILENCE_ALARM(AccessLevel.OFFICER, false, false),
    /** Operators only: non-admins are always refused, whatever their level. */
    ADMIN_UNLOCK(AccessLevel.OWNER, true, false),
    /** Whether officers also receive the installation's alarm chat messages. */
    SET_NOTIFY_OFFICERS(AccessLevel.OWNER, false, false),
    /** Walking through a force field. Emitters pass their own minimum (and keycard filter). */
    PASS_FIELD(AccessLevel.MEMBER, false, true),
    /** Binding or unbinding a faction, colony or station integration. Always OWNER. */
    BIND_INTEGRATION(AccessLevel.OWNER, false, false),
    /**
     * Opening the hub screen and the privacy screen behind it. OFFICER, because the screen edits members
     * and shows their names; there is no read-only member view of the hub.
     */
    OPEN_HUB(AccessLevel.OFFICER, false, false),
    /**
     * Looking at installation information without changing it: the monitor screen, the programmer
     * screen and the bindings listing. Membership and integration bindings only.
     */
    VIEW_INSTALLATION(AccessLevel.MEMBER, false, false);

    public final AccessLevel defaultMinimum;
    public final boolean adminOnly;
    /** Whether a grant from the keycard source counts for this action. True only for physical passage. */
    public final boolean keycardMayGrant;

    AccessAction(AccessLevel defaultMinimum, boolean adminOnly, boolean keycardMayGrant) {
        this.defaultMinimum = defaultMinimum;
        this.adminOnly = adminOnly;
        this.keycardMayGrant = keycardMayGrant;
    }
}
