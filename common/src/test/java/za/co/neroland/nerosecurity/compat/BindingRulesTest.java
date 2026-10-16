package za.co.neroland.nerosecurity.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.access.KeycardSource;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.compat.BindingService.Outcome;
import za.co.neroland.nerosecurity.compat.BindingService.Status;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge.Role;
import za.co.neroland.nerosecurity.compat.colonies.ColonySource;
import za.co.neroland.nerosecurity.compat.factions.FactionSource;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceBridge;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The binding service: OWNER only (re-resolved), a bind needs the integration active, the max level is MEMBER or
 * OFFICER, and the binder must belong to the faction or colony (or own the station). An unbind needs only OWNER,
 * even with the integration absent or disabled. Plus the unbind and suspension events. Plain JVM, server null, fake bridges.
 */
class BindingRulesTest {

    private static final Identifier FACTION = Identifier.fromNamespaceAndPath("nerofactions", "guild");
    private static final UUID COLONY = UUID.randomUUID();
    private static final int STATION = 7;

    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private SecurityInstallations store;
    private Installation installation;
    private final List<SecurityEvent> events = new ArrayList<>();
    private SecurityEventBus.Subscription subscription;
    private final AtomicInteger invalidations = new AtomicInteger();

    // Fakes: who is a member/owner, and switches for "absent" and "disabled".
    private final Set<UUID> factionMembers = new HashSet<>();
    private final Map<Role, Set<UUID>> colonyRoles = new EnumMap<>(Role.class);
    private final Set<UUID> stationOwners = new HashSet<>();
    private final AtomicBoolean factionsPresent = new AtomicBoolean(true);
    private final AtomicBoolean coloniesPresent = new AtomicBoolean(true);
    private final AtomicBoolean nerospacePresent = new AtomicBoolean(true);
    private final AtomicBoolean factionsEnabled = new AtomicBoolean(true);
    private final AtomicReference<RuntimeException> factionsThrow = new AtomicReference<>();

    private final FactionsBridge factions = new FactionsBridge() {
        @Override
        public boolean isMember(MinecraftServer server, UUID player, Identifier faction) {
            if (factionsThrow.get() != null) {
                throw factionsThrow.get();
            }
            return FACTION.equals(faction) && factionMembers.contains(player);
        }

        @Override
        public Set<Identifier> membershipsOf(MinecraftServer server, UUID player) {
            return factionMembers.contains(player) ? Set.of(FACTION) : Set.of();
        }

        @Override
        public Tier tierOf(MinecraftServer server, UUID player, Identifier faction) {
            return factionMembers.contains(player) ? Tier.TRUSTED : Tier.OUTSIDER;
        }
    };

    private final ColoniesBridge colonies = new ColoniesBridge() {
        @Override
        public boolean exists(MinecraftServer server, UUID colony) {
            return COLONY.equals(colony);
        }

        @Override
        public Role roleOf(MinecraftServer server, UUID colony, UUID player) {
            if (!COLONY.equals(colony)) {
                return Role.STRANGER;
            }
            for (Map.Entry<Role, Set<UUID>> e : colonyRoles.entrySet()) {
                if (e.getValue().contains(player)) {
                    return e.getKey();
                }
            }
            return Role.STRANGER;
        }

        @Override
        public UUID colonyAt(ServerLevel level, BlockPos pos) {
            return null;
        }
    };

    private final NerospaceBridge nerospace = new NerospaceBridge() {
        @Override
        public boolean ownedBy(MinecraftServer server, int station, UUID player) {
            return station == STATION && stationOwners.contains(player);
        }

        @Override
        public List<Integer> stationsOwnedBy(MinecraftServer server, UUID player) {
            return stationOwners.contains(player) ? List.of(STATION) : List.of();
        }
    };

