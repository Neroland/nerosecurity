package za.co.neroland.nerosecurity.menu.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.monitor.SecurityMonitorBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.monitor.MonitorPayloads.MonitorRefresh;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server side of the monitor screen. Viewing is VIEW_INSTALLATION at MEMBER at the monitor (the monitor shows
 * only actor kinds, so it is not a log read; a keycard does not grant it). A refresh trusts nothing the client says: the sender's open
 * container must be a {@link MonitorMenu} bound to the claimed position and still valid, the
 * installation is re-read from the store, OPEN is re-resolved, and refreshes are rate-limited per open
 * menu. Failures fail closed in the {@code runSafely} shape.
 */
public final class MonitorPayloadHandlers {

    private MonitorPayloadHandlers() {
    }

    /** The decision for viewing the monitor at {@code at}: VIEW_INSTALLATION at MEMBER (membership, never a card). */
    public static AccessDecision resolveView(ServerPlayer player, @Nullable Installation installation, DevicePos at) {
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.VIEW_INSTALLATION, AccessLevel.MEMBER,
                at, null);
    }

    /** Builds and sends the snapshot to a player who was just allowed to view the monitor. */
    public static void sendSnapshot(ServerPlayer player, SecurityMonitorBlockEntity monitor, ServerLevel level,
            Installation installation) {
        Services.NETWORK.sendToPlayer(player, MonitorSnapshots.build(level, installation, monitor.getBlockPos()));
    }

    public static void handleRefresh(MonitorRefresh payload, ServerPlayer player) {
        runSafely(player, "monitor_refresh", () -> {
            MonitorMenu menu = bound(player, payload.pos());
            if (menu == null) {
                return;
            }
            SecurityMonitorBlockEntity monitor = menu.monitor();
            if (monitor == null || !(monitor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            if (!menu.tryRefresh(level.getGameTime())) {
                return; // too soon after the last one; the screen re-enables its button by itself
            }
            DevicePos at = DevicePos.of(level, monitor.getBlockPos());
            Installation installation = SecurityInstallations.get(level.getServer()).atDevice(at).orElse(null);
            if (installation == null) {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = resolveView(player, installation, at);
            if (!decision.allowed()) {
                player.closeContainer();
                player.sendSystemMessage(decision.message());
                return;
            }
            sendSnapshot(player, monitor, level, installation);
        });
    }

    /** The sender's open monitor menu if it is bound to {@code claimed} and still valid; else null. */
    private static @Nullable MonitorMenu bound(ServerPlayer player, @Nullable BlockPos claimed) {
        if (!(player.containerMenu instanceof MonitorMenu menu) || claimed == null
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale monitor payload dropped");
            return null;
        }
        SecurityMonitorBlockEntity monitor = menu.monitor();
        if (monitor == null || monitor.getLevel() != player.level()
                || monitor.getLevel().getBlockEntity(claimed) != monitor) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale monitor payload dropped");
            return null;
        }
        return menu;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. Logged and reported by payload type and
     * exception class only (never a name, UUID or position); the screen is closed and the sender told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] monitor payload {} failed ({}); nothing was sent.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "monitor_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
