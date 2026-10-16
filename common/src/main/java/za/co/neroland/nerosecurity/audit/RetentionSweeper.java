package za.co.neroland.nerosecurity.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * Enforces audit retention: at server start and every {@code privacyPurgeIntervalMinutes}, every log is
 * purged under its installation's {@link RetentionPolicy} (hard-capped at 90 days) and the logs of
 * installations that no longer exist are dropped. Reads also purge lazily ({@link AuditService}).
 *
 * <p>Each installation that lost at least one row gets one {@code AUDIT_PURGED} event on the bus (no
 * actor; never persisted). One info line per sweep carries the total count only.
 */
public final class RetentionSweeper {

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();

    private RetentionSweeper() {
    }

    /** Init step 4h: sweep on start and on the configured cadence. Idempotent. */
    public static void init() {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        ServerHooks.onStarted(RetentionSweeper::sweep);
        ServerHooks.onTick(server -> {
            long interval = Math.max(1L, NeroSecurityConfig.privacyPurgeIntervalMinutes() * 1200L);
            int tick = server.getTickCount();
            if (tick > 0 && tick % interval == 0) {
                sweep(server);
            }
        });
    }

    /** One full sweep against the live stores. */
    public static int sweep(MinecraftServer server) {
        SecurityInstallations installations = SecurityInstallations.get(server);
        int total = sweep(installations, SecurityAuditLog.get(server), System.currentTimeMillis(),
                SecurityEventBus::publish);
        if (total > 0) {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] audit retention sweep removed {} entr(ies).", total);
        }
        return total;
    }

    /** The sweep over explicit stores (plain-JVM testable). Returns how many rows were removed. */
    public static int sweep(SecurityInstallations installations, SecurityAuditLog audit, long now,
            Consumer<SecurityEvent> publisher) {
        Map<UUID, Installation> live = new HashMap<>();
        for (Installation installation : installations.all()) {
            live.put(installation.id(), installation);
        }
        Set<UUID> liveIds = live.keySet();
        Map<UUID, Integer> removed = audit.purgeAll(now,
                id -> RetentionPolicy.fromConfig(live.get(id) == null ? null : live.get(id).auditRetentionDaysOverride()),
                liveIds);
        int total = 0;
        for (Map.Entry<UUID, Integer> entry : removed.entrySet()) {
            total += entry.getValue();
            if (liveIds.contains(entry.getKey())) {
                publisher.accept(SecurityEvent.of(SecurityEventType.AUDIT_PURGED, entry.getKey(), null, null, now));
            }
        }
        return total;
    }
}
