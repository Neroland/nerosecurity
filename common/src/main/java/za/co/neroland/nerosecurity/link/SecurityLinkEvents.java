package za.co.neroland.nerosecurity.link;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * The one {@link SecurityEventBus} consumer that feeds the companion app: each mapped event becomes a live
 * row on Core's link bus (topic {@code nerosecurity.events}) for each recipient, and, when the notification
 * window allows, a {@code _notify} object on that row plus a stored Core alert.
 *
 * <h2>Per event, per recipient, in this order</h2>
 * <ol>
 *   <li>the coalescer decides "notify now" or "count into the open window";</li>
 *   <li>the live row is published, carrying {@code _notify} only when notifying now (first, so the bridge's
 *       rich push wins over the generic alert push);</li>
 *   <li>the alert {@code nerosecurity.<installationId>.<kind>} is raised (replacing the previous one, so one
 *       kind at one installation is one alert whose text refreshes "+N more").</li>
 * </ol>
 *
 * <p>Recipients come from {@link SecurityLinkAccess#recipients} (stored owners, officers when "notify
 * officers" is on, never the actor). Rows carry an {@code actorKind}, never an actor. All notification and
 * alert text comes from {@link PushCopy}. Gates: the module and {@code pushNotificationsEnabled} switches,
 * a running server, and the installation still existing.
 *
 * <p>Nothing here is persisted: windows, counters, dedup and rate-limit entries are memory only and keyed
 * by recipient; the {@code "link"} eraser forgets them. Alerts older than the audit retention are
 * dismissed on the retention cadence.
 */
public final class SecurityLinkEvents {

    /** Drain cadence in server ticks. */
    static final int DRAIN_INTERVAL_TICKS = 20;
    static final String TOPIC = "events";
    static final int MAX_RAISED_FOR = 4096;

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();

    private SecurityLinkEvents() {
    }

    /** Subscribes the live pipeline to the bus and the server tick. Idempotent. */
    static void init(Pipeline pipeline) {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        SecurityEventBus.subscribe(event -> {
            try {
                pipeline.onEvent(event);
            } catch (RuntimeException | LinkageError e) {
                NeroSecurityCommon.LOGGER.warn("[NeroSecurity] link event for {} failed ({}).",
                        event == null ? "?" : event.type(), e.getClass().getName());
            }
        });
        ServerHooks.onTick(server -> {
            int tick = server.getTickCount();
            if (tick % DRAIN_INTERVAL_TICKS == 0) {
                pipeline.drain();
            }
            long interval = Math.max(1L, NeroSecurityConfig.privacyPurgeIntervalMinutes() * 1200L);
            if (tick > 0 && tick % interval == 0) {
                pipeline.sweepAlerts(server);
            }
        });
        ServerHooks.onStarted(pipeline::sweepAlerts);
        ServerHooks.onStopping(server -> pipeline.reset());
    }

    /** The {@code "link"} eraser for {@link SecurityData}: transient per-player state only, nothing to back up. */
    static SecurityData.StoreEraser eraser(Pipeline pipeline, LinkRequestDedup dedup, LinkRateLimiter limiter) {
        return new SecurityData.StoreEraser() {
            @Override
            public String name() {
                return "link";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                if (player == null) {
                    return;
                }
                pipeline.forget(player);
                dedup.forget(player);
                limiter.forget(player);
                LoginAlertSummary.forget(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                // Nothing persisted: alerts are Core's (erased and backed up by Core), audit rows the audit store's.
            }
        };
    }

    /** The event pipeline with its collaborators injected (tests build one over a fake context). */
    static final class Pipeline {

        private final LinkContext ctx;
        private final LinkEventMapper mapper;
        private final PushCoalescer coalescer;
        /** Players alerted this session (for the retention sweep); memory only, bounded, erased per player. */
        private final Set<UUID> raisedFor = new LinkedHashSet<>();

        Pipeline(LinkContext ctx) {
            this.ctx = ctx;
            this.mapper = new LinkEventMapper(() -> ctx.settings().deniedThreshold(),
                    () -> ctx.settings().deniedWindowMs());
            this.coalescer = new PushCoalescer(() -> ctx.settings().cooldownMs(), PushCoalescer.DEFAULT_MAX_WINDOWS);
        }

        void onEvent(SecurityEvent event) {
            if (event == null) {
                return;
            }
            LinkSettings settings = ctx.settings();
            if (!settings.moduleEnabled() || !settings.pushEnabled()) {
                return;
            }
            SecurityInstallations store = ctx.installations();
            if (store == null) {
                return;
            }
            Installation installation = store.byId(event.installationId()).orElse(null);
            if (installation == null) {
                return;
            }
            long now = ctx.now();
            Optional<LinkEventMapper.Mapped> mapped = mapper.map(event, now);
            if (mapped.isEmpty()) {
                return;
            }
            PushKind push = mapped.get().push();
            // Identity-free row: an opted-out actor reads as any other player (never "withheld" here).
            String actorKind = event.actor() == null ? "none" : "player";
            for (UUID recipient : SecurityLinkAccess.recipients(installation, event.actor())) {
                PushCoalescer.Offer offer = push == null ? null
                        : coalescer.offer(recipient, installation.id(), push, push.severity(), now);
                if (offer != null && offer.flushed() != null) {
                    emitSummary(installation, offer.flushed(), now);
                }
                Set<String> alertIds = alertIds(recipient);
                if (push != null && offer != null && offer.emitted()) {
                    alertIds.add(PushCopy.alertId(installation.id(), push));
                }
                JsonObject row = SecurityLinkSnapshots.eventRow(installation, event.at(), event.type(),
                        event.severity(), event.devicePos(), actorKind, alertIds);
                row.addProperty("schema_version", SecurityLinkModule.SCHEMA_VERSION);
                if (push != null && offer != null && offer.emitted()) {
                    row.add(PushCopy.FIELD, PushCopy.notify(installation.name(), push, 0, installation.id()));
                }
                ctx.publishLink(LinkEvent.forPlayer(SecurityLinkModule.MODULE_ID, TOPIC, recipient, row));
                if (push != null && offer != null && offer.emitted()) {
                    raise(recipient, installation, push, push.severity(), 0);
                }
            }
        }

        /** Summaries for closed windows that counted further events. */
        void drain() {
            LinkSettings settings = ctx.settings();
            if (!settings.moduleEnabled() || !settings.pushEnabled()) {
                coalescer.clear();
                return;
            }
            List<PushCoalescer.Emit> due = coalescer.drain(ctx.now());
            if (due.isEmpty()) {
                return;
            }
            SecurityInstallations store = ctx.installations();
            if (store == null) {
                return;
            }
            long now = ctx.now();
            for (PushCoalescer.Emit emit : due) {
                try {
                    Installation installation = store.byId(emit.installationId()).orElse(null);
                    if (installation != null) {
                        emitSummary(installation, emit, now);
                    }
                } catch (RuntimeException | LinkageError e) {
                    NeroSecurityCommon.LOGGER.warn("[NeroSecurity] link summary for {} failed ({}).", emit.kind(),
                            e.getClass().getName());
                }
            }
        }

        private void emitSummary(Installation installation, PushCoalescer.Emit emit, long now) {
            // Membership may have changed since the window opened: only still-current recipients are told.
            if (!SecurityLinkAccess.recipients(installation, null).contains(emit.recipient())) {
                return;
            }
            JsonObject row = SecurityLinkSnapshots.header(now);
            row.addProperty("type", PushKind.SUMMARY.jsonName());
            row.addProperty("at", now);
            row.addProperty("installationId", installation.id().toString());
            row.addProperty("installationName", installation.name());
            row.addProperty("extra", emit.extra());
            row.add(PushCopy.FIELD, PushCopy.notify(installation.name(), PushKind.SUMMARY, emit.extra(),
                    installation.id()));
            ctx.publishLink(LinkEvent.forPlayer(SecurityLinkModule.MODULE_ID, TOPIC, emit.recipient(), row));
            raise(emit.recipient(), installation, emit.kind(), emit.severity(), emit.extra());
        }

        private void raise(UUID recipient, Installation installation, PushKind kind, LinkAlert.Severity severity,
                int extra) {
            synchronized (raisedFor) {
                raisedFor.remove(recipient);
                raisedFor.add(recipient);
                while (raisedFor.size() > MAX_RAISED_FOR) {
                    Iterator<UUID> oldest = raisedFor.iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
            ctx.raiseAlert(recipient, LinkAlert.raise(PushCopy.alertId(installation.id(), kind),
                    SecurityLinkModule.MODULE_ID, severity, PushCopy.alertText(installation.name(), kind, extra)));
        }

        private Set<String> alertIds(UUID recipient) {
            Set<String> out = new HashSet<>();
            try {
                for (LinkAlert alert : ctx.alerts(recipient)) {
                    out.add(alert.id());
                }
            } catch (RuntimeException | LinkageError e) {
                // An alert store that cannot be read only costs the row its alert link.
            }
            return out;
        }

        /** The sweep on the configured cadence (server hook). */
        void sweepAlerts(MinecraftServer server) {
            try {
                sweepAlerts(ctx.now(), NeroSecurityConfig.privacyAuditRetentionDays());
            } catch (RuntimeException | LinkageError e) {
                NeroSecurityCommon.LOGGER.warn("[NeroSecurity] link alert retention sweep failed ({}).",
                        e.getClass().getName());
            }
        }

        /**
         * Dismisses this module's alerts older than the audit retention of the installation they describe:
         * {@code min(globalDays, the installation's override)}, or {@code globalDays} when the installation is
         * gone or the id cannot be parsed. Visits every stored owner and member, plus every player this
         * pipeline raised an alert for during this server session (so a member removed since keeps nothing
         * past retention).
         *
         * <p>Backstop: a former recipient from an <em>earlier</em> session who has no stored relationship any
         * more cannot be enumerated (Core's alert store offers no listing of players); their alerts are still
         * bounded by Core's per-module alert cap, removed by their own
         * acknowledgement, and by any data-erasure request.
         *
         * @return how many alerts were dismissed
         */
        int sweepAlerts(long now, int globalDays) {
            SecurityInstallations store = ctx.installations();
            if (store == null) {
                return 0;
            }
            Set<UUID> players = new HashSet<>(raisedForSnapshot());
            for (Installation installation : store.all()) {
                players.addAll(SecurityLinkAccess.recipients(installation, null));
                players.addAll(installation.members().keySet());
            }
            int dismissed = 0;
            for (UUID player : players) {
                for (LinkAlert alert : ctx.alerts(player)) {
                    if (!SecurityLinkModule.MODULE_ID.equals(alert.moduleId()) || alert.id() == null) {
                        continue;
                    }
                    long cutoff = now - retentionDays(store, alert.id(), globalDays) * 86_400_000L;
                    if (alert.createdAt() < cutoff) {
                        ctx.dismissAlert(player, alert.id());
                        dismissed++;
                    }
                }
            }
            return dismissed;
        }

        /** {@code min(global, override)} for the installation named in {@code nerosecurity.<uuid>.<kind>}. */
        static int retentionDays(SecurityInstallations store, String alertId, int globalDays) {
            int global = Math.max(1, globalDays);
            String prefix = SecurityLinkModule.MODULE_ID + ".";
            if (!alertId.startsWith(prefix)) {
                return global;
            }
            String rest = alertId.substring(prefix.length());
            int dot = rest.indexOf('.');
            UUID id = SecurityLinkAccess.exactUuid(dot < 0 ? rest : rest.substring(0, dot));
            Installation installation = id == null ? null : store.byId(id).orElse(null);
            Integer override = installation == null ? null : installation.auditRetentionDaysOverride();
            return override == null ? global : Math.max(1, Math.min(global, override));
        }

        private List<UUID> raisedForSnapshot() {
            synchronized (raisedFor) {
                return List.copyOf(raisedFor);
            }
        }

        void forget(UUID player) {
            coalescer.forget(player);
            synchronized (raisedFor) {
                raisedFor.remove(player);
            }
        }

        boolean holdsAnyFor(UUID player) {
            synchronized (raisedFor) {
                if (raisedFor.contains(player)) {
                    return true;
                }
            }
            return coalescer.holdsAnyFor(player);
        }

        void reset() {
            coalescer.clear();
            mapper.clear();
        }

        PushCoalescer coalescer() {
            return coalescer;
        }
    }
}
