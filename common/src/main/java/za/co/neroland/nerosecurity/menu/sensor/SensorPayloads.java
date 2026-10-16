package za.co.neroland.nerosecurity.menu.sensor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.screen.sensor.ClientSensorState;
import za.co.neroland.nerosecurity.menu.SnapshotMessages;
import za.co.neroland.nerosecurity.network.SecurityNetwork;
import za.co.neroland.nerosecurity.sensor.SensorSettings;

/**
 * The shared sensor settings screen's payloads on {@code nerosecurity:main}: one clientbound snapshot
 * ({@code nerosecurity:sensor_snapshot}) for display and one serverbound configure intent
 * ({@code nerosecurity:sensor_configure}), which the server re-authorises from scratch. No player
 * identity and nothing the sensor sensed travels in either.
 */
public final class SensorPayloads {

    private static boolean declared;

    private SensorPayloads() {
    }

    /** Declares the sensor payloads with {@link SecurityNetwork}. Call once from network init. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(SensorSnapshot.TYPE, SensorSnapshot.STREAM_CODEC, ClientSensorState::accept);
        SecurityNetwork.serverbound(SensorConfigure.TYPE, SensorConfigure.STREAM_CODEC,
                SensorPayloadHandlers::handleConfigure);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    /**
     * Server truth for the open sensor screen, sent only to a player just allowed to configure this
     * sensor. {@code viewerLevel} lets the screen disable the OWNER button for a non-owner; {@code laser}
     * hides the field-of-view control. Display only. Energy, armed and module count come through the
     * menu's data slots. {@code ok}/{@code message} are the outcome of the action that produced the snapshot.
     */
    public record SensorSnapshot(BlockPos pos, SensorSettings settings, boolean laser, boolean linked,
            AccessLevel viewerLevel, boolean ok, @Nullable Component message) implements CustomPacketPayload {

        public static final Type<SensorSnapshot> TYPE = new Type<>(id("sensor_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SensorSnapshot> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    SensorSettings.STREAM_CODEC.encode(buf, v.settings);
                    buf.writeBoolean(v.laser);
                    buf.writeBoolean(v.linked);
                    AccessLevel.STREAM_CODEC.encode(buf, v.viewerLevel);
                    SnapshotMessages.write(buf, v.ok, v.message);
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    SensorSettings settings = SensorSettings.STREAM_CODEC.decode(buf);
                    boolean laser = buf.readBoolean();
                    boolean linked = buf.readBoolean();
                    AccessLevel viewer = AccessLevel.STREAM_CODEC.decode(buf);
                    boolean ok = SnapshotMessages.readOk(buf);
                    Component message = SnapshotMessages.readMessage(buf);
                    return new SensorSnapshot(pos, settings, laser, linked, viewer, ok, message);
                });

        public SensorSnapshot {
            settings = settings == null ? SensorSettings.DEFAULT : settings;
            viewerLevel = viewerLevel == null ? AccessLevel.PUBLIC : viewerLevel;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set the sensor's level, entity mode and (cameras only) field of view. */
    public record SensorConfigure(BlockPos pos, SensorSettings settings) implements CustomPacketPayload {

        public static final Type<SensorConfigure> TYPE = new Type<>(id("sensor_configure"));
        public static final StreamCodec<FriendlyByteBuf, SensorConfigure> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    SensorSettings.STREAM_CODEC.encode(buf, v.settings);
                },
                buf -> new SensorConfigure(buf.readBlockPos(), SensorSettings.STREAM_CODEC.decode(buf)));

        public SensorConfigure {
            settings = settings == null ? SensorSettings.DEFAULT : settings;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
