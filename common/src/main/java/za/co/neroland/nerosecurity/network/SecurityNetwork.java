package za.co.neroland.nerosecurity.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.client.ClientFieldPassability;
import za.co.neroland.nerosecurity.client.ClientHubState;
import za.co.neroland.nerosecurity.client.ClientProgrammerState;
import za.co.neroland.nerosecurity.client.screen.alarm.ClientAlarmState;
import za.co.neroland.nerosecurity.client.screen.chest.ClientChestState;
import za.co.neroland.nerosecurity.client.screen.door.ClientDoorState;
import za.co.neroland.nerosecurity.client.screen.field.ClientFieldState;
import za.co.neroland.nerosecurity.client.screen.monitor.ClientMonitorState;
import za.co.neroland.nerosecurity.client.screen.sensor.ClientSensorState;
import za.co.neroland.nerosecurity.feedback.FeedbackPayloads;
import za.co.neroland.nerosecurity.menu.HubPayloadHandlers;
import za.co.neroland.nerosecurity.menu.HubPayloads;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads;
import za.co.neroland.nerosecurity.menu.monitor.MonitorPayloads;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads;

/**
 * The cross-loader payload registry: NeroSecurity declares its payloads here once (type + stream
 * codec + handler), and each loader module iterates the lists and wires them to its own networking
 * API — NeoForge's {@code PayloadRegistrar}, Forge's {@code ChannelBuilder}, Fabric's
 * {@code PayloadTypeRegistry} + {@code Client/ServerPlayNetworking}. Sending goes through the
 * {@link Services#NETWORK} seam. The channel is {@code nerosecurity:main}.
 *
 * <p>This is Neroland Core's {@code CoreNetwork} architecture reproduced on NeroSecurity's own
 * channel. It cannot reuse Core's instance: Core drains its payload lists during Core's own
 * bootstrap (on Forge the channel is {@code build()}-sealed inside Core's constructor), so a
 * downstream registration would be silently dropped — see
 * {@link za.co.neroland.nerosecurity.platform.NetworkPlatform} for the full reasoning.
 *
 * <p>Declared today: the Security Hub screen's snapshot (server to client, display only) and its four
 * edit intents (client to server), plus the keycard programmer, door, secure chest, sensor settings, alarm, monitor and force field screens' payloads (and the field passability sync). New features add {@code clientbound(...)}/{@code serverbound(...)}
 * lines to {@link #init()}; the loader registration passes iterate whatever is declared.
 * NeroSecurity is server-authoritative end to end: access decisions are made server-side and the
 * client renders what it is told.
 */
public final class SecurityNetwork {

    /** A server &rarr; client payload plus the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /** A client &rarr; server payload plus the server-side handler (with the sending player). */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private static boolean declared;

    private SecurityNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    /** Every declared server &rarr; client payload, for each loader's registration pass. */
    public static List<Clientbound<?>> clientbound() {
        return CLIENTBOUND;
    }

    /** Every declared client &rarr; server payload, for each loader's registration pass. */
    public static List<Serverbound<?>> serverbound() {
        return SERVERBOUND;
    }

    /**
     * Declares the payloads. Called once from common init, before any loader registers them (each
     * loader entry point runs common init first, then its own network registration). On Forge in
     * particular the channel is sealed at {@code build()}, so a payload declared later would never
     * exist.
     */
    public static void init() {
        if (declared) {
            return; // defensive: a second call must not duplicate registrations
        }
        declared = true;
        // Hub screen: one display snapshot down, four edit intents up (each re-authorised server-side).
        clientbound(HubPayloads.HubSnapshot.TYPE, HubPayloads.HubSnapshot.STREAM_CODEC, ClientHubState::accept);
        serverbound(HubPayloads.HubSetMember.TYPE, HubPayloads.HubSetMember.STREAM_CODEC,
                HubPayloadHandlers::handleSetMember);
        serverbound(HubPayloads.HubAddMember.TYPE, HubPayloads.HubAddMember.STREAM_CODEC,
                HubPayloadHandlers::handleAddMember);
        serverbound(HubPayloads.HubLockdown.TYPE, HubPayloads.HubLockdown.STREAM_CODEC,
                HubPayloadHandlers::handleLockdown);
        serverbound(HubPayloads.HubRename.TYPE, HubPayloads.HubRename.STREAM_CODEC,
                HubPayloadHandlers::handleRename);
        // Access devices. Each declarer adds its own snapshot + intents. Declaration order is not
        // load-bearing: every loader registers payloads by their unique Type id (NeoForge registrar,
        // Forge payloadChannel, Fabric PayloadTypeRegistry), never by list index.
        ProgrammerPayloads.declare();
        DoorPayloads.declare();
        ChestPayloads.declare();
        // Sensors and alerts: the shared sensor settings screen (laser and camera), the alarm and the monitor.
        SensorPayloads.declare();
        AlarmPayloads.declare();
        MonitorPayloads.declare();
        // Force fields: the force field settings screen, plus the local player's own field passability.
        FieldPayloads.declare();
        // Privacy and feedback: the hub's Privacy tab (bound to the open hub menu) and per-player device feedback.
        PrivacyPayloads.declare();
        FeedbackPayloads.declare();
    }

    /**
     * Drops every client-side mirror. Each loader calls this when the client leaves a world or
     * server, so one session's security state can never bleed into the next — or appear at all on a
     * server that does not run NeroSecurity. Today that is the hub, programmer, door, chest, sensor, alarm,
     * monitor and force field snapshot mirrors and the local player's field passability; any synced cache added later must be cleared here too.
     */
    public static void clearClientCaches() {
        ClientHubState.clear();
        ClientProgrammerState.clear();
        ClientDoorState.clear();
        ClientChestState.clear();
        ClientSensorState.clear();
        ClientAlarmState.clear();
        ClientMonitorState.clear();
        ClientFieldState.clear();
        ClientFieldPassability.clear();
    }
}
