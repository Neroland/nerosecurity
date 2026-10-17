package za.co.neroland.nerosecurity.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge.Role;
import za.co.neroland.nerosecurity.compat.colonies.ColonySource;
import za.co.neroland.nerosecurity.compat.factions.FactionSource;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceBridge;
import za.co.neroland.nerosecurity.compat.nerospace.StationSource;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationNames;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.link.LinkErasureFixture;
import za.co.neroland.nerosecurity.notice.NoticeErasureFixture;

/**
 * POPIA/GDPR <b>erasure conformance</b> for NeroSecurity, run through Core's reusable
 * {@link ErasureConformance} harness.
 *
 * <p>The real installation store is probed directly (owners and member rows), and a seeded in-memory
 * stand-in store proves the composite path itself: the request reaches
 * {@link SecurityData}'s eraser through Core's fan-out, the store loses the subject's rows, a
 * bystander keeps theirs, the backup refresh is skipped without a live server, and a failing store
 * neither stops the next one nor lets the request report a clean erasure.
 *
 * <p>When a real player-keyed store lands, add a probe for it here next to the stand-in.
 *
 * <p>Plain JVM, modelled on NeroFactions' conformance test: {@code server} is {@code null}, every
 * UUID is random and never logged. The harness's isolation pass deliberately logs one "Data eraser
 * ... failed" warning per run — that is the assertion working.
 */
class SecurityErasureConformanceTest {

    /** Stores this test added to the composite, removed again so one test cannot leak into the next. */
    private final List<SecurityData.StoreEraser> added = new ArrayList<>();

    private boolean eraserRegistered;

    @AfterEach
    void restoreRegistries() {
        added.forEach(SecurityData::removeStore);
        added.clear();
        if (eraserRegistered) {
            PlayerDataErasure.unregister(SecurityData.eraser());
            eraserRegistered = false;
        }
    }

    private void registerSecurityEraser() {
        PlayerDataErasure.register(SecurityData.eraser());
        eraserRegistered = true;
    }

    private void addStore(SecurityData.StoreEraser store) {
        SecurityData.addStore(store);
        added.add(store);
    }

    /** A player-keyed stand-in store, shaped like the real player-keyed stores. */
    private static final class InMemoryStore implements SecurityData.StoreEraser {

        final Map<UUID, Set<String>> rows = new ConcurrentHashMap<>();
        final AtomicInteger backups = new AtomicInteger();
        final AtomicInteger erasures = new AtomicInteger();
        private final String name;

        InMemoryStore(String name) {
            this.name = name;
        }

        void seed(UUID player) {
            rows.computeIfAbsent(player, p -> ConcurrentHashMap.newKeySet()).add("row");
        }

        boolean hasRow(UUID player) {
            return rows.containsKey(player);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            erasures.incrementAndGet();
            rows.remove(player);
        }

        @Override
        public void backupNow(MinecraftServer server) {
            backups.incrementAndGet();
        }
    }

    @Test
    void erasureThroughCoreReachesEveryStoreInTheComposite() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();

        InMemoryStore store = new InMemoryStore("test_store");
        store.seed(player);
        store.seed(bystander);
        addStore(store);
        registerSecurityEraser();

        ErasureConformance.create()
                .probe("nerosecurity:test_store", store::hasRow)
                .verify(null, player);

