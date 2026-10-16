package za.co.neroland.nerosecurity.link;

import java.util.Locale;
import java.util.Optional;

import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecurityEventType;

/**
 * Everything that can become a phone notification and a stored alert: the mapped security event types,
 * plus two link-level kinds that are not {@link SecurityEventType}s ({@link #REPEATED_DENIED} and the
 * coalescer's {@link #SUMMARY}). Append new values at the end only.
 */
public enum PushKind {
    LASER_TRIPPED(SecurityEventType.LASER_TRIPPED, LinkAlert.Severity.CRITICAL, false),
    DOOR_FORCED(SecurityEventType.DOOR_FORCED, LinkAlert.Severity.CRITICAL, false),
    CHEST_FORCED(SecurityEventType.CHEST_FORCED, LinkAlert.Severity.CRITICAL, false),
    DEVICE_FORCED(SecurityEventType.DEVICE_FORCED, LinkAlert.Severity.CRITICAL, false),
    FIELD_OVERLOADED(SecurityEventType.FIELD_OVERLOADED, LinkAlert.Severity.CRITICAL, false),
    MOTION_DETECTED(SecurityEventType.MOTION_DETECTED, LinkAlert.Severity.WARN, false),
    POWER_LOST(SecurityEventType.POWER_LOST, LinkAlert.Severity.WARN, false),
    FIELD_COLLAPSED(SecurityEventType.FIELD_COLLAPSED, LinkAlert.Severity.WARN, false),
    LOCKDOWN_ENTERED(SecurityEventType.LOCKDOWN_ENTERED, LinkAlert.Severity.CRITICAL, true),
    LOCKDOWN_EXITED(SecurityEventType.LOCKDOWN_EXITED, LinkAlert.Severity.INFO, true),
    LOCKDOWN_AUTO_RELEASED(SecurityEventType.LOCKDOWN_AUTO_RELEASED, LinkAlert.Severity.INFO, true),
    ADMIN_UNLOCK(SecurityEventType.ADMIN_UNLOCK, LinkAlert.Severity.WARN, true),
    /** {@code pushDeniedThreshold} denials within {@code pushDeniedWindowSeconds} at one installation. */
    REPEATED_DENIED(null, LinkAlert.Severity.WARN, false),
    /** A coalescer window closed with further events counted into it. */
    SUMMARY(null, LinkAlert.Severity.INFO, false);

    /** The event type this kind stands for; null for the two link-level kinds. */
    final SecurityEventType eventType;
    final LinkAlert.Severity severity;
    /** Lockdown changes skip the per-installation notification window. */
    final boolean bypassesCooldown;

    PushKind(SecurityEventType eventType, LinkAlert.Severity severity, boolean bypassesCooldown) {
        this.eventType = eventType;
        this.severity = severity;
        this.bypassesCooldown = bypassesCooldown;
    }

    /** {@code "laser_tripped"}, {@code "repeated_denied"}, {@code "summary"}. */
    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public LinkAlert.Severity severity() {
        return severity;
    }

    public boolean bypassesCooldown() {
        return bypassesCooldown;
    }

    /** The lang key of this kind's fixed phrase. */
    String phraseKey() {
        return eventType != null ? EventPhrases.key(eventType) : "link.nerosecurity." + jsonName();
    }

    /** The kind a security event type is notified as, if it is notified directly. */
    public static Optional<PushKind> of(SecurityEventType type) {
        if (type == null) {
            return Optional.empty();
        }
        for (PushKind kind : values()) {
            if (kind.eventType == type) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
