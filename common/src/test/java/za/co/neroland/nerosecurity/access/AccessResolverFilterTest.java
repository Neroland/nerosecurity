package za.co.neroland.nerosecurity.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.create;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.issue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.keycard.KeycardData;

/** The granting-source filter (keycard-only devices). Plain JVM. */
class AccessResolverFilterTest {

    private static final Set<Identifier> KEYCARD_ONLY = Set.of(KeycardSource.ID);

    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private SecurityInstallations store;
    private Installation installation;
    private List<KeycardData> hands = List.of();

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = create(store, owner, "A", 0);
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
    }

    private KeycardSource cards() {
        return new KeycardSource((server, who) -> hands, () -> 0L);
    }

    private static AccessSource fixed(String path, AccessVote vote) {
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

    private AccessQuery query(UUID actor, Installation target, AccessAction action) {
        return new AccessQuery(null, actor, target, action, null);
    }

    @Test
    void keycardOnlyIgnoresMembership() {
        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE, cards()),
                AdminCheck.NONE, () -> true);

        AccessDecision noCard = resolver.resolve(query(owner, installation, AccessAction.OPEN), AccessLevel.MEMBER,
                KEYCARD_ONLY);
        assertFalse(noCard.allowed(), "the owner without a card is refused at a keycard-only device");
        assertEquals(DenyReason.INSUFFICIENT_LEVEL, noCard.reason());
        assertEquals(AccessLevel.PUBLIC, noCard.effective());
        assertTrue(resolver.resolve(query(owner, installation, AccessAction.OPEN), AccessLevel.MEMBER, null).allowed(),
                "the same owner passes without the filter (settings screens are never filtered)");

        hands = List.of(issue(store, installation, AccessLevel.MEMBER, false, null));
        AccessDecision withCard = resolver.resolve(query(stranger, installation, AccessAction.OPEN),
                AccessLevel.MEMBER, KEYCARD_ONLY);
        assertTrue(withCard.allowed());
        assertEquals(AccessLevel.MEMBER, withCard.effective());

        AccessSource deny = fixed("deny", new AccessVote.Deny(DenyReason.SOURCE_DENY, "x"));
        AccessResolver withDeny = new AccessResolver(() -> List.of(MembershipSource.INSTANCE, cards(), deny),
                AdminCheck.NONE, () -> true);
        AccessDecision denied = withDeny.resolve(query(stranger, installation, AccessAction.OPEN), AccessLevel.MEMBER,
                KEYCARD_ONLY);
        assertFalse(denied.allowed(), "a deny from a source outside the filter still denies");
        assertEquals(DenyReason.SOURCE_DENY, denied.reason());

        AccessResolver admin = new AccessResolver(() -> List.of(MembershipSource.INSTANCE, cards()),
                (server, actor) -> true, () -> true);
        hands = List.of();
        assertTrue(admin.resolve(query(stranger, installation, AccessAction.OPEN), AccessLevel.MEMBER, KEYCARD_ONLY)
                .admin(), "the operator override is untouched by the filter");
    }

    @Test
    void nullFilterIsUnchanged() {
        AccessSource deny = fixed("deny", new AccessVote.Deny(DenyReason.SOURCE_DENY, "x"));
        AccessSource grantOwner = fixed("grant", new AccessVote.Grant(AccessLevel.OWNER));
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
        List<List<AccessSource>> sourceSets = List.of(
                List.of(MembershipSource.INSTANCE),
                List.of(MembershipSource.INSTANCE, deny),
                List.of(grantOwner, deny),
                List.of(MembershipSource.INSTANCE, broken),
                List.of(MembershipSource.INSTANCE, cards()));
        hands = List.of(issue(store, installation, AccessLevel.OFFICER, false, null));
        List<UUID> actors = List.of(owner, officer, member, stranger);
        int compared = 0;
        for (boolean locked : List.of(false, true)) {
            store.setLockdown(installation.id(), locked ? new Lockdown(true, 1L, true) : Lockdown.NONE);
            for (List<AccessSource> sources : sourceSets) {
                for (boolean admin : List.of(false, true)) {
                    for (boolean lockdownEnabled : List.of(true, false)) {
                        AccessResolver resolver = new AccessResolver(() -> sources, (s, a) -> admin, () -> lockdownEnabled);
                        for (UUID actor : actors) {
                            for (AccessAction action : AccessAction.values()) {
                                for (Installation target : new ArrayList<>(java.util.Arrays.asList(installation, null))) {
                                    AccessQuery q = query(actor, target, action);
                                    assertEquals(resolver.resolve(q, action.defaultMinimum),
                                            resolver.resolve(q, action.defaultMinimum, null));
                                    compared++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(compared > 1000);
    }
}
