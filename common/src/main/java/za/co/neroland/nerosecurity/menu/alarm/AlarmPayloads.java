package za.co.neroland.nerosecurity.menu.alarm;

import java.util.EnumSet;
import java.util.Set;

import io.netty.handler.codec.DecoderException;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.alarm.AlarmResponse;
import za.co.neroland.nerosecurity.client.screen.alarm.ClientAlarmState;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The alarm settings screen's payloads on {@code nerosecurity:main}: one clientbound snapshot for
 * display and two serverbound intents (configure, silence) that the server re-authorises from scratch.
 * Sets travel as var-int ordinal lists whose length is capped at the enum's size; an out-of-range length
 * or ordinal fails the decode. No payload carries a player identity.
 */
public final class AlarmPayloads {

    private static boolean declared;

    private AlarmPayloads() {
    }

    /** Declares the alarm payloads. The orchestrated network init calls this once; repeats are ignored. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(AlarmSnapshot.TYPE, AlarmSnapshot.STREAM_CODEC, ClientAlarmState::accept);
        SecurityNetwork.serverbound(AlarmConfigure.TYPE, AlarmConfigure.STREAM_CODEC,
                AlarmPayloadHandlers::handleConfigure);
        SecurityNetwork.serverbound(AlarmSilence.TYPE, AlarmSilence.STREAM_CODEC, AlarmPayloadHandlers::handleSilence);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    static <E extends Enum<E>> void writeSet(FriendlyByteBuf buf, Set<E> set) {
        buf.writeVarInt(set.size());
        for (E value : set) {
            buf.writeVarInt(value.ordinal());
        }
    }

    static <E extends Enum<E>> EnumSet<E> readSet(FriendlyByteBuf buf, E[] values, EnumSet<E> into) {
        int count = buf.readVarInt();
        if (count < 0 || count > values.length) {
            throw new DecoderException("alarm set too large");
        }
        for (int i = 0; i < count; i++) {
            int ordinal = buf.readVarInt();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new DecoderException("alarm set entry out of range");
            }
            into.add(values[ordinal]);
        }
        return into;
    }

    static EnumSet<SecurityEventType> readTypes(FriendlyByteBuf buf) {
        return readSet(buf, SecurityEventType.values(), EnumSet.noneOf(SecurityEventType.class));
    }

    static EnumSet<AlarmResponse> readResponses(FriendlyByteBuf buf) {
        return readSet(buf, AlarmResponse.values(), EnumSet.noneOf(AlarmResponse.class));
    }

    /**
     * Server truth for the open settings screen. Display only: the client never decides anything from it.
     *
     * @param pos                              the alarm the screen is bound to
     * @param subscribed                       event types it reacts to
     * @param responses                        what it does when it fires
     * @param silencedForSeconds               seconds of silence left (0 = armed)
     * @param viewerLevel                      the viewer's effective level
     * @param admin                            whether the viewer is acting as an operator
     * @param lockdownResponseAllowedByServer  whether the server lets any alarm trigger a lockdown
     * @param autoReleaseMinutes               how long an alarm lockdown lasts before it clears itself
     */
    public record AlarmSnapshot(BlockPos pos, Set<SecurityEventType> subscribed, Set<AlarmResponse> responses,
            int silencedForSeconds, AccessLevel viewerLevel, boolean admin, boolean lockdownResponseAllowedByServer,
            int autoReleaseMinutes) implements CustomPacketPayload {

        public static final Type<AlarmSnapshot> TYPE = new Type<>(id("alarm_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, AlarmSnapshot> STREAM_CODEC =
                StreamCodec.of(AlarmSnapshot::write, AlarmSnapshot::read);

        public AlarmSnapshot {
            subscribed = Set.copyOf(subscribed);
            responses = Set.copyOf(responses);
        }

        private static void write(FriendlyByteBuf buf, AlarmSnapshot value) {
            buf.writeBlockPos(value.pos);
            writeSet(buf, value.subscribed.isEmpty() ? EnumSet.noneOf(SecurityEventType.class)
                    : EnumSet.copyOf(value.subscribed));
            writeSet(buf, value.responses.isEmpty() ? EnumSet.noneOf(AlarmResponse.class)
                    : EnumSet.copyOf(value.responses));
            buf.writeVarInt(Math.max(0, value.silencedForSeconds));
            AccessLevel.STREAM_CODEC.encode(buf, value.viewerLevel);
            buf.writeBoolean(value.admin);
            buf.writeBoolean(value.lockdownResponseAllowedByServer);
            buf.writeVarInt(Math.max(0, value.autoReleaseMinutes));
        }

        private static AlarmSnapshot read(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            EnumSet<SecurityEventType> subscribed = readTypes(buf);
            EnumSet<AlarmResponse> responses = readResponses(buf);
            int silenced = buf.readVarInt();
            AccessLevel viewer = AccessLevel.STREAM_CODEC.decode(buf);
            boolean admin = buf.readBoolean();
            boolean allowed = buf.readBoolean();
            int minutes = buf.readVarInt();
            return new AlarmSnapshot(pos, subscribed, responses, silenced, viewer, admin, allowed, minutes);
        }

        /** Whether the viewer may touch the lockdown checkbox (the server re-checks). */
        public boolean mayEditLockdown() {
            return (admin || viewerLevel == AccessLevel.OWNER) && lockdownResponseAllowedByServer;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Replace the alarm's subscriptions and responses. Re-authorised and bound to the open menu. */
    public record AlarmConfigure(BlockPos pos, Set<SecurityEventType> subscribed, Set<AlarmResponse> responses)
            implements CustomPacketPayload {

        public static final Type<AlarmConfigure> TYPE = new Type<>(id("alarm_configure"));
        public static final StreamCodec<FriendlyByteBuf, AlarmConfigure> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    writeSet(buf, v.subscribed.isEmpty() ? EnumSet.noneOf(SecurityEventType.class)
                            : EnumSet.copyOf(v.subscribed));
                    writeSet(buf, v.responses.isEmpty() ? EnumSet.noneOf(AlarmResponse.class)
                            : EnumSet.copyOf(v.responses));
                },
                buf -> new AlarmConfigure(buf.readBlockPos(), readTypes(buf), readResponses(buf)));

        public AlarmConfigure {
            subscribed = Set.copyOf(subscribed);
            responses = Set.copyOf(responses);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Silence the alarm for {@code minutes} (the server caps it). Re-authorised and bound to the open menu. */
    public record AlarmSilence(BlockPos pos, int minutes) implements CustomPacketPayload {

        public static final Type<AlarmSilence> TYPE = new Type<>(id("alarm_silence"));
        public static final StreamCodec<FriendlyByteBuf, AlarmSilence> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    buf.writeVarInt(v.minutes);
                },
                buf -> new AlarmSilence(buf.readBlockPos(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
