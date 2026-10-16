package za.co.neroland.nerosecurity.event;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;

/**
 * Something security-relevant that happened to one installation. In-memory only in this build:
 * events are delivered synchronously and never persisted or sent off the server.
 *
 * @param actor the player who caused it, or null (non-player removal, erasure, console)
 * @param at    epoch ms
 */
public record SecurityEvent(SecurityEventType type, UUID installationId, @Nullable DevicePos devicePos,
        @Nullable UUID actor, long at, SecuritySeverity severity) {

    /** An event at its type's default severity. */
    public static SecurityEvent of(SecurityEventType type, UUID installationId, @Nullable DevicePos pos,
            @Nullable UUID actor, long at) {
        return new SecurityEvent(type, installationId, pos, actor, at, type.defaultSeverity);
    }
}
