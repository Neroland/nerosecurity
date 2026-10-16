package za.co.neroland.nerosecurity.menu.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.audit.AuditService;
import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceKind;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceRow;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceState;
import za.co.neroland.nerosecurity.sensor.SensorHost;

/**
 * Builds {@link MonitorSnapshot}s on the server. {@link #build} reads the live world, config and audit
 * store; everything it delegates to ({@link #rows}, {@link #deviceRows}, {@link #state}, {@link #assemble})
 * is pure over store data, so the identity and size rules are tested in a plain JVM.
 *
 * <p>Events come only through {@link AuditService#peekAnonymous}, the identity-free read: the monitor
 * shows the actor <em>kind</em>, so viewing it is not a {@code LOG_READ} and needs only OPEN at MEMBER.
 */
public final class MonitorSnapshots {

    private MonitorSnapshots() {
    }

    /** The snapshot for a monitor at {@code monitorPos} linked to {@code installation} (server thread). */
    public static MonitorSnapshot build(ServerLevel level, Installation installation, BlockPos monitorPos) {
        long now = System.currentTimeMillis();
        boolean auditEnabled = NeroSecurityConfig.isAuditLogEnabled();
        boolean camerasEnabled = NeroSecurityConfig.isCamerasEnabled();
        int limit = NeroSecurityConfig.monitorEventCount();
        List<AnonymousEvent> events = auditEnabled
                ? AuditService.peekAnonymous(level.getServer(), installation, limit) : List.of();
        List<DeviceRow> devices = deviceRows(installation.devices(), level.dimension().identifier(), camerasEnabled,
                (pos, kind) -> probe(level, pos, kind));
        return assemble(monitorPos, installation.name(), installation.lockdown(), NeroSecurityConfig.isLockdownEnabled(),
                now, camerasEnabled, auditEnabled, devices, events, limit);
    }

    /** The state of the sensor at {@code pos}; reads the block entity only when its chunk is loaded. */
    private static DeviceState probe(ServerLevel level, BlockPos pos, DeviceKind kind) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SensorHost host)) {
            return DeviceState.UNLOADED;
        }
        long perTick = kind == DeviceKind.LASER ? NeroSecurityConfig.laserPowerPerTick()
                : NeroSecurityConfig.cameraPowerPerTick();
        return state(kind, host.armed(), host.energyAmount() >= Math.max(1L, perTick));
    }

    // --- pure pieces ------------------------------------------------------------------------------

    /**
     * The state word for a loaded sensor: armed wins; a powered laser that is not armed has no partner;
     * anything else lacks power.
     */
    public static DeviceState state(DeviceKind kind, boolean armed, boolean powered) {
        if (armed) {
            return DeviceState.ARMED;
        }
        return kind == DeviceKind.LASER && powered ? DeviceState.UNPAIRED : DeviceState.UNPOWERED;
    }

    /** The monitor's device kind for a stored block id; null for anything a monitor does not list. */
    public static @Nullable DeviceKind kindOf(@Nullable Identifier blockId) {
        if (DeviceIds.SECURITY_CAMERA.equals(blockId)) {
            return DeviceKind.CAMERA;
        }
        if (DeviceIds.LASER_GRID_NODE.equals(blockId)) {
            return DeviceKind.LASER;
        }
        return null;
    }

    /**
     * Camera and laser rows for the devices in {@code dimension}, cameras first, each kind by position,
     * at most {@link MonitorSnapshot#MAX_DEVICES}. Cameras are left out when they are disabled. The
     * probe runs only for rows that are kept.
     */
    public static List<DeviceRow> deviceRows(Map<DevicePos, Identifier> devices, Identifier dimension,
            boolean camerasEnabled, BiFunction<BlockPos, DeviceKind, DeviceState> probe) {
        record Candidate(BlockPos pos, DeviceKind kind) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<DevicePos, Identifier> entry : devices.entrySet()) {
            DevicePos at = entry.getKey();
            DeviceKind kind = kindOf(entry.getValue());
            if (at == null || kind == null || !dimension.equals(at.dimension())) {
                continue;
            }
            if (kind == DeviceKind.CAMERA && !camerasEnabled) {
                continue;
            }
            candidates.add(new Candidate(at.pos(), kind));
        }
        candidates.sort(Comparator.comparing(Candidate::kind).thenComparing(Candidate::pos));
        int n = Math.min(candidates.size(), MonitorSnapshot.MAX_DEVICES);
        List<DeviceRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Candidate c = candidates.get(i);
            DeviceState state = probe.apply(c.pos(), c.kind());
            rows.add(new DeviceRow(c.pos(), c.kind(), state == null ? DeviceState.UNLOADED : state));
        }
        return rows;
    }

    /**
     * Audit entries as identity-free rows, newest first, at most {@code limit} (clamped to
     * {@code 1..MAX_EVENTS}). The actor becomes {@code NONE}, {@code PLAYER} or {@code WITHHELD}.
     */
    public static List<AnonymousEvent> rows(List<AuditEntry> entries, long now, int limit) {
        List<AuditEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(AuditEntry::at).reversed());
        int n = Math.min(sorted.size(), clampLimit(limit));
        return AnonymousEvent.of(sorted.subList(0, n), now);
    }

    /** Assembles a snapshot from already-read values. */
    public static MonitorSnapshot assemble(BlockPos monitorPos, String installationName, Lockdown lockdown,
            boolean lockdownEnabled, long now, boolean camerasEnabled, boolean auditEnabled, List<DeviceRow> devices,
            List<AnonymousEvent> events, int limit) {
        boolean active = lockdownEnabled && lockdown != null && lockdown.active();
        int since = 0;
        int release = 0;
        @Nullable LockdownSource source = null;
        if (active && lockdown != null) {
            since = seconds(now - lockdown.since(), false);
            release = lockdown.isAlarm() ? seconds(lockdown.autoReleaseAt() - now, true) : 0;
            source = lockdown.source();
        }
        List<AnonymousEvent> shown = auditEnabled ? events : List.of();
        int n = Math.min(shown.size(), clampLimit(limit));
        return new MonitorSnapshot(monitorPos, installationName, active,
                source, since, release, !camerasEnabled, !auditEnabled,
                devices, shown.subList(0, n));
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(MonitorSnapshot.MAX_EVENTS, limit));
    }

    private static int seconds(long millis, boolean roundUp) {
        if (millis <= 0L) {
            return 0;
        }
        long s = roundUp ? (millis + 999L) / 1000L : millis / 1000L;
        return (int) Math.min(Integer.MAX_VALUE, s);
    }
}
