package za.co.neroland.nerosecurity.link;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig.LinkLockdownMode;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The companion app's three actions, and nothing else: {@code lockdown}, {@code ack_event},
 * {@code silence_alarm}. No action touches membership, levels, keycards, names, device settings or
 * automation.
 *
 * <h2>Pipeline (every call, in order)</h2>
 * <ol>
 *   <li>a player id, the module switch and a running server;</li>
 *   <li>the action's own config switch ({@code ACTION_DISABLED});</li>
 *   <li>a replayed {@code requestId} returns the first result, with no second transition;</li>
 *   <li>the per-player rate limit ({@code VALIDATION});</li>
 *   <li>the installation, re-read from the store: owner-only actions require
 *       {@code installation.isOwner(player)} from stored state <b>and</b> a resolver allow with no operator
 *       override (deny-wins sources still refuse). Missing, unknown and not-yours are one answer:
 *       {@code NOT_OWNER};</li>
 *   <li>the action body; the result is remembered for the request id.</li>
 * </ol>
 *
 * <p>Remote lockdown ships {@code off}: {@code release} may only clear a lockdown, {@code full} may also start
 * or confirm one, and neither ever records the lockdown-drill milestone. Messages are fixed text; logs carry
 * the action id and exception class only.
 */
final class SecurityLinkActions implements LinkActionHandler {

    private final LinkContext ctx;
    private final LinkRequestDedup dedup;
    private final LinkRateLimiter limiter;

    SecurityLinkActions(LinkContext ctx, LinkRequestDedup dedup, LinkRateLimiter limiter) {
        this.ctx = ctx;
        this.dedup = dedup;
        this.limiter = limiter;
    }

    @Override
    public String moduleId() {
        return SecurityLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return SecurityLinkModule.ACTIONS;
    }

