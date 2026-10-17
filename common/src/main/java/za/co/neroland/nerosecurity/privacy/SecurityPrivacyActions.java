package za.co.neroland.nerosecurity.privacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * The real {@link PrivacyActions}, shared by the hub's Privacy tab and {@code /nerosecurity privacy}. Every
 * action acts on the asking {@link ServerPlayer} only, by their own UUID, re-read on the server: nothing the
 * client sends names a player. Export is rate limited per player ({@code privacyExportCooldownSeconds}).
 */
public final class SecurityPrivacyActions implements PrivacyActions {

    public static final String KEY_EXPORTED_PATH = "command.nerosecurity.privacy.exported";
    public static final String KEY_EXPORT_FAILED = "command.nerosecurity.privacy.export_failed";
    public static final String KEY_EXPORT_WAIT = "command.nerosecurity.privacy.export_wait";
    public static final String KEY_OPTOUT_ON = "privacy.nerosecurity.optout.on";
    public static final String KEY_OPTOUT_OFF = "privacy.nerosecurity.optout.off";
    public static final String KEY_EXPORTED = "privacy.nerosecurity.exported";

    public static final SecurityPrivacyActions INSTANCE = new SecurityPrivacyActions(System::currentTimeMillis,
            () -> NeroSecurityConfig.privacyExportCooldownSeconds() * 1000L);

    private final LongSupplier clock;
    private final LongSupplier cooldownMs;
    /** Player UUID to the time of their last export. Transient; cleared by the "exports" eraser. */
    private final Map<UUID, Long> lastExport = new ConcurrentHashMap<>();

    SecurityPrivacyActions(LongSupplier clock, LongSupplier cooldownMs) {
        this.clock = clock;
        this.cooldownMs = cooldownMs;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Outcome export(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID self = player.getUUID();
        long wait = tryExport(self, clock.getAsLong());
        if (wait > 0) {
            return new Outcome(false, Component.translatable(KEY_EXPORT_WAIT, (wait + 999L) / 1000L));
        }
        try {
            PrivacyExport.Result result = PrivacyExport.export(server, self, PrivacyOptOuts.get(server).isOptedOut(self),
                    ownAlerts(server, self));
            player.sendSystemMessage(Component.translatable(KEY_EXPORTED_PATH, result.relativePath(), result.owned(),
                    result.memberships(), result.cards(), result.auditRows(), PrivacyExport.RETENTION_DAYS));
            return new Outcome(true, Component.translatable(KEY_EXPORTED));
        } catch (IOException | RuntimeException failure) {
            lastExport.remove(self); // a failed export does not use up the player's turn
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] privacy export failed ({}).", failure.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(
                    new IOException("Export failed: " + failure.getClass().getName()), "privacy", "export");
            return new Outcome(false, Component.translatable(KEY_EXPORT_FAILED));
        }
    }

    @Override
    public Outcome setOptOut(ServerPlayer player, boolean optedOut) {
        MinecraftServer server = player.level().getServer();
        UUID self = player.getUUID();
        applyOptOut(PrivacyOptOuts.get(server), self, optedOut);
        return new Outcome(true, Component.translatable(optedOut ? KEY_OPTOUT_ON : KEY_OPTOUT_OFF));
    }

    /**
     * The opt-out is prospective only: it changes who new audit rows name from this moment on and touches no
     * existing row (rows recorded earlier keep the id until retention removes them or the player asks for
     * erasure). Idempotent and cheap: repeating the same choice changes nothing and dirties nothing.
     *
     * @return whether the stored choice changed
     */
    static boolean applyOptOut(PrivacyOptOuts store, UUID self, boolean optedOut) {
        return store.set(self, optedOut);
    }

    @Override
    public Status status(ServerPlayer player) {
        return new Status(PrivacyOptOuts.get(player.level().getServer()).isOptedOut(player.getUUID()));
    }

    /**
     * The rate limit: 0 when {@code player} may export now (and the attempt is recorded), else the milliseconds
     * left. Pure apart from the map. Entries whose cooldown has passed are dropped on every call, so the map
     * only ever holds players still inside their cooldown (exports are rare; the sweep is cheap).
     */
    long tryExport(UUID player, long now) {
        long cooldown = Math.max(0L, cooldownMs.getAsLong());
        lastExport.values().removeIf(at -> now - at >= cooldown);
        Long last = lastExport.get(player);
        if (last != null && now - last < cooldown) {
            return cooldown - (now - last);
        }
        lastExport.put(player, now);
        return 0L;
    }

    /** Erasure: forgets the player's rate-limit entry. */
    void forget(UUID player) {
        if (player != null) {
            lastExport.remove(player);
        }
    }

    /** Tests: how many players are inside their export cooldown. */
    int trackedPlayers() {
        return lastExport.size();
    }

    /** Conformance probe. */
    boolean holdsAnyFor(UUID player) {
        return player != null && lastExport.containsKey(player);
    }

    /**
     * The player's own NeroSecurity alerts in Core's per-player alert list (module {@code nerosecurity} only), for
     * the export; empty if Core cannot say.
     */
    static List<LinkAlert> ownAlerts(MinecraftServer server, UUID player) {
        try {
            List<LinkAlert> out = new ArrayList<>();
            for (LinkAlert alert : LinkAlerts.get(server).list(player)) {
                if (alert != null && NeroSecurityCommon.MOD_ID.equals(alert.moduleId())) {
                    out.add(alert);
                }
            }
            return out;
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }
}
