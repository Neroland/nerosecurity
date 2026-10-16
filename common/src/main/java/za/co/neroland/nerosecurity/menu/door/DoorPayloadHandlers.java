package za.co.neroland.nerosecurity.menu.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.block.door.RedstoneMode;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorConfigure;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorSnapshot;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server-side handler for the door settings screen. It trusts nothing the client says: the sender's
 * open container must be a {@link SecurityDoorMenu} bound to the same controller position (else the
 * payload is dropped), the installation is re-read from the store, and the change is re-authorised
 * through the resolver ({@code CONFIGURE_DEVICE} at OFFICER, never keycard-filtered). An unlinked door
 * has no settings. Setting a door to OWNER, or changing anything on a door that is already OWNER, needs
 * an effective OWNER ({@link DeviceSettingsRules}), so an officer can neither lock themselves out nor
 * lower an owner's door. Failures fail closed.
 */
public final class DoorPayloadHandlers {

    private DoorPayloadHandlers() {
    }

    /** Sends the door snapshot to a player the resolver has just allowed to configure the door. */
    public static void sendSnapshot(ServerPlayer player, SecurityDoorBlockEntity door,
            @Nullable Installation installation, AccessDecision decision) {
        sendSnapshot(player, door, installation, decision, true, null);
    }

    /** As above, carrying the outcome of the action that produced it for the screen's footer. */
    public static void sendSnapshot(ServerPlayer player, SecurityDoorBlockEntity door,
            @Nullable Installation installation, AccessDecision decision, boolean ok, @Nullable Component message) {
        DeviceAccessSettings settings = door.settings();
        Services.NETWORK.sendToPlayer(player, new DoorSnapshot(door.getBlockPos(), settings.minLevel(),
                settings.keycardOnly(), door.redstoneMode(), door.getEnergy().getAmount(),
                door.getEnergy().getCapacity(), SecurityDoorBlockEntity.lockdownActive(installation),
                installation != null, decision.admin() ? AccessLevel.OWNER : decision.effective(),
                installation == null ? "" : installation.name(), NeroSecurityConfig.doorRedstoneAllowed(), ok, message));
    }

    public static void handleConfigure(DoorConfigure payload, ServerPlayer player) {
        runSafely(player, "door_configure", () -> {
            SecurityDoorBlockEntity door = bound(player, payload.pos());
            if (door == null || !(door.getLevel() instanceof ServerLevel level)) {
                return;
            }
            DevicePos at = DevicePos.of(level, door.getBlockPos());
            Installation installation = SecurityInstallations.get(level.getServer()).atDevice(at).orElse(null);
            if (installation == null) {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = SecurityDoorBlockEntity.resolveConfigure(player, installation, at);
            if (!decision.allowed()) {
                player.closeContainer();
                player.sendSystemMessage(decision.message());
                return;
            }
            AccessLevel minLevel = payload.minLevel() == null ? AccessLevel.MEMBER : payload.minLevel();
            boolean owner = decision.admin() || decision.effective() == AccessLevel.OWNER;
            if (!DeviceSettingsRules.mayApply(owner, door.settings().minLevel(), minLevel)) {
                Component refusal = Component.translatable("door.nerosecurity.owner_only_setting");
                player.sendSystemMessage(refusal);
                sendSnapshot(player, door, installation, decision, false, refusal);
                return;
            }
            RedstoneMode mode = payload.mode() == null ? RedstoneMode.IGNORE : payload.mode();
            if (mode == RedstoneMode.OPEN_WHILE_POWERED && !NeroSecurityConfig.doorRedstoneAllowed()) {
                mode = door.redstoneMode() == RedstoneMode.OPEN_WHILE_POWERED ? mode : RedstoneMode.IGNORE;
            }
            door.applySettings(new DeviceAccessSettings(minLevel, payload.keycardOnly()), mode);
            SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.DEVICE_CONFIGURED, installation.id(), at,
                    player.getUUID(), System.currentTimeMillis()));
            Component done = Component.translatable("door.nerosecurity.configured");
            player.sendOverlayMessage(done);
            sendSnapshot(player, door, installation, decision, true, done);
        });
    }

    /**
     * The door the sender's open menu is bound to, if it is at {@code claimed}; null (and a debug line)
     * for a stale or forged payload.
     */
    private static @Nullable SecurityDoorBlockEntity bound(ServerPlayer player, @Nullable BlockPos claimed) {
        if (!(player.containerMenu instanceof SecurityDoorMenu menu) || claimed == null
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale door payload dropped");
            return null;
        }
        SecurityDoorBlockEntity door = menu.door();
        if (door == null || door.getLevel() != player.level()
                || door.getLevel().getBlockEntity(claimed) != door) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale door payload dropped");
            return null;
        }
        return door;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. It is logged and reported by payload type and
     * exception class only (never a name, UUID or position), the screen is closed, and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] door payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "door_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
