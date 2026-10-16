package za.co.neroland.nerosecurity.menu.chest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.block.chest.GatedChestAccess;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestConfigure;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestSnapshot;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server side of the secure chest settings screen. Everything starts from nothing the client claims:
 * the sender's open menu must be a {@link ChestSettingsMenu} bound to the same position, the block there
 * must still be a secure chest, the installation is looked up again from the store, and the change is
 * authorised again by the resolver ({@code CONFIGURE_DEVICE} at OFFICER, never keycard filtered).
 *
 * <p>An unlinked chest has no settings screen: it guards nothing until it is linked, and linking resets
 * its settings to the defaults, so nobody can pre-configure a chest before its owner links it. Every
 * handler is guarded: an
 * unexpected exception fails closed, is reported by payload type and exception class only, and the
 * screen closes.
 */
public final class ChestPayloadHandlers {

    private ChestPayloadHandlers() {
    }

    /** Sneak-use on a secure chest: authorise, send the snapshot, open the settings menu. */
    public static void openSettings(ServerPlayer player, SecureChestBlockEntity chest) {
        runSafely(player, "chest_open_settings", () -> {
            Installation installation = chest.installation();
            if (installation == null) {
                player.sendOverlayMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = resolveConfigure(player, chest, installation);
            if (!decision.allowed()) {
                player.sendOverlayMessage(decision.message());
                SecurityDevice.denyFeedback(player, chest.getBlockPos());
                return;
            }
            BlockPos pos = chest.getBlockPos().immutable();
            sendSnapshot(player, chest, installation, decision);
            MenuOpener.open(player, new SimpleMenuProvider(
                    (containerId, inventory, opener) -> new ChestSettingsMenu(containerId, inventory, pos,
                            ContainerLevelAccess.create(chest.getLevel(), pos)),
                    Component.translatable("chest.nerosecurity.title")));
        });
    }

    public static void handleConfigure(ChestConfigure payload, ServerPlayer player) {
        runSafely(player, "chest_configure", () -> {
            SecureChestBlockEntity chest = bound(player, payload.pos());
            if (chest == null) {
                return;
            }
            Installation installation = chest.installation();
            DeviceAccessSettings current = chest.settings();
            DeviceAccessSettings wanted = new DeviceAccessSettings(payload.minLevel(), payload.keycardOnly());
            if (installation == null) {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = resolveConfigure(player, chest, installation);
            if (!decision.allowed()) {
                player.closeContainer();
                player.sendSystemMessage(decision.message());
                return;
            }
            boolean ownerOrAdmin = decision.admin() || decision.effective() == AccessLevel.OWNER;
            if (!DeviceSettingsRules.mayApply(ownerOrAdmin, current.minLevel(), wanted.minLevel())) {
                Component refusal = Component.translatable("chest.nerosecurity.owner_only_setting");
                player.sendSystemMessage(refusal);
                refresh(player, chest, false, refusal);
                return;
            }
            if (!wanted.equals(current)) {
                chest.applySettings(wanted);
                if (player.level() instanceof ServerLevel level) {
                    SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.DEVICE_CONFIGURED, installation.id(),
                            DevicePos.of(level, chest.getBlockPos()), player.getUUID(), System.currentTimeMillis()));
                }
            }
            Component done = Component.translatable("chest.nerosecurity.configured");
            player.sendOverlayMessage(done);
            refresh(player, chest, true, done);
        });
    }

    /** Sends the display snapshot. {@code decision} is null for an unlinked chest. */
    static void sendSnapshot(ServerPlayer player, SecureChestBlockEntity chest, @Nullable Installation installation,
            @Nullable AccessDecision decision) {
        sendSnapshot(player, chest, installation, decision, true, null);
    }

    /** As above, carrying the outcome of the action that produced it for the screen's footer. */
    static void sendSnapshot(ServerPlayer player, SecureChestBlockEntity chest, @Nullable Installation installation,
            @Nullable AccessDecision decision, boolean ok, @Nullable Component message) {
        DeviceAccessSettings settings = chest.settings();
        AccessLevel viewer = decision == null ? AccessLevel.OWNER : decision.effective();
        boolean lockdown = SecureChestBlockEntity.lockdownActive(installation);
        AccessLevel policy = installation == null ? null : installation.automationLevel();
        Services.NETWORK.sendToPlayer(player, new ChestSnapshot(chest.getBlockPos().immutable(), settings.minLevel(),
                settings.keycardOnly(), policy, chest.isAutomationAllowed(), lockdown, installation != null, viewer,
                GatedChestAccess.why(installation != null, policy, settings, lockdown), ok, message));
    }

    /** Re-sends the snapshot while the player may still configure the chest; closes the screen otherwise. */
    private static void refresh(ServerPlayer player, SecureChestBlockEntity chest, boolean ok,
            @Nullable Component message) {
        Installation installation = chest.installation();
        if (installation == null) {
            sendSnapshot(player, chest, null, null, ok, message);
            return;
        }
        AccessDecision decision = resolveConfigure(player, chest, installation);
        if (decision.allowed()) {
            sendSnapshot(player, chest, installation, decision, ok, message);
        } else {
            player.closeContainer();
            player.sendSystemMessage(decision.message());
        }
    }

    private static AccessDecision resolveConfigure(ServerPlayer player, SecureChestBlockEntity chest,
            Installation installation) {
        DevicePos at = player.level() instanceof ServerLevel level ? DevicePos.of(level, chest.getBlockPos()) : null;
        // Never keycard filtered: the owner must always be able to reach the settings.
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.CONFIGURE_DEVICE,
                AccessAction.CONFIGURE_DEVICE.defaultMinimum, at, null);
    }

    /**
     * The secure chest the sender's open settings menu is bound to, if it matches {@code claimed}; null
     * (and a debug line) for a stale or forged payload.
     */
    private static @Nullable SecureChestBlockEntity bound(ServerPlayer player, @Nullable BlockPos claimed) {
        if (claimed == null || !(player.containerMenu instanceof ChestSettingsMenu menu)
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(claimed) instanceof SecureChestBlockEntity chest)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale chest payload dropped");
            return null;
        }
        return chest;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. Logged and reported by payload type and
     * exception class only (never a name, UUID or position); the screen closes and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] chest payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "chest_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
