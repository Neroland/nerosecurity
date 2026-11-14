package za.co.neroland.nerosecurity.compat.factions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.create;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.isAbstain;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.query;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.resolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * Access decisions through the NeroFactions public-API shape: memberships as a set of faction ids and tiers as
 * lower-case tier ids ({@code FactionTierView.id()}), mapped by {@link FactionTierMapping#tierFromId}. The fake
 * mirrors the API contract (an unknown faction has no tier view; negative standing is "outsider") without
 * NeroFactions on the classpath. Plain JVM, server null.
 */
class FactionApiDecisionTest {

    private static final Identifier FACTION = Identifier.fromNamespaceAndPath("nerofactions", "guild");
    private static final Identifier GONE = Identifier.fromNamespaceAndPath("nerofactions", "disbanded");

    /** Fixed ids (not random) so nothing in a failure message varies between runs. */
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private SecurityInstallations store;
    private Installation installation;

    /**
     * The API's shape in NeroSecurity types: {@code membershipOf} is a set, {@code tierOf} is empty for an unknown
     * faction and otherwise the id of the highest tier whose threshold the standing reaches ("outsider" below it).
     */
    private static final class ApiShapedFactions implements FactionsBridge {
        final Set<Identifier> known = new HashSet<>();
        final Map<UUID, Set<Identifier>> memberships = new HashMap<>();
        final Map<UUID, Integer> standing = new HashMap<>();
        /** Overrides the ladder with a raw tier id, to feed ids this build does not know. */
        final Map<UUID, String> rawTierId = new HashMap<>();

        void join(UUID player, Identifier faction) {
            memberships.computeIfAbsent(player, p -> new HashSet<>()).add(faction);
        }

        Optional<String> tierViewId(UUID player, Identifier faction) {
            if (!known.contains(faction)) {
                return Optional.empty();
            }
            if (rawTierId.containsKey(player)) {
                return Optional.of(rawTierId.get(player));
            }
            int value = standing.getOrDefault(player, 0);
            String id = "outsider";
            if (value >= 10) {
                id = "associate";
            }
            if (value >= 50) {
                id = "member";
            }
            if (value >= 200) {
                id = "trusted";
            }
            if (value >= 500) {
                id = "inner_circle";
            }
            return Optional.of(id);
        }

        @Override
        public boolean isMember(MinecraftServer server, UUID player, Identifier faction) {
            return membershipsOf(server, player).contains(faction);
        }

        @Override
        public Set<Identifier> membershipsOf(MinecraftServer server, UUID player) {
            return Set.copyOf(memberships.getOrDefault(player, Set.of()));
        }

        @Override
        public Tier tierOf(MinecraftServer server, UUID player, Identifier faction) {
            return tierViewId(player, faction).flatMap(FactionTierMapping::tierFromId).orElse(null);
        }
    }

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = create(store, OWNER, 0);
    }

    private Installation bind(Identifier faction, AccessLevel max) {
        assertTrue(store.bindFaction(installation.id(), faction, max));
        return store.byId(installation.id()).orElseThrow();
    }

    private static AccessDecision decide(FactionsBridge bridge, Installation i, UUID who, AccessLevel required) {
        List<AccessSource> sources = List.of(MembershipSource.INSTANCE, new FactionSource(() -> bridge, () -> true));
        return resolver(sources).resolve(query(who, i, AccessAction.OPEN), required);
    }

    private static AccessDecision localOnly(Installation i, UUID who, AccessLevel required) {
        return resolver(List.of(MembershipSource.INSTANCE)).resolve(query(who, i, AccessAction.OPEN), required);
    }

    private ApiShapedFactions guild() {
        ApiShapedFactions fake = new ApiShapedFactions();
        fake.known.add(FACTION);
        fake.join(OWNER, FACTION);
        return fake;
    }

    @Test
    void tierIdsMapToTheLocalEnum() {
        assertEquals(Optional.of(Tier.OUTSIDER), FactionTierMapping.tierFromId("outsider"));
        assertEquals(Optional.of(Tier.ASSOCIATE), FactionTierMapping.tierFromId("associate"));
        assertEquals(Optional.of(Tier.MEMBER), FactionTierMapping.tierFromId("member"));
        assertEquals(Optional.of(Tier.TRUSTED), FactionTierMapping.tierFromId("trusted"));
        assertEquals(Optional.of(Tier.INNER_CIRCLE), FactionTierMapping.tierFromId("inner_circle"));
        // Every local tier round-trips through its lower-case id.
        for (Tier tier : Tier.values()) {
            assertEquals(Optional.of(tier), FactionTierMapping.tierFromId(tier.name().toLowerCase(Locale.ROOT)));
        }
        // Case-insensitive in Locale.ROOT (a Turkish default locale would otherwise break "INNER_CIRCLE").
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(Optional.of(Tier.INNER_CIRCLE), FactionTierMapping.tierFromId("INNER_CIRCLE"));
            assertEquals(Optional.of(Tier.TRUSTED), FactionTierMapping.tierFromId("Trusted"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void unknownOrMissingTierIdsAbstain() {
        for (String id : new String[] {null, "", "  ", "legend", "inner circle", "innercircle", "officer"}) {
            assertEquals(Optional.empty(), FactionTierMapping.tierFromId(id), String.valueOf(id));
        }
        ApiShapedFactions fake = guild();
        fake.join(MEMBER, FACTION);
        fake.rawTierId.put(MEMBER, "legend");
        Installation i = bind(FACTION, AccessLevel.OFFICER);
        assertNull(fake.tierOf(null, MEMBER, FACTION), "an unmapped tier id is no tier");
        assertTrue(isAbstain(new FactionSource(() -> fake, () -> true).vote(query(MEMBER, i, AccessAction.OPEN))));
        assertFalse(decide(fake, i, MEMBER, AccessLevel.MEMBER).allowed(), "an unmapped tier grants nothing");
        assertEquals(localOnly(i, MEMBER, AccessLevel.MEMBER), decide(fake, i, MEMBER, AccessLevel.MEMBER));
    }

    @Test
    void memberIsGrantedByTier() {
        ApiShapedFactions fake = guild();
        fake.join(MEMBER, FACTION);
        Installation i = bind(FACTION, AccessLevel.OFFICER);

        fake.standing.put(MEMBER, 10);
        assertEquals(new AccessVote.Grant(AccessLevel.PUBLIC),
                new FactionSource(() -> fake, () -> true).vote(query(MEMBER, i, AccessAction.OPEN)), "associate");
        assertFalse(decide(fake, i, MEMBER, AccessLevel.MEMBER).allowed(), "an associate is below MEMBER");

        fake.standing.put(MEMBER, 50);
        AccessDecision asMember = decide(fake, i, MEMBER, AccessLevel.MEMBER);
        assertTrue(asMember.allowed(), "member tier reaches MEMBER");
        assertEquals(AccessLevel.MEMBER, asMember.effective());
        assertFalse(decide(fake, i, MEMBER, AccessLevel.OFFICER).allowed(), "member tier is below OFFICER");

        for (int value : new int[] {200, 500}) {
            fake.standing.put(MEMBER, value);
            AccessDecision high = decide(fake, i, MEMBER, AccessLevel.OFFICER);
            assertTrue(high.allowed(), "trusted and inner circle reach OFFICER");
            assertEquals(AccessLevel.OFFICER, high.effective(), "never above OFFICER");
            assertFalse(decide(fake, i, MEMBER, AccessLevel.OWNER).allowed(), "a faction never yields OWNER");
        }

        // The binding's max still caps the grant.
        assertTrue(store.unbindFaction(installation.id()));
        Installation capped = bind(FACTION, AccessLevel.MEMBER);
        assertEquals(AccessLevel.MEMBER, decide(fake, capped, MEMBER, AccessLevel.MEMBER).effective());
    }

    @Test
    void outsiderIsDeniedIncludingNegativeStanding() {
        ApiShapedFactions fake = guild();
        Installation i = bind(FACTION, AccessLevel.OFFICER);

        // Not a member at all: the source abstains and the local rule decides.
        AccessDecision notMember = decide(fake, i, OUTSIDER, AccessLevel.MEMBER);
        assertFalse(notMember.allowed());
        assertEquals(localOnly(i, OUTSIDER, AccessLevel.MEMBER), notMember);

        // A member whose standing is below Associate, or negative, is the "outsider" tier: nothing granted.
        fake.join(OUTSIDER, FACTION);
        for (int value : new int[] {0, 9, -1, -5000, Integer.MIN_VALUE}) {
            fake.standing.put(OUTSIDER, value);
            assertEquals(Tier.OUTSIDER, fake.tierOf(null, OUTSIDER, FACTION), "standing " + value);
            assertTrue(isAbstain(new FactionSource(() -> fake, () -> true).vote(query(OUTSIDER, i, AccessAction.OPEN))),
                    "standing " + value);
            AccessDecision d = decide(fake, i, OUTSIDER, AccessLevel.MEMBER);
            assertFalse(d.allowed(), "standing " + value);
            assertEquals(localOnly(i, OUTSIDER, AccessLevel.MEMBER), d, "standing " + value);
        }
    }

    @Test
    void missingFactionAbstains() {
        // Bound to a faction NeroFactions no longer defines: no tier view, even for a lingering membership.
        ApiShapedFactions fake = guild();
        fake.join(OWNER, GONE);
        fake.join(MEMBER, GONE);
        fake.standing.put(MEMBER, 500);
        Installation i = bind(GONE, AccessLevel.OFFICER);
        assertNull(fake.tierOf(null, MEMBER, GONE));
        assertTrue(isAbstain(new FactionSource(() -> fake, () -> true).vote(query(MEMBER, i, AccessAction.OPEN))));
        assertEquals(localOnly(i, MEMBER, AccessLevel.MEMBER), decide(fake, i, MEMBER, AccessLevel.MEMBER));

        // And with no membership left either, the binding is suspended: still an abstention, never a deny.
        ApiShapedFactions empty = new ApiShapedFactions();
        FactionSource source = new FactionSource(() -> empty, () -> true);
        assertTrue(isAbstain(source.vote(query(MEMBER, i, AccessAction.OPEN))));
        assertTrue(source.isSuspended(null, i));
        assertTrue(decide(empty, i, OWNER, AccessLevel.OWNER).allowed(), "the owner keeps OWNER");
    }

    @Test
    void absentNeroFactionsLeavesTheLocalRuleUnchanged() {
        Installation i = bind(FACTION, AccessLevel.OFFICER);
        List<AccessSource> withAbsent = List.of(MembershipSource.INSTANCE, new FactionSource(() -> null, () -> true));
        for (UUID who : new UUID[] {OWNER, MEMBER, OUTSIDER}) {
            for (AccessLevel required : AccessLevel.values()) {
                assertEquals(localOnly(i, who, required),
                        resolver(withAbsent).resolve(query(who, i, AccessAction.OPEN), required),
                        who + " / " + required);
            }
        }
        assertTrue(resolver(withAbsent).resolve(query(OWNER, i, AccessAction.OPEN), AccessLevel.OWNER).allowed());
        assertFalse(resolver(withAbsent).resolve(query(MEMBER, i, AccessAction.OPEN), AccessLevel.MEMBER).allowed());
        assertEquals(FACTION, store.byId(i.id()).orElseThrow().boundFaction(), "the binding is kept, inert");
    }
}
