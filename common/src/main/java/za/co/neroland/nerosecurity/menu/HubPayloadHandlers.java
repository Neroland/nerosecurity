package za.co.neroland.nerosecurity.menu;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubAddMember;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubLockdown;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubRename;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSetMember;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server-side handlers for the hub screen's payloads. All run on the server thread, and every one
 * starts from nothing the client claims: the sender's open container must be a hub menu bound to the
 * same installation (otherwise the payload is dropped silently), and the edit itself is authorised again
 * by {@link InstallationService} through the resolver.
 *
 * <ul>
 *   <li>{@code HubSetMember} only changes or removes an <b>existing</b> member row; a UUID the
 *       installation does not already list is refused, so a client can never insert arbitrary ids.</li>
 *   <li>{@code HubAddMember} adds by name, resolved against the <b>online</b> player list only; no
 *       network lookup ever runs on the server thread.</li>
 *   <li>Every handler is guarded: an unexpected exception fails closed (nothing further happens, the
 *       screen closes), is reported by payload type and exception class only, and the sender is told.</li>
 * </ul>
 *
 * <p>Every outcome re-sends the snapshot (so the screen's buttons re-enable) while the player may still
 * open the hub, and closes the screen when they no longer may. Failures are reported to the sender only.
 */
public final class HubPayloadHandlers {

    private HubPayloadHandlers() {
    }

    /**
     * Sends the hub snapshot to a player the resolver has just allowed to open the hub. Display names are
     * resolved locally (online list, then the in-memory profile cache) for this installation's own rows
     * only, and kept nowhere.
     */
    public static void sendSnapshot(ServerPlayer player, Installation installation, AccessDecision decision) {
        sendSnapshot(player, installation, decision, true, null);
    }

    /** As above, carrying the outcome of the action that produced it for the screen's footer. */
    public static void sendSnapshot(ServerPlayer player, Installation installation, AccessDecision decision,
            boolean ok, @Nullable Component message) {
        MinecraftServer server = player.level().getServer();
        Services.NETWORK.sendToPlayer(player, HubSnapshots.build(server, installation, decision, ok, message));
    }

    public static void handleSetMember(HubSetMember payload, ServerPlayer player) {
        runSafely(player, "hub_set_member", () -> {
            Installation installation = bound(player, payload.installationId());
            if (installation == null) {
                return;
            }
            UUID target = payload.target();
            ServiceResult result;
            if (target != null && !installation.owners().contains(target)
                    && !InstallationService.isExistingMemberRow(installation, target)) {
                // Additions go only through HubAddMember (by name); never insert a client-chosen id.
                result = ServiceResult.failure(Component.translatable("hub.nerosecurity.member.unknown_player"));
            } else {
                result = InstallationService.setMember(player, installation, target, payload.level());
            }
            finish(player, installation.id(), result);
        });
    }

    public static void handleAddMember(HubAddMember payload, ServerPlayer player) {
        runSafely(player, "hub_add_member", () -> {
            Installation installation = bound(player, payload.installationId());
            if (installation == null) {
                return;
            }
            Optional<UUID> target = PlayerNames.lookupOnline(player.level().getServer(), payload.playerName());
            ServiceResult result = target.isEmpty()
                    ? ServiceResult.failure(Component.translatable("hub.nerosecurity.member.not_online"))
                    : InstallationService.setMember(player, installation, target.get(), payload.level());
            finish(player, installation.id(), result);
        });
    }

    public static void handleLockdown(HubLockdown payload, ServerPlayer player) {
        runSafely(player, "hub_lockdown", () -> {
            Installation installation = bound(player, payload.installationId());
            if (installation == null) {
                return;
            }
            ServiceResult result = payload.enabled()
                    ? InstallationService.enterLockdown(player, installation)
                    : InstallationService.exitLockdown(player, installation);
            finish(player, installation.id(), result);
        });
    }

    public static void handleRename(HubRename payload, ServerPlayer player) {
        runSafely(player, "hub_rename", () -> {
            Installation installation = bound(player, payload.installationId());
            if (installation == null) {
                return;
            }
            finish(player, installation.id(), InstallationService.rename(player, installation, payload.name()));
        });
    }

    /**
     * The installation the sender's open hub menu is bound to, if it matches {@code claimed}; null (and a
     * debug line) for a stale or forged payload.
     */
    private static @Nullable Installation bound(ServerPlayer player, UUID claimed) {
        if (!(player.containerMenu instanceof SecurityHubMenu menu) || claimed == null
                || !claimed.equals(menu.installationId()) || !menu.stillValid(player)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale hub payload dropped");
            return null;
        }
        return SecurityInstallations.get(player.level().getServer()).byId(claimed).orElse(null);
    }

    /** Reports the outcome, then refreshes or closes the screen according to a fresh decision. */
    private static void finish(ServerPlayer player, UUID installationId, ServiceResult result) {
        if (!result.ok()) {
            player.sendSystemMessage(result.message());
        } else {
            player.sendOverlayMessage(result.message());
        }
        Installation fresh = SecurityInstallations.get(player.level().getServer()).byId(installationId).orElse(null);
        if (fresh == null) {
            player.closeContainer();
            return;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(new AccessQuery(player.level().getServer(),
                player.getUUID(), fresh, AccessAction.OPEN_HUB, fresh.hubPos()), AccessAction.OPEN_HUB.defaultMinimum);
        if (decision.allowed()) {
            sendSnapshot(player, fresh, decision, result.ok(), result.message());
        } else {
            player.closeContainer();
            player.sendSystemMessage(decision.message());
        }
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. It is logged and reported by payload type and
     * exception class only (never a name, UUID or position), the screen is closed, and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] hub payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "hub_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
