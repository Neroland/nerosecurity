package za.co.neroland.nerosecurity.notice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * The "recording active" notice (notice before logging, POPIA s18 / GDPR Art. 13): the first time in a
 * session that a player is inside the monitored area of an installation that records anything, they
 * get one action-bar line naming the installation's own label (never its owner) and saying presence and
 * access events may be logged. Owners and members get it too.
 *
 * <p>Two paths deliver it, so nobody is logged before they were told: the periodic area check (every
 * {@code noticeCheckIntervalTicks}), and {@link #noticeBeforeLogging}, which a sensor calls in the same
 * tick, just before it publishes a presence or breach event naming a player who has not had this
 * installation's notice yet this session (a sprinting player can reach a camera's cone between two
 * area checks).
 *
 * <p><b>Not configurable off.</b> No config key disables it; the only way to stop it is to disable both
 * logging features, and then nothing records. The notice itself is not logged. The per-player memory
 * is transient and dropped when the player leaves.
 */
public final class RecordingNotice {

    public static final String KEY = "notice.nerosecurity.recording";

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();
    /** Player UUID to the installations they have been told about this session. Transient. */
    private static final Map<UUID, Set<UUID>> NOTICED = new ConcurrentHashMap<>();

    private RecordingNotice() {
    }

    /** Init step 4g. Idempotent. */
    public static void init() {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        ServerHooks.onStarted(server -> {
            NOTICED.clear();
            InstallationAreas.invalidateAll();
        });
        ServerHooks.onStopping(server -> NOTICED.clear());
        ServerHooks.onPlayerLeave(player -> NOTICED.remove(player.getUUID()));
        ServerHooks.onTick(RecordingNotice::tick);
        SecurityData.addStore(ERASER);
    }

    /** Transient per-player memory joins the composite eraser as store {@code "notices"}; nothing to back up. */
    static final SecurityData.StoreEraser ERASER = new SecurityData.StoreEraser() {
        @Override
        public String name() {
            return "notices";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            forget(player);
        }

        @Override
        public void backupNow(MinecraftServer server) {
            // Never persisted: nothing to refresh.
        }
    };

    /** Erasure: forgets which installations {@code player} has been told about. */
    public static void forget(UUID player) {
        if (player != null) {
            NOTICED.remove(player);
        }
    }

    /** Conformance probe: is anything remembered for {@code player}? */
    public static boolean holdsAnyFor(UUID player) {
        return player != null && NOTICED.containsKey(player);
    }

    private static void tick(MinecraftServer server) {
        int interval = Math.max(1, NeroSecurityConfig.noticeCheckIntervalTicks());
        if (server.getTickCount() % interval != 0) {
            return;
        }
        boolean audit = NeroSecurityConfig.isAuditLogEnabled();
        boolean cameras = NeroSecurityConfig.isCamerasEnabled();
        SecurityInstallations store = null;
        Map<Identifier, List<InstallationAreas.Area>> byDimension = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Identifier dimension = player.level().dimension().identifier();
            List<InstallationAreas.Area> areas = byDimension.computeIfAbsent(dimension,
                    d -> InstallationAreas.areas(server, d));
            for (InstallationAreas.Area area : areas) {
                if (!area.box().contains(player.position())) {
                    continue;
                }
                Set<UUID> seen = NOTICED.computeIfAbsent(player.getUUID(), id -> ConcurrentHashMap.newKeySet());
                if (seen.contains(area.installationId())) {
                    continue;
                }
                if (store == null) {
                    store = SecurityInstallations.get(server);
                }
                Installation installation = store.byId(area.installationId()).orElse(null);
                if (installation == null || !records(installation, audit, cameras)) {
                    continue;
                }
                seen.add(area.installationId());
                player.sendOverlayMessage(Component.translatable(KEY, installation.name()));
            }
        }
    }

    /**
     * A sensor or device is about to log {@code player} for {@code installation}: if they have not had that
     * installation's notice this session and it records anything, deliver it now (before the event is
     * published). Never throws: a failure here must not stop the sensor.
     */
    public static void noticeBeforeLogging(ServerPlayer player, Installation installation) {
        if (player == null || installation == null) {
            return;
        }
        try {
            if (!records(installation, NeroSecurityConfig.isAuditLogEnabled(), NeroSecurityConfig.isCamerasEnabled())) {
                return;
            }
            if (markNoticed(player.getUUID(), installation.id())) {
                player.sendOverlayMessage(Component.translatable(KEY, installation.name()));
            }
        } catch (RuntimeException | LinkageError e) {
            // The periodic check still covers the player.
        }
    }

    /**
     * A device is about to publish an event naming {@code actor} (a deny, a forced break, an open): if that
     * player is online, deliver the notice first, exactly as {@link #noticeBeforeLogging(ServerPlayer,
     * Installation)} does. A player who arrives by teleport, pearl or respawn and clicks a door before the next
     * periodic check is therefore told before the row exists. An offline or unknown actor gets nothing (nothing
     * can be shown). Never throws.
     */
    public static void noticeBeforeLogging(@Nullable MinecraftServer server, @Nullable UUID actor,
            @Nullable Installation installation) {
        if (server == null || actor == null || installation == null) {
            return;
        }
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(actor);
            if (player != null) {
                noticeBeforeLogging(player, installation);
            }
        } catch (RuntimeException | LinkageError e) {
            // The periodic check still covers the player.
        }
    }

    /** Records that {@code player} has had {@code installation}'s notice; true when this is the first time. */
    static boolean markNoticed(UUID player, UUID installation) {
        return NOTICED.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).add(installation);
    }

    /** Tests only: forget every player. */
    static void resetForTests() {
        NOTICED.clear();
    }

    /**
     * Whether {@code installation} records anything: the audit log is on, or cameras are on and it has a
     * camera or laser grid node linked. Pure.
     */
    public static boolean records(Installation installation, boolean auditLogEnabled, boolean camerasEnabled) {
        if (installation == null) {
            return false;
        }
        if (auditLogEnabled) {
            return true;
        }
        if (!camerasEnabled) {
            return false;
        }
        for (Identifier block : installation.devices().values()) {
            if (DeviceIds.isSensor(block)) {
                return true;
            }
        }
        return false;
    }

    /** Tests only: how many players are remembered. */
    static int rememberedPlayers() {
        return NOTICED.size();
    }
}
