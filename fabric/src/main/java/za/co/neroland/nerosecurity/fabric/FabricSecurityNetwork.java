package za.co.neroland.nerosecurity.fabric;

//? if >=1.20.5 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.network.SecurityNetwork;
import za.co.neroland.nerosecurity.platform.NetworkPlatform;

/**
 * Fabric side of the networking seam. {@link #registerCommon()} (mod init, both sides) registers the
 * payload types; {@link #registerClient()} (client init) registers the receivers, keeping
 * {@code ClientPlayNetworking} off the dedicated server. Registered as the {@link NetworkPlatform}
 * implementation via {@code META-INF/services}.
 *
 * <p>Receivers hop to the client thread via {@code context.client().execute}, which is what makes
 * plain-data client mirror caches safe without any locking. The registration passes iterate
 * whatever {@link SecurityNetwork} declares — an empty list (the current foundation state) simply
 * registers nothing.
 */
public final class FabricSecurityNetwork implements NetworkPlatform {

    /**
     * Mod-init (both sides): the clientbound payload <em>types</em>, plus the serverbound types and
     * their receivers. Serverbound receivers belong here rather than in {@link #registerClient()},
     * because they are what a dedicated server needs — and keeping them out of the client method is
     * what stops {@code ClientPlayNetworking} ever loading on one.
     */
    public static void registerCommon() {
        for (SecurityNetwork.Clientbound<?> cb : SecurityNetwork.clientbound()) {
            registerClientboundType(cb);
        }
        for (SecurityNetwork.Serverbound<?> sb : SecurityNetwork.serverbound()) {
            registerServerbound(sb);
        }
    }

    /** Client-init: clientbound receivers (client-only API). */
    public static void registerClient() {
        for (SecurityNetwork.Clientbound<?> cb : SecurityNetwork.clientbound()) {
            registerClientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            SecurityNetwork.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            SecurityNetwork.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            SecurityNetwork.Serverbound<T> sb) {
        PayloadTypeRegistry.serverboundPlay().register(sb.type(), sb.codec());
        ServerPlayNetworking.registerGlobalReceiver(sb.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            // Hop to the server thread: every intent handler touches saved data.
            player.level().getServer().execute(() -> sb.handler().accept(payload, player));
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
//?} else {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import za.co.neroland.nerosecurity.network.SecurityNetwork;
import za.co.neroland.nerosecurity.platform.NetworkPlatform;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerolandcore.network.legacy.LegacyStreamCodec;

public final class FabricSecurityNetwork implements NetworkPlatform {
    public static void registerCommon() {
        SecurityNetwork.serverbound().forEach(FabricSecurityNetwork::registerServerbound);
    }

    public static void registerClient() {
        SecurityNetwork.clientbound().forEach(FabricSecurityNetwork::registerClientbound);
    }

    private static <T extends LegacyPayload> void registerServerbound(SecurityNetwork.Serverbound<T> packet) {
        ServerPlayNetworking.registerGlobalReceiver(packet.type().id(), (server, player, handler, buffer, sender) -> {
            T message = packet.codec().decode(buffer);
            server.execute(() -> packet.handler().accept(message, player));
        });
    }

    private static <T extends LegacyPayload> void registerClientbound(SecurityNetwork.Clientbound<T> packet) {
        ClientPlayNetworking.registerGlobalReceiver(packet.type().id(), (client, handler, buffer, sender) -> {
            T message = packet.codec().decode(buffer);
            client.execute(() -> packet.handler().accept(message));
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        var id = payload.type().id();
        if (!ServerPlayNetworking.canSend(player, id)) {
            return;
        }
        for (var packet : SecurityNetwork.clientbound()) {
            if (packet.type().equals(payload.type())) {
                ServerPlayNetworking.send(player, id, encodeClientbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered clientbound payload " + id);
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var id = payload.type().id();
        if (!ClientPlayNetworking.canSend(id)) {
            return;
        }
        for (var packet : SecurityNetwork.serverbound()) {
            if (packet.type().equals(payload.type())) {
                ClientPlayNetworking.send(id, encodeServerbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered serverbound payload " + id);
    }

    private static <T extends LegacyPayload> FriendlyByteBuf encodeClientbound(
            SecurityNetwork.Clientbound<T> packet, LegacyPayload payload) {
        return encode(packet.type(), packet.codec(), payload);
    }

    private static <T extends LegacyPayload> FriendlyByteBuf encodeServerbound(
            SecurityNetwork.Serverbound<T> packet, LegacyPayload payload) {
        return encode(packet.type(), packet.codec(), payload);
    }

    private static <T extends LegacyPayload> FriendlyByteBuf encode(LegacyPayload.Type<T> type,
            LegacyStreamCodec<? super FriendlyByteBuf, T> codec, LegacyPayload payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        codec.encode(buffer, type.payloadClass().cast(payload));
        return buffer;
    }
}
*///?}
