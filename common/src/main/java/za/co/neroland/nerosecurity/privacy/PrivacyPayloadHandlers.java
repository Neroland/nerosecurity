package za.co.neroland.nerosecurity.privacy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.audit.RetentionPolicy;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.SecurityHubMenu;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.HubPrivacyAction;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.HubPrivacyRequest;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacySnapshot;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server side of the hub's Privacy tab. Trusts nothing the client says: the sender's open container must be a
 * {@link SecurityHubMenu} that is still valid, the installation is re-read from the store by the menu's id,
 * OPEN at OFFICER is re-resolved at the hub (the same rule as opening the hub), and at most one request or
 * action is answered per {@link SecurityHubMenu#PRIVACY_MIN_INTERVAL_TICKS} per open menu. Actions go through
 * {@link PrivacyActions#current()} and act on the sender only. Every handler is guarded by {@code runSafely}.
 */
public final class PrivacyPayloadHandlers {

    private PrivacyPayloadHandlers() {
    }

    public static void handleRequest(HubPrivacyRequest payload, ServerPlayer player) {
        runSafely(player, "hub_privacy_request", () -> {
            Installation installation = authorised(player);
            if (installation != null) {
                send(player, installation, true, null);
            }
        });
    }

    public static void handleAction(HubPrivacyAction payload, ServerPlayer player) {
        runSafely(player, "hub_privacy_action", () -> {
            Installation installation = authorised(player);
            if (installation == null) {
                return;
            }
            PrivacyActions actions = PrivacyActions.current();
            PrivacyActions.Outcome outcome = switch (payload.action()) {
                case EXPORT -> actions.export(player);
                case OPT_OUT -> actions.setOptOut(player, true);
                case OPT_IN -> actions.setOptOut(player, false);
            };
            if (outcome == null) {
                outcome = new PrivacyActions.Outcome(false, Component.translatable(PrivacyActions.UNAVAILABLE_KEY));
            }
            send(player, installation, outcome.ok(), outcome.message());
        });
    }

    /** Sends the snapshot of what {@code installation} records to {@code player} only. */
    public static void send(ServerPlayer player, Installation installation, boolean ok, @Nullable Component message) {
        MinecraftServer server = player.level().getServer();
        SecurityAuditLog log = SecurityAuditLog.get(server);
        RetentionPolicy policy = RetentionPolicy.fromConfig(installation.auditRetentionDaysOverride());
        PrivacyActions actions = PrivacyActions.current();
        PrivacyActions.Status status = actions.status(player);
        Services.NETWORK.sendToPlayer(player, assemble(NeroSecurityConfig.isAuditLogEnabled(), policy,
                log.size(installation.id()), log.oldestAt(installation.id()), System.currentTimeMillis(),
                NeroSecurityConfig.isCamerasEnabled(), NeroSecurityConfig.privacyCameraLogsMembers(),
                status != null && status.optedOut(), actions.available(), ok, message));
    }

    /** Pure: the snapshot from already-read values ({@code oldestAt} epoch ms, 0 = no rows). */
    public static PrivacySnapshot assemble(boolean auditEnabled, RetentionPolicy policy, int entryCount, long oldestAt,
            long now, boolean camerasEnabled, boolean cameraLogsMembers, boolean optedOut, boolean available,
            boolean ok, @Nullable Component message) {
        int oldestAge = oldestAt <= 0L || now <= oldestAt ? 0 : (int) Math.min(Integer.MAX_VALUE, (now - oldestAt) / 1000L);
        return new PrivacySnapshot(auditEnabled, policy.auditDays(), policy.maxEntries(), entryCount, oldestAge,
                camerasEnabled, cameraLogsMembers, optedOut, available, ok, message);
    }

    /**
     * The installation the sender's open hub menu is bound to, if they may still open it and the rate limit
     * allows an answer; null otherwise (a refusal closes the screen and says why).
     */
    private static @Nullable Installation authorised(ServerPlayer player) {
        if (!(player.containerMenu instanceof SecurityHubMenu menu) || menu.installationId() == null
                || !menu.stillValid(player)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale privacy payload dropped");
            return null;
        }
        if (!menu.tryPrivacy(player.level().getGameTime())) {
            return null; // too soon; the screen re-enables its buttons by itself
        }
        MinecraftServer server = player.level().getServer();
        Installation installation = SecurityInstallations.get(server).byId(menu.installationId()).orElse(null);
        if (installation == null) {
            player.closeContainer();
            return null;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(new AccessQuery(server, player.getUUID(), installation,
                AccessAction.OPEN, installation.hubPos()), AccessLevel.OFFICER);
        if (!decision.allowed()) {
            player.closeContainer();
            player.sendSystemMessage(decision.message());
            return null;
        }
        return installation;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. Logged and reported by payload type and
     * exception class only (never a name, UUID or position); the screen closes and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] privacy payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "privacy_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
