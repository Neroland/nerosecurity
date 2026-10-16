package za.co.neroland.nerosecurity.event;

/** Every kind of security event, each with its default severity. */
public enum SecurityEventType {
    INSTALLATION_CREATED(SecuritySeverity.INFO),
    INSTALLATION_DISSOLVED(SecuritySeverity.WARN),
    INSTALLATION_ORPHANED(SecuritySeverity.WARN),
    MEMBER_ADDED(SecuritySeverity.INFO),
    MEMBER_REMOVED(SecuritySeverity.INFO),
    LEVEL_CHANGED(SecuritySeverity.INFO),
    DEVICE_LINKED(SecuritySeverity.INFO),
    DEVICE_UNLINKED(SecuritySeverity.INFO),
    LOCKDOWN_ENTERED(SecuritySeverity.WARN),
    LOCKDOWN_EXITED(SecuritySeverity.INFO),
    ADMIN_UNLOCK(SecuritySeverity.WARN),
    KEYCARD_WRITTEN(SecuritySeverity.INFO),
    KEYCARD_CLONED(SecuritySeverity.INFO),
    KEYCARD_WIPED(SecuritySeverity.INFO),
    KEYCARD_REVOKED(SecuritySeverity.WARN),
    /** A card for this installation failed verification (tampered, revoked, or not its holder's). */
    KEYCARD_REJECTED(SecuritySeverity.WARN),
    DOOR_OPENED(SecuritySeverity.INFO),
    DOOR_DENIED(SecuritySeverity.WARN),
    /** A door was broken by someone not allowed to open it, or removed by an explosion or command. */
    DOOR_FORCED(SecuritySeverity.CRITICAL),
    POWER_LOST(SecuritySeverity.WARN),
    CHEST_OPENED(SecuritySeverity.INFO),
    /** Contents changed: once per open session, or rate-limited and actor-less for automation. */
    CHEST_CHANGED(SecuritySeverity.INFO),
    CHEST_DENIED(SecuritySeverity.WARN),
    CHEST_FORCED(SecuritySeverity.CRITICAL),
    DEVICE_CONFIGURED(SecuritySeverity.INFO),
    AUTOMATION_POLICY_CHANGED(SecuritySeverity.INFO),
    /** Someone or something not permitted crossed a laser grid beam. A breach, not presence data. */
    LASER_TRIPPED(SecuritySeverity.CRITICAL),
    /** A camera saw someone or something not permitted (presence data: shorter retention). */
    MOTION_DETECTED(SecuritySeverity.WARN),
    /** A camera saw a permitted player; only when the server opts members into logging (presence data). */
    CAMERA_MEMBER_SEEN(SecuritySeverity.INFO),
    /** A sensor, alarm or monitor was broken by someone not allowed to use it, or removed by a non-player. */
    DEVICE_FORCED(SecuritySeverity.CRITICAL),
    ALARM_TRIGGERED(SecuritySeverity.WARN),
    ALARM_SILENCED(SecuritySeverity.INFO),
    /** An alarm would have locked down but the installation's alarm-lockdown cooldown is running. */
    ALARM_LOCKDOWN_SUPPRESSED(SecuritySeverity.WARN),
    /** An alarm lockdown ended by itself after its time box. */
    LOCKDOWN_AUTO_RELEASED(SecuritySeverity.INFO),
    /** An owner turned an alarm lockdown into a manual one (it no longer clears itself). */
    LOCKDOWN_CONFIRMED(SecuritySeverity.INFO),
    /** Someone read the installation's audit log. Audited before the rows are returned. */
    LOG_READ(SecuritySeverity.INFO),
    /** A retention sweep removed old entries. Published on the bus only; never persisted. */
    AUDIT_PURGED(SecuritySeverity.INFO),
    /** An owner changed whether officers receive alarm chat messages. */
    NOTIFY_OFFICERS_CHANGED(SecuritySeverity.INFO),
    // Stage 4. Append only: anonymous events and alarm subscriptions encode these by ordinal.
    /** A force field took more load than it could hold and collapsed for its cooldown. */
    FIELD_OVERLOADED(SecuritySeverity.CRITICAL),
    /** A force field lost power and collapsed. */
    FIELD_COLLAPSED(SecuritySeverity.WARN),
    /** A collapsed force field formed again. */
    FIELD_RESTORED(SecuritySeverity.INFO),
    /** Someone not permitted hit a force field (rate-limited per attacker per emitter). */
    FIELD_BREACH_ATTEMPT(SecuritySeverity.WARN),
    FACTION_BOUND(SecuritySeverity.INFO),
    FACTION_UNBOUND(SecuritySeverity.INFO),
    /** No owner is still a member of the bound faction, so the binding grants nothing. */
    FACTION_SUSPENDED(SecuritySeverity.WARN),
    COLONY_BOUND(SecuritySeverity.INFO),
    COLONY_UNBOUND(SecuritySeverity.INFO),
    /** No owner is still a member of the bound colony, so the binding grants nothing. */
    COLONY_SUSPENDED(SecuritySeverity.WARN),
    STATION_BOUND(SecuritySeverity.INFO),
    STATION_UNBOUND(SecuritySeverity.INFO);

    public final SecuritySeverity defaultSeverity;

    SecurityEventType(SecuritySeverity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }
}
