package za.co.neroland.nerosecurity.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** Reading the audit log: privileged, audited, opt-out aware, switchable off; the monitor sees no identity. */
class AuditServiceRulesTest {

    private static final DevicePos HUB = new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(0, 64, 0));
    private static final DevicePos DOOR = new DevicePos(HUB.dimension(), new BlockPos(3, 64, 0));
    private static final long NOW = 1_000_000_000L;

    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private Installation installation;
    private SecurityAuditLog audit;
    private final List<SecurityEvent> published = new ArrayList<>();
    private final AtomicBoolean auditEnabled = new AtomicBoolean(true);
    private ActorPolicy policy = ActorPolicy.NONE;
    private SecurityEventBus.Listener recorder;
    private AuditService service;

    @BeforeEach
    void setUp() {
        SecurityInstallations store = new SecurityInstallations();
        installation = store.create(owner, "Base", HUB, 0L, 8).installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
        audit = new SecurityAuditLog();
        recorder = AuditRecorder.listener(() -> audit, () -> null, auditEnabled::get, () -> 500, () -> policy);
        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE), AdminCheck.NONE, () -> true);
        service = new AuditService(resolver, server -> audit, auditEnabled::get, () -> NOW,
                i -> RetentionPolicy.of(7, 60, 500, 90, null), event -> {
                    published.add(event);
                    recorder.on(event);
                });
    }

    private void record(SecurityEventType type, UUID actor) {
        recorder.on(SecurityEvent.of(type, installation.id(), DOOR, actor, NOW - 1000L));
    }

    /** 6. */
    @Test
    void logReadRequiresOfficerAndIsAudited() {
        record(SecurityEventType.DOOR_DENIED, stranger);

        AuditService.ReadResult denied = service.readAs(null, member, installation, null, 20);
        assertFalse(denied.ok());
        assertEquals(AuditService.Status.DENIED, denied.status());
        assertTrue(denied.entries().isEmpty());
        assertTrue(published.isEmpty(), "a refused read publishes no LOG_READ");

        AuditService.ReadResult ok = service.readAs(null, officer, installation, null, 20);
        assertTrue(ok.ok());
        assertEquals(1, published.size());
        assertEquals(SecurityEventType.LOG_READ, published.get(0).type());
        assertEquals(officer, published.get(0).actor(), "the reader is the actor");
        AuditEntry first = ok.entries().get(0);
        assertEquals(SecurityEventType.LOG_READ, first.type(), "the read was recorded before the rows were returned");
        assertEquals(officer, first.actor());
        assertEquals(SecurityEventType.DOOR_DENIED, ok.entries().get(1).type());
    }

    /** 7. */
    @Test
    void optOutAnonymises() {
        policy = (server, actor) -> actor.equals(member);
        record(SecurityEventType.DOOR_OPENED, member);
        record(SecurityEventType.DOOR_OPENED, officer);

        List<AuditEntry> rows = audit.newestFirst(installation.id(), 10);
        AuditEntry bystander = rows.get(0);
        AuditEntry subject = rows.get(1);
        assertNull(subject.actor());
        assertTrue(subject.actorWithheld());
        assertEquals(ActorKind.WITHHELD, subject.actorKind());
        assertEquals(officer, bystander.actor());
        assertFalse(bystander.actorWithheld());
        assertFalse(audit.hasActorRows(member));

        policy = (server, actor) -> {
            throw new IllegalStateException("opt-out store unavailable");
        };
        record(SecurityEventType.DOOR_OPENED, officer);
        assertTrue(audit.newestFirst(installation.id(), 1).get(0).actorWithheld(), "a failing lookup withholds");
    }

    /** 8. */
    @Test
    void auditDisabledPersistsNothing() {
        auditEnabled.set(false);
        record(SecurityEventType.DOOR_FORCED, stranger);
        record(SecurityEventType.LASER_TRIPPED, null);
        assertEquals(0, audit.size(installation.id()));

        AuditService.ReadResult result = service.readAs(null, owner, installation, null, 20);
        assertEquals(AuditService.Status.DISABLED, result.status());
        assertTrue(published.isEmpty());
        assertTrue(service.peekAs(null, installation, 10).isEmpty());
    }

    @Test
    void purgeEventsAreNeverStored() {
        recorder.on(SecurityEvent.of(SecurityEventType.AUDIT_PURGED, installation.id(), null, null, NOW));
        assertEquals(0, audit.size(installation.id()));
    }

    @Test
    void readsPurgeLazilyAndPeekHasNoLogRead() {
        recorder.on(SecurityEvent.of(SecurityEventType.DOOR_OPENED, installation.id(), DOOR, member, NOW - 8 * 86_400_000L));
        recorder.on(SecurityEvent.of(SecurityEventType.MOTION_DETECTED, installation.id(), DOOR, stranger, NOW - 2 * 3_600_000L));
        record(SecurityEventType.CHEST_OPENED, member);

        List<AuditEntry> peeked = service.peekAs(null, installation, 10);
        assertEquals(List.of(SecurityEventType.CHEST_OPENED), peeked.stream().map(AuditEntry::type).toList());
        assertTrue(published.isEmpty(), "the monitor's read is not a LOG_READ");
    }

    /** 9. */
    @Test
    void monitorRowsNeverExposeIdentity() {
        policy = (server, actor) -> actor.equals(member);
        record(SecurityEventType.DOOR_OPENED, stranger);
        record(SecurityEventType.DOOR_OPENED, member);
        record(SecurityEventType.LASER_TRIPPED, null);

        List<AnonymousEvent> rows = AnonymousEvent.of(service.peekAs(null, installation, 10), NOW);
        assertEquals(List.of(ActorKind.NONE, ActorKind.PLAYER, ActorKind.PLAYER),
                rows.stream().map(AnonymousEvent::actorKind).toList(), "an opted-out actor reads as any player");
        assertEquals(ActorKind.PLAYER, new AnonymousEvent(SecurityEventType.DOOR_OPENED, SecuritySeverity.INFO, 0,
                null, ActorKind.WITHHELD).actorKind(), "the identity-free shape cannot carry WITHHELD at all");
        assertEquals(1, rows.get(0).agoSeconds());
        assertEquals(DOOR.pos(), rows.get(0).devicePos());
        for (AnonymousEvent row : rows) {
            for (UUID id : Set.of(owner, officer, member, stranger)) {
                assertFalse(row.toString().contains(id.toString()), "no UUID in a monitor row");
            }
        }
    }
}
