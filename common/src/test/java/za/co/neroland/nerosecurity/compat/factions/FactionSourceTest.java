package za.co.neroland.nerosecurity.compat.factions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.create;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.isAbstain;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.query;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.storeWithOwners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The faction source: tier-mapped grants capped at OFFICER, suspension, never a deny. Plain JVM, server null. */
class FactionSourceTest {

    private static final Identifier FACTION = Identifier.fromNamespaceAndPath("nerofactions", "guild");

    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerC = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private SecurityInstallations store;
    private Installation installation;

    /** A fake: an explicit member set and tier table, plus a call counter. */
    private static class FakeFactions implements FactionsBridge {
        final Set<UUID> members = new HashSet<>();
        final Map<UUID, Tier> tiers = new HashMap<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean isMember(MinecraftServer server, UUID player, Identifier faction) {
            calls.incrementAndGet();
            return FACTION.equals(faction) && members.contains(player);
        }

        @Override
        public Set<Identifier> membershipsOf(MinecraftServer server, UUID player) {
            calls.incrementAndGet();
            return members.contains(player) ? Set.of(FACTION) : Set.of();
        }

        @Override
        public Tier tierOf(MinecraftServer server, UUID player, Identifier faction) {
            calls.incrementAndGet();
            return tiers.get(player);
        }
    }

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = create(store, ownerA, 0);
    }

    private Installation bound(AccessLevel max) {
        assertTrue(store.bindFaction(installation.id(), FACTION, max));
        return store.byId(installation.id()).orElseThrow();
    }

    private static AccessVote vote(FactionsBridge bridge, Installation i, UUID who) {
        return new FactionSource(() -> bridge, () -> true).vote(query(who, i, AccessAction.OPEN));
    }

    @Test
    void tierMappingCappedAndNeverOwner() {
        for (AccessLevel max : new AccessLevel[] {AccessLevel.MEMBER, AccessLevel.OFFICER}) {
            assertNull(FactionTierMapping.level(Tier.OUTSIDER, max));
            assertNull(FactionTierMapping.level(null, max));
            assertEquals(AccessLevel.PUBLIC, FactionTierMapping.level(Tier.ASSOCIATE, max));
            assertEquals(AccessLevel.MEMBER, FactionTierMapping.level(Tier.MEMBER, max));
            assertEquals(max, FactionTierMapping.level(Tier.TRUSTED, max));
            assertEquals(max, FactionTierMapping.level(Tier.INNER_CIRCLE, max));
        }
        // Even a max of OWNER (unreachable through the store) is capped at OFFICER.
        assertEquals(AccessLevel.OFFICER, FactionTierMapping.level(Tier.INNER_CIRCLE, AccessLevel.OWNER));
        assertFalse(store.bindFaction(installation.id(), FACTION, AccessLevel.OWNER), "the store refuses an OWNER max");
        assertFalse(store.bindFaction(installation.id(), FACTION, AccessLevel.PUBLIC));
        assertNull(store.byId(installation.id()).orElseThrow().boundFaction(), "a refused bind writes nothing");

        FakeFactions fake = new FakeFactions();
        fake.members.add(ownerA);
        fake.members.add(actor);
        Installation i = bound(AccessLevel.MEMBER);
        for (Tier tier : Tier.values()) {
            fake.tiers.put(actor, tier);
            AccessVote v = vote(fake, i, actor);
            AccessLevel expected = FactionTierMapping.level(tier, AccessLevel.MEMBER);
            if (expected == null) {
                assertTrue(isAbstain(v), tier.name());
            } else {
                assertEquals(new AccessVote.Grant(expected), v, tier.name());
                assertFalse(expected.atLeast(AccessLevel.OWNER), tier.name());
            }
        }
    }

    @Test
    void absentOrDisabledFactionsAbstain() {
        FakeFactions fake = new FakeFactions();
        fake.members.add(ownerA);
        fake.members.add(actor);
        fake.tiers.put(actor, Tier.TRUSTED);
        Installation i = bound(AccessLevel.OFFICER);
        assertTrue(isAbstain(new FactionSource(() -> null, () -> true).vote(query(actor, i, AccessAction.OPEN))),
                "NeroFactions absent: abstain");
        assertTrue(isAbstain(new FactionSource(() -> fake, () -> false).vote(query(actor, i, AccessAction.OPEN))),
                "integration switched off: abstain");
        assertEquals(FACTION, store.byId(i.id()).orElseThrow().boundFaction(), "the binding is kept, inert");

        SecurityInstallations other = new SecurityInstallations();
        Installation unbound = create(other, ownerA, 5);
        fake.calls.set(0);
        assertTrue(isAbstain(vote(fake, unbound, actor)), "no binding: abstain");
        assertEquals(0, fake.calls.get(), "no binding: the bridge is never called");
    }

    @Test
    void suspendedWhenNoOwnerIsMember() {
        FakeFactions fake = new FakeFactions();
        fake.members.add(actor);
        fake.tiers.put(actor, Tier.TRUSTED);
        Installation i = bound(AccessLevel.OFFICER);
        FactionSource source = new FactionSource(() -> fake, () -> true);
        assertTrue(isAbstain(source.vote(query(actor, i, AccessAction.OPEN))), "owner A left the faction: suspended");
        assertTrue(source.isSuspended(null, i));

        SecurityInstallations shared = storeWithOwners(UUID.randomUUID(), ownerA, ownerC);
        Installation twoOwners = shared.all().get(0);
        assertTrue(shared.bindFaction(twoOwners.id(), FACTION, AccessLevel.OFFICER));
        twoOwners = shared.byId(twoOwners.id()).orElseThrow();
        assertTrue(source.isSuspended(null, twoOwners), "neither owner is a member yet");
        fake.members.add(ownerC);
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), source.vote(query(actor, twoOwners, AccessAction.OPEN)));
        assertFalse(source.isSuspended(null, twoOwners));
    }

    @Test
    void factionsOnlyGrant() {
        Installation i = bound(AccessLevel.OFFICER);
        for (boolean ownerMember : new boolean[] {false, true}) {
            for (boolean actorMember : new boolean[] {false, true}) {
                for (Tier tier : new Tier[] {null, Tier.OUTSIDER, Tier.ASSOCIATE, Tier.MEMBER, Tier.TRUSTED,
                        Tier.INNER_CIRCLE}) {
                    FakeFactions fake = new FakeFactions();
                    if (ownerMember) {
                        fake.members.add(ownerA);
                    }
                    if (actorMember) {
                        fake.members.add(actor);
                    }
                    if (tier != null) {
                        fake.tiers.put(actor, tier);
                    }
                    for (UUID who : new UUID[] {actor, ownerA, Installation.TOMBSTONE_OWNER}) {
                        AccessVote v = vote(fake, i, who);
                        assertFalse(v instanceof AccessVote.Deny, "factions never deny");
                        if (v instanceof AccessVote.Grant g) {
                            assertFalse(g.level().atLeast(AccessLevel.OWNER), "factions never grant OWNER");
                        }
                    }
                }
            }
        }
        FactionsBridge broken = new FakeFactions() {
            @Override
            public boolean isMember(MinecraftServer server, UUID player, Identifier faction) {
                throw new IllegalStateException("boom");
            }
        };
        assertTrue(isAbstain(vote(broken, i, actor)), "a failing bridge abstains, never denies");
    }
}
