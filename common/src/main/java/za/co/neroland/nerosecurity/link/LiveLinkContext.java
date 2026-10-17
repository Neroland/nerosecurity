package za.co.neroland.nerosecurity.link;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AccessSources;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.alarm.AlarmDispatcher;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.audit.AuditService;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.server.ServerHooks;

/** The production {@link LinkContext}: the running server from {@link ServerHooks#currentServer()}. */
final class LiveLinkContext implements LinkContext {

    static final LiveLinkContext INSTANCE = new LiveLinkContext();

    /** The production sources and lockdown rule with the operator override switched off. */
    private static final AccessResolver NO_OPERATOR = new AccessResolver(AccessSources::all, AdminCheck.NONE,
            NeroSecurityConfig::isLockdownEnabled);

    private LiveLinkContext() {
    }

    @Override
    public @Nullable MinecraftServer server() {
        return ServerHooks.currentServer();
    }

    @Override
    public @Nullable SecurityInstallations installations() {
        MinecraftServer server = server();
        return server == null ? null : SecurityInstallations.get(server);
    }

    @Override
    public LinkSettings settings() {
        return LinkSettings.fromConfig();
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public List<AuditEntry> peek(Installation installation, int limit) {
        MinecraftServer server = server();
        return server == null ? List.of() : AuditService.peek(server, installation, limit);
    }

    @Override
    public void publish(SecurityEvent event) {
        SecurityEventBus.publish(event);
    }

    @Override
    public DeviceStatusSource deviceStatus() {
        MinecraftServer server = server();
        if (server == null) {
            return (installation, pos, blockId, now) -> DeviceStatusSource.Status.UNLOADED;
        }
        return new DeviceStatusReader(server);
    }

    @Override
    public AccessResolver resolver() {
        return NO_OPERATOR;
    }

    @Override
    public List<LinkAlert> alerts(UUID player) {
        MinecraftServer server = server();
        return server == null || player == null ? List.of() : LinkAlerts.get(server).list(player);
    }

    @Override
    public boolean ackAlert(UUID player, String alertId) {
        MinecraftServer server = server();
        return server != null && LinkAlerts.get(server).ack(server, player, alertId);
    }

    @Override
    public void raiseAlert(UUID player, LinkAlert alert) {
        MinecraftServer server = server();
        if (server != null) {
            LinkAlerts.get(server).raise(server, player, alert);
        }
    }

    @Override
    public void dismissAlert(UUID player, String alertId) {
        MinecraftServer server = server();
        if (server != null) {
            LinkAlerts.get(server).dismiss(player, alertId);
        }
    }

    @Override
    public void publishLink(LinkEvent event) {
        NeroLinkRegistry.eventBus().publish(event);
    }

    @Override
    public String displayName(UUID player) {
        return PlayerNames.display(server(), player);
    }

    @Override
    public boolean isOnline(UUID player) {
        MinecraftServer server = server();
        return server != null && player != null && server.getPlayerList().getPlayer(player) != null;
    }

    @Override
    public @Nullable Lockdown enterLockdown(Installation installation, UUID actor, long now) {
        SecurityInstallations store = installations();
        return store == null ? null : InstallationService.enterLockdownRemote(server(), store, actor, installation, now);
    }

    @Override
    public @Nullable Lockdown confirmLockdown(Installation installation, UUID actor, long now) {
        SecurityInstallations store = installations();
        return store == null ? null : InstallationService.confirmLockdownRemote(server(), store, actor, installation, now);
    }

    @Override
    public @Nullable Lockdown exitLockdown(Installation installation, UUID actor, long now) {
        SecurityInstallations store = installations();
        return store == null ? null : InstallationService.exitLockdownRemote(server(), store, actor, installation, now);
    }

    @Override
    public void silenceAlarms(Installation installation, long until) {
        SecurityInstallations store = installations();
        if (store != null) {
            AlarmDispatcher.silenceAll(server(), store, installation, until);
        }
    }
}