        assertTrue(store.hasRow(bystander), "erasure is per-subject: the bystander's rows must survive");
        assertEquals(0, store.backups.get(),
                "backupNow needs a live level, so it must be skipped when the server is null");
        assertTrue(store.erasures.get() >= 2,
                "the harness's isolation pass and the real request both reach the composite");
    }

    @Test
    void conformanceReportIsCleanAndCarriesNoPlayerIdentity() {
        UUID player = UUID.randomUUID();

        InMemoryStore store = new InMemoryStore("test_store");
        store.seed(player);
        addStore(store);
        registerSecurityEraser();

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:test_store", store::hasRow)
                .run(null, player);

        assertTrue(report.passed(), report.describe());
        assertTrue(report.retainedSubsystems().isEmpty());
        assertTrue(report.erasersRegistered() >= 1, "SecurityData's eraser must be in the fan-out");
        assertFalse(report.describe().contains(player.toString()),
                "the report must never carry the player UUID (it is personal data)");
    }

    @Test
    void aFailingStoreDoesNotStopTheNextAndMarksTheRequestIncomplete() {
        UUID player = UUID.randomUUID();

        SecurityData.StoreEraser broken = new SecurityData.StoreEraser() {
            @Override
            public String name() {
                return "broken_store";
            }

            @Override
            public void erase(MinecraftServer server, UUID uuid) {
                throw new IllegalStateException("deliberate test failure, safe to ignore");
            }

            @Override
            public void backupNow(MinecraftServer server) {
                // never reached
            }
        };
        InMemoryStore after = new InMemoryStore("after_store");
        after.seed(player);
        addStore(broken);
        addStore(after);

        // Called directly (not through Core) so the summary failure is observable: Core would
        // catch it and log the request as INCOMPLETE instead of a clean erasure.
        IllegalStateException summary = assertThrows(IllegalStateException.class,
                () -> SecurityData.eraser().erase(null, player));
        assertFalse(after.hasRow(player), "a store after a failing one must still be erased");
        assertFalse(summary.getMessage().contains(player.toString()),
                "the failure summary must never carry the player UUID");
    }

    /**
     * The installation store, seeded with three shapes: (a) owned solely by the subject, with one
     * other member; (b) owned by a bystander, with the subject as OFFICER; (c) co-owned by subject and
     * bystander. After erasure: (a) is tombstoned and orphaned and keeps its other member; (b) loses
     * the subject's row and keeps its owner; (c) keeps the bystander as sole owner and stays live.
     */
    @Test
    void installationStoreErasureTombstonesSoleOwnedAndKeepsCoOwners() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        String json = "{\"schema_version\":1,\"installations\":["
                + row(a, "[\"" + subject + "\"]", "[{\"player\":\"" + other + "\",\"level\":\"member\"}]", 0) + ","
                + row(b, "[\"" + bystander + "\"]", "[{\"player\":\"" + subject + "\",\"level\":\"officer\"}]", 10) + ","
                + row(c, "[\"" + subject + "\",\"" + bystander + "\"]", "[]", 20)
                + "]}";
        SecurityInstallations store = SecurityInstallations.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertTrue(store.isOwnerAnywhere(subject) && store.hasMemberRow(subject), "seeded");

        addStore(SecurityInstallations.eraser(server -> store));
        registerSecurityEraser();

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:installations.members", store::hasMemberRow)
                .probe("nerosecurity:installations.owners", store::isOwnerAnywhere)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");

        Installation soleOwned = store.byId(a).orElseThrow();
        assertEquals(Set.of(Installation.TOMBSTONE_OWNER), soleOwned.owners());
        assertTrue(soleOwned.isOrphaned());
        assertEquals(AccessLevel.MEMBER, soleOwned.memberLevel(other), "erasure is per-subject");
        assertEquals(InstallationNames.orphanedFor(a), soleOwned.name(),
                "the erased owner's chosen label does not survive on the orphan");
        assertTrue(soleOwned.name().startsWith("Orphaned installation ") && soleOwned.name().length() <= Installation.NAME_MAX);

        Installation bystanders = store.byId(b).orElseThrow();
        assertFalse(bystanders.members().containsKey(subject));
        assertEquals(Set.of(bystander), bystanders.owners());

        Installation coOwned = store.byId(c).orElseThrow();
        assertEquals(Set.of(bystander), coOwned.owners());
        assertFalse(coOwned.isOrphaned());
        assertEquals("T", coOwned.name(), "a co-owned installation keeps its name (it is the co-owners' too)");
        assertEquals(2, store.countOwnedBy(bystander), "bystander owns (b) and (c)");
        assertEquals(0, store.countOwnedBy(subject));
    }

    /**
     * The card ledger: a card bound to the subject loses its row (so it is dead for everyone); an
     * unbound card and a card bound to a bystander survive; the report carries no UUID.
     */
    @Test
    void installationStoreErasureRemovesCardsBoundToTheSubject() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "C",
                new za.co.neroland.nerosecurity.installation.DevicePos(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                        new net.minecraft.core.BlockPos(0, 64, 0)), 0L, 8).installation();
        long boundToSubject = store.issueCard(installation.id(), AccessLevel.OFFICER, false, subject, 0L, 512).serial();
        long unbound = store.issueCard(installation.id(), AccessLevel.MEMBER, false, null, 0L, 512).serial();
        long boundToBystander = store.issueCard(installation.id(), AccessLevel.MEMBER, true, bystander, 0L, 512).serial();
        assertTrue(store.hasCardBoundTo(subject), "seeded");

        addStore(SecurityInstallations.eraser(server -> store));
        registerSecurityEraser();

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:installations.cards", store::hasCardBoundTo)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");

        assertFalse(store.hasCardBoundTo(subject));
        assertEquals(null, installation.card(boundToSubject), "the bound card's row is gone");
        assertTrue(installation.cards().containsKey(unbound), "unbound cards are not personal data");
        assertEquals(bystander, installation.card(boundToBystander).boundOwner(), "erasure is per-subject");
        assertTrue(store.hasCardBoundTo(bystander));
        assertTrue(installation.isOwner(owner), "the owner is untouched");
    }

    /**
     * The audit store: every row naming the subject goes, across installations; a bystander's rows and
     * actor-less rows stay; the report carries no UUID.
     */
    @Test
    void auditStoreErasureRemovesTheSubjectsRows() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SecurityAuditLog audit = new SecurityAuditLog();
        audit.append(a, new AuditEntry(1L, SecurityEventType.DOOR_OPENED, subject, false, null, null), 100);
        audit.append(a, new AuditEntry(2L, SecurityEventType.DOOR_OPENED, bystander, false, null, null), 100);
        audit.append(b, new AuditEntry(3L, SecurityEventType.MOTION_DETECTED, subject, false, null, null), 100);
        audit.append(b, new AuditEntry(4L, SecurityEventType.LASER_TRIPPED, null, false, null, null), 100);
        assertTrue(audit.hasActorRows(subject), "seeded");

        addStore(SecurityAuditLog.eraser(server -> audit));
        registerSecurityEraser();

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:audit.actors", audit::hasActorRows)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");

        assertFalse(audit.hasActorRows(subject));
        assertTrue(audit.hasActorRows(bystander), "erasure is per-subject");
        assertEquals(1, audit.size(a));
        assertEquals(1, audit.size(b), "the actor-less laser row stays");
    }

    /**
     * The two transient stores, {@code "notices"} (who has had which recording notice) and {@code "link"}
     * (coalescer windows, request dedup, rate-limit buckets, the pending login summary), probed here through the
     * composite like every persisted store, so this shared test holds a probe for every store in it.
     */
    @Test
    void transientNoticeAndLinkStoresAreErasedThroughTheComposite() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID installation = UUID.randomUUID();
        try {
            NoticeErasureFixture.seed(subject, installation);
            NoticeErasureFixture.seed(bystander, installation);
            LinkErasureFixture.Seeded link = LinkErasureFixture.seed(subject, bystander);
            assertTrue(NoticeErasureFixture.holds(subject) && link.holds().test(subject), "seeded");

            addStore(NoticeErasureFixture.eraser());
            addStore(link.eraser());
            registerSecurityEraser();

            ErasureConformance.Report report = ErasureConformance.create()
                    .probe("nerosecurity:notices", NoticeErasureFixture::holds)
                    .probe("nerosecurity:link.transient", link.holds()::test)
                    .run(null, subject);
            assertTrue(report.passed(), report.describe());
            assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");
            assertTrue(NoticeErasureFixture.holds(bystander), "erasure is per-subject");
            assertTrue(link.holds().test(bystander), "erasure is per-subject");
        } finally {
            NoticeErasureFixture.reset();
        }
    }

    /** The keycard source's transient rejection cooldown ({@code "keycard_cooldowns"}) is erased per subject. */
    @Test
    void keycardRejectionCooldownIsErasedThroughTheComposite() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = za.co.neroland.nerosecurity.keycard.KeycardTestSupport.create(store,
                UUID.randomUUID(), "K", 0);
        za.co.neroland.nerosecurity.keycard.KeycardData real = za.co.neroland.nerosecurity.keycard.KeycardTestSupport
                .issue(store, installation, AccessLevel.MEMBER, false, null);
        za.co.neroland.nerosecurity.keycard.KeycardPayload p = real.payload();
        za.co.neroland.nerosecurity.keycard.KeycardData tampered = za.co.neroland.nerosecurity.keycard.KeycardTestSupport
                .withPayload(real, new za.co.neroland.nerosecurity.keycard.KeycardPayload(p.installationId(),
                        AccessLevel.OWNER, p.serial(), p.encrypted(), p.bound(), p.issuedAt()));
        za.co.neroland.nerosecurity.access.KeycardSource cards = new za.co.neroland.nerosecurity.access.KeycardSource(
                (server, who) -> List.of(tampered), () -> 10_000L);
        try {
            cards.vote(new AccessQuery(null, subject, installation, AccessAction.OPEN, null));
            cards.vote(new AccessQuery(null, bystander, installation, AccessAction.OPEN, null));
            assertTrue(cards.holdsAnyFor(subject) && cards.holdsAnyFor(bystander), "seeded");

            addStore(cards.eraser());
            registerSecurityEraser();

            ErasureConformance.Report report = ErasureConformance.create()
                    .probe("nerosecurity:keycard_cooldowns", cards::holdsAnyFor)
                    .run(null, subject);
            assertTrue(report.passed(), report.describe());
            assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");
            assertTrue(cards.holdsAnyFor(bystander), "erasure is per-subject");
        } finally {
            NoticeErasureFixture.reset();
        }
    }

    private static String row(UUID id, String owners, String members, int x) {
        return "{\"id\":\"" + id + "\",\"name\":\"T\",\"owners\":" + owners + ",\"members\":" + members
                + ",\"hub\":{\"dim\":\"minecraft:overworld\",\"x\":" + x + ",\"y\":64,\"z\":0}}";
    }

    @Test
    void addingTheSameStoreTwiceIsIgnored() {
        InMemoryStore store = new InMemoryStore("test_store");
        int before = SecurityData.storeCount();
        addStore(store);
        SecurityData.addStore(store);
        assertEquals(before + 1, SecurityData.storeCount(), "a store joins the composite once");
    }

    /**
     * Integration bindings hold organisation ids only. When the sole owner is erased the installation is
     * tombstoned, the bindings stay stored, the faction and colony bindings suspend (no real owner is a member any
     * more) and the station binding keeps granting the station's current owner, by design.
     */
    @Test
    void erasedSoleOwnerSuspendsFactionAndColonyBindingsAndKeepsThem() {
        UUID subject = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID captain = UUID.randomUUID();
        UUID colony = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Identifier faction = Identifier.fromNamespaceAndPath("nerofactions", "guild");
        String json = "{\"schema_version\":1,\"installations\":[" + row(id, "[\"" + subject + "\"]", "[]", 0) + "]}";
        SecurityInstallations store = SecurityInstallations.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertTrue(store.bindFaction(id, faction, AccessLevel.OFFICER));
        assertTrue(store.bindColony(id, colony, AccessLevel.OFFICER));
        assertTrue(store.bindStation(id, 7));

        // Everyone real is a TRUSTED member / colony chief; the erasure tombstone never is.
        FactionsBridge factions = new FactionsBridge() {
            @Override
            public boolean isMember(MinecraftServer server, UUID player, Identifier f) {
                return !Installation.TOMBSTONE_OWNER.equals(player);
            }

            @Override
            public Set<Identifier> membershipsOf(MinecraftServer server, UUID player) {
                return Set.of(faction);
            }

            @Override
            public Tier tierOf(MinecraftServer server, UUID player, Identifier f) {
                return Tier.TRUSTED;
            }
        };
        ColoniesBridge colonies = new ColoniesBridge() {
            @Override
            public boolean exists(MinecraftServer server, UUID c) {
                return true;
            }

            @Override
            public Role roleOf(MinecraftServer server, UUID c, UUID player) {
                return Installation.TOMBSTONE_OWNER.equals(player) ? Role.STRANGER : Role.CHIEF;
            }

            @Override
            public UUID colonyAt(ServerLevel level, BlockPos pos) {
                return null;
            }
        };
        NerospaceBridge stations = new NerospaceBridge() {
            @Override
            public boolean ownedBy(MinecraftServer server, int station, UUID player) {
                return station == 7 && captain.equals(player);
            }

            @Override
            public List<Integer> stationsOwnedBy(MinecraftServer server, UUID player) {
                return List.of();
            }
        };
        FactionSource factionSource = new FactionSource(() -> factions, () -> true);
        ColonySource colonySource = new ColonySource(() -> colonies, () -> true);
        StationSource stationSource = new StationSource(() -> stations, () -> true);
        Installation before = store.byId(id).orElseThrow();
        AccessQuery q = new AccessQuery(null, member, before, AccessAction.OPEN, null);
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), factionSource.vote(q), "seeded: the binding grants");
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), colonySource.vote(q), "seeded: the binding grants");

        addStore(SecurityInstallations.eraser(server -> store));
        registerSecurityEraser();
        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:installations.owners", store::isOwnerAnywhere)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");

        Installation after = store.byId(id).orElseThrow();
        assertEquals(Set.of(Installation.TOMBSTONE_OWNER), after.owners());
        assertEquals(faction, after.boundFaction(), "bindings are kept");
        assertEquals(colony, after.boundColony());
        assertEquals(Integer.valueOf(7), after.boundStation());
        AccessQuery q2 = new AccessQuery(null, member, after, AccessAction.OPEN, null);
        assertEquals(AccessVote.Abstain.INSTANCE, factionSource.vote(q2), "no real owner is a member: suspended");
        assertEquals(AccessVote.Abstain.INSTANCE, colonySource.vote(q2), "no real owner is a member: suspended");
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER),
                stationSource.vote(new AccessQuery(null, captain, after, AccessAction.OPEN, null)),
                "a station binding keys off the station, not the binder");
        String saved = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow().toString();
        assertFalse(saved.contains(subject.toString()), "nothing at rest names the erased player");
    }
}
