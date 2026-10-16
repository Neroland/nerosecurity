package za.co.neroland.nerosecurity.menu.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.door.RedstoneMode;
import za.co.neroland.nerosecurity.client.screen.door.ClientDoorState;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The door settings screen's payloads on {@code nerosecurity:main}: one clientbound snapshot for display
 * and one serverbound configure intent, which the server re-authorises from scratch. No player identity
 * travels in either.
 */
public final class DoorPayloads {

    private static boolean declared;

    private DoorPayloads() {
    }

    /** Declares the door payloads with {@link SecurityNetwork}. Call once from network init. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(DoorSnapshot.TYPE, DoorSnapshot.STREAM_CODEC, ClientDoorState::accept);
        SecurityNetwork.serverbound(DoorConfigure.TYPE, DoorConfigure.STREAM_CODEC, DoorPayloadHandlers::handleConfigure);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    private static AccessLevel readLevel(FriendlyByteBuf buf) {
        return AccessLevel.STREAM_CODEC.decode(buf);
    }

    private static void writeLevel(FriendlyByteBuf buf, AccessLevel level) {
        AccessLevel.STREAM_CODEC.encode(buf, level);
    }

    /**
     * Server truth for the open door screen, sent only to a player just allowed to configure this door.
     * {@code viewerLevel} lets the screen disable the OWNER button for a non-owner. Display only.
     */
    public record DoorSnapshot(BlockPos pos, AccessLevel minLevel, boolean keycardOnly, RedstoneMode mode,
            long energy, long capacity, boolean lockdown, boolean linked, AccessLevel viewerLevel)
            implements CustomPacketPayload {

        public static final Type<DoorSnapshot> TYPE = new Type<>(id("door_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, DoorSnapshot> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    writeLevel(buf, v.minLevel);
                    buf.writeBoolean(v.keycardOnly);
                    RedstoneMode.STREAM_CODEC.encode(buf, v.mode);
                    buf.writeVarLong(Math.max(0L, v.energy));
                    buf.writeVarLong(Math.max(0L, v.capacity));
                    buf.writeBoolean(v.lockdown);
                    buf.writeBoolean(v.linked);
                    writeLevel(buf, v.viewerLevel);
                },
                buf -> new DoorSnapshot(buf.readBlockPos(), readLevel(buf), buf.readBoolean(),
                        RedstoneMode.STREAM_CODEC.decode(buf), Math.max(0L, buf.readVarLong()),
                        Math.max(0L, buf.readVarLong()), buf.readBoolean(), buf.readBoolean(), readLevel(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set the door's access level, keycard-only flag and redstone mode. */
    public record DoorConfigure(BlockPos pos, AccessLevel minLevel, boolean keycardOnly, RedstoneMode mode)
            implements CustomPacketPayload {

        public static final Type<DoorConfigure> TYPE = new Type<>(id("door_configure"));
        public static final StreamCodec<FriendlyByteBuf, DoorConfigure> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    writeLevel(buf, v.minLevel);
                    buf.writeBoolean(v.keycardOnly);
                    RedstoneMode.STREAM_CODEC.encode(buf, v.mode);
                },
                buf -> new DoorConfigure(buf.readBlockPos(), readLevel(buf), buf.readBoolean(),
                        RedstoneMode.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
