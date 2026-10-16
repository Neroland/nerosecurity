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
