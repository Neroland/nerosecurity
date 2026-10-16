package za.co.neroland.nerosecurity.menu.chest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.screen.chest.ClientChestState;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The secure chest settings screen's payloads on {@code nerosecurity:main}: one clientbound snapshot for
 * display and one serverbound settings intent, which the server re-authorises from scratch. Nothing
 * about the chest's contents is ever part of a payload.
 */
public final class ChestPayloads {

    private static boolean declared;

    private ChestPayloads() {
    }

    /** Declares the chest payloads. The orchestrated network init calls this once; repeats are ignored. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(ChestSnapshot.TYPE, ChestSnapshot.STREAM_CODEC, ClientChestState::accept);
        SecurityNetwork.serverbound(ChestConfigure.TYPE, ChestConfigure.STREAM_CODEC,
                ChestPayloadHandlers::handleConfigure);
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

    /** A presence flag, then the level when present (absent = automation off, or unlinked). */
    private static void writeOptionalLevel(FriendlyByteBuf buf, @Nullable AccessLevel level) {
        buf.writeBoolean(level != null);
        if (level != null) {
            writeLevel(buf, level);
        }
    }

    private static @Nullable AccessLevel readOptionalLevel(FriendlyByteBuf buf) {
        return buf.readBoolean() ? readLevel(buf) : null;
    }

    /**
     * Server truth for the open settings screen. Display only: the client never decides anything from it.
     *
     * @param pos               the chest the screen is bound to
     * @param minLevel          the level needed to open the chest
     * @param keycardOnly       whether only presented keycards count
     * @param automation        the installation's automation policy (null = off, or unlinked)
     * @param automationAllowed whether automation can reach this chest right now
     * @param lockdown          whether the installation is sealed by lockdown
     * @param linked            whether the chest is linked to an installation
     * @param viewerLevel       the viewer's effective level (OWNER for an operator, and on an unlinked chest)
     */
    public record ChestSnapshot(BlockPos pos, AccessLevel minLevel, boolean keycardOnly,
            @Nullable AccessLevel automation, boolean automationAllowed, boolean lockdown, boolean linked,
            AccessLevel viewerLevel) implements CustomPacketPayload {

        public static final Type<ChestSnapshot> TYPE = new Type<>(id("chest_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, ChestSnapshot> STREAM_CODEC =
                StreamCodec.of(ChestSnapshot::write, ChestSnapshot::read);

        private static void write(FriendlyByteBuf buf, ChestSnapshot value) {
            buf.writeBlockPos(value.pos);
            writeLevel(buf, value.minLevel);
            buf.writeBoolean(value.keycardOnly);
            writeOptionalLevel(buf, value.automation);
            buf.writeBoolean(value.automationAllowed);
            buf.writeBoolean(value.lockdown);
            buf.writeBoolean(value.linked);
            writeLevel(buf, value.viewerLevel);
        }

        private static ChestSnapshot read(FriendlyByteBuf buf) {
            return new ChestSnapshot(buf.readBlockPos(), readLevel(buf), buf.readBoolean(), readOptionalLevel(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), readLevel(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set the chest's minimum level and keycard-only flag. Re-authorised and bound to the open menu. */
    public record ChestConfigure(BlockPos pos, AccessLevel minLevel, boolean keycardOnly)
            implements CustomPacketPayload {

        public static final Type<ChestConfigure> TYPE = new Type<>(id("chest_configure"));
        public static final StreamCodec<FriendlyByteBuf, ChestConfigure> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBlockPos(v.pos);
                    writeLevel(buf, v.minLevel);
                    buf.writeBoolean(v.keycardOnly);
                },
                buf -> new ChestConfigure(buf.readBlockPos(), readLevel(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
