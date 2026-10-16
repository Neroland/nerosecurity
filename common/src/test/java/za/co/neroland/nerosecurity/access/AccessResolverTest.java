package za.co.neroland.nerosecurity.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The resolver in a plain JVM: hand-made sources, injected admin check and lockdown flag, no server.
 */
class AccessResolverTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private SecurityInstallations store;
    private Installation installation;

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = store.create(owner, "Test", new DevicePos(OVERWORLD, new BlockPos(0, 64, 0)), 0L, 8)
                .installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
    }

    private static AccessSource source(String path, AccessVote vote) {
        Identifier id = Identifier.fromNamespaceAndPath("test", path);
        return new AccessSource() {
            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                return vote;
            }
        };
    }

    private static AccessResolver resolver(List<AccessSource> sources, boolean admin, boolean lockdownEnabled) {
        return new AccessResolver(() -> sources, (server, actor) -> admin, () -> lockdownEnabled);
    }

    private AccessQuery query(UUID actor, Installation target, AccessAction action) {
        return new AccessQuery(null, actor, target, action, null);
    }

    private void lockdown() {
        store.setLockdown(installation.id(), new Lockdown(true, 1L, true));
    }

    @Test
    void denyWinsOverAnyGrant() {
        AccessSource deny = source("deny", new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.source"));
        AccessResolver resolver = resolver(List.of(source("grant", new AccessVote.Grant(AccessLevel.OWNER)), deny),
                false, true);
        AccessDecision decision = resolver.resolve(query(stranger, installation, AccessAction.OPEN), AccessLevel.MEMBER);
        assertFalse(decision.allowed());
        assertEquals(DenyReason.SOURCE_DENY, decision.reason());
        assertEquals(List.of(deny.id()), decision.denyingSources());
        assertEquals(AccessLevel.OWNER, decision.effective(), "the highest grant is still reported for the UI");
    }

    @Test
    void effectiveIsMaxAcrossSources() {
        AccessResolver resolver = resolver(List.of(
                source("a", new AccessVote.Grant(AccessLevel.MEMBER)),
                source("b", AccessVote.Abstain.INSTANCE),
                source("c", new AccessVote.Grant(AccessLevel.OFFICER))), false, true);
        AccessDecision decision = resolver.resolve(query(stranger, installation, AccessAction.EDIT_MEMBERS),
                AccessLevel.OFFICER);
        assertTrue(decision.allowed());
        assertEquals(AccessLevel.OFFICER, decision.effective());
    }

    @Test
    void adminOverridesEverything() {
        lockdown();
        AccessResolver resolver = resolver(List.of(
                source("deny", new AccessVote.Deny(DenyReason.SOURCE_DENY, "x"))), true, true);
        AccessDecision decision = resolver.resolve(query(stranger, installation, AccessAction.ADMIN_UNLOCK),
                AccessAction.ADMIN_UNLOCK.defaultMinimum);
        assertTrue(decision.allowed());
        assertTrue(decision.admin());
        assertEquals(AccessLevel.OWNER, decision.effective());
    }

    @Test
    void unknownInstallationIsPublic() {
        AtomicInteger asked = new AtomicInteger();
        AccessSource counting = new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("test", "counting");
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                asked.incrementAndGet();
                return new AccessVote.Grant(AccessLevel.OWNER);
            }
        };
        AccessResolver resolver = resolver(List.of(counting), false, true);
        AccessDecision open = resolver.resolve(query(owner, null, AccessAction.OPEN), AccessLevel.PUBLIC);
        assertTrue(open.allowed());
        assertEquals(AccessLevel.PUBLIC, open.effective());
        AccessDecision gated = resolver.resolve(query(owner, null, AccessAction.OPEN), AccessLevel.MEMBER);
        assertFalse(gated.allowed());
        assertEquals(DenyReason.UNKNOWN_INSTALLATION, gated.reason());
        assertEquals(0, asked.get(), "no source is asked about an unknown installation");
    }

    @Test
    void lockdownRaisesRequirementToOwner() {
        lockdown();
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE), false, true);
        AccessDecision decision = resolver.resolve(query(officer, installation, AccessAction.OPEN), AccessLevel.MEMBER);
        assertFalse(decision.allowed());
        assertEquals(DenyReason.LOCKDOWN, decision.reason());
        assertEquals(AccessLevel.OFFICER, decision.effective());
        assertEquals(AccessLevel.OWNER, decision.required());
    }

    @Test
    void ownerIsNeverAffectedByLockdown() {
        lockdown();
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE), false, true);
        for (AccessAction action : List.of(AccessAction.OPEN, AccessAction.EDIT_MEMBERS, AccessAction.LOCKDOWN_TOGGLE)) {
            AccessDecision decision = resolver.resolve(query(owner, installation, action), action.defaultMinimum);
            assertTrue(decision.allowed(), action + " must stay open to the owner during lockdown");
        }
        // The hub itself (opened at OFFICER) stays reachable for the owner during lockdown.
        assertTrue(resolver.resolve(query(owner, installation, AccessAction.OPEN), AccessLevel.OFFICER).allowed());
    }

    @Test
    void officerCannotStandDownLockdown() {
        lockdown();
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE), false, true);
        AccessDecision decision = resolver.resolve(query(officer, installation, AccessAction.LOCKDOWN_TOGGLE),
                AccessAction.LOCKDOWN_TOGGLE.defaultMinimum);
        assertFalse(decision.allowed(), "only an owner (or operator) can exit lockdown");
    }

    @Test
    void memberCannotEnterLockdown() {
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE), false, true);
        AccessDecision decision = resolver.resolve(query(member, installation, AccessAction.LOCKDOWN_TOGGLE),
                AccessAction.LOCKDOWN_TOGGLE.defaultMinimum);
        assertFalse(decision.allowed());
        assertEquals(DenyReason.INSUFFICIENT_LEVEL, decision.reason());
    }

    @Test
    void lockdownIgnoredWhenDisabled() {
        lockdown();
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE), false, false);
        AccessDecision decision = resolver.resolve(query(officer, installation, AccessAction.OPEN), AccessLevel.MEMBER);
        assertTrue(decision.allowed());
    }

    @Test
    void adminOnlyActionDeniedForOwner() {
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE), false, true);
        AccessDecision decision = resolver.resolve(query(owner, installation, AccessAction.ADMIN_UNLOCK),
                AccessAction.ADMIN_UNLOCK.defaultMinimum);
        assertFalse(decision.allowed());
        assertEquals(DenyReason.ADMIN_ONLY, decision.reason());
    }

    @Test
    void throwingSourceFailsClosed() {
        AccessSource broken = new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("test", "broken");
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                throw new IllegalStateException("deliberate test failure, safe to ignore");
            }
        };
        AccessResolver resolver = resolver(List.of(MembershipSource.INSTANCE, broken), false, true);
        AccessDecision decision = resolver.resolve(query(owner, installation, AccessAction.OPEN), AccessLevel.MEMBER);
        assertFalse(decision.allowed());
        assertEquals(DenyReason.SOURCE_DENY, decision.reason());
        assertEquals(List.of(broken.id()), decision.denyingSources());
    }

    @Test
    void messagesCarryNoIdentity() {
        List<AccessDecision> denials = new ArrayList<>();
        AccessResolver membership = resolver(List.of(MembershipSource.INSTANCE), false, true);
        denials.add(membership.resolve(query(member, installation, AccessAction.EDIT_MEMBERS), AccessLevel.OFFICER));
        denials.add(membership.resolve(query(owner, installation, AccessAction.ADMIN_UNLOCK), AccessLevel.OWNER));
        denials.add(membership.resolve(query(owner, null, AccessAction.OPEN), AccessLevel.MEMBER));
        denials.add(resolver(List.of(source("deny", new AccessVote.Deny(DenyReason.SOURCE_DENY, "x"))), false, true)
                .resolve(query(owner, installation, AccessAction.OPEN), AccessLevel.MEMBER));
        lockdown();
        denials.add(membership.resolve(query(officer, installation, AccessAction.OPEN), AccessLevel.MEMBER));

        List<DenyReason> seen = new ArrayList<>();
        for (AccessDecision decision : denials) {
            assertFalse(decision.allowed());
            seen.add(decision.reason());
            String text = decision.message().getString();
            for (UUID id : List.of(owner, officer, member, stranger, installation.id())) {
                assertFalse(text.contains(id.toString()), "a deny message must never carry an id");
            }
        }
        assertTrue(seen.containsAll(List.of(DenyReason.INSUFFICIENT_LEVEL, DenyReason.ADMIN_ONLY,
                DenyReason.UNKNOWN_INSTALLATION, DenyReason.SOURCE_DENY, DenyReason.LOCKDOWN)));
    }

    @Test
    void sourceRegistryRejectsDuplicatesAndKeepsMembershipFirst() {
        AccessSources.reset();
        try {
            AccessSources.init();
            AccessSources.init();
            assertEquals(2, AccessSources.all().size(), "init is idempotent");
            assertEquals(MembershipSource.ID, AccessSources.all().get(0).id());
            assertEquals(KeycardSource.ID, AccessSources.all().get(1).id(), "keycards come straight after membership");
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> AccessSources.register(MembershipSource.INSTANCE));
        } finally {
            AccessSources.reset();
        }
    }
}