    /** Lockdown follows {@code linkLockdownAllowOffline} live; ack and silence work offline. */
    @Override
    public boolean allowOffline(String actionId) {
        if (SecurityLinkModule.ACTION_LOCKDOWN.equals(actionId)) {
            try {
                return ctx.settings().lockdownOffline();
            } catch (RuntimeException | LinkageError e) {
                return false;
            }
        }
        return SecurityLinkModule.ACTION_ACK.equals(actionId) || SecurityLinkModule.ACTION_SILENCE.equals(actionId);
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        try {
            return run(playerId, actionId, params);
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] link action {} failed ({}).", safeAction(actionId),
                    e.getClass().getName());
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "Something went wrong; nothing was changed.");
        }
    }

    private LinkActionResult run(@Nullable UUID player, @Nullable String actionId, @Nullable JsonObject params) {
        if (player == null || actionId == null || !SecurityLinkModule.ACTIONS.contains(actionId)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "Unknown action.");
        }
        LinkSettings settings = ctx.settings();
        if (!settings.moduleEnabled()) {
            return disabled("NeroSecurity's companion-app module is switched off on this server.");
        }
        SecurityInstallations store = ctx.installations();
        if (store == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "The server is not ready.");
        }
        LinkActionResult off = switchedOff(actionId, settings);
        if (off != null) {
            return off;
        }
        String requestId = SecurityLinkAccess.string(params, "requestId");
        long now = ctx.now();
        LinkActionResult replay = dedup.lookup(player, actionId, requestId, now);
        if (replay != null) {
            return replay;
        }
        long wait = limiter.tryAcquire(player, actionId, now);
        if (wait > 0L) {
            long seconds = Math.max(1L, (wait + 999L) / 1000L);
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "Too many requests; try again in " + seconds + " s.");
        }
        LinkActionResult result = switch (actionId) {
            case SecurityLinkModule.ACTION_LOCKDOWN -> lockdown(store, player, params, settings, now);
            case SecurityLinkModule.ACTION_ACK -> ack(player, params);
            case SecurityLinkModule.ACTION_SILENCE -> silence(store, player, params, settings, now);
            default -> LinkActionResult.error(LinkActionResult.Error.VALIDATION, "Unknown action.");
        };
        dedup.remember(player, actionId, requestId, result, now);
        return result;
    }

    private static @Nullable LinkActionResult switchedOff(String actionId, LinkSettings settings) {
        return switch (actionId) {
            case SecurityLinkModule.ACTION_LOCKDOWN -> settings.lockdownMode() == LinkLockdownMode.OFF
                    || !settings.lockdownEnabled()
                    ? disabled("Changing lockdown from the app is switched off on this server.") : null;
            case SecurityLinkModule.ACTION_ACK -> settings.ackEnabled() ? null
                    : disabled("Acknowledging alerts from the app is switched off on this server.");
            case SecurityLinkModule.ACTION_SILENCE -> settings.silenceEnabled() ? null
                    : disabled("Silencing alarms from the app is switched off on this server.");
            default -> null;
        };
    }

    // --- lockdown ---------------------------------------------------------------------------------

    private LinkActionResult lockdown(SecurityInstallations store, UUID player, @Nullable JsonObject params,
            LinkSettings settings, long now) {
        Boolean enabled = SecurityLinkAccess.bool(params, "enabled");
        Installation installation = ownedInstallation(store, player, params, AccessAction.LOCKDOWN_TOGGLE);
        if (installation == null) {
            return notOwner();
        }
        if (enabled == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "Say whether lockdown should be on or off.");
        }
        Lockdown current = installation.lockdown();
        if (enabled) {
            if (settings.lockdownMode() != LinkLockdownMode.FULL) {
                return disabled("This server allows lockdown to be released from the app, not started.");
            }
            if (current.active() && !current.isAlarm()) {
                return ok(lockdownState(installation.id(), current, settings));
            }
            Lockdown next = current.isAlarm() ? ctx.confirmLockdown(installation, player, now)
                    : ctx.enterLockdown(installation, player, now);
            return ok(lockdownState(installation.id(), next != null ? next : reread(store, installation), settings));
        }
        if (!current.active()) {
            return ok(lockdownState(installation.id(), current, settings));
        }
        Lockdown next = ctx.exitLockdown(installation, player, now);
        return ok(lockdownState(installation.id(), next != null ? next : reread(store, installation), settings));
    }

    private static Lockdown reread(SecurityInstallations store, Installation installation) {
        return store.byId(installation.id()).map(Installation::lockdown).orElse(Lockdown.NONE);
    }

    private static JsonObject lockdownState(UUID installationId, Lockdown lockdown, LinkSettings settings) {
        JsonObject state = new JsonObject();
        state.addProperty("installationId", installationId.toString());
        state.add("lockdown", SecurityLinkSnapshots.lockdownJson(lockdown, settings.lockdownEnabled()));
        return state;
    }

    // --- ack_event --------------------------------------------------------------------------------

    private LinkActionResult ack(UUID player, @Nullable JsonObject params) {
        String eventId = SecurityLinkAccess.string(params, "eventId");
        if (eventId == null || !eventId.startsWith(SecurityLinkModule.MODULE_ID + ".") || eventId.length() > 128) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "Unknown alert.");
        }
        boolean mine = false;
        for (LinkAlert alert : ctx.alerts(player)) {
            if (eventId.equals(alert.id()) && SecurityLinkModule.MODULE_ID.equals(alert.moduleId())) {
                mine = true;
                break;
            }
        }
        if (!mine || !ctx.ackAlert(player, eventId)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "Unknown alert.");
        }
        JsonObject state = new JsonObject();
        state.addProperty("eventId", eventId);
        state.addProperty("acked", true);
        return ok(state);
    }

    // --- silence_alarm ----------------------------------------------------------------------------

    private LinkActionResult silence(SecurityInstallations store, UUID player, @Nullable JsonObject params,
            LinkSettings settings, long now) {
        Installation installation = ownedInstallation(store, player, params, AccessAction.SILENCE_ALARM);
        if (installation == null) {
            return notOwner();
        }
        Integer minutes = SecurityLinkAccess.integer(params, "minutes");
        int max = Math.max(1, settings.silenceMaxMinutes());
        if (minutes == null || minutes < 1 || minutes > max) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "Choose between 1 and " + max + " minutes.");
        }
        long until = now + minutes * 60_000L;
        ctx.silenceAlarms(installation, until);
        ctx.publish(SecurityEvent.of(SecurityEventType.ALARM_SILENCED, installation.id(), null, player, now));
        JsonObject state = new JsonObject();
        state.addProperty("installationId", installation.id().toString());
        state.addProperty("silencedUntil", until);
        return ok(state);
    }

    // --- shared -----------------------------------------------------------------------------------

    /**
     * The installation the {@code installationId} parameter names, if {@code player} is one of its stored
     * owners and the resolver (no operator override) allows {@code action} at OWNER; otherwise null.
     */
    private @Nullable Installation ownedInstallation(SecurityInstallations store, UUID player,
            @Nullable JsonObject params, AccessAction action) {
        UUID id = SecurityLinkAccess.exactUuid(SecurityLinkAccess.string(params, "installationId"));
        Installation installation = id == null ? null : store.byId(id).orElse(null);
        if (installation == null || !installation.isOwner(player)) {
            return null;
        }
        AccessDecision decision = ctx.resolver().resolve(
                new AccessQuery(ctx.server(), player, installation, action, null), AccessLevel.OWNER);
        if (!decision.allowed() || decision.admin()) {
            return null;
        }
        return installation;
    }

    private static LinkActionResult ok(JsonObject state) {
        return LinkActionResult.ok(state);
    }

    private static LinkActionResult notOwner() {
        return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "Only an owner of that installation can do this.");
    }

    private static LinkActionResult disabled(String message) {
        return LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED, message);
    }

    private static String safeAction(@Nullable String actionId) {
        return actionId != null && SecurityLinkModule.ACTIONS.contains(actionId) ? actionId : "?";
    }
}
