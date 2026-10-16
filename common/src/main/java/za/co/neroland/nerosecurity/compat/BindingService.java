package za.co.neroland.nerosecurity.compat;

import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.block.field.FieldPassability;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesCompat;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge;
import za.co.neroland.nerosecurity.compat.factions.FactionsCompat;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceBridge;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceCompat;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;

/**
 * The only writer of integration bindings (faction, colony, station). Every method, in this order:
 * <ol>
 *   <li>re-reads the installation from the store by id and resolves {@link AccessAction#BIND_INTEGRATION} (OWNER)
 *       through the access resolver, so a demoted or removed owner is refused on the very next command. Only
 *       <b>stored ownership</b> counts: grants are taken from the membership rows alone ({@link #GRANTING_SOURCES}),
 *       so an OWNER keycard or an integration grant can never bind or unbind; denies, the lockdown rule and the
 *       operator override apply as always;</li>
 *   <li>for a bind only: refuses with {@code compat.nerosecurity.absent} unless that integration is active (the
 *       sibling is loaded, this build has its bridge, and its {@code *IntegrationEnabled} switch is on). An unbind
 *       is always allowed for an OWNER, even with the sibling absent or switched off: it only ever removes a
 *       grant, so an owner can always clear a binding the server can no longer check;</li>
 *   <li>for a bind: refuses a max level other than MEMBER or OFFICER ({@link #DEFAULT_MAX_LEVEL}, MEMBER, when the
 *       caller does not choose; OFFICER, which lets the organisation's members open the hub roster and read the audit
 *       log, must be asked for explicitly), then checks the <b>precondition</b>: the
 *       binder is a current member of the faction, a member (ally, chief, owner) of the colony, or the current
 *       owner of the station;</li>
 *   <li>writes the store, marks every force field's verdict cache stale, and publishes {@code *_BOUND} /
 *       {@code *_UNBOUND} with the binder as actor (audited through the normal recorder).</li>
 * </ol>
 *
 * <p>A binding stores the organisation id only, never the binder. A bridge that fails reads as "not a member"
 * (fail closed). Messages never name another player. Server thread only.
 *
 * <p>Built by constructor injection so the rules run in a plain JVM; {@link #DEFAULT} wires the production
 * resolver, the façades' bridges and the config switches.
 */
public final class BindingService {

    /** What happened, for callers and tests; {@link Outcome#result()} carries the message to show. */
    public enum Status {
        BOUND, UNBOUND, DENIED, ABSENT, MAX_LEVEL, NOT_MEMBER, NOT_STATION_OWNER, NOT_BOUND, GONE
    }

    /**
     * @param deny the resolver's reason when {@code status == DENIED}, else {@link DenyReason#NONE}
     */
    public record Outcome(Status status, DenyReason deny, ServiceResult result) {

        public boolean ok() {
            return result.ok();
        }

        static Outcome of(Status status, boolean ok, Component message) {
            return new Outcome(status, DenyReason.NONE, ok ? ServiceResult.success(message) : ServiceResult.failure(message));
        }
    }

    /** The max level of a faction or colony binding when the binder does not name one. OFFICER is opt-in. */
    public static final AccessLevel DEFAULT_MAX_LEVEL = AccessLevel.MEMBER;

    /** The only source whose grants count for binding: the installation's stored owners (and members) rows. */
    public static final Set<Identifier> GRANTING_SOURCES = Set.of(MembershipSource.ID);

    public static final BindingService DEFAULT = new BindingService(AccessResolver.DEFAULT,
            FactionsCompat::bridgeOrNull, NeroSecurityConfig::isFactionsIntegrationEnabled,
            ColoniesCompat::bridgeOrNull, NeroSecurityConfig::isColoniesIntegrationEnabled,
            NerospaceCompat::bridgeOrNull, NeroSecurityConfig::isNerospaceIntegrationEnabled,
            FieldPassability::bump, System::currentTimeMillis);

    private final AccessResolver resolver;
    private final Supplier<FactionsBridge> factions;
    private final BooleanSupplier factionsEnabled;
    private final Supplier<ColoniesBridge> colonies;
    private final BooleanSupplier coloniesEnabled;
    private final Supplier<NerospaceBridge> nerospace;
    private final BooleanSupplier nerospaceEnabled;
    private final Runnable invalidateFields;
    private final LongSupplier clock;

    public BindingService(AccessResolver resolver,
            Supplier<FactionsBridge> factions, BooleanSupplier factionsEnabled,
            Supplier<ColoniesBridge> colonies, BooleanSupplier coloniesEnabled,
            Supplier<NerospaceBridge> nerospace, BooleanSupplier nerospaceEnabled,
            Runnable invalidateFields, LongSupplier clock) {
        this.resolver = resolver;
        this.factions = factions;
        this.factionsEnabled = factionsEnabled;
        this.colonies = colonies;
        this.coloniesEnabled = coloniesEnabled;
        this.nerospace = nerospace;
        this.nerospaceEnabled = nerospaceEnabled;
        this.invalidateFields = invalidateFields;
        this.clock = clock;
    }

