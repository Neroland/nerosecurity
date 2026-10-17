package za.co.neroland.nerosecurity.link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.alarm.AlarmDispatcher;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** An in-memory {@link LinkContext}: no server, no Core bootstrap. */
class FakeLinkContext implements LinkContext {

    static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    SecurityInstallations store = new SecurityInstallations();
    LinkSettings settings = LinkSettings.defaults();
    long now = 1_792_000_000_000L;
    final Map<UUID, List<AuditEntry>> audit = new HashMap<>();
    final List<SecurityEvent> published = new ArrayList<>();
    final List<LinkEvent> linkEvents = new ArrayList<>();
    final Map<UUID, Map<String, LinkAlert>> alerts = new HashMap<>();
    final Map<UUID, String> names = new HashMap<>();
    DeviceStatusSource status = (i, pos, id, t) -> DeviceStatusSource.Status.UNLOADED;
    List<AccessSource> sources = new ArrayList<>(List.of(MembershipSource.INSTANCE));
    AdminCheck admin = AdminCheck.NONE;
    boolean alertsThrow;
    final java.util.Set<UUID> online = new java.util.HashSet<>();

    // --- builders ---------------------------------------------------------------------------------

    Installation install(UUID owner, String name, int x) {
        DevicePos hub = new DevicePos(OVERWORLD, new BlockPos(x, 64, 0));
        return store.create(owner, name, hub, 0L, 64).installation();
    }

    DevicePos link(Installation installation, String path, int x, int y, int z) {
        DevicePos pos = new DevicePos(OVERWORLD, new BlockPos(x, y, z));
        store.link(installation.id(), pos, Identifier.fromNamespaceAndPath("nerosecurity", path), 4096);
        return pos;
    }

    Installation fresh(Installation installation) {
        return store.byId(installation.id()).orElseThrow();
    }

    void addAudit(Installation installation, AuditEntry entry) {
        audit.computeIfAbsent(installation.id(), k -> new ArrayList<>()).add(0, entry);
    }

    // --- LinkContext ------------------------------------------------------------------------------

    @Override
    public MinecraftServer server() {
        return null;
    }

    @Override
    public SecurityInstallations installations() {
        return store;
    }

    @Override
    public LinkSettings settings() {
        return settings;
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public List<AuditEntry> peek(Installation installation, int limit) {
        List<AuditEntry> rows = audit.getOrDefault(installation.id(), List.of());
        return List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
    }

    @Override
    public void publish(SecurityEvent event) {
        published.add(event);
    }

    @Override
    public DeviceStatusSource deviceStatus() {
        return status;
    }

    @Override
    public AccessResolver resolver() {
        return new AccessResolver(() -> sources, admin, () -> settings.lockdownEnabled());
    }

    @Override
    public List<LinkAlert> alerts(UUID player) {
        if (alertsThrow) {
            throw new IllegalStateException("store down");
        }
        return new ArrayList<>(alerts.getOrDefault(player, Map.of()).values());
    }

    @Override
    public boolean ackAlert(UUID player, String alertId) {
        Map<String, LinkAlert> mine = alerts.get(player);
        if (mine == null || !mine.containsKey(alertId)) {
            return false;
        }
        mine.put(alertId, mine.get(alertId).withAcked());
        return true;
    }

    @Override
    public void raiseAlert(UUID player, LinkAlert alert) {
        alerts.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(alert.id(), alert);
    }

    @Override
    public void dismissAlert(UUID player, String alertId) {
        Map<String, LinkAlert> mine = alerts.get(player);
        if (mine != null) {
            mine.remove(alertId);
        }
    }

    @Override
    public void publishLink(LinkEvent event) {
        linkEvents.add(event);
    }

    @Override
    public String displayName(UUID player) {
        return names.getOrDefault(player, "?");
    }

    @Override
    public boolean isOnline(UUID player) {
        return online.contains(player);
    }

    @Override
    public Lockdown enterLockdown(Installation installation, UUID actor, long t) {
        return InstallationService.enterLockdownRemote(null, store, actor, installation, t);
    }

    @Override
    public Lockdown confirmLockdown(Installation installation, UUID actor, long t) {
        return InstallationService.confirmLockdownRemote(null, store, actor, installation, t);
    }

    @Override
    public Lockdown exitLockdown(Installation installation, UUID actor, long t) {
        return InstallationService.exitLockdownRemote(null, store, actor, installation, t);
    }

    @Override
    public void silenceAlarms(Installation installation, long until) {
        AlarmDispatcher.silenceAll(null, store, installation, until);
    }

    Set<String> alertIds(UUID player) {
        return alerts.getOrDefault(player, Map.of()).keySet();
    }
}
