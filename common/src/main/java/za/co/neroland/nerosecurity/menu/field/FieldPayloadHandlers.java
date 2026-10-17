package za.co.neroland.nerosecurity.menu.field;

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
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldConfigure;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Opening the force field settings and handling its configure intent.
 *
 * <p>It trusts nothing the client says: the sender's open container must be a {@link FieldMenu} bound to the same
 * emitter (else the payload is dropped), the installation is re-read from the store, the change is re-authorised
 * through the resolver ({@code CONFIGURE_DEVICE} at OFFICER, never keycard-filtered), and the geometry is re-validated
 * against the emitter's own range and the server's area cap. An unlinked emitter has no settings. Setting the field to
 * OWNER, or changing anything on a field that is already OWNER, needs an effective OWNER; while force fields are
 * disabled on the server nothing changes ({@link FieldConfigureRules}). A refused open sends the deny feedback (sound
 * and flash) beside the action-bar reason; a refused change keeps the screen open and answers with a snapshot
 * carrying {@code ok = false} and the reason, which also goes to chat. Failures fail closed and change nothing.
 */
public final class FieldPayloadHandlers {

    private FieldPayloadHandlers() {
    }

    /** A player used an emitter (server thread): refuse an unlinked one, resolve, send the snapshot, open the menu. */
    public static void openSettings(ServerPlayer player, ForceFieldEmitterBlockEntity emitter) {
        if (!(emitter.getLevel() instanceof ServerLevel level)) {
            return;
        }
        DevicePos at = DevicePos.of(level, emitter.getBlockPos());
        Installation installation = SecurityInstallations.get(level.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            player.sendOverlayMessage(Component.translatable("device.nerosecurity.unlinked"));
            return;
        }
        AccessDecision decision = resolveConfigure(player, installation, at);
        if (!decision.allowed()) {
            player.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(player, emitter.getBlockPos());
            return;
        }
        sendSnapshot(player, level, emitter, decision);
        MenuOpener.open(player, emitter);
    }

    /** The settings decision ({@code CONFIGURE_DEVICE} at OFFICER); re-run for every change and by the open menu. */
    public static AccessDecision resolveConfigure(ServerPlayer player, @Nullable Installation installation, DevicePos at) {
        AccessLevel required = installation == null ? AccessLevel.PUBLIC : AccessLevel.OFFICER;
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.CONFIGURE_DEVICE, required, at, null);
    }

    public static void sendSnapshot(ServerPlayer player, ServerLevel level, ForceFieldEmitterBlockEntity emitter,
            AccessDecision decision) {
        sendSnapshot(player, level, emitter, decision, true, null);
    }

    /** As above, carrying the outcome of the action that produced it for the screen's footer. */
    public static void sendSnapshot(ServerPlayer player, ServerLevel level, ForceFieldEmitterBlockEntity emitter,
            AccessDecision decision, boolean ok, @Nullable Component message) {
        Services.NETWORK.sendToPlayer(player, FieldSnapshots.build(level, emitter, decision, ok, message));
    }

    public static void handleConfigure(FieldConfigure payload, ServerPlayer player) {
        runSafely(player, "field_configure", () -> {
            ForceFieldEmitterBlockEntity emitter = bound(player, payload.pos());
            if (emitter == null || !(emitter.getLevel() instanceof ServerLevel level)) {
                return;
            }
            DevicePos at = DevicePos.of(level, emitter.getBlockPos());
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
                SecurityDevice.denyFeedback(player, emitter.getBlockPos());
                return;
            }
            FieldSettings current = emitter.settings();
            FieldSettings wanted = payload.settings();
            if (wanted.equals(current)) {
                sendSnapshot(player, level, emitter, decision);
                return;
            }
            boolean owner = decision.admin() || decision.effective() == AccessLevel.OWNER;
            Component refusal = FieldConfigureRules.refusal(NeroSecurityConfig.isForceFieldsEnabled(), owner, current,
                    wanted, emitter.range(), NeroSecurityConfig.maxFieldArea());
            if (refusal != null) {
                player.sendSystemMessage(refusal);
                sendSnapshot(player, level, emitter, decision, false, refusal);
                return;
            }
            emitter.setSettings(wanted);
            SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.DEVICE_CONFIGURED, installation.id(), at,
                    player.getUUID(), System.currentTimeMillis()));
            emitter.reconcileNow(level);
            Component done = Component.translatable("field.nerosecurity.configured");
            player.sendOverlayMessage(done);
            sendSnapshot(player, level, emitter, decision, true, done);
        });
    }

    /** The emitter the sender's open menu is bound to, if it is at {@code claimed}; null for a stale or forged payload. */
    private static @Nullable ForceFieldEmitterBlockEntity bound(ServerPlayer player, @Nullable BlockPos claimed) {
        if (!(player.containerMenu instanceof FieldMenu menu) || claimed == null
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale field payload dropped");
            return null;
        }
        ForceFieldEmitterBlockEntity emitter = menu.emitter();
        if (emitter == null || emitter.isRemoved() || emitter.getLevel() != player.level()
                || emitter.getLevel().getBlockEntity(claimed) != emitter) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale field payload dropped");
            return null;
        }
        return emitter;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. Logged and reported by payload type and exception class
     * only (never a name, UUID or position); the screen is closed and the sender told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] field payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "field_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
