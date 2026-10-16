package za.co.neroland.nerosecurity.data;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * NeroSecurity's single entry into Neroland Core's per-player data-erasure hook (POPIA/GDPR).
 *
 * <p>{@link #init()} registers <b>one</b> composite {@link PlayerDataEraser} with
 * {@link PlayerDataErasure} at common init — early, and before any store exists — so that an
 * erasure request or Core's inactivity retention sweep reaches NeroSecurity from the very first
 * build that stores anything. The composite walks a static list of per-store erasers
 * ({@link StoreEraser}); for each one it erases the player's rows and then immediately refreshes
 * that store's last-known-good backup.
 *
 * <h2>Contract for every later stage</h2>
 * <ul>
 *   <li>Every player-keyed store added to NeroSecurity (keycard registrations, access lists,
 *       access/camera logs, installation ownership, per-player preferences, anything keyed by a
 *       player UUID) <b>MUST</b> add itself here with {@link #addStore(StoreEraser)} from its own
 *       init, before the first server starts. A store that is not listed here is a store an
 *       erasure request silently misses.</li>
 *   <li>Its {@link StoreEraser#backupNow(MinecraftServer)} <b>MUST</b> call
 *       {@link za.co.neroland.nerolandcore.data.SavedDataRecovery#backupNow
 *       SavedDataRecovery.backupNow} after erasing. Otherwise the last-known-good backup still
 *       holds the erased rows, and the next corrupt-{@code .dat} recovery would restore them.</li>
 *   <li>Every store's {@code SavedData} is read through
 *       {@link za.co.neroland.nerolandcore.data.SavedDataRecovery#get SavedDataRecovery.get}, never
 *       a bare {@code computeIfAbsent}.</li>
 *   <li>Every new store gets a probe in {@code SecurityErasureConformanceTest}.</li>
 * </ul>
 *
 * <p>No store exists yet: the list is empty and the registered eraser is a no-op until the first
 * one is added. Erasers must tolerate a player they have never heard of (the retention sweep and
 * Core's conformance harness both do that).
 *
 * <p><b>Privacy:</b> nothing here logs a player name or UUID. A failing store is logged by its
 * stable store name and the exception class only.
 */
public final class SecurityData {

    /** One player-keyed store's erasure step plus its post-erasure backup refresh. */
    public interface StoreEraser {

        /** A short, stable, non-identifying store name for logs (e.g. {@code "keycards"}). */
        String name();

        /** Remove every row this store holds for {@code player}. Must tolerate an unknown player. */
        void erase(MinecraftServer server, UUID player);

        /**
         * Refresh this store's last-known-good backup via {@code SavedDataRecovery.backupNow}, so
         * the erased rows cannot come back from a stale backup. Called right after
         * {@link #erase(MinecraftServer, UUID)}, and only when a live server is present.
         */
        void backupNow(MinecraftServer server);
    }

    private static final List<StoreEraser> STORES = new CopyOnWriteArrayList<>();

    /** The single composite eraser registered with Core. */
    private static final PlayerDataEraser ERASER = SecurityData::eraseAll;

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private SecurityData() {
    }

    /**
     * Registers NeroSecurity's composite eraser with Core's {@link PlayerDataErasure}. Step 4 of
     * common init — before any store or compat code, so nothing that stores player data can come
     * online ahead of its erasure path. Idempotent.
     */
    public static void init() {
        if (REGISTERED.compareAndSet(false, true)) {
            PlayerDataErasure.register(ERASER);
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] data-erasure hook registered ({} store(s))",
                    STORES.size());
        }
    }

    /**
     * Adds a player-keyed store to the composite eraser. Call once from the store's own init, before
     * the first server starts. Adding the same instance twice is ignored.
     */
    public static void addStore(StoreEraser store) {
        if (store == null) {
            throw new IllegalArgumentException("A security store eraser must not be null");
        }
        if (!STORES.contains(store)) {
            STORES.add(store);
        }
    }

    /**
     * Removes a store from the composite. Test teardown only — removing a real store at runtime
     * would silently break erasure for it.
     */
    public static boolean removeStore(StoreEraser store) {
        return STORES.remove(store);
    }

    /** How many stores the composite eraser currently walks. */
    public static int storeCount() {
        return STORES.size();
    }

    /** The composite eraser registered with Core (exposed so tests can register and unregister it). */
    public static PlayerDataEraser eraser() {
        return ERASER;
    }

    /**
     * Erases {@code player} from every listed store, refreshing each store's backup straight after
     * its own erasure. Every store is attempted even if an earlier one fails; any failure is then
     * re-thrown as a single summary so Core counts this request as incomplete instead of reporting
     * a clean erasure.
     */
    private static void eraseAll(MinecraftServer server, UUID player) {
        int failed = 0;
        for (StoreEraser store : STORES) {
            try {
                store.erase(server, player);
                if (server != null) {
                    store.backupNow(server);
                }
            } catch (RuntimeException | LinkageError e) {
                failed++;
                NeroSecurityCommon.LOGGER.warn(
                        "[NeroSecurity] erasure step for store '{}' failed ({}); continuing with the rest.",
                        store.name(), e.getClass().getName());
            }
        }
        if (failed > 0) {
            throw new IllegalStateException(
                    "NeroSecurity erasure incomplete: " + failed + " of " + STORES.size() + " store(s) failed");
        }
    }
}
