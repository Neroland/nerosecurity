package za.co.neroland.nerosecurity.audit;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.DevicePos;

/**
 * One persisted audit row. Minimal by construction (POPIA s10 / GDPR 5(1)(c)): when, what, who (a UUID
 * or nothing), whether the actor was withheld by their opt-out, which of the owner's devices, and how
 * loud. There is no payload field, no name and never a player position: {@code devicePos} is the
 * position of the installation's own block.
 *
 * <p>Only {@link AuditRecorder} creates entries (and the codec, on load).
 *
 * @param at            epoch ms
 * @param actor         the player who caused it, or null (no player, or withheld)
 * @param actorWithheld true when a player caused it but their opt-out withheld the identity
 *                      ("anonymous"); distinct from {@code actor == null} with no player at all
 */
public record AuditEntry(long at, SecurityEventType type, @Nullable UUID actor, boolean actorWithheld,
        @Nullable DevicePos devicePos, SecuritySeverity severity) {

    public AuditEntry {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (severity == null) {
            severity = type.defaultSeverity;
        }
        if (actorWithheld) {
            actor = null;
        }
    }

    /**
     * The on-disk row. Every field is lenient so one bad row is dropped on load rather than failing the
     * whole log: decode through {@link #fromRaw}, which returns empty for an unknown type or a malformed
     * actor. Fields: {@code at, type, actor?, withheld, pos?, severity}.
     */
    record Raw(long at, String type, Optional<String> actor, boolean withheld, Optional<DevicePos> pos,
            String severity) {
        static final Codec<Raw> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.lenientOptionalFieldOf("at", 0L).forGetter(Raw::at),
                Codec.STRING.lenientOptionalFieldOf("type", "").forGetter(Raw::type),
                Codec.STRING.lenientOptionalFieldOf("actor").forGetter(Raw::actor),
                Codec.BOOL.lenientOptionalFieldOf("withheld", false).forGetter(Raw::withheld),
                DevicePos.CODEC.lenientOptionalFieldOf("pos").forGetter(Raw::pos),
                Codec.STRING.lenientOptionalFieldOf("severity", "").forGetter(Raw::severity)
        ).apply(inst, Raw::new));
    }

    Raw toRaw() {
        return new Raw(at, lower(type.name()), Optional.ofNullable(actor).map(UUID::toString), actorWithheld,
                Optional.ofNullable(devicePos), lower(severity.name()));
    }

    /** The entry a raw row describes, or empty when its type is unknown or its actor malformed. */
    static Optional<AuditEntry> fromRaw(Raw raw) {
        SecurityEventType type = parseType(raw.type());
        if (type == null) {
            return Optional.empty();
        }
        UUID actor = null;
        if (raw.actor().isPresent()) {
            try {
                actor = UUID.fromString(raw.actor().get());
            } catch (IllegalArgumentException malformed) {
                return Optional.empty();
            }
        }
        SecuritySeverity severity = parseSeverity(raw.severity());
        return Optional.of(new AuditEntry(raw.at(), type, actor, raw.withheld(), raw.pos().orElse(null),
                severity == null ? type.defaultSeverity : severity));
    }

    /** One entry; fails (rather than drops) on an unknown type. Lists use the lenient path in {@link AuditLog}. */
    public static final Codec<AuditEntry> CODEC = Raw.CODEC.comapFlatMap(
            raw -> fromRaw(raw).map(com.mojang.serialization.DataResult::success)
                    .orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "unknown or malformed audit entry")),
            AuditEntry::toRaw);

    /** Presence data (who was seen where): kept only for the shorter presence retention. */
    public boolean isPresence() {
        return type == SecurityEventType.MOTION_DETECTED || type == SecurityEventType.CAMERA_MEMBER_SEEN;
    }

    /** What kind of actor this row has, for surfaces that must never show an identity. */
    public ActorKind actorKind() {
        return ActorKind.of(this);
    }

    private static @Nullable SecurityEventType parseType(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return SecurityEventType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static @Nullable SecuritySeverity parseSeverity(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return SecuritySeverity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Never prints the actor. */
    @Override
    public String toString() {
        return "AuditEntry[" + type + ", " + severity + ", at=" + at + "]";
    }
}
