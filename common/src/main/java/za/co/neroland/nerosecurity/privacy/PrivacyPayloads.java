package za.co.neroland.nerosecurity.privacy;

import io.netty.handler.codec.DecoderException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.client.ClientHubState;
import za.co.neroland.nerosecurity.network.SecurityNetwork;

/**
 * The hub Privacy tab's payloads on {@code nerosecurity:main}. Both serverbound payloads are bound to the
 * sender's open hub menu (they name no installation: the menu does), re-resolved on every receipt, rate
 * limited per menu, and answered with a {@link PrivacySnapshot} to the sender only. No payload here carries a
 * player id, a name, a position of a person or a single audit row: only the installation's counts and the
 * server's settings.
 */
public final class PrivacyPayloads {

    private static boolean declared;

    private PrivacyPayloads() {
    }

    /** Declares the privacy payloads with {@link SecurityNetwork}. Call once from network init. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(PrivacySnapshot.TYPE, PrivacySnapshot.STREAM_CODEC, ClientHubState::acceptPrivacy);
        SecurityNetwork.serverbound(HubPrivacyRequest.TYPE, HubPrivacyRequest.STREAM_CODEC,
                PrivacyPayloadHandlers::handleRequest);
        SecurityNetwork.serverbound(HubPrivacyAction.TYPE, HubPrivacyAction.STREAM_CODEC,
                PrivacyPayloadHandlers::handleAction);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    /** The three actions the tab can ask for. Erasure is not one of them (it is Core's chat command). */
    public enum PrivacyAction {
        EXPORT,
        OPT_OUT,
        OPT_IN;

        /** The wire byte: 0 EXPORT, 1 OPT_OUT, 2 OPT_IN. */
        public byte code() {
            return (byte) ordinal();
        }

        /** The action for a wire byte; anything else is a decode error. */
        public static PrivacyAction byCode(int code) {
            PrivacyAction[] all = values();
            if (code < 0 || code >= all.length) {
                throw new DecoderException("unknown privacy action");
            }
            return all[code];
        }
    }

    /**
     * What the installation records, for the Privacy tab: server settings and this installation's counts only.
     *
     * @param auditEnabled      the audit log is on on this server
     * @param retentionDays     effective audit retention for this installation, days
     * @param maxEntries        the per-installation row cap
     * @param entryCount        rows this installation holds now
     * @param oldestAgeSeconds  age of its oldest row (0 when none); relative, never an absolute time
     * @param camerasEnabled    cameras are on on this server
     * @param cameraLogsMembers permitted members seen by cameras are logged too (the amber line)
     * @param viewerOptedOut    the viewer has asked not to be named (from {@link PrivacyActions#status})
     * @param actionsAvailable  the actions do something on this server yet
     * @param ok                whether {@code message} reports a success
     * @param message           the outcome of the action that produced this snapshot, or null
     */
    public record PrivacySnapshot(boolean auditEnabled, int retentionDays, int maxEntries, int entryCount,
            int oldestAgeSeconds, boolean camerasEnabled, boolean cameraLogsMembers, boolean viewerOptedOut,
            boolean actionsAvailable, boolean ok, @Nullable Component message) implements CustomPacketPayload {

        public static final Type<PrivacySnapshot> TYPE = new Type<>(id("privacy_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PrivacySnapshot> STREAM_CODEC =
                StreamCodec.of(PrivacySnapshot::write, PrivacySnapshot::read);

        public PrivacySnapshot {
            retentionDays = Math.max(0, retentionDays);
            maxEntries = Math.max(0, maxEntries);
            entryCount = Math.max(0, entryCount);
            oldestAgeSeconds = Math.max(0, oldestAgeSeconds);
        }

        private static void write(RegistryFriendlyByteBuf buf, PrivacySnapshot v) {
            buf.writeBoolean(v.auditEnabled);
            buf.writeVarInt(v.retentionDays);
            buf.writeVarInt(v.maxEntries);
            buf.writeVarInt(v.entryCount);
            buf.writeVarInt(v.oldestAgeSeconds);
            buf.writeBoolean(v.camerasEnabled);
            buf.writeBoolean(v.cameraLogsMembers);
            buf.writeBoolean(v.viewerOptedOut);
            buf.writeBoolean(v.actionsAvailable);
            buf.writeBoolean(v.ok);
            buf.writeBoolean(v.message != null);
            if (v.message != null) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, v.message);
            }
        }

        private static PrivacySnapshot read(RegistryFriendlyByteBuf buf) {
            boolean auditEnabled = buf.readBoolean();
            int days = buf.readVarInt();
            int max = buf.readVarInt();
            int count = buf.readVarInt();
            int oldest = buf.readVarInt();
            boolean cameras = buf.readBoolean();
            boolean members = buf.readBoolean();
            boolean optedOut = buf.readBoolean();
            boolean available = buf.readBoolean();
            boolean ok = buf.readBoolean();
            Component message = buf.readBoolean() ? ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf) : null;
            return new PrivacySnapshot(auditEnabled, days, max, count, oldest, cameras, members, optedOut, available,
                    ok, message);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "Send me the Privacy tab for the hub I have open." No fields: the open menu names the installation. */
    public record HubPrivacyRequest() implements CustomPacketPayload {

        public static final HubPrivacyRequest INSTANCE = new HubPrivacyRequest();
        public static final Type<HubPrivacyRequest> TYPE = new Type<>(id("hub_privacy_request"));
        public static final StreamCodec<FriendlyByteBuf, HubPrivacyRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One privacy action for the asking player only (0 EXPORT, 1 OPT_OUT, 2 OPT_IN; anything else is refused). */
    public record HubPrivacyAction(PrivacyAction action) implements CustomPacketPayload {

        public static final Type<HubPrivacyAction> TYPE = new Type<>(id("hub_privacy_action"));
        public static final StreamCodec<FriendlyByteBuf, HubPrivacyAction> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> buf.writeByte(v.action.code()),
                buf -> new HubPrivacyAction(PrivacyAction.byCode(buf.readByte())));

        public HubPrivacyAction {
            if (action == null) {
                throw new IllegalArgumentException("action must not be null");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
