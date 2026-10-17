package za.co.neroland.nerosecurity.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.create;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.issue;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.withPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.keycard.KeycardData;
import za.co.neroland.nerosecurity.keycard.KeycardPayload;

/** The keycard source with injected hands and clock. Plain JVM, server null. */
class KeycardSourceTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong(10_000L);
    private final List<SecurityEvent> events = new ArrayList<>();

    private SecurityInstallations store;
    private Installation a;
    private Installation b;
    private List<KeycardData> hands = List.of();
    private SecurityEventBus.Subscription subscription;

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        a = create(store, owner, "A", 0);
        b = create(store, UUID.randomUUID(), "B", 100);
        subscription = SecurityEventBus.subscribe(events::add);
    }

    @AfterEach
    void tearDown() {
        subscription.close();
    }

    private KeycardSource source() {
        return new KeycardSource((server, who) -> who.equals(actor) ? hands : List.of(), clock::get);
    }

    private AccessQuery query(UUID who, Installation installation) {
        return new AccessQuery(null, who, installation, AccessAction.OPEN, null);
    }

    private AccessQuery query(UUID who, Installation installation, AccessAction action) {
        return new AccessQuery(null, who, installation, action, null);
    }

    private AccessResolver membershipAndCards() {
        return new AccessResolver(() -> List.of(MembershipSource.INSTANCE, source()), AdminCheck.NONE, () -> true);
    }

    private static final java.util.Set<AccessAction> PASSAGE =
            java.util.EnumSet.of(AccessAction.OPEN, AccessAction.SENSOR_PASS, AccessAction.PASS_FIELD);

    @Test
    void onlyPhysicalPassageTakesKeycardGrants() {
        for (AccessAction action : AccessAction.values()) {
            assertEquals(PASSAGE.contains(action), action.keycardMayGrant, action.name());
        }
        assertEquals(AccessLevel.OFFICER, AccessAction.OPEN_HUB.defaultMinimum, "the hub screen needs OFFICER");
        assertEquals(AccessAction.values().length - 2, AccessAction.OPEN_HUB.ordinal(),
                "new actions are appended, never inserted");

        // A non-member holding the highest card: for every non-passage action the card counts for nothing.
        hands = List.of(issue(store, a, AccessLevel.OWNER, true, null));
        AccessResolver resolver = membershipAndCards();
        for (AccessAction action : AccessAction.values()) {
            AccessDecision decision = resolver.resolve(query(actor, a, action), action.defaultMinimum);
            if (PASSAGE.contains(action)) {
                assertTrue(decision.allowed(), action + ": a card grants passage");
            } else {
                assertFalse(decision.allowed(), action + ": a card never administers");
                assertEquals(AccessLevel.PUBLIC, decision.effective(), action + ": the card is an abstention");
            }
        }
    }

    @Test
    void anOfficerCardInTheOffHandCannotAdminister() {
        // The visitor's main hand holds a tool (no card); the OFFICER card is in the off hand.
        hands = List.of(issue(store, a, AccessLevel.OFFICER, false, null));
        AccessResolver resolver = membershipAndCards();
        for (AccessAction action : List.of(AccessAction.LINK_DEVICE, AccessAction.OPEN_HUB,
                AccessAction.LOCKDOWN_TOGGLE, AccessAction.READ_LOG, AccessAction.SILENCE_ALARM,
                AccessAction.EDIT_MEMBERS, AccessAction.CONFIGURE_DEVICE, AccessAction.PROGRAM_CARD,
                AccessAction.VIEW_INSTALLATION)) {
            AccessDecision decision = resolver.resolve(query(actor, a, action), action.defaultMinimum);
            assertFalse(decision.allowed(), "an OFFICER card alone cannot " + action);
        }
        assertTrue(resolver.resolve(query(actor, a, AccessAction.OPEN), AccessLevel.OFFICER).allowed(),
                "... but it still opens an OFFICER door");
    }

    @Test
    void anOwnerCardOpensButDoesNotAdminister() {
        hands = List.of(issue(store, a, AccessLevel.OWNER, true, null));
        AccessResolver resolver = membershipAndCards();
        for (AccessAction action : List.of(AccessAction.DISSOLVE, AccessAction.REVOKE_CARD,
                AccessAction.PROGRAM_CARD, AccessAction.RENAME, AccessAction.SET_AUTOMATION,
                AccessAction.CONFIGURE_ALARM_LOCKDOWN, AccessAction.SET_NOTIFY_OFFICERS,
                AccessAction.BIND_INTEGRATION)) {
            AccessDecision decision = resolver.resolve(query(actor, a, action), action.defaultMinimum);
            assertFalse(decision.allowed(), "an OWNER card alone cannot " + action);
        }
        // Cloning resolves PROGRAM_CARD at MEMBER: a card holder who is not a member cannot clone either.
        assertFalse(resolver.resolve(query(actor, a, AccessAction.PROGRAM_CARD), AccessLevel.MEMBER).allowed());
        assertTrue(resolver.resolve(query(actor, a, AccessAction.OPEN), AccessLevel.OWNER).allowed(),
                "it opens an OWNER door");
        assertTrue(resolver.resolve(query(actor, a, AccessAction.OPEN), AccessLevel.MEMBER,
                java.util.Set.of(KeycardSource.ID)).allowed(), "and a keycard-only door");

        store.setLockdown(a.id(), new Lockdown(true, 1L, true));
        Installation locked = store.byId(a.id()).orElseThrow();
        assertTrue(resolver.resolve(query(actor, locked, AccessAction.OPEN), AccessLevel.MEMBER).allowed(),
                "an OWNER card still opens a door during lockdown");
        assertTrue(resolver.resolve(query(actor, locked, AccessAction.OPEN), AccessLevel.MEMBER,
                java.util.Set.of(KeycardSource.ID)).allowed(), "... a keycard-only one too");
        assertFalse(resolver.resolve(query(actor, locked, AccessAction.LOCKDOWN_TOGGLE), AccessLevel.OFFICER).allowed(),
                "but cannot end the lockdown");
    }

    @Test
    void membershipAndIntegrationGrantsStillAdminister() {
        UUID officer = UUID.randomUUID();
        store.setMember(a.id(), officer, AccessLevel.OFFICER, 64);
        Installation withOfficer = store.byId(a.id()).orElseThrow();
        AccessResolver resolver = membershipAndCards();
        assertTrue(resolver.resolve(query(officer, withOfficer, AccessAction.LINK_DEVICE),
                AccessAction.LINK_DEVICE.defaultMinimum).allowed(), "a stored officer links without a card");
        assertTrue(resolver.resolve(query(owner, withOfficer, AccessAction.DISSOLVE),
                AccessAction.DISSOLVE.defaultMinimum).allowed(), "a stored owner dissolves");

        AccessSource faction = new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("nerosecurity", "faction");
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                return new AccessVote.Grant(AccessLevel.OFFICER);
            }
        };
        AccessResolver bound = new AccessResolver(() -> List.of(MembershipSource.INSTANCE, source(), faction),
                AdminCheck.NONE, () -> true);
        assertTrue(bound.resolve(query(actor, a, AccessAction.LINK_DEVICE), AccessLevel.OFFICER).allowed(),
                "an owner-chosen integration binding still grants non-passage actions");
    }

    private static AccessSource deny() {
        return new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("test", "deny");
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                return new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.source");
            }
        };
    }

    private long rejectedEvents() {
        return events.stream().filter(e -> e.type() == SecurityEventType.KEYCARD_REJECTED).count();
    }

    @Test
    void grantsHighestValidCardAndNeverDenies() {
        KeycardData member = issue(store, a, AccessLevel.MEMBER, false, null);
        KeycardData realOwnerCard = issue(store, a, AccessLevel.MEMBER, false, null);
        KeycardPayload p = realOwnerCard.payload();
        KeycardData tamperedOwner = withPayload(realOwnerCard,
                new KeycardPayload(p.installationId(), AccessLevel.OWNER, p.serial(), p.encrypted(), p.bound(), p.issuedAt()));
        hands = List.of(member, tamperedOwner);
        KeycardSource source = source();

        assertEquals(new AccessVote.Grant(AccessLevel.MEMBER), source.vote(query(actor, a)));
        assertEquals(1, rejectedEvents(), "the tampered card raises one rejection");
        source.vote(query(actor, a));
        assertEquals(1, rejectedEvents(), "rejections are cooled down per installation and actor");
        clock.addAndGet(KeycardSource.REJECTED_COOLDOWN_MS);
        source.vote(query(actor, a));
        assertEquals(2, rejectedEvents(), "and raised again after the cooldown");
        SecurityEvent event = events.get(events.size() - 1);
        assertEquals(a.id(), event.installationId());

        hands = List.of(issue(store, b, AccessLevel.OWNER, true, null));
        assertEquals(AccessVote.Abstain.INSTANCE, source.vote(query(actor, a)), "a foreign card grants nothing");
        assertEquals(2, rejectedEvents(), "and raises nothing");

        hands = List.of(tamperedOwner);
        assertTrue(source.vote(query(actor, a)) instanceof AccessVote.Abstain, "a dead card abstains, never denies");
        assertEquals(AccessVote.Abstain.INSTANCE, source.vote(query(UUID.randomUUID(), a)),
                "another player's hands are never read");
        assertEquals(AccessVote.Abstain.INSTANCE, source.vote(query(actor, null)));
    }

    @Test
    void denyStillWinsWithValidOwnerCard() {
        hands = List.of(issue(store, a, AccessLevel.OWNER, true, null));
        AccessResolver resolver = new AccessResolver(() -> List.of(source(), deny()), AdminCheck.NONE, () -> true);
        AccessDecision decision = resolver.resolve(query(actor, a), AccessLevel.MEMBER);
        assertFalse(decision.allowed());
        assertEquals(DenyReason.SOURCE_DENY, decision.reason());
        assertEquals(AccessLevel.OWNER, decision.effective());
    }

    @Test
    void lockdownStillAppliesToOfficerCard() {
        store.setLockdown(a.id(), new Lockdown(true, 1L, true));
        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE, source()),
                AdminCheck.NONE, () -> true);

        hands = List.of(issue(store, a, AccessLevel.OFFICER, false, null));
        AccessDecision officer = resolver.resolve(query(actor, a), AccessLevel.MEMBER);
        assertFalse(officer.allowed());
        assertEquals(DenyReason.LOCKDOWN, officer.reason());

        hands = List.of(issue(store, a, AccessLevel.OWNER, true, null));
        AccessDecision ownerCard = resolver.resolve(query(actor, a), AccessLevel.MEMBER);
        assertTrue(ownerCard.allowed(), "an OWNER card opens during lockdown, like an owner");
        assertFalse(ownerCard.admin());
    }
}
