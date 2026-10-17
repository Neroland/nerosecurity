package za.co.neroland.nerosecurity.audit;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * The one security-event listener that persists. Every event published on {@link SecurityEventBus}
 * while a server is running becomes one {@link AuditEntry} in {@link SecurityAuditLog}, unless the audit
 * log is switched off or the event is {@code AUDIT_PURGED} (never stored: a purge must not create rows).
 *
 * <p>Before storing, the actor passes through the {@link ActorPolicy} (the privacy opt-out): a withheld
 * actor is stored as {@code null} with {@code actorWithheld = true}. This is the only class that
 * constructs audit entries.
 *
 * <p>Producers (devices, services) never call this class: they publish on the bus, and the bus isolates
 * this listener, so a failing audit write can never stop an alarm or a lockdown transition.
 */
public final class AuditRecorder {

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();
    private static final AtomicBoolean WARNED_UNBOUND = new AtomicBoolean();
    private static volatile @Nullable MinecraftServer bound;
    private static volatile ActorPolicy policy = ActorPolicy.NONE;

    private AuditRecorder() {
    }

    /** Init step 4e: subscribes to the bus and binds to the running server via {@link ServerHooks}. Idempotent. */
    public static void init() {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        ServerHooks.onStarted(server -> bound = server);
        ServerHooks.onStopping(server -> bound = null);
        SecurityEventBus.subscribe(listener(
                () -> {
                    MinecraftServer server = bound;
                    return server == null ? null : SecurityAuditLog.get(server);
                },
                () -> bound,
                NeroSecurityConfig::isAuditLogEnabled,
                NeroSecurityConfig::privacyAuditMaxEntriesPerInstallation,
                () -> policy));
    }

    /** Installs the privacy opt-out lookup (the default withholds nobody). */
    public static void setActorPolicy(ActorPolicy actorPolicy) {
        policy = actorPolicy == null ? ActorPolicy.NONE : actorPolicy;
    }

    /** The current opt-out policy. */
    public static ActorPolicy actorPolicy() {
        return policy;
    }

    /**
     * A recording listener with injected collaborators (tests pass a store built with {@code new}).
     *
     * @param store        the store to write to, or null while no server is running (nothing is written)
     * @param server       the running server for the policy lookup, or null
     */
    public static SecurityEventBus.Listener listener(Supplier<@Nullable SecurityAuditLog> store,
            Supplier<@Nullable MinecraftServer> server, BooleanSupplier auditEnabled, IntSupplier cap,
            Supplier<ActorPolicy> actorPolicy) {
        return event -> {
            if (event == null || event.type() == SecurityEventType.AUDIT_PURGED || !auditEnabled.getAsBoolean()) {
                return;
            }
            SecurityAuditLog target = store.get();
            if (target == null) {
                if (WARNED_UNBOUND.compareAndSet(false, true)) {
                    NeroSecurityCommon.LOGGER.debug("[NeroSecurity] security event {} published with no server "
                            + "running; not recorded.", event.type());
                }
                return;
            }
            target.append(event.installationId(), entryFor(event, server.get(), actorPolicy.get()), cap.getAsInt());
        };
    }

    /**
     * The entry an event becomes after the opt-out policy. {@code LOG_READ} is exempt: it records who read
     * other people's names, an accountability record that must stay attributable to the reader, so it always
     * names them whatever their own opt-out says.
     */
    static AuditEntry entryFor(SecurityEvent event, @Nullable MinecraftServer server, @Nullable ActorPolicy actorPolicy) {
        UUID actor = event.actor();
        boolean withheld = false;
        if (actor != null && !alwaysAttributed(event.type())) {
            ActorPolicy p = actorPolicy == null ? ActorPolicy.NONE : actorPolicy;
            try {
                withheld = p.withholdActor(server, actor);
            } catch (RuntimeException | LinkageError e) {
                // An opt-out lookup that fails withholds (fail private).
                withheld = true;
            }
        }
        return new AuditEntry(event.at(), event.type(), withheld ? null : actor, withheld, event.devicePos(),
                event.severity());
    }

    /** Event types whose actor is never withheld by the opt-out (accountability records). */
    public static boolean alwaysAttributed(@Nullable SecurityEventType type) {
        return type == SecurityEventType.LOG_READ;
    }
}
