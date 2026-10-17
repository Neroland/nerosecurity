package za.co.neroland.nerosecurity.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/**
 * An audit row with every identity removed: what happened, how loud, how long ago, at which of the
 * owner's devices, and only the <em>kind</em> of actor. The shape monitors and other non-privileged
 * screens send to clients; it cannot carry a UUID or a name.
 *
 * @param agoSeconds how long ago, never an absolute time
 * @param devicePos  the device's block position (the dimension is the viewer's own), or null
 */
public record AnonymousEvent(SecurityEventType type, SecuritySeverity severity, int agoSeconds,
        @Nullable BlockPos devicePos, ActorKind actorKind) {

    private static final StreamCodec<ByteBuf, SecurityEventType> TYPE_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> SecurityEventType.values()[Math.floorMod(i, SecurityEventType.values().length)],
                    SecurityEventType::ordinal);
    private static final StreamCodec<ByteBuf, SecuritySeverity> SEVERITY_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> SecuritySeverity.values()[Math.floorMod(i, SecuritySeverity.values().length)],
                    SecuritySeverity::ordinal);
    private static final StreamCodec<ByteBuf, ActorKind> KIND_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> ActorKind.values()[Math.floorMod(i, ActorKind.values().length)],
                    ActorKind::ordinal);

    public static final StreamCodec<ByteBuf, AnonymousEvent> STREAM_CODEC = StreamCodec.composite(
            TYPE_CODEC, AnonymousEvent::type,
            SEVERITY_CODEC, AnonymousEvent::severity,
            ByteBufCodecs.VAR_INT, AnonymousEvent::agoSeconds,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), e -> Optional.ofNullable(e.devicePos()),
            KIND_CODEC, AnonymousEvent::actorKind,
            (type, severity, ago, pos, kind) -> new AnonymousEvent(type, severity, ago, pos.orElse(null), kind));

    public AnonymousEvent {
        agoSeconds = Math.max(0, agoSeconds);
        // Identity-free by construction: never tells a withheld (opted-out) actor from any other player.
        actorKind = actorKind == null ? ActorKind.NONE : actorKind.identityFree();
    }

    /** The identity-free form of {@code entry}, relative to {@code now} (epoch ms). */
    public static AnonymousEvent of(AuditEntry entry, long now) {
        long ago = Math.max(0L, now - entry.at()) / 1000L;
        return new AnonymousEvent(entry.type(), entry.severity(), (int) Math.min(Integer.MAX_VALUE, ago),
                entry.devicePos() == null ? null : entry.devicePos().pos(), ActorKind.of(entry).identityFree());
    }

    /** Every entry mapped through {@link #of(AuditEntry, long)}, order kept. */
    public static List<AnonymousEvent> of(List<AuditEntry> entries, long now) {
        List<AnonymousEvent> out = new ArrayList<>(entries.size());
        for (AuditEntry entry : entries) {
            out.add(of(entry, now));
        }
        return out;
    }
}
