package za.co.neroland.nerosecurity.compat.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.create;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.isAbstain;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.query;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.resolver;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.compat.factions.FactionSource;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** Claims only ever deny, and a deny beats every grant. Plain JVM, server null. */
class ClaimSourceTest {

    private static final Identifier FACTION = Identifier.fromNamespaceAndPath("nerofactions", "guild");

    private final UUID owner = UUID.randomUUID();
    private SecurityInstallations store;
    private Installation installation;

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = create(store, owner, 0);
    }

    private static ClaimProvider provider(boolean answer) {
        return new ClaimProvider() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("test", "claims");
            }

            @Override
            public boolean mayInteract(MinecraftServer server, Identifier dimension, BlockPos pos, UUID actor) {
                return answer;
            }
        };
    }

    private static ClaimProvider throwing() {
        return new ClaimProvider() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("test", "broken");
            }

            @Override
            public boolean mayInteract(MinecraftServer server, Identifier dimension, BlockPos pos, UUID actor) {
                throw new IllegalStateException("boom");
            }
        };
    }

    private static ClaimSource claims(List<ClaimProvider> providers, boolean enabled) {
        return new ClaimSource(() -> providers, () -> enabled);
    }

    /** A faction bridge in which everyone is an INNER_CIRCLE member of {@link #FACTION}. */
    private static FactionsBridge everyoneInnerCircle() {
        return new FactionsBridge() {
            @Override
            public boolean isMember(MinecraftServer server, UUID player, Identifier faction) {
                return FACTION.equals(faction);
            }

            @Override
            public Set<Identifier> membershipsOf(MinecraftServer server, UUID player) {
                return Set.of(FACTION);
            }

            @Override
            public Tier tierOf(MinecraftServer server, UUID player, Identifier faction) {
                return Tier.INNER_CIRCLE;
            }
        };
    }

    @Test
    void claimDenyWinsOverOwnerAndEveryGrant() {
        assertTrue(store.bindFaction(installation.id(), FACTION, AccessLevel.OFFICER));
        Installation bound = store.byId(installation.id()).orElseThrow();
        FactionSource faction = new FactionSource(ClaimSourceTest::everyoneInnerCircle, () -> true);
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), faction.vote(query(owner, bound, AccessAction.OPEN)),
                "the faction source grants OFFICER on its own");

        AccessDecision decision = resolver(List.of(MembershipSource.INSTANCE, faction,
                claims(List.of(provider(false)), true)))
                .resolve(query(owner, bound, AccessAction.OPEN), AccessLevel.MEMBER);

        assertFalse(decision.allowed());
        assertEquals(DenyReason.SOURCE_DENY, decision.reason());
        assertEquals(List.of(ClaimSource.ID), decision.denyingSources());
        assertEquals(AccessLevel.OWNER, decision.effective(), "the owner grant is still reported for the UI");
    }

    @Test
    void claimNeverGrantsAndAbstainsOnWilderness() {
        AccessQuery q = query(owner, installation, AccessAction.OPEN);
        assertTrue(isAbstain(claims(List.of(provider(true)), true).vote(q)), "allowed land: abstain, never grant");
        assertTrue(isAbstain(claims(List.of(), true).vote(q)), "no provider: abstain");
        assertTrue(isAbstain(claims(List.of(provider(false)), true)
                .vote(new AccessQuery(null, owner, installation, AccessAction.OPEN, null))), "no position: abstain");
        assertInstanceOf(AccessVote.Deny.class, claims(List.of(provider(true), provider(false)), true).vote(q),
                "providers are AND-ed");

        AccessDecision broken = resolver(List.of(MembershipSource.INSTANCE, claims(List.of(throwing()), true)))
                .resolve(q, AccessLevel.MEMBER);
        assertFalse(broken.allowed(), "a throwing provider fails closed");
        assertEquals(DenyReason.SOURCE_DENY, broken.reason());
        assertEquals(List.of(ClaimSource.ID), broken.denyingSources());

        for (AccessAction action : AccessAction.values()) {
            AccessVote vote = claims(List.of(provider(true)), true).vote(query(owner, installation, action));
            assertFalse(vote instanceof AccessVote.Grant, "claims never grant: " + action);
        }
    }

    @Test
    void aClaimOverTheHubDoesNotStopLockdownExitByCommand() {
        store.setLockdown(installation.id(), new za.co.neroland.nerosecurity.installation.Lockdown(true, 1L, false));
        Installation locked = store.byId(installation.id()).orElseThrow();
        var resolver = resolver(List.of(MembershipSource.INSTANCE, claims(List.of(provider(false)), true)));
        // Hub-level actions resolve with no position (InstallationService.claimPosition), so the claim abstains.
        for (AccessAction action : List.of(AccessAction.LOCKDOWN_TOGGLE, AccessAction.EDIT_MEMBERS,
                AccessAction.RENAME, AccessAction.SET_NOTIFY_OFFICERS, AccessAction.REVOKE_CARD,
                AccessAction.SET_AUTOMATION, AccessAction.READ_LOG)) {
            AccessDecision decision = resolver.resolve(new AccessQuery(null, owner, locked, action, null),
                    action.defaultMinimum);
            assertTrue(decision.allowed(), "a hostile claim over the hub never blocks " + action);
        }
        // A block interaction at the hub (breaking it) still meets the claim.
        AccessDecision dissolve = resolver.resolve(new AccessQuery(null, owner, locked, AccessAction.DISSOLVE,
                locked.hubPos()), AccessAction.DISSOLVE.defaultMinimum);
        assertFalse(dissolve.allowed());
        assertEquals(DenyReason.SOURCE_DENY, dissolve.reason());
    }

    @Test
    void claimsDisabledAbstains() {
        assertTrue(isAbstain(claims(List.of(provider(false)), false).vote(query(owner, installation, AccessAction.OPEN))));
        AccessDecision decision = resolver(List.of(MembershipSource.INSTANCE, claims(List.of(provider(false)), false)))
                .resolve(query(owner, installation, AccessAction.OPEN), AccessLevel.MEMBER);
        assertTrue(decision.allowed());
    }

    @Test
    void mayBuildNeedsARealOwnerWithAccess() {
        UUID other = UUID.randomUUID();
        ClaimProvider onlyOther = new ClaimProvider() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("test", "only_other");
            }

            @Override
            public boolean mayInteract(MinecraftServer server, Identifier dimension, BlockPos pos, UUID actor) {
                return other.equals(actor);
            }
        };
        Identifier dim = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        assertTrue(ClaimsCompat.mayBuild(List.of(), null, dim, 0, 0, Set.of(owner)), "no provider: build anywhere");
        assertFalse(ClaimsCompat.mayBuild(List.of(onlyOther), null, dim, 0, 0, Set.of(owner)));
        assertTrue(ClaimsCompat.mayBuild(List.of(onlyOther), null, dim, 0, 0, Set.of(owner, other)));
        ClaimProvider all = provider(true);
        assertFalse(ClaimsCompat.mayBuild(List.of(all), null, dim, 0, 0, Set.of(Installation.TOMBSTONE_OWNER)),
                "the erasure tombstone never qualifies");
    }
}
