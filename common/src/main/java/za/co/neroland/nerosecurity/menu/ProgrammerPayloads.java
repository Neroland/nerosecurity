package za.co.neroland.nerosecurity.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.ClientProgrammerState;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The programmer screen's payloads on {@code nerosecurity:main}: one clientbound snapshot for display and
 * three serverbound intents (write, copy, wipe). Each intent names only the programmer position the
 * client believes is open; the server checks it against the sender's open menu, re-reads the installation
 * at that position, re-resolves and re-reads the slots. No payload carries a card, a secret, a serial or
 * any player identity.
 */
public final class ProgrammerPayloads {

    private static boolean declared;

    private ProgrammerPayloads() {
    }

    /**
     * Declares the four programmer payloads with {@link SecurityNetwork}. Call once from
     * {@code SecurityNetwork.init()}, before any loader registers the payload lists.
     */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(ProgrammerSnapshot.TYPE, ProgrammerSnapshot.STREAM_CODEC,
                ClientProgrammerState::accept);
        SecurityNetwork.serverbound(ProgrammerWrite.TYPE, ProgrammerWrite.STREAM_CODEC,
                ProgrammerPayloadHandlers::handleWrite);
        SecurityNetwork.serverbound(ProgrammerClone.TYPE, ProgrammerClone.STREAM_CODEC,
                ProgrammerPayloadHandlers::handleClone);
        SecurityNetwork.serverbound(ProgrammerWipe.TYPE, ProgrammerWipe.STREAM_CODEC,
                ProgrammerPayloadHandlers::handleWipe);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    private static void writeLevel(FriendlyByteBuf buf, AccessLevel level) {
        AccessLevel.STREAM_CODEC.encode(buf, level);
    }

    private static AccessLevel readLevel(FriendlyByteBuf buf) {
        return AccessLevel.STREAM_CODEC.decode(buf);
    }

    /**
     * Server truth for the open programmer screen. Sent only to a player the resolver just allowed to view
     * this programmer. {@code viewerLevel} is the effective level (an operator shows as OWNER);
     * {@code message} is the last action's outcome, server-authored, or null. Display only.
     */
    public record ProgrammerSnapshot(BlockPos pos, AccessLevel viewerLevel, boolean hasModule,
            AccessLevel basicMaxLevel, int cardsIssued, int cardCap, @Nullable Component message)
            implements CustomPacketPayload {

        public static final Type<ProgrammerSnapshot> TYPE = new Type<>(id("programmer_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProgrammerSnapshot> STREAM_CODEC =
                StreamCodec.of(ProgrammerSnapshot::write, ProgrammerSnapshot::read);

        private static void write(RegistryFriendlyByteBuf buf, ProgrammerSnapshot value) {
            buf.writeBlockPos(value.pos);
            writeLevel(buf, value.viewerLevel);
            buf.writeBoolean(value.hasModule);
            writeLevel(buf, value.basicMaxLevel);
            buf.writeVarInt(Math.max(0, value.cardsIssued));
            buf.writeVarInt(Math.max(0, value.cardCap));
            buf.writeBoolean(value.message != null);
            if (value.message != null) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.message);
            }
        }

        private static ProgrammerSnapshot read(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            AccessLevel viewer = readLevel(buf);
            boolean hasModule = buf.readBoolean();
            AccessLevel basicMax = readLevel(buf);
            int issued = Math.max(0, buf.readVarInt());
            int cap = Math.max(0, buf.readVarInt());
            Component message = buf.readBoolean() ? ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf) : null;
            return new ProgrammerSnapshot(pos, viewer, hasModule, basicMax, issued, cap, message);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Write a new card onto the blank in the CARD slot. Every field is re-checked on the server. */
    public record ProgrammerWrite(BlockPos pos, AccessLevel level, boolean encrypted, boolean bind)
            implements CustomPacketPayload {

        public static final Type<ProgrammerWrite> TYPE = new Type<>(id("programmer_write"));
        public static final StreamCodec<FriendlyByteBuf, ProgrammerWrite> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    writeLevel(buf, v.level);
                    buf.writeBoolean(v.encrypted);
                    buf.writeBoolean(v.bind);
                },
                buf -> new ProgrammerWrite(buf.readBlockPos(), readLevel(buf), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Copy the card in the CARD slot onto the blank in the BLANK slot. */
    public record ProgrammerClone(BlockPos pos) implements CustomPacketPayload {

        public static final Type<ProgrammerClone> TYPE = new Type<>(id("programmer_clone"));
        public static final StreamCodec<FriendlyByteBuf, ProgrammerClone> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> buf.writeBlockPos(v.pos), buf -> new ProgrammerClone(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Wipe the card in the CARD slot back to blank. */
    public record ProgrammerWipe(BlockPos pos) implements CustomPacketPayload {

        public static final Type<ProgrammerWipe> TYPE = new Type<>(id("programmer_wipe"));
        public static final StreamCodec<FriendlyByteBuf, ProgrammerWipe> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> buf.writeBlockPos(v.pos), buf -> new ProgrammerWipe(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
