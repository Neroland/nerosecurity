package za.co.neroland.nerosecurity.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.audit.AuditService;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;
import za.co.neroland.nerosecurity.menu.HubPayloads.DeviceRow;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSnapshot;
import za.co.neroland.nerosecurity.menu.HubPayloads.MemberRow;

/**
 * Builds {@link HubSnapshot} v2 on the server. {@link #build} reads the live world, config and audit store;
 * {@link #deviceRows} and {@link #assemble} are pure over store data, so the size and identity rules are
 * tested in a plain JVM.
 *
 * <p>Events come only through {@link AuditService#peekAnonymous}, the identity-free read, so opening the hub
 * is not a {@code LOG_READ}. Member names come from {@link PlayerNames#display} (online list, then the
 * in-memory profile cache), for this installation's own rows only, and are kept nowhere.
 */
public final class HubSnapshots {

    private HubSnapshots() {
    }

    /** The snapshot for a viewer the resolver has just allowed to open the hub (server thread). */
    public static HubSnapshot build(MinecraftServer server, Installation installation, AccessDecision decision,
            boolean ok, @Nullable Component message) {
        List<MemberRow> owners = new ArrayList<>();
        for (UUID owner : installation.owners()) {
            if (!Installation.TOMBSTONE_OWNER.equals(owner)) {
                owners.add(new MemberRow(owner, PlayerNames.display(server, owner), AccessLevel.OWNER));
            }
        }
        List<MemberRow> members = new ArrayList<>();
        for (Map.Entry<UUID, AccessLevel> member : installation.members().entrySet()) {
            members.add(new MemberRow(member.getKey(), PlayerNames.display(server, member.getKey()), member.getValue()));
        }
        List<DeviceRow> devices = deviceRows(installation.devices(), (at, block) -> {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, at.dimension()));
            return DeviceStates.of(level, at.pos());
        });
        List<AnonymousEvent> events = NeroSecurityConfig.isAuditLogEnabled()
                ? AuditService.peekAnonymous(server, installation, HubPayloads.MAX_EVENTS) : List.of();
        return assemble(installation.id(), installation.name(), installation.lockdown(),
                NeroSecurityConfig.isLockdownEnabled(), System.currentTimeMillis(), members, owners,
                decision.effective(), decision.admin(), devices, events, ok, message);
    }

    // --- pure pieces ------------------------------------------------------------------------------

    /**
     * One row per linked device, sorted by block id then position, at most {@link HubPayloads#MAX_DEVICES}.
     * The probe runs only for rows that are kept; a null answer reads as unloaded.
     */
    public static List<DeviceRow> deviceRows(Map<DevicePos, Identifier> devices,
            BiFunction<DevicePos, Identifier, DeviceState> probe) {
        List<Map.Entry<DevicePos, Identifier>> sorted = new ArrayList<>();
        for (Map.Entry<DevicePos, Identifier> entry : devices.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                sorted.add(entry);
            }
        }
        sorted.sort(Comparator.<Map.Entry<DevicePos, Identifier>, String>comparing(e -> e.getValue().toString())
                .thenComparing(e -> e.getKey().pos()));
        int n = Math.min(sorted.size(), HubPayloads.MAX_DEVICES);
        List<DeviceRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map.Entry<DevicePos, Identifier> entry = sorted.get(i);
            DeviceState state = probe.apply(entry.getKey(), entry.getValue());
            rows.add(new DeviceRow(entry.getKey().pos(), entry.getValue(), state == null ? DeviceState.UNLOADED : state));
        }
        return rows;
    }

    /** Assembles a snapshot from already-read values (lockdown timing relative to {@code now}, epoch ms). */
    public static HubSnapshot assemble(UUID installationId, String name, @Nullable Lockdown lockdown,
            boolean lockdownEnabled, long now, List<MemberRow> members, List<MemberRow> owners,
            AccessLevel viewerLevel, boolean admin, List<DeviceRow> devices, List<AnonymousEvent> events, boolean ok,
            @Nullable Component message) {
        boolean active = lockdownEnabled && lockdown != null && lockdown.active();
        int since = 0;
        int release = 0;
        LockdownSource source = LockdownSource.MANUAL;
        if (active && lockdown != null) {
            since = seconds(now - lockdown.since(), false);
            release = lockdown.isAlarm() ? seconds(lockdown.autoReleaseAt() - now, true) : 0;
            source = lockdown.source();
        }
        List<AnonymousEvent> newest = events.size() > HubPayloads.MAX_EVENTS
                ? events.subList(0, HubPayloads.MAX_EVENTS) : events;
        return new HubSnapshot(installationId, name, active, source, since, release,
                lockdownEnabled, members, owners, viewerLevel, admin, devices, newest, ok, message);
    }

    private static int seconds(long millis, boolean roundUp) {
        if (millis <= 0L) {
            return 0;
        }
        long s = roundUp ? (millis + 999L) / 1000L : millis / 1000L;
        return (int) Math.min(Integer.MAX_VALUE, s);
    }
}