    // --- player conveniences (commands) ---------------------------------------------------------

    public Outcome bindFaction(ServerPlayer actor, Installation installation, Identifier faction, AccessLevel max) {
        MinecraftServer server = actor.level().getServer();
        return bindFaction(server, SecurityInstallations.get(server), actor.getUUID(), installation.id(), faction, max);
    }

    public Outcome bindColony(ServerPlayer actor, Installation installation, UUID colony, AccessLevel max) {
        MinecraftServer server = actor.level().getServer();
        return bindColony(server, SecurityInstallations.get(server), actor.getUUID(), installation.id(), colony, max);
    }

    public Outcome bindStation(ServerPlayer actor, Installation installation, int station) {
        MinecraftServer server = actor.level().getServer();
        return bindStation(server, SecurityInstallations.get(server), actor.getUUID(), installation.id(), station);
    }

    public Outcome unbindFaction(ServerPlayer actor, Installation installation) {
        MinecraftServer server = actor.level().getServer();
        return unbindFaction(server, SecurityInstallations.get(server), actor.getUUID(), installation.id());
    }

    public Outcome unbindColony(ServerPlayer actor, Installation installation) {
        MinecraftServer server = actor.level().getServer();
        return unbindColony(server, SecurityInstallations.get(server), actor.getUUID(), installation.id());
    }

    public Outcome unbindStation(ServerPlayer actor, Installation installation) {
        MinecraftServer server = actor.level().getServer();
        return unbindStation(server, SecurityInstallations.get(server), actor.getUUID(), installation.id());
    }

    // --- the rules (explicit server and store; server is null in plain-JVM tests) ----------------

    public Outcome bindFaction(@Nullable MinecraftServer server, SecurityInstallations store, UUID actor,
            UUID installationId, Identifier faction, AccessLevel max) {
        Installation installation = current(store, installationId);
        Outcome refused = authorise(server, actor, installation);
        if (refused != null) {
            return refused;
        }
        FactionsBridge bridge = active(factions, factionsEnabled);
        if (bridge == null) {
            return absent();
        }
        if (!SecurityInstallations.isBindingMaxLevel(max)) {
            return maxLevel();
        }
        if (faction == null || !isMember(bridge, server, actor, faction)) {
            return Outcome.of(Status.NOT_MEMBER, false, Component.translatable("command.nerosecurity.bind.not_member"));
        }
        if (!store.bindFaction(installationId, faction, max)) {
            return gone();
        }
        changed(SecurityEventType.FACTION_BOUND, installationId, actor);
        return Outcome.of(Status.BOUND, true,
                Component.translatable("command.nerosecurity.bind.faction", faction.toString(), max.displayName()));
    }

    public Outcome bindColony(@Nullable MinecraftServer server, SecurityInstallations store, UUID actor,
            UUID installationId, UUID colony, AccessLevel max) {
        Installation installation = current(store, installationId);
        Outcome refused = authorise(server, actor, installation);
        if (refused != null) {
            return refused;
        }
        ColoniesBridge bridge = active(colonies, coloniesEnabled);
        if (bridge == null) {
            return absent();
        }
        if (!SecurityInstallations.isBindingMaxLevel(max)) {
            return maxLevel();
        }
        if (colony == null || !isColonyMember(bridge, server, colony, actor)) {
            return Outcome.of(Status.NOT_MEMBER, false, Component.translatable("command.nerosecurity.bind.not_member"));
        }
        if (!store.bindColony(installationId, colony, max)) {
            return gone();
        }
        changed(SecurityEventType.COLONY_BOUND, installationId, actor);
        return Outcome.of(Status.BOUND, true, Component.translatable("command.nerosecurity.bind.colony", max.displayName()));
    }

    public Outcome bindStation(@Nullable MinecraftServer server, SecurityInstallations store, UUID actor,
            UUID installationId, int station) {
        Installation installation = current(store, installationId);
        Outcome refused = authorise(server, actor, installation);
        if (refused != null) {
            return refused;
        }
        NerospaceBridge bridge = active(nerospace, nerospaceEnabled);
        if (bridge == null) {
            return absent();
        }
        if (!ownsStation(bridge, server, station, actor)) {
            return Outcome.of(Status.NOT_STATION_OWNER, false,
                    Component.translatable("command.nerosecurity.bind.not_station_owner"));
        }
        if (!store.bindStation(installationId, station)) {
            return gone();
        }
        changed(SecurityEventType.STATION_BOUND, installationId, actor);
        return Outcome.of(Status.BOUND, true, Component.translatable("command.nerosecurity.bind.station", station));
    }

