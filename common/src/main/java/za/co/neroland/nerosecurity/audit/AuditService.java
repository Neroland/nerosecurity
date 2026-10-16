package za.co.neroland.nerosecurity.audit;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The only read path into the audit log.
 *
 * <ul>
 *   <li>{@link #read} — the privileged read that may show identities (the {@code /nerosecurity log}
 *       command, the hub's log view). Resolves {@code READ_LOG} at OFFICER, then publishes a
 *       {@code LOG_READ} event with the reader as actor <em>before</em> returning any row, so the read
 *       is itself on record even if rendering fails. Purges expired rows first.</li>
 *   <li>{@link #peek} — the identity-free read for the monitor and other surfaces that show only
 *       {@link ActorKind}. No {@code LOG_READ}; callers must never render the actor UUID. Purges
 *       expired rows first.</li>
 * </ul>
 *
 * <p>Built by constructor injection so the whole path runs in a plain JVM; {@link #DEFAULT} wires the
 * production resolver, store, config, clock and event bus.
 */
public final class AuditService {

    /** The most rows one read returns. */
    public static final int MAX_READ = 100;

    public static final AuditService DEFAULT = new AuditService(AccessResolver.DEFAULT, SecurityAuditLog::get,
            NeroSecurityConfig::isAuditLogEnabled, System::currentTimeMillis,
            installation -> RetentionPolicy.fromConfig(installation.auditRetentionDaysOverride()),
            SecurityEventBus::publish);

    /** How a read ended. */
    public enum Status { OK, DENIED, DISABLED }

    /**
     * A read's outcome: on {@link Status#OK} the rows, newest first; otherwise the message to show the
     * reader (no rows).
     */
    public record ReadResult(Status status, Component message, List<AuditEntry> entries) {

        public ReadResult {
            entries = List.copyOf(entries);
        }

        public boolean ok() {
            return status == Status.OK;
        }

        static ReadResult denied(Component message) {
            return new ReadResult(Status.DENIED, message, List.of());
        }

        static ReadResult disabled() {
            return new ReadResult(Status.DISABLED, Component.translatable("command.nerosecurity.log.disabled"), List.of());
        }

        static ReadResult of(List<AuditEntry> entries) {
            return new ReadResult(Status.OK, entries.isEmpty()
                    ? Component.translatable("command.nerosecurity.log.none") : Component.empty(), entries);
        }
    }

    private final AccessResolver resolver;
    private final Function<MinecraftServer, SecurityAuditLog> store;
    private final BooleanSupplier auditEnabled;
    private final LongSupplier clock;
    private final Function<Installation, RetentionPolicy> policy;
    private final Consumer<SecurityEvent> publisher;

    public AuditService(AccessResolver resolver, Function<MinecraftServer, SecurityAuditLog> store,
            BooleanSupplier auditEnabled, LongSupplier clock, Function<Installation, RetentionPolicy> policy,
            Consumer<SecurityEvent> publisher) {
        this.resolver = resolver;
        this.store = store;
        this.auditEnabled = auditEnabled;
        this.clock = clock;
        this.policy = policy;
        this.publisher = publisher;
    }

    // --- production entry points ------------------------------------------------------------------

    /** The privileged, audited read by a player. {@code at} is the device read from, if any. */
    public static ReadResult read(ServerPlayer reader, Installation installation, @Nullable DevicePos at, int limit) {
        return DEFAULT.readAs(reader.level().getServer(), reader.getUUID(), installation, at, limit);
    }

    /** The identity-free read (monitor): rows for {@link ActorKind} display only, no {@code LOG_READ}. */
    public static List<AuditEntry> peek(MinecraftServer server, Installation installation, int limit) {
        return DEFAULT.peekAs(server, installation, limit);
    }

    /** {@link #peek} already mapped to identity-free rows. */
    public static List<AnonymousEvent> peekAnonymous(MinecraftServer server, Installation installation, int limit) {
        return AnonymousEvent.of(peek(server, installation, limit), System.currentTimeMillis());
    }

    // --- injectable core --------------------------------------------------------------------------

    /** {@link #read} with an explicit server (null in tests) and reader id. */
    public ReadResult readAs(@Nullable MinecraftServer server, UUID reader, Installation installation,
            @Nullable DevicePos at, int limit) {
        AccessDecision decision = resolver.resolve(
                new AccessQuery(server, reader, installation, AccessAction.READ_LOG, at), AccessAction.READ_LOG.defaultMinimum);
        if (!decision.allowed()) {
            return ReadResult.denied(decision.message());
        }
        if (!auditEnabled.getAsBoolean()) {
            return ReadResult.disabled();
        }
        long now = clock.getAsLong();
        // Audited before anything is read, so the read is on record even if what follows throws.
        publisher.accept(SecurityEvent.of(SecurityEventType.LOG_READ, installation.id(), at, reader, now));
        SecurityAuditLog log = store.apply(server);
        log.purge(installation.id(), now, policy.apply(installation));
        return ReadResult.of(log.newestFirst(installation.id(), clamp(limit)));
    }

    /** {@link #peek} with an explicit server (null in tests). */
    public List<AuditEntry> peekAs(@Nullable MinecraftServer server, Installation installation, int limit) {
        if (!auditEnabled.getAsBoolean()) {
            return List.of();
        }
        SecurityAuditLog log = store.apply(server);
        log.purge(installation.id(), clock.getAsLong(), policy.apply(installation));
        return log.newestFirst(installation.id(), clamp(limit));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(MAX_READ, limit));
    }
}
