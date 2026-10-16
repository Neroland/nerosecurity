package za.co.neroland.nerosecurity.link;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * Everything the link module reads or does outside itself, behind one seam so sections, actions and the
 * event pipeline run in a plain JVM with {@code server = null}. Production: {@link LiveLinkContext}.
 * Server thread only.
 */
interface LinkContext {

    /** The running server, or null (no world loaded, or a plain-JVM test). */
    @Nullable MinecraftServer server();

    /** The installation store, or null when no server is running. */
    @Nullable SecurityInstallations installations();

    LinkSettings settings();

    long now();

    /** The identity-free audit read (no {@code LOG_READ}); newest first; empty when the log is off. */
    List<AuditEntry> peek(Installation installation, int limit);

    /** Publishes on NeroSecurity's own event bus (the audit recorder persists it). */
    void publish(SecurityEvent event);

    /** Live device status; never force-loads. */
    DeviceStatusSource deviceStatus();

    /**
     * The resolver for link calls: the production sources and lockdown rule, and <b>no operator
     * override</b> (an operator's phone is a phone).
     */
    AccessResolver resolver();

    /** Core's stored alerts for {@code player}, newest first. */
    List<LinkAlert> alerts(UUID player);

    boolean ackAlert(UUID player, String alertId);

    void raiseAlert(UUID player, LinkAlert alert);

    void dismissAlert(UUID player, String alertId);

    /** Publishes on Core's link event bus (the bridge forwards it to that player's app only). */
    void publishLink(LinkEvent event);

    /** A display name from the online list or the memory-only profile cache, else {@code "?"}. */
    String displayName(UUID player);

    /** Whether the privacy opt-out withholds this actor's identity. Fails private (true) on error. */
    boolean withheld(UUID actor);

    boolean isOnline(UUID player);

    @Nullable Lockdown enterLockdown(Installation installation, UUID actor, long now);

    @Nullable Lockdown confirmLockdown(Installation installation, UUID actor, long now);

    @Nullable Lockdown exitLockdown(Installation installation, UUID actor, long now);

    void silenceAlarms(Installation installation, long until);
}
