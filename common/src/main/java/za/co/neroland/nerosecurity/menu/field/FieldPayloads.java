package za.co.neroland.nerosecurity.menu.field;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.ClientFieldPassability;
import za.co.neroland.nerosecurity.client.screen.field.ClientFieldState;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The force field's payloads on {@code nerosecurity:main}:
 * <ul>
 *   <li>clientbound {@code nerosecurity:field_snapshot}: display truth for the open settings screen, sent only to a
 *       player just allowed to configure the emitter;</li>
 *   <li>serverbound {@code nerosecurity:field_configure}: a settings intent the server re-authorises and re-validates
 *       from scratch;</li>
 *   <li>clientbound {@code nerosecurity:field_passability}: "you may / may not pass the emitter at X", about the
 *       receiving player only, so its client predicts collision. Never another player's verdict, name or UUID.</li>
 * </ul>
 */
public final class FieldPayloads {

    /** Longest dimension id accepted from the wire. */
    private static final int MAX_DIMENSION_CHARS = 256;

    private static boolean declared;

    private FieldPayloads() {
    }

    /** Declares the field payloads with {@link SecurityNetwork}. Call once from network init. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(FieldSnapshot.TYPE, FieldSnapshot.STREAM_CODEC, ClientFieldState::accept);
        SecurityNetwork.serverbound(FieldConfigure.TYPE, FieldConfigure.STREAM_CODEC,
                FieldPayloadHandlers::handleConfigure);
        SecurityNetwork.clientbound(FieldPassabilityUpdate.TYPE, FieldPassabilityUpdate.STREAM_CODEC,
                update -> ClientFieldPassability.set(update.emitter(), update.passable()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    /**
     * Server truth for the open field screen. Display only: the server re-checks everything a configure intent says.
     * {@code viewerLevel} (OWNER for an operator) lets the screen disable owner-only choices; {@code serverEnabled}
     * is the server's {@code forceFieldsEnabled}. {@code placed} is how many field blocks are actually up, next to the
     * {@code area} the shape asks for: fewer means holes the owner should clear.
     */
    public record FieldSnapshot(BlockPos pos, FieldSettings settings, int strength, int maxStrength, int range,
            int area, int placed, int maxArea, int drawPerTick, long energy, long capacity, FieldState state, float loadFraction,
            int cooldownSeconds, AccessLevel viewerLevel, boolean admin, boolean breachable, boolean serverEnabled)
            implements CustomPacketPayload {

        public static final Type<FieldSnapshot> TYPE = new Type<>(id("field_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, FieldSnapshot> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    FieldSettings.STREAM_CODEC.encode(buf, v.settings);
                    buf.writeVarInt(v.strength);
                    buf.writeVarInt(v.maxStrength);
                    buf.writeVarInt(v.range);
                    buf.writeVarInt(v.area);
                    buf.writeVarInt(v.placed);
                    buf.writeVarInt(v.maxArea);
                    buf.writeVarInt(v.drawPerTick);
                    buf.writeVarLong(v.energy);
                    buf.writeVarLong(v.capacity);
                    FieldState.STREAM_CODEC.encode(buf, v.state);
                    buf.writeFloat(v.loadFraction);
                    buf.writeVarInt(v.cooldownSeconds);
                    AccessLevel.STREAM_CODEC.encode(buf, v.viewerLevel);
                    buf.writeBoolean(v.admin);
                    buf.writeBoolean(v.breachable);
                    buf.writeBoolean(v.serverEnabled);
                },
                buf -> new FieldSnapshot(buf.readBlockPos(), FieldSettings.STREAM_CODEC.decode(buf), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong(), FieldState.STREAM_CODEC.decode(buf), buf.readFloat(),
                        buf.readVarInt(), AccessLevel.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean(),
                        buf.readBoolean()));

        public FieldSnapshot {
            settings = settings == null ? FieldSettings.DEFAULT : settings;
            state = state == null ? FieldState.IDLE : state;
            viewerLevel = viewerLevel == null ? AccessLevel.PUBLIC : viewerLevel;
            placed = Math.max(0, placed);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set the emitter's shape, distance, dimensions, who may pass, keycard-only and on/off. */
    public record FieldConfigure(BlockPos pos, FieldSettings settings) implements CustomPacketPayload {

        public static final Type<FieldConfigure> TYPE = new Type<>(id("field_configure"));
        public static final StreamCodec<FriendlyByteBuf, FieldConfigure> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    FieldSettings.STREAM_CODEC.encode(buf, v.settings);
                },
                buf -> new FieldConfigure(buf.readBlockPos(), FieldSettings.STREAM_CODEC.decode(buf)));

        public FieldConfigure {
            settings = settings == null ? FieldSettings.DEFAULT : settings;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "The receiving player may (or may not) pass the field of the emitter at {@code emitter}." */
    public record FieldPassabilityUpdate(DevicePos emitter, boolean passable) implements CustomPacketPayload {

        public static final Type<FieldPassabilityUpdate> TYPE = new Type<>(id("field_passability"));
        /** A dimension id that fails to parse becomes this, which matches no level: the entry is inert. */
        private static final Identifier INVALID_DIMENSION = id("invalid");

        public static final StreamCodec<FriendlyByteBuf, FieldPassabilityUpdate> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.emitter.dimension().toString(), MAX_DIMENSION_CHARS);
                    buf.writeBlockPos(v.emitter.pos());
                    buf.writeBoolean(v.passable);
                },
                buf -> {
                    Identifier dimension = Identifier.tryParse(buf.readUtf(MAX_DIMENSION_CHARS));
                    BlockPos pos = buf.readBlockPos();
                    boolean passable = buf.readBoolean();
                    return new FieldPassabilityUpdate(
                            new DevicePos(dimension == null ? INVALID_DIMENSION : dimension, pos), passable);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
