package za.co.neroland.nerosecurity.menu.door;

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
import za.co.neroland.nerosecurity.block.door.RedstoneMode;
import za.co.neroland.nerosecurity.client.screen.door.ClientDoorState;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.menu.SnapshotMessages;
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
     * {@code viewerLevel} lets the screen disable the OWNER button for a non-owner; {@code installationName}
     * is the linked installation's own name ({@code ""} when unlinked; never a player's); {@code redstoneAllowed}
     * hides the redstone row on servers that forbid it; {@code ok}/{@code message} are the outcome of the action
     * that produced the snapshot (null message = nothing to say). Display only.
     */
    public record DoorSnapshot(BlockPos pos, AccessLevel minLevel, boolean keycardOnly, RedstoneMode mode,
            long energy, long capacity, boolean lockdown, boolean linked, AccessLevel viewerLevel,
            String installationName, boolean redstoneAllowed, boolean ok, @Nullable Component message)
            implements CustomPacketPayload {

        public static final Type<DoorSnapshot> TYPE = new Type<>(id("door_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DoorSnapshot> STREAM_CODEC = StreamCodec.of(
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
                    buf.writeUtf(v.installationName, Installation.NAME_MAX);
                    buf.writeBoolean(v.redstoneAllowed);
                    SnapshotMessages.write(buf, v.ok, v.message);
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    AccessLevel minLevel = readLevel(buf);
                    boolean keycardOnly = buf.readBoolean();
                    RedstoneMode mode = RedstoneMode.STREAM_CODEC.decode(buf);
                    long energy = Math.max(0L, buf.readVarLong());
                    long capacity = Math.max(0L, buf.readVarLong());
                    boolean lockdown = buf.readBoolean();
                    boolean linked = buf.readBoolean();
                    AccessLevel viewer = readLevel(buf);
                    String name = buf.readUtf(Installation.NAME_MAX);
                    boolean redstoneAllowed = buf.readBoolean();
                    boolean ok = SnapshotMessages.readOk(buf);
                    Component message = SnapshotMessages.readMessage(buf);
                    return new DoorSnapshot(pos, minLevel, keycardOnly, mode, energy, capacity, lockdown, linked,
                            viewer, name, redstoneAllowed, ok, message);
                });

        public DoorSnapshot {
            installationName = installationName == null ? ""
                    : installationName.length() > Installation.NAME_MAX
                            ? installationName.substring(0, Installation.NAME_MAX) : installationName;
        }

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
