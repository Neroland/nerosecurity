package za.co.neroland.nerosecurity.audit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.data.SecurityData;

/**
 * The persistent audit store {@code nerosecurity:audit}: one capped {@link AuditLog} per installation.
 * Separate from the installation store so per-event writes and purges never churn the file every access
 * check reads, and so a corrupt audit file falls back to its own backup without touching ownership.
 *
 * <p>Overworld-resident, read only through {@link #get(MinecraftServer)} (Core's
 * {@code SavedDataRecovery}). Written only by {@link AuditRecorder}; read only through
 * {@link AuditService}; purged by {@link RetentionSweeper} and lazily on every read.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Rows hold an event type, a time, a severity, the position of the
 * owner's device and at most a player UUID. Erasure ({@link #removeActor}) deletes every row naming the
 * player, across every installation, and the composite eraser then refreshes the backup. Logs carry
 * counts only, never a UUID or a position.
 */
public final class SecurityAuditLog extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "audit");
    public static final int SCHEMA_VERSION = 1;

    /** The ring-buffer cap used on load when the config cannot be read (the largest configurable cap). */
    static final int CAP_FALLBACK = 5000;

    private record LogRow(String installation, List<AuditEntry.Raw> entries) {
        static final Codec<LogRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.lenientOptionalFieldOf("installation", "").forGetter(LogRow::installation),
                AuditEntry.Raw.CODEC.listOf().lenientOptionalFieldOf("entries", List.of()).forGetter(LogRow::entries)
        ).apply(inst, LogRow::new));
    }

    private record Root(int schemaVersion, List<LogRow> logs) {
    }

    /** The whole on-disk format: {@code { schema_version, logs: [ { installation, entries: [...] } ] }}. */
    public static final Codec<SecurityAuditLog> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            LogRow.CODEC.listOf().optionalFieldOf("logs", List.of()).forGetter(Root::logs)
    ).apply(inst, Root::new)).xmap(SecurityAuditLog::fromData, SecurityAuditLog::toData);

    public static final SavedDataType<SecurityAuditLog> TYPE = new SavedDataType<>(ID, SecurityAuditLog::new, CODEC, null);

    private final Map<UUID, AuditLog> logs = new LinkedHashMap<>();

    public SecurityAuditLog() {
    }

    /** The only accessor. */
    public static SecurityAuditLog get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, SecurityAuditLog::new, ID.toString());
    }

    /** Init step 4d: joins the composite eraser. Idempotent. */
    public static void registerEraser() {
        SecurityData.addStore(ERASER);
    }

    private static final SecurityData.StoreEraser ERASER = eraser(SecurityAuditLog::get);

    /**
     * A {@link SecurityData.StoreEraser} over the store {@code lookup} returns. Erase removes every row
     * whose actor is the player, in every installation; backupNow refreshes the last-known-good copy.
     */
    public static SecurityData.StoreEraser eraser(Function<MinecraftServer, SecurityAuditLog> lookup) {
        return new SecurityData.StoreEraser() {
            @Override
            public String name() {
                return "audit";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                lookup.apply(server).removeActor(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                SavedDataRecovery.backupNow(server.overworld(), TYPE, lookup.apply(server), ID.toString());
            }
        };
    }

    // --- writes -----------------------------------------------------------------------------------

    public synchronized void append(UUID installationId, AuditEntry entry, int cap) {
        if (installationId == null || entry == null) {
            return;
        }
        logs.computeIfAbsent(installationId, id -> new AuditLog()).append(entry, cap);
        setDirty();
    }

    /** Purges one installation's log. Returns how many entries were removed. */
    public synchronized int purge(UUID installationId, long now, RetentionPolicy policy) {
        AuditLog log = installationId == null ? null : logs.get(installationId);
        if (log == null) {
            return 0;
        }
        int removed = log.purge(now, policy.auditMs(), policy.presenceMs());
        if (log.isEmpty()) {
            logs.remove(installationId);
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /**
     * Purges every log under its own policy and drops the logs of installations that no longer exist.
     *
     * @return removed entries per installation (only installations that lost at least one entry)
     */
    public synchronized Map<UUID, Integer> purgeAll(long now, Function<UUID, RetentionPolicy> policyFor,
            Set<UUID> liveInstallations) {
        Map<UUID, Integer> removed = new LinkedHashMap<>();
        for (Iterator<Map.Entry<UUID, AuditLog>> it = logs.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, AuditLog> entry = it.next();
            AuditLog log = entry.getValue();
            if (!liveInstallations.contains(entry.getKey())) {
                if (!log.isEmpty()) {
                    removed.put(entry.getKey(), log.size());
                }
                it.remove();
                continue;
            }
            RetentionPolicy policy = policyFor.apply(entry.getKey());
            int count = log.purge(now, policy.auditMs(), policy.presenceMs());
            if (count > 0) {
                removed.put(entry.getKey(), count);
            }
            if (log.isEmpty()) {
                it.remove();
            }
        }
        if (!removed.isEmpty()) {
            setDirty();
        }
        return removed;
    }

    /** Erasure: removes every row naming {@code actor}, across every installation. Returns how many. */
    public synchronized int removeActor(UUID actor) {
        int removed = 0;
        for (AuditLog log : logs.values()) {
            removed += log.removeActor(actor);
        }
        logs.values().removeIf(AuditLog::isEmpty);
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** The installation was dissolved: its whole log goes. */
    public synchronized void drop(UUID installationId) {
        if (installationId != null && logs.remove(installationId) != null) {
            setDirty();
        }
    }

    // --- reads ------------------------------------------------------------------------------------

    public synchronized List<AuditEntry> newestFirst(UUID installationId, int limit) {
        AuditLog log = installationId == null ? null : logs.get(installationId);
        return log == null ? List.of() : log.newestFirst(Math.max(0, limit));
    }

    public synchronized int size(UUID installationId) {
        AuditLog log = installationId == null ? null : logs.get(installationId);
        return log == null ? 0 : log.size();
    }

    /** Conformance probe: does any row anywhere still name {@code actor}? */
    public synchronized boolean hasActorRows(UUID actor) {
        for (AuditLog log : logs.values()) {
            if (log.hasActor(actor)) {
                return true;
            }
        }
        return false;
    }

    // --- persistence --------------------------------------------------------------------------------

    private synchronized Root toData() {
        List<LogRow> rows = new ArrayList<>();
        logs.forEach((id, log) -> rows.add(new LogRow(id.toString(), log.toRaw())));
        return new Root(SCHEMA_VERSION, rows);
    }

    /**
     * Rebuilds the store, dropping malformed installation ids, duplicates, unknown or malformed rows,
     * and rows beyond the ring cap (keeping the newest). Warnings carry counts only.
     */
    private static SecurityAuditLog fromData(Root root) {
        SecurityAuditLog store = new SecurityAuditLog();
        if (root.schemaVersion() > SCHEMA_VERSION) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] audit store has schema version {} (this build reads {}); "
                    + "loading what is understood.", root.schemaVersion(), SCHEMA_VERSION);
        }
        int cap = capForLoad();
        int droppedLogs = 0;
        int droppedEntries = 0;
        for (LogRow row : root.logs()) {
            UUID id;
            try {
                id = UUID.fromString(row.installation());
            } catch (IllegalArgumentException malformed) {
                droppedLogs++;
                continue;
            }
            if (store.logs.containsKey(id)) {
                droppedLogs++;
                continue;
            }
            AuditLog.Loaded loaded = AuditLog.fromRaw(row.entries(), cap);
            droppedEntries += loaded.dropped();
            if (!loaded.log().isEmpty()) {
                store.logs.put(id, loaded.log());
            }
        }
        if (droppedLogs > 0 || droppedEntries > 0) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] audit store: dropped {} invalid log(s) and {} entr(ies) "
                    + "on load.", droppedLogs, droppedEntries);
        }
        return store;
    }

    private static int capForLoad() {
        try {
            return NeroSecurityConfig.privacyAuditMaxEntriesPerInstallation();
        } catch (RuntimeException | LinkageError unavailable) {
            return CAP_FALLBACK;
        }
    }
}
