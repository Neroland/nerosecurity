package za.co.neroland.nerosecurity.menu.sensor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads.SensorConfigure;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads.SensorSnapshot;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.sensor.SensorSettings;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Opening the shared sensor settings screen and handling its configure intent, for both sensors.
 *
 * <p>It trusts nothing the client says: the sender's open container must be a {@link SensorSettingsMenu}
 * bound to the same sensor position (else the payload is dropped), the installation is re-read from the
 * store, and the change is re-authorised through the resolver ({@code CONFIGURE_DEVICE} at OFFICER,
 * never keycard-filtered). An unlinked sensor has no settings. Setting a sensor to OWNER, or changing
 * anything on a sensor that is already OWNER, needs an effective OWNER ({@link SensorSettings#mayApply}).
 * A laser keeps its field of view whatever the client sends. Failures fail closed.
 */
public final class SensorPayloadHandlers {

    private SensorPayloadHandlers() {
    }

    /**
     * A player used a sensor (server thread): refuse an unlinked sensor, resolve {@code CONFIGURE_DEVICE}
     * at OFFICER, then send the snapshot and open the shared menu. The camera calls this too.
     */
    public static void openSettings(ServerPlayer player, SensorMenuHost host) {
        BlockEntity be = host.sensorBlockEntity();
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        DevicePos at = DevicePos.of(level, be.getBlockPos());
        Installation installation = SecurityInstallations.get(level.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            player.sendOverlayMessage(Component.translatable("device.nerosecurity.unlinked"));
            return;
        }
        AccessDecision decision = resolveConfigure(player, installation, at);
        if (!decision.allowed()) {
            player.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(player, be.getBlockPos());
            return;
        }
        sendSnapshot(player, host, installation, decision);
        MenuOpener.open(player, host);
    }

    /** The settings-screen decision; the payload handler re-runs it for every change. */
    public static AccessDecision resolveConfigure(ServerPlayer player, @Nullable Installation installation, DevicePos at) {
        AccessLevel required = installation == null ? AccessLevel.PUBLIC : AccessLevel.OFFICER;
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.CONFIGURE_DEVICE, required, at, null);
    }

    /** Sends the sensor snapshot to a player the resolver has just allowed to configure the sensor. */
    public static void sendSnapshot(ServerPlayer player, SensorMenuHost host, @Nullable Installation installation,
            AccessDecision decision) {
        sendSnapshot(player, host, installation, decision, true, null);
    }

    /** As above, carrying the outcome of the action that produced it for the screen's footer. */
    public static void sendSnapshot(ServerPlayer player, SensorMenuHost host, @Nullable Installation installation,
            AccessDecision decision, boolean ok, @Nullable Component message) {
        Services.NETWORK.sendToPlayer(player, new SensorSnapshot(host.sensorBlockEntity().getBlockPos(),
                host.sensorSettings(), host.isLaser(), installation != null,
                decision.admin() ? AccessLevel.OWNER : decision.effective(), ok, message));
    }

    public static void handleConfigure(SensorConfigure payload, ServerPlayer player) {
        runSafely(player, "sensor_configure", () -> {
            SensorMenuHost host = bound(player, payload.pos());
            if (host == null || !(host.sensorBlockEntity().getLevel() instanceof ServerLevel level)) {
                return;
            }
            DevicePos at = DevicePos.of(level, host.sensorBlockEntity().getBlockPos());
            Installation installation = SecurityInstallations.get(level.getServer()).atDevice(at).orElse(null);
            if (installation == null) {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = resolveConfigure(player, installation, at);
            if (!decision.allowed()) {
                player.closeContainer();
                player.sendSystemMessage(decision.message());
                return;
            }
            SensorSettings current = host.sensorSettings();
            SensorSettings wanted = payload.settings();
            if (host.isLaser()) {
                wanted = wanted.withFov(current.fovDegrees()); // lasers have no field of view
            }
            boolean owner = decision.admin() || decision.effective() == AccessLevel.OWNER;
            if (!SensorSettings.mayApply(true, owner, current, wanted)) {
                Component refusal = Component.translatable("door.nerosecurity.owner_only_setting");
                player.sendSystemMessage(refusal);
                sendSnapshot(player, host, installation, decision, false, refusal);
                return;
            }
            Component done = null;
            if (!wanted.equals(current)) {
                host.setSensorSettings(wanted);
                SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.DEVICE_CONFIGURED, installation.id(), at,
                        player.getUUID(), System.currentTimeMillis()));
                done = Component.translatable("sensor.nerosecurity.configured");
                player.sendOverlayMessage(done);
            }
            sendSnapshot(player, host, installation, decision, true, done);
        });
    }

    /**
     * The sensor the sender's open menu is bound to, if it is at {@code claimed}; null (and a debug line)
     * for a stale or forged payload.
     */
    private static @Nullable SensorMenuHost bound(ServerPlayer player, @Nullable BlockPos claimed) {
        if (!(player.containerMenu instanceof SensorSettingsMenu menu) || claimed == null
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale sensor payload dropped");
            return null;
        }
        SensorMenuHost host = menu.host();
        BlockEntity be = host == null ? null : host.sensorBlockEntity();
        if (be == null || be.getLevel() != player.level() || be.getLevel().getBlockEntity(claimed) != be) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale sensor payload dropped");
            return null;
        }
        return host;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. It is logged and reported by payload type and
     * exception class only (never a name, UUID or position), the screen is closed, and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] sensor payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "sensor_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