    public Outcome unbindFaction(@Nullable MinecraftServer server, SecurityInstallations store, UUID actor,
            UUID installationId) {
        Installation installation = current(store, installationId);
        Outcome refused = authorise(server, actor, installation);
        if (refused != null) {
            return refused;
        }
        // No integration check: removing a grant is always allowed for an OWNER, sibling present or not.
        if (installation.boundFaction() == null || !store.unbindFaction(installationId)) {
            return notBound();
        }
        changed(SecurityEventType.FACTION_UNBOUND, installationId, actor);
        return unbound();
    }

    public Outcome unbindColony(@Nullable MinecraftServer server, SecurityInstallations store, UUID actor,
            UUID installationId) {
        Installation installation = current(store, installationId);
        Outcome refused = authorise(server, actor, installation);
        if (refused != null) {
            return refused;
        }
        // No integration check: removing a grant is always allowed for an OWNER, sibling present or not.
        if (installation.boundColony() == null || !store.unbindColony(installationId)) {
            return notBound();
        }
        changed(SecurityEventType.COLONY_UNBOUND, installationId, actor);
        return unbound();
    }

    public Outcome unbindStation(@Nullable MinecraftServer server, SecurityInstallations store, UUID actor,
            UUID installationId) {
        Installation installation = current(store, installationId);
        Outcome refused = authorise(server, actor, installation);
        if (refused != null) {
            return refused;
        }
        // No integration check: removing a grant is always allowed for an OWNER, sibling present or not.
        if (installation.boundStation() == null || !store.unbindStation(installationId)) {
            return notBound();
        }
        changed(SecurityEventType.STATION_UNBOUND, installationId, actor);
        return unbound();
    }

    /** Whether faction bindings do anything right now (for the "(mod not installed)" marker). */
    public boolean factionsActive() {
        return active(factions, factionsEnabled) != null;
    }

    public boolean coloniesActive() {
        return active(colonies, coloniesEnabled) != null;
    }

    public boolean nerospaceActive() {
        return active(nerospace, nerospaceEnabled) != null;
    }

    // --- plumbing -------------------------------------------------------------------------------

    private static @Nullable Installation current(SecurityInstallations store, UUID installationId) {
        return store == null || installationId == null ? null : store.byId(installationId).orElse(null);
    }

    /** Null when the actor may bind (a stored OWNER, or an operator); otherwise the refusal. */
    private @Nullable Outcome authorise(@Nullable MinecraftServer server, UUID actor, @Nullable Installation installation) {
        if (installation == null || actor == null) {
            return gone();
        }
        AccessQuery query = new AccessQuery(server, actor, installation, AccessAction.BIND_INTEGRATION, installation.hubPos());
        AccessDecision decision = resolver.resolve(query, AccessAction.BIND_INTEGRATION.defaultMinimum, GRANTING_SOURCES);
        if (decision.allowed()) {
            return null;
        }
        return new Outcome(Status.DENIED, decision.reason(), ServiceResult.failure(decision.message()));
    }

    private static <B> @Nullable B active(Supplier<B> bridge, BooleanSupplier enabled) {
        try {
            return enabled.getAsBoolean() ? bridge.get() : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean isMember(FactionsBridge bridge, @Nullable MinecraftServer server, UUID actor, Identifier faction) {
        try {
            return bridge.isMember(server, actor, faction);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean isColonyMember(ColoniesBridge bridge, @Nullable MinecraftServer server, UUID colony, UUID actor) {
        try {
            ColoniesBridge.Role role = bridge.roleOf(server, colony, actor);
            return role != null && role.member();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean ownsStation(NerospaceBridge bridge, @Nullable MinecraftServer server, int station, UUID actor) {
        try {
            return bridge.ownedBy(server, station, actor);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private void changed(SecurityEventType type, UUID installationId, UUID actor) {
        invalidateFields.run();
        SecurityEventBus.publish(SecurityEvent.of(type, installationId, null, actor, clock.getAsLong()));
    }

    private static Outcome absent() {
        return Outcome.of(Status.ABSENT, false, Component.translatable("compat.nerosecurity.absent"));
    }

    private static Outcome maxLevel() {
        return Outcome.of(Status.MAX_LEVEL, false, Component.translatable("command.nerosecurity.bind.max_level"));
    }

    private static Outcome gone() {
        return Outcome.of(Status.GONE, false, Component.translatable("command.nerosecurity.not_found"));
    }

    private static Outcome notBound() {
        return Outcome.of(Status.NOT_BOUND, false, Component.translatable("command.nerosecurity.unbind.none"));
    }

    private static Outcome unbound() {
        return Outcome.of(Status.UNBOUND, true, Component.translatable("command.nerosecurity.unbind.done"));
    }
}
