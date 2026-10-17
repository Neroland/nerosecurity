package za.co.neroland.nerosecurity.privacy;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.audit.AuditRecorder;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * Wires the privacy surface at common init: the opt-out store and the export files join the composite eraser,
 * the opt-out becomes the audit recorder's {@link za.co.neroland.nerosecurity.audit.ActorPolicy}, the real
 * {@link PrivacyActions} replace the placeholder (so the hub's Privacy tab buttons work), and export files are
 * purged after {@link PrivacyExport#RETENTION_DAYS} at server start and on the retention cadence.
 */
public final class SecurityPrivacy {

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();

    private SecurityPrivacy() {
    }

    /** Init step 4m, after the audit recorder (4e). Idempotent. */
    public static void init() {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        PrivacyOptOuts.registerEraser();
        SecurityData.addStore(exportsEraser(
                server -> server == null ? null : PrivacyExport.directory(server), SecurityPrivacyActions.INSTANCE));
        AuditRecorder.setActorPolicy(PrivacyOptOuts.policy());
        PrivacyActions.install(SecurityPrivacyActions.INSTANCE);
        ServerHooks.onStarted(SecurityPrivacy::purgeExports);
        ServerHooks.onTick(server -> {
            long interval = Math.max(1L, NeroSecurityConfig.privacyPurgeIntervalMinutes() * 1200L);
            int tick = server.getTickCount();
            if (tick > 0 && tick % interval == 0) {
                purgeExports(server);
            }
        });
    }

    private static void purgeExports(MinecraftServer server) {
        int removed = PrivacyExport.purgeExpired(PrivacyExport.directory(server), System.currentTimeMillis());
        if (removed > 0) {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] export retention removed {} file(s).", removed);
        }
    }

    /**
     * Store {@code "exports"}: deletes the subject's export file and forgets their export rate-limit entry. The
     * file is not SavedData, so there is no backup to refresh.
     */
    static SecurityData.StoreEraser exportsEraser(Function<MinecraftServer, @Nullable Path> directory,
            SecurityPrivacyActions actions) {
        return new SecurityData.StoreEraser() {
            @Override
            public String name() {
                return "exports";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                actions.forget(player);
                PrivacyExport.delete(directory.apply(server), player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                // A plain file, not SavedData: nothing to refresh.
            }
        };
    }
}