    private BindingService service;

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = CompatFixtures.create(store, owner, 0);
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE), AdminCheck.NONE, () -> true);
        service = serviceWith(resolver);
        subscription = SecurityEventBus.subscribe(installation.id(), events::add);
    }

    private BindingService serviceWith(AccessResolver resolver) {
        return new BindingService(resolver,
                () -> factionsPresent.get() ? factions : null, factionsEnabled::get,
                () -> coloniesPresent.get() ? colonies : null, () -> true,
                () -> nerospacePresent.get() ? nerospace : null, () -> true,
                invalidations::incrementAndGet, () -> 42L);
    }

    /** A source granting {@code who} OWNER under {@code id}: an encrypted OWNER keycard, or any integration. */
    private static AccessSource grantsOwner(Identifier id, UUID who) {
        return new AccessSource() {
            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public AccessVote vote(za.co.neroland.nerosecurity.access.AccessQuery query) {
                return who.equals(query.actor()) ? new AccessVote.Grant(AccessLevel.OWNER) : AccessVote.Abstain.INSTANCE;
            }
        };
    }

    // --- S4-8: stored ownership only ----------------------------------------------------------------

    @Test
    void bindingNeedsStoredOwnershipNotAKeycardOrIntegrationGrant() {
        factionMembers.add(actor);
        assertTrue(store.bindColony(installation.id(), COLONY, AccessLevel.MEMBER));
        for (Identifier sourceId : List.of(KeycardSource.ID, FactionSource.ID, ColonySource.ID)) {
            BindingService carded = serviceWith(new AccessResolver(
                    () -> List.of(MembershipSource.INSTANCE, grantsOwner(sourceId, actor)), AdminCheck.NONE, () -> true));
            Outcome bind = carded.bindFaction(null, store, actor, installation.id(), FACTION, AccessLevel.MEMBER);
            assertEquals(Status.DENIED, bind.status(), "an OWNER grant from " + sourceId + " must not bind");
            assertEquals(DenyReason.INSUFFICIENT_LEVEL, bind.deny());
            assertEquals(Status.DENIED, carded.unbindColony(null, store, actor, installation.id()).status(),
                    "nor unbind");
            // The stored owner still binds through the same resolver.
            factionMembers.add(owner);
            assertEquals(Status.BOUND,
                    carded.bindFaction(null, store, owner, installation.id(), FACTION, AccessLevel.MEMBER).status());
            assertTrue(store.unbindFaction(installation.id()));
        }
        assertNull(fresh().boundFaction());
        assertEquals(COLONY, fresh().boundColony(), "the refused unbinds changed nothing");

        // The operator override still applies, with no membership row at all.
        BindingService admin = serviceWith(new AccessResolver(() -> List.of(MembershipSource.INSTANCE),
                (server, who) -> actor.equals(who), () -> true));
        assertEquals(Status.BOUND,
                admin.bindFaction(null, store, actor, installation.id(), FACTION, AccessLevel.MEMBER).status());
        assertEquals(Status.UNBOUND, admin.unbindColony(null, store, actor, installation.id()).status());
    }

    // --- S4-11: MEMBER unless OFFICER is asked for --------------------------------------------------

    @Test
    void defaultBindingMaxLevelIsMember() {
        assertEquals(AccessLevel.MEMBER, BindingService.DEFAULT_MAX_LEVEL);
        factionMembers.add(owner);
        factionMembers.add(actor); // TRUSTED in the fake bridge: OFFICER-worthy, but capped by the binding
        assertEquals(Status.BOUND, bindFaction(owner, BindingService.DEFAULT_MAX_LEVEL).status());
        assertEquals(AccessLevel.MEMBER, fresh().factionMaxLevel());
        FactionSource source = new FactionSource(() -> factions, () -> true, () -> 1L);
        AccessVote vote = source.vote(CompatFixtures.query(actor, fresh(), AccessAction.OPEN));
        assertTrue(vote instanceof AccessVote.Grant grant && grant.level() == AccessLevel.MEMBER,
                "a default binding grants at most MEMBER: no roster, no audit log");
        // OFFICER only when asked for.
        assertEquals(Status.BOUND, bindFaction(owner, AccessLevel.OFFICER).status());
        assertEquals(AccessLevel.OFFICER, fresh().factionMaxLevel());
    }

    @AfterEach
    void tearDown() {
        subscription.close();
    }

    private Installation fresh() {
        return store.byId(installation.id()).orElseThrow();
    }

    private List<SecurityEventType> types() {
        return events.stream().map(SecurityEvent::type).toList();
    }

    private Outcome bindFaction(UUID who, AccessLevel max) {
        return service.bindFaction(null, store, who, installation.id(), FACTION, max);
    }

    // --- 17 ---------------------------------------------------------------------------------------

    @Test
    void bindingPreconditions() {
        factionMembers.add(officer);

        // An OFFICER (even one who is a member of the faction) is refused by the resolver: OWNER only.
        Outcome byOfficer = bindFaction(officer, AccessLevel.MEMBER);
        assertEquals(Status.DENIED, byOfficer.status());
        assertEquals(DenyReason.INSUFFICIENT_LEVEL, byOfficer.deny());
        assertFalse(byOfficer.ok());
        assertNull(fresh().boundFaction());

        // A stranger to the installation likewise.
        assertEquals(Status.DENIED, bindFaction(actor, AccessLevel.MEMBER).status());

        // The owner, but not a member of the faction.
        Outcome notMember = bindFaction(owner, AccessLevel.MEMBER);
        assertEquals(Status.NOT_MEMBER, notMember.status());
        assertNull(fresh().boundFaction());

        // A max level of OWNER (or PUBLIC) is refused: an integration grants MEMBER or OFFICER at most.
        factionMembers.add(owner);
        assertEquals(Status.MAX_LEVEL, bindFaction(owner, AccessLevel.OWNER).status());
        assertEquals(Status.MAX_LEVEL, bindFaction(owner, AccessLevel.PUBLIC).status());
        assertNull(fresh().boundFaction());
        assertTrue(events.isEmpty(), "no refused bind publishes anything");
        assertEquals(0, invalidations.get());

        // The owner who is a member: bound, audited, fields invalidated.
        Outcome bound = bindFaction(owner, AccessLevel.MEMBER);
        assertEquals(Status.BOUND, bound.status());
        assertTrue(bound.ok());
        assertEquals(FACTION, fresh().boundFaction());
        assertEquals(AccessLevel.MEMBER, fresh().factionMaxLevel());
        assertEquals(List.of(SecurityEventType.FACTION_BOUND), types());
        assertEquals(owner, events.get(0).actor());
        assertEquals(42L, events.get(0).at());
        assertEquals(1, invalidations.get());

        // Absent sibling: refused, and the existing binding is preserved untouched.
        factionsPresent.set(false);
        assertEquals(Status.ABSENT, bindFaction(owner, AccessLevel.OFFICER).status());
        assertEquals(FACTION, fresh().boundFaction());
        assertEquals(AccessLevel.MEMBER, fresh().factionMaxLevel());

        // The integration switched off reads exactly like absent.
        factionsPresent.set(true);
        factionsEnabled.set(false);
        assertEquals(Status.ABSENT, bindFaction(owner, AccessLevel.OFFICER).status());
        assertEquals(AccessLevel.MEMBER, fresh().factionMaxLevel());
        factionsEnabled.set(true);

        // A failing bridge reads as "not a member" (fail closed), never as a bind.
        factionsThrow.set(new IllegalStateException("boom"));
        assertEquals(Status.NOT_MEMBER, bindFaction(owner, AccessLevel.OFFICER).status());
        assertEquals(AccessLevel.MEMBER, fresh().factionMaxLevel());
        assertEquals(1, events.size(), "only the one successful bind was published");
    }

    @Test
    void colonyAndStationPreconditions() {
        UUID colonyId = COLONY;
        // Strangers and enemies are not members.
        assertEquals(Status.NOT_MEMBER,
                service.bindColony(null, store, owner, installation.id(), colonyId, AccessLevel.OFFICER).status());
        colonyRoles.put(Role.ENEMY, Set.of(owner));
        assertEquals(Status.NOT_MEMBER,
                service.bindColony(null, store, owner, installation.id(), colonyId, AccessLevel.OFFICER).status());
        assertNull(fresh().boundColony());
        // Officers cannot bind even as colony owners.
        colonyRoles.clear();
        colonyRoles.put(Role.OWNER, Set.of(officer));
        assertEquals(Status.DENIED,
                service.bindColony(null, store, officer, installation.id(), colonyId, AccessLevel.OFFICER).status());
        assertEquals(Status.MAX_LEVEL,
                service.bindColony(null, store, owner, installation.id(), colonyId, AccessLevel.OWNER).status());
        // An ally is a member.
        colonyRoles.put(Role.ALLY, Set.of(owner));
        Outcome colony = service.bindColony(null, store, owner, installation.id(), colonyId, AccessLevel.OFFICER);
        assertEquals(Status.BOUND, colony.status());
        assertEquals(colonyId, fresh().boundColony());
        assertEquals(AccessLevel.OFFICER, fresh().colonyMaxLevel());

        // Stations: the binder must own it right now.
        assertEquals(Status.NOT_STATION_OWNER,
                service.bindStation(null, store, owner, installation.id(), STATION).status());
        stationOwners.add(owner);
        assertEquals(Status.NOT_STATION_OWNER,
                service.bindStation(null, store, owner, installation.id(), STATION + 1).status());
        stationOwners.add(officer);
        assertEquals(Status.DENIED, service.bindStation(null, store, officer, installation.id(), STATION).status());
        assertEquals(Status.BOUND, service.bindStation(null, store, owner, installation.id(), STATION).status());
        assertEquals(Integer.valueOf(STATION), fresh().boundStation());

        nerospacePresent.set(false);
        assertEquals(Status.ABSENT, service.bindStation(null, store, owner, installation.id(), STATION).status());
        assertEquals(Integer.valueOf(STATION), fresh().boundStation(), "an absent sibling leaves the binding as it was");

        assertEquals(List.of(SecurityEventType.COLONY_BOUND, SecurityEventType.STATION_BOUND), types());
        assertEquals(2, invalidations.get());
    }

    @Test
    void ownerMayUnbindWhileTheIntegrationIsAbsentOrDisabled() {
        assertTrue(store.bindFaction(installation.id(), FACTION, AccessLevel.OFFICER));
        assertTrue(store.bindColony(installation.id(), COLONY, AccessLevel.OFFICER));
        assertTrue(store.bindStation(installation.id(), STATION));
        // Factions switched off; colonies and Nerospace not installed.
        factionsEnabled.set(false);
        coloniesPresent.set(false);
        nerospacePresent.set(false);

        // Still OWNER only: an officer is refused by the resolver and nothing changes.
        assertEquals(Status.DENIED, service.unbindFaction(null, store, officer, installation.id()).status());
        assertEquals(Status.DENIED, service.unbindColony(null, store, officer, installation.id()).status());
        assertEquals(Status.DENIED, service.unbindStation(null, store, officer, installation.id()).status());
        assertEquals(FACTION, fresh().boundFaction());
        assertTrue(events.isEmpty());
        assertEquals(0, invalidations.get());

        // Binding is still refused while inactive...
        assertEquals(Status.ABSENT, bindFaction(owner, AccessLevel.MEMBER).status());
        // ...but the owner can always remove a grant: audited, and every field's cache invalidated.
        assertEquals(Status.UNBOUND, service.unbindFaction(null, store, owner, installation.id()).status());
        assertEquals(Status.UNBOUND, service.unbindColony(null, store, owner, installation.id()).status());
        assertEquals(Status.UNBOUND, service.unbindStation(null, store, owner, installation.id()).status());
        assertNull(fresh().boundFaction());
        assertNull(fresh().boundColony());
        assertNull(fresh().boundStation());
        assertEquals(List.of(SecurityEventType.FACTION_UNBOUND, SecurityEventType.COLONY_UNBOUND,
                SecurityEventType.STATION_UNBOUND), types());
        assertEquals(owner, events.get(0).actor());
        assertEquals(3, invalidations.get());

        // Nothing left: refused, nothing more published.
        assertEquals(Status.NOT_BOUND, service.unbindColony(null, store, owner, installation.id()).status());
        assertEquals(3, events.size());
    }

    @Test
    void bindingStoresOrganisationIdsOnly() {
        factionMembers.add(owner);
        assertTrue(bindFaction(owner, AccessLevel.OFFICER).ok());
        Installation i = fresh();
        assertEquals(FACTION, i.boundFaction());
        // The binder is not recorded anywhere on the installation beyond the owner row it already had.
        assertEquals(Set.of(owner), Set.copyOf(i.owners()));
        assertFalse(i.members().containsKey(owner));
    }

    @Test
    void unknownInstallationIsGone() {
        assertEquals(Status.GONE,
                service.bindFaction(null, store, owner, UUID.randomUUID(), FACTION, AccessLevel.OFFICER).status());
        assertEquals(Status.GONE, service.unbindStation(null, store, owner, UUID.randomUUID()).status());
    }

    // --- 18 ---------------------------------------------------------------------------------------

    @Test
    void unbindAndSuspendEvents() {
        assertTrue(store.bindFaction(installation.id(), FACTION, AccessLevel.OFFICER));
        assertTrue(store.bindColony(installation.id(), COLONY, AccessLevel.OFFICER));
        assertTrue(store.bindStation(installation.id(), STATION));

        // Unbinding is OWNER only.
        assertEquals(Status.DENIED, service.unbindFaction(null, store, officer, installation.id()).status());
        assertEquals(FACTION, fresh().boundFaction());

        assertEquals(Status.UNBOUND, service.unbindFaction(null, store, owner, installation.id()).status());
        assertEquals(Status.UNBOUND, service.unbindColony(null, store, owner, installation.id()).status());
        assertEquals(Status.UNBOUND, service.unbindStation(null, store, owner, installation.id()).status());
        assertNull(fresh().boundFaction());
        assertNull(fresh().boundColony());
        assertNull(fresh().boundStation());
        assertEquals(List.of(SecurityEventType.FACTION_UNBOUND, SecurityEventType.COLONY_UNBOUND,
                SecurityEventType.STATION_UNBOUND), types());
        assertEquals(owner, events.get(0).actor());
        assertEquals(3, invalidations.get());

        // Nothing left to unbind: refused, nothing published.
        assertEquals(Status.NOT_BOUND, service.unbindFaction(null, store, owner, installation.id()).status());
        assertEquals(3, events.size());
        events.clear();

        // Suspension: bound, but no owner is a member any more, while the actor still is.
        assertTrue(store.bindFaction(installation.id(), FACTION, AccessLevel.OFFICER));
        assertTrue(store.bindColony(installation.id(), COLONY, AccessLevel.OFFICER));
        factionMembers.add(actor);
        colonyRoles.put(Role.CHIEF, Set.of(actor));
        FactionSource factionSource = new FactionSource(() -> factions, () -> true, () -> 1L);
        ColonySource colonySource = new ColonySource(() -> colonies, () -> true, () -> 1L);
        Installation i = fresh();
        for (int n = 0; n < 100; n++) {
            assertTrue(CompatFixtures.isAbstain(factionSource.vote(CompatFixtures.query(actor, i, AccessAction.OPEN))));
            assertTrue(CompatFixtures.isAbstain(colonySource.vote(CompatFixtures.query(actor, i, AccessAction.OPEN))));
        }
        assertEquals(List.of(SecurityEventType.FACTION_SUSPENDED, SecurityEventType.COLONY_SUSPENDED), types(),
                "one suspension notice per binding per session, however many votes");
        assertNull(events.get(0).actor(), "a suspension notice names nobody");
        assertNull(events.get(1).actor());

        // A new session may report it again.
        factionSource.clearSession();
        factionSource.vote(CompatFixtures.query(actor, i, AccessAction.OPEN));
        assertEquals(3, events.size());
        assertEquals(SecurityEventType.FACTION_SUSPENDED, events.get(2).type());
    }
}
