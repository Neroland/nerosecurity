package za.co.neroland.nerosecurity.notice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The monitored area of every installation, per dimension: the box enclosing its hub and every linked
 * device in that dimension, grown by {@link #margin} = the largest of {@code noticeMarginBlocks},
 * {@code cameraMaxRange} and {@code laserMaxRange}, so the area covers the whole sensing envelope of
 * every device (a camera with range modules sees past the notice margin). Used only to show the
 * recording notice; a box is not a boundary anyone can see (the notice carries no position).
 *
 * <p>Cached and derived from the store only. {@code InstallationService} calls {@link #invalidate} on
 * every create, link, unlink, loss, orphan and dissolve; the whole cache is dropped on server start.
 * Server thread only.
 */
public final class InstallationAreas {

    /** One installation's monitored box in one dimension. */
    public record Area(UUID installationId, Identifier dimension, AABB box) {
    }

    /** Installation id to its areas (one per dimension it has blocks in). */
    private static final Map<UUID, List<Area>> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> STALE = ConcurrentHashMap.newKeySet();
    private static volatile boolean rebuildAll = true;
    /** The margin the cache was built with; a config change rebuilds everything. */
    private static int builtMargin = -1;

    private InstallationAreas() {
    }

    /** One installation's area changed (or it is gone). */
    public static void invalidate(UUID installationId) {
        if (installationId != null) {
            STALE.add(installationId);
        }
    }

    /** Drop everything; the next query rebuilds from the store. */
    public static void invalidateAll() {
        rebuildAll = true;
    }

    /** Every area in {@code dimension}, rebuilding stale entries from the store first. */
    public static List<Area> areas(MinecraftServer server, Identifier dimension) {
        refresh(SecurityInstallations.get(server), margin(NeroSecurityConfig.noticeMarginBlocks(),
                NeroSecurityConfig.cameraMaxRange(), NeroSecurityConfig.laserMaxRange()));
        List<Area> out = new ArrayList<>();
        for (List<Area> areas : CACHE.values()) {
            for (Area area : areas) {
                if (area.dimension().equals(dimension)) {
                    out.add(area);
                }
            }
        }
        return out;
    }

    /**
     * The area margin: no smaller than the configured notice margin, and no smaller than the farthest any
     * camera or laser grid node can sense, so everyone a sensor can log is inside the noticed area. Pure.
     */
    public static int margin(int noticeMarginBlocks, int cameraMaxRange, int laserMaxRange) {
        return Math.max(0, Math.max(noticeMarginBlocks, Math.max(cameraMaxRange, laserMaxRange)));
    }

    private static synchronized void refresh(SecurityInstallations store, int margin) {
        if (margin != builtMargin) {
            builtMargin = margin;
            rebuildAll = true;
        }
        if (rebuildAll) {
            rebuildAll = false;
            STALE.clear();
            CACHE.clear();
            for (Installation installation : store.all()) {
                put(installation, margin);
            }
            return;
        }
        if (STALE.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(STALE)) {
            STALE.remove(id);
            CACHE.remove(id);
            store.byId(id).ifPresent(installation -> put(installation, margin));
        }
    }

    private static void put(Installation installation, int margin) {
        List<Area> areas = areasFor(installation, margin);
        if (!areas.isEmpty()) {
            CACHE.put(installation.id(), areas);
        }
    }

    /**
     * The pure rule: per dimension, the block-aligned box over the hub (if any) and every device there,
     * inflated by {@code margin}. An installation with no hub and no devices has no area.
     */
    public static List<Area> areasFor(Installation installation, int margin) {
        Map<Identifier, int[]> bounds = new LinkedHashMap<>();
        List<DevicePos> positions = new ArrayList<>(installation.devices().keySet());
        if (installation.hubPos() != null) {
            positions.add(installation.hubPos());
        }
        for (DevicePos pos : positions) {
            BlockPos p = pos.pos();
            int[] b = bounds.computeIfAbsent(pos.dimension(), d -> new int[] {
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
            b[0] = Math.min(b[0], p.getX());
            b[1] = Math.min(b[1], p.getY());
            b[2] = Math.min(b[2], p.getZ());
            b[3] = Math.max(b[3], p.getX());
            b[4] = Math.max(b[4], p.getY());
            b[5] = Math.max(b[5], p.getZ());
        }
        int m = Math.max(0, margin);
        List<Area> out = new ArrayList<>(bounds.size());
        bounds.forEach((dimension, b) -> out.add(new Area(installation.id(), dimension,
                new AABB(b[0], b[1], b[2], b[3] + 1.0, b[4] + 1.0, b[5] + 1.0).inflate(m))));
        return out;
    }

    /** Tests only. */
    static Map<UUID, List<Area>> snapshot() {
        return new HashMap<>(CACHE);
    }
}
