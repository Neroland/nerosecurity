package za.co.neroland.nerosecurity.menu.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.client.screen.monitor.ClientMonitorState;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The Security Monitor's payloads on {@code nerosecurity:main}: the clientbound
 * {@link MonitorSnapshot} ({@code nerosecurity:monitor_snapshot}) and the serverbound
 * {@link MonitorRefresh} ({@code nerosecurity:monitor_refresh}). Neither carries a player identity.
 */
public final class MonitorPayloads {

    private static boolean declared;

    private MonitorPayloads() {
    }

    /** Declares the monitor payloads with {@link SecurityNetwork}. Call once from network init. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(MonitorSnapshot.TYPE, MonitorSnapshot.STREAM_CODEC, ClientMonitorState::accept);
        SecurityNetwork.serverbound(MonitorRefresh.TYPE, MonitorRefresh.STREAM_CODEC,
                MonitorPayloadHandlers::handleRefresh);
    }

    /**
     * "Send me a fresh snapshot of the monitor I have open." Only a request: the server checks that the
     * sender's open menu is a {@link MonitorMenu} for {@code pos} and re-resolves OPEN before answering.
     */
    public record MonitorRefresh(BlockPos pos) implements CustomPacketPayload {

        public static final Type<MonitorRefresh> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "monitor_refresh"));
        public static final StreamCodec<FriendlyByteBuf, MonitorRefresh> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> buf.writeBlockPos(v.pos), buf -> new MonitorRefresh(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
