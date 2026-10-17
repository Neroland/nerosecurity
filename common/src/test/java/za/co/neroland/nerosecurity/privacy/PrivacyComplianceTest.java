package za.co.neroland.nerosecurity.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.ActorKind;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.audit.AuditRecorder;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.link.LinkErasureFixture;
import za.co.neroland.nerosecurity.notice.NoticeErasureFixture;

/**
 * Compliance over the privacy surface, plain JVM ({@code server = null}, stores built with {@code new}):
 * the opt-out store and its audit policy (prospective only, {@code LOG_READ} always attributed), the export document (the subject's data and
 * nobody else's), the export files (safe path, erasure, retention), the export rate limit, and Core's
 * {@link ErasureConformance} over <b>every persisted player-keyed store at once</b>.
 */
class PrivacyComplianceTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final DevicePos HUB = new DevicePos(OVERWORLD, new BlockPos(0, 64, 0));
    private static final DevicePos DOOR = new DevicePos(OVERWORLD, new BlockPos(2, 64, 0));
    private static final DevicePos OTHER_HUB = new DevicePos(OVERWORLD, new BlockPos(500, 64, 500));
    private static final long NOW = 1_800_000_000_000L;

    private final List<SecurityData.StoreEraser> added = new ArrayList<>();
    private boolean registered;

    @TempDir
    Path tmp;

    @AfterEach
    void cleanup() {
        added.forEach(SecurityData::removeStore);
        added.clear();
        if (registered) {
            PlayerDataErasure.unregister(SecurityData.eraser());
            registered = false;
        }
        NoticeErasureFixture.reset();
    }

    private void addStore(SecurityData.StoreEraser store) {
        SecurityData.addStore(store);
        added.add(store);
    }

    // --- opt-out ------------------------------------------------------------------------------------------

    @Test
    void optOutStoreRoundTripsAndDropsMalformedRows() {
        PrivacyOptOuts store = new PrivacyOptOuts();
        UUID a = UUID.randomUUID();
        assertTrue(store.set(a, true));
        assertFalse(store.set(a, true), "idempotent");
        JsonObject json = PrivacyOptOuts.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow().getAsJsonObject();
        json.getAsJsonArray("opted_out").add("not-a-uuid");
        PrivacyOptOuts back = PrivacyOptOuts.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(back.isOptedOut(a));
        assertEquals(1, back.size(), "the malformed row is dropped");
        assertTrue(back.set(a, false));
        assertFalse(back.isOptedOut(a), "opt-in removes the row entirely");
    }

    @Test
    void anOptedOutPlayerIsAnonymousInEveryNewRowButTheEventStillFires() {
        PrivacyOptOuts optOuts = new PrivacyOptOuts();
        SecurityAuditLog audit = new SecurityAuditLog();
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID installation = UUID.randomUUID();
        optOuts.set(subject, true);
        SecurityEventBus.Listener recorder = AuditRecorder.listener(() -> audit, () -> null, () -> true, () -> 500,
                () -> PrivacyOptOuts.policy(server -> optOuts));

        for (SecurityEventType type : SecurityEventType.values()) {
            if (type != SecurityEventType.AUDIT_PURGED) {
                recorder.on(SecurityEvent.of(type, installation, DOOR, subject, NOW));
            }
        }
        recorder.on(SecurityEvent.of(SecurityEventType.LASER_TRIPPED, installation, DOOR, bystander, NOW));

        List<AuditEntry> naming = audit.rowsByActor(subject).getOrDefault(installation, List.of());
        assertEquals(List.of(SecurityEventType.LOG_READ), naming.stream().map(AuditEntry::type).toList(),
                "no row names the subject except the record of them reading a log");
        assertTrue(audit.hasActorRows(bystander), "only the opted-out player is withheld");
        List<AuditEntry> rows = audit.newestFirst(installation, 500);
        assertEquals(SecurityEventType.values().length, rows.size(), "every event was still recorded");
        long withheld = rows.stream().filter(AuditEntry::actorWithheld).count();
        assertEquals(SecurityEventType.values().length - 2, withheld, "all but LOG_READ and the bystander's row");
        assertEquals(ActorKind.WITHHELD, rows.get(1).actorKind());
    }

    /** A log read is an accountability record: it names the reader even when the reader has opted out. */
    @Test
    void aLogReadAlwaysNamesTheReaderWhateverTheirOptOut() {
        PrivacyOptOuts optOuts = new PrivacyOptOuts();
        SecurityAuditLog audit = new SecurityAuditLog();
        UUID reader = UUID.randomUUID();
        UUID installation = UUID.randomUUID();
        optOuts.set(reader, true);
        SecurityEventBus.Listener recorder = AuditRecorder.listener(() -> audit, () -> null, () -> true, () -> 500,
                () -> PrivacyOptOuts.policy(server -> optOuts));
        recorder.on(SecurityEvent.of(SecurityEventType.LOG_READ, installation, null, reader, NOW));
        recorder.on(SecurityEvent.of(SecurityEventType.DOOR_OPENED, installation, DOOR, reader, NOW));
        // Even a failing policy lookup (which otherwise fails private) does not anonymise a log read.
        SecurityEventBus.Listener failing = AuditRecorder.listener(() -> audit, () -> null, () -> true, () -> 500,
                () -> (server, actor) -> {
                    throw new IllegalStateException("lookup failed");
                });
        failing.on(SecurityEvent.of(SecurityEventType.LOG_READ, installation, null, reader, NOW + 1));

        List<AuditEntry> rows = audit.newestFirst(installation, 10);
        assertEquals(reader, rows.get(0).actor(), "the second log read names the reader");
        assertFalse(rows.get(0).actorWithheld());
        assertNull(rows.get(1).actor(), "an ordinary event is still withheld");
        assertTrue(rows.get(1).actorWithheld());
        assertEquals(reader, rows.get(2).actor(), "the log read names the reader");
        assertTrue(AuditRecorder.alwaysAttributed(SecurityEventType.LOG_READ));
        assertFalse(AuditRecorder.alwaysAttributed(SecurityEventType.DOOR_DENIED));
    }

    /**
     * The opt-out is prospective only: rows recorded before it keep the player's id (until retention or erasure
     * removes them); only rows recorded afterwards are anonymous.
     */
    @Test
    void optingOutLeavesRowsThatAlreadyNameThePlayer() {
        PrivacyOptOuts optOuts = new PrivacyOptOuts();
        SecurityAuditLog audit = new SecurityAuditLog();
        UUID subject = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        SecurityEventBus.Listener recorder = AuditRecorder.listener(() -> audit, () -> null, () -> true, () -> 500,
                () -> PrivacyOptOuts.policy(server -> optOuts));
        recorder.on(SecurityEvent.of(SecurityEventType.DOOR_OPENED, a, DOOR, subject, 1L));

        assertTrue(SecurityPrivacyActions.applyOptOut(optOuts, subject, true));
        recorder.on(SecurityEvent.of(SecurityEventType.DOOR_DENIED, a, DOOR, subject, 2L));

        List<AuditEntry> rows = audit.newestFirst(a, 10);
        assertEquals(2, rows.size());
        assertNull(rows.get(0).actor(), "a row recorded after the opt-out is anonymous");
        assertTrue(rows.get(0).actorWithheld());
        assertEquals(subject, rows.get(1).actor(), "a row recorded before the opt-out keeps the id");
        assertFalse(rows.get(1).actorWithheld());
        assertTrue(audit.hasActorRows(subject), "past rows go only by retention or erasure");
    }

    /** Opting out again changes nothing and dirties nothing (C6-1: idempotent and cheap). */
    @Test
    void optingOutTwiceTouchesNothingTheSecondTime() {
        PrivacyOptOuts optOuts = new PrivacyOptOuts();
        UUID subject = UUID.randomUUID();
        assertTrue(SecurityPrivacyActions.applyOptOut(optOuts, subject, true));
        optOuts.setDirty(false);
        assertFalse(SecurityPrivacyActions.applyOptOut(optOuts, subject, true), "a repeat is a no-op");
        assertFalse(optOuts.isDirty(), "a repeat does not force a save");
        assertTrue(SecurityPrivacyActions.applyOptOut(optOuts, subject, false));
        assertFalse(SecurityPrivacyActions.applyOptOut(optOuts, subject, false));
    }

    // --- export -------------------------------------------------------------------------------------------

    @Test
    void exportHoldsTheSubjectsDataAndNoOneElsesIdentity() {
        UUID subject = UUID.randomUUID();
        UUID coOwner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation mine = store.create(subject, "Mine", HUB, NOW, 8).installation();
        store.setMember(mine.id(), member, AccessLevel.OFFICER, 64);
        store = withCoOwner(store, mine.id(), coOwner);
        assertTrue(store.byId(mine.id()).orElseThrow().owners().contains(coOwner), "seeded: a real co-owner");
        Installation theirs = store.create(otherOwner, "Theirs", OTHER_HUB, NOW, 8).installation();
        store.setMember(theirs.id(), subject, AccessLevel.MEMBER, 64);
        store.issueCard(theirs.id(), AccessLevel.MEMBER, false, subject, NOW, 512);
        store.issueCard(theirs.id(), AccessLevel.MEMBER, false, member, NOW, 512);
        store.issueCard(theirs.id(), AccessLevel.MEMBER, false, null, NOW, 512);
        SecurityAuditLog audit = new SecurityAuditLog();
        audit.append(theirs.id(), new AuditEntry(NOW, SecurityEventType.DOOR_OPENED, subject, false, DOOR, null), 500);
        audit.append(theirs.id(), new AuditEntry(NOW, SecurityEventType.DOOR_DENIED, otherOwner, false, DOOR, null), 500);
        audit.append(theirs.id(), new AuditEntry(NOW, SecurityEventType.LASER_TRIPPED, null, true, DOOR, null), 500);

        List<LinkAlert> alerts = List.of(
                new LinkAlert("nerosecurity.a", "nerosecurity", LinkAlert.Severity.CRITICAL, "Door forced - Theirs", NOW,
                        false, 0L),
                new LinkAlert("nerosecurity.b", "nerosecurity", LinkAlert.Severity.WARN, "Laser tripped - Theirs", NOW,
                        false, NOW + 60_000L),
                new LinkAlert("nerosecurity.c", "nerosecurity", LinkAlert.Severity.INFO, "Lockdown ended - Theirs", NOW,
                        false, 0L),
                new LinkAlert("nerosecurity.d", "nerosecurity", LinkAlert.Severity.INFO, "Alarm silenced - Theirs", NOW,
                        true, 0L),
                new LinkAlert("core.e", "core", LinkAlert.Severity.WARN, "Another module's alert", NOW, false, 0L));
        JsonObject doc = PrivacyExport.build(subject, store, audit, true, alerts, NOW);
        String text = doc.toString();

        assertEquals(subject.toString(), doc.get("subject").getAsString());
        assertTrue(doc.getAsJsonObject("privacy").get("opted_out").getAsBoolean());
        JsonArray owned = doc.getAsJsonArray("installations_owned");
        assertEquals(1, owned.size());
        assertEquals(1, owned.get(0).getAsJsonObject().get("member_count").getAsInt(), "members as a count");
        assertEquals(1, owned.get(0).getAsJsonObject().get("other_owner_count").getAsInt(), "co-owners as a count");
        assertEquals(1, doc.getAsJsonArray("memberships").size());
        assertEquals("member", doc.getAsJsonArray("memberships").get(0).getAsJsonObject().get("level").getAsString());
        assertEquals(1, doc.getAsJsonArray("keycards_bound_to_you").size(), "only cards bound to the subject");
        assertEquals(1, doc.getAsJsonArray("audit_rows_naming_you").size(), "only rows where the subject acted");
        assertEquals(3, doc.getAsJsonObject("link").get("unackedAlerts").getAsInt());
        JsonArray exportedAlerts = doc.getAsJsonObject("link").getAsJsonArray("alerts");
        assertEquals(4, exportedAlerts.size(), "the subject's own NeroSecurity alerts, with their text");
        assertEquals("Door forced - Theirs", exportedAlerts.get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(exportedAlerts.get(1).getAsJsonObject().has("snoozed_until"));
        assertFalse(text.contains("Another module's alert"), "other modules' alerts are theirs to export");
        for (UUID other : List.of(coOwner, member, otherOwner)) {
            assertFalse(text.contains(other.toString()), "another player's id never appears in the export");
        }
    }

    /** Adds {@code coOwner} as a second owner of {@code id} through the stored shape (there is no in-game setter). */
    private static SecurityInstallations withCoOwner(SecurityInstallations store, UUID id, UUID coOwner) {
        JsonObject json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow().getAsJsonObject();
        for (JsonElement row : json.getAsJsonArray("installations")) {
            JsonObject installation = row.getAsJsonObject();
            if (id.toString().equals(installation.get("id").getAsString())) {
                installation.getAsJsonArray("owners").add(coOwner.toString());
            }
        }
        return SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void exportFilesStayInsideTheFolderAreRestrictedErasedAndExpire() throws IOException {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        Path dir = PrivacyExport.directory(tmp);
        Path file = PrivacyExport.file(dir, subject);
        assertEquals(dir, file.getParent(), "named by the subject's own id, directly inside the export folder");
        assertEquals("nerosecurity/exports/" + subject + ".json", PrivacyExport.relativePath(subject));

        PrivacyExport.write(dir, file, "{}");
        PrivacyExport.write(dir, PrivacyExport.file(dir, bystander), "{}");
        assertTrue(PrivacyExport.exists(dir, subject));
        assertEquals("{}", Files.readString(file), "a second write replaces, never appends");

        Files.setLastModifiedTime(PrivacyExport.file(dir, bystander),
                FileTime.fromMillis(NOW - (PrivacyExport.RETENTION_DAYS + 1) * 86_400_000L));
        Files.setLastModifiedTime(file, FileTime.fromMillis(NOW));
        assertEquals(1, PrivacyExport.purgeExpired(dir, NOW), "only the expired export goes");
        assertTrue(PrivacyExport.exists(dir, subject));
        assertFalse(PrivacyExport.exists(dir, bystander));

        assertTrue(PrivacyExport.delete(dir, subject));
        assertFalse(PrivacyExport.exists(dir, subject));
        assertFalse(PrivacyExport.delete(null, subject), "no folder, nothing to do, no throw");
    }

    @Test
    void exportIsRateLimitedPerPlayer() {
        AtomicLong clock = new AtomicLong(NOW);
        SecurityPrivacyActions actions = new SecurityPrivacyActions(clock::get, () -> 60_000L);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(0L, actions.tryExport(a, clock.get()));
        assertEquals(60_000L, actions.tryExport(a, clock.get()), "the second export at once is refused");
        assertEquals(0L, actions.tryExport(b, clock.get()), "per player");
        assertEquals(1_000L, actions.tryExport(a, clock.get() + 59_000L));
        assertEquals(0L, actions.tryExport(a, clock.get() + 60_000L));
        assertTrue(actions.available(), "the real actions are available");
    }

    /** N-6: the rate-limit map forgets a player once their cooldown has passed; it never grows without bound. */
    @Test
    void theRateLimitMapForgetsPlayersOnceTheirCooldownHasPassed() {
        SecurityPrivacyActions actions = new SecurityPrivacyActions(() -> NOW, () -> 60_000L);
        for (int i = 0; i < 50; i++) {
            actions.tryExport(UUID.randomUUID(), NOW);
        }
        assertEquals(50, actions.trackedPlayers());
        UUID late = UUID.randomUUID();
        assertEquals(0L, actions.tryExport(late, NOW + 60_000L));
        assertEquals(1, actions.trackedPlayers(), "only the player still inside a cooldown is kept");
        assertTrue(actions.holdsAnyFor(late));
    }

    // --- erasure over every store ---------------------------------------------------------------------------

    /**
     * Every persisted player-keyed store plus the export file and the export rate limit, seeded for a subject and
     * a bystander, erased through Core's fan-out into NeroSecurity's composite eraser. The subject is gone from
     * all of them, the bystander is untouched, a sole-owned installation is tombstoned and orphaned (not deleted),
     * and the report carries no UUID. The transient {@code "notices"} and {@code "link"} stores run in the same
     * harness pass, so this one run covers every store in the composite.
     */
    @Test
    void erasureClearsEveryPlayerKeyedStoreAndKeepsOwnedInstallations() throws IOException {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        SecurityInstallations installations = new SecurityInstallations();
        Installation sole = installations.create(subject, "Sole", HUB, NOW, 8).installation();
        Installation theirs = installations.create(bystander, "Theirs", OTHER_HUB, NOW, 8).installation();
        installations.setMember(theirs.id(), subject, AccessLevel.OFFICER, 64);
        installations.issueCard(theirs.id(), AccessLevel.OFFICER, false, subject, NOW, 512);
        installations.issueCard(theirs.id(), AccessLevel.MEMBER, false, bystander, NOW, 512);
        SecurityAuditLog audit = new SecurityAuditLog();
        audit.append(theirs.id(), new AuditEntry(NOW, SecurityEventType.DOOR_OPENED, subject, false, DOOR, null), 500);
        audit.append(theirs.id(), new AuditEntry(NOW, SecurityEventType.DOOR_OPENED, bystander, false, DOOR, null), 500);
        PrivacyOptOuts optOuts = new PrivacyOptOuts();
        optOuts.set(subject, true);
        optOuts.set(bystander, true);
        Path dir = PrivacyExport.directory(tmp);
        PrivacyExport.write(dir, PrivacyExport.file(dir, subject), "{}");
        PrivacyExport.write(dir, PrivacyExport.file(dir, bystander), "{}");
        SecurityPrivacyActions actions = new SecurityPrivacyActions(() -> NOW, () -> 60_000L);
        actions.tryExport(subject, NOW);
        actions.tryExport(bystander, NOW);
        NoticeErasureFixture.seed(subject, theirs.id());
        NoticeErasureFixture.seed(bystander, theirs.id());
        LinkErasureFixture.Seeded link = LinkErasureFixture.seed(subject, bystander);

        addStore(SecurityInstallations.eraser(server -> installations));
        addStore(SecurityAuditLog.eraser(server -> audit));
        addStore(PrivacyOptOuts.eraser(server -> optOuts));
        addStore(SecurityPrivacy.exportsEraser(server -> dir, actions));
        addStore(NoticeErasureFixture.eraser());
        addStore(link.eraser());
        PlayerDataErasure.register(SecurityData.eraser());
        registered = true;

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:installations.members", installations::hasMemberRow)
                .probe("nerosecurity:installations.owners", installations::isOwnerAnywhere)
                .probe("nerosecurity:installations.cards", installations::hasCardBoundTo)
                .probe("nerosecurity:audit.actors", audit::hasActorRows)
                .probe("nerosecurity:privacy.optouts", optOuts::holds)
                .probe("nerosecurity:exports.file", p -> PrivacyExport.exists(dir, p))
                .probe("nerosecurity:exports.ratelimit", actions::holdsAnyFor)
                .probe("nerosecurity:notices", NoticeErasureFixture::holds)
                .probe("nerosecurity:link.transient", link.holds()::test)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(subject.toString()), "the report never carries the UUID");

        Installation kept = installations.byId(sole.id()).orElseThrow();
        assertEquals(Set.of(Installation.TOMBSTONE_OWNER), kept.owners(), "not deleted: passed to the tombstone");
        assertTrue(kept.isOrphaned(), "flagged for an admin");
        assertTrue(installations.isOwnerAnywhere(bystander));
        assertTrue(installations.hasCardBoundTo(bystander));
        assertTrue(audit.hasActorRows(bystander));
        assertTrue(optOuts.isOptedOut(bystander));
        assertTrue(PrivacyExport.exists(dir, bystander));
        assertTrue(actions.holdsAnyFor(bystander));
        assertTrue(NoticeErasureFixture.holds(bystander));
        assertTrue(link.holds().test(bystander));
    }
}
