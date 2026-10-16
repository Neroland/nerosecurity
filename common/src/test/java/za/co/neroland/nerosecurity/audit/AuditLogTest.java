package za.co.neroland.nerosecurity.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;

/** The audit ring buffer, expiry, codec and erasure. Plain JVM. */
class AuditLogTest {

    private static final DevicePos POS = new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(1, 64, 2));
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static AuditEntry entry(long at, SecurityEventType type, UUID actor) {
        return new AuditEntry(at, type, actor, false, POS, type.defaultSeverity);
    }

    /** 1. */
    @Test
    void ringBufferEvictsOldestAtCap() {
        AuditLog log = new AuditLog();
        for (int i = 1; i <= 5; i++) {
            log.append(entry(i, SecurityEventType.DOOR_OPENED, null), 3);
        }
        assertEquals(3, log.size());
        List<AuditEntry> newest = log.newestFirst(10);
        assertEquals(List.of(5L, 4L, 3L), newest.stream().map(AuditEntry::at).toList(), "first two gone, order kept");
        log.append(entry(6, SecurityEventType.DOOR_OPENED, null), 2);
        assertEquals(2, log.size(), "a lowered cap shrinks the log on the next append");
        assertEquals(List.of(6L, 5L), log.newestFirst(10).stream().map(AuditEntry::at).toList());
    }

    /** 2. */
    @Test
    void expiryRemovesOnlyExpiredAndPresenceEarlier() {
        long now = 100 * DAY;
        AuditLog log = new AuditLog();
        log.append(entry(now - 8 * DAY, SecurityEventType.DOOR_OPENED, null), 100);
        log.append(entry(now - 2 * HOUR, SecurityEventType.MOTION_DETECTED, null), 100);
        log.append(entry(now - 2 * HOUR, SecurityEventType.DOOR_OPENED, null), 100);
        log.append(entry(now - 2 * HOUR, SecurityEventType.CAMERA_MEMBER_SEEN, null), 100);
        log.append(entry(now - 30 * MINUTE, SecurityEventType.MOTION_DETECTED, null), 100);
        log.append(entry(now - 2 * HOUR, SecurityEventType.LASER_TRIPPED, null), 100);

        int removed = log.purge(now, 7 * DAY, 60 * MINUTE);

        assertEquals(3, removed, "the 8-day door row and both 2-hour presence rows");
        List<SecurityEventType> left = log.newestFirst(10).stream().map(AuditEntry::type).toList();
        assertEquals(List.of(SecurityEventType.LASER_TRIPPED, SecurityEventType.MOTION_DETECTED,
                SecurityEventType.DOOR_OPENED), left, "a laser trip is a breach and keeps the audit retention");
    }

    /** 3. */
    @Test
    void codecRoundTripAndTruncation() {
        UUID actor = UUID.randomUUID();
        AuditLog log = new AuditLog();
        log.append(entry(1L, SecurityEventType.DOOR_OPENED, actor), 10);
        log.append(new AuditEntry(2L, SecurityEventType.LOG_READ, null, true, null, null), 10);
        log.append(entry(3L, SecurityEventType.LASER_TRIPPED, null), 10);

        JsonElement json = AuditLog.codec(() -> 10).encodeStart(JsonOps.INSTANCE, log).getOrThrow();
        AuditLog back = AuditLog.codec(() -> 10).parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(log.newestFirst(10), back.newestFirst(10), "round trip is exact");
        AuditEntry withheld = back.newestFirst(10).get(1);
        assertTrue(withheld.actorWithheld());
        assertEquals(null, withheld.actor());

        AuditLog truncated = AuditLog.codec(() -> 2).parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(3L, 2L), truncated.newestFirst(10).stream().map(AuditEntry::at).toList(),
                "a list longer than the cap keeps the newest");

        JsonArray rows = json.getAsJsonArray().deepCopy();
        JsonObject unknown = rows.get(1).getAsJsonObject();
        unknown.addProperty("type", "teleported_somewhere");
        AuditLog lenient = AuditLog.codec(() -> 10).parse(JsonOps.INSTANCE, rows).getOrThrow();
        assertEquals(List.of(3L, 1L), lenient.newestFirst(10).stream().map(AuditEntry::at).toList(),
                "an unknown type is dropped, the rest intact");
    }

    /** 4. */
    @Test
    void erasureRemovesActorRows() {
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SecurityAuditLog store = new SecurityAuditLog();
        store.append(a, entry(1, SecurityEventType.DOOR_OPENED, subject), 100);
        store.append(a, entry(2, SecurityEventType.DOOR_OPENED, bystander), 100);
        store.append(a, entry(3, SecurityEventType.DOOR_FORCED, null), 100);
        store.append(b, entry(4, SecurityEventType.MOTION_DETECTED, subject), 100);
        store.append(b, entry(5, SecurityEventType.LOG_READ, subject), 100);
        assertTrue(store.hasActorRows(subject));

        assertEquals(3, store.removeActor(subject));

        assertFalse(store.hasActorRows(subject));
        assertTrue(store.hasActorRows(bystander), "erasure is per-subject");
        assertEquals(2, store.size(a), "the bystander's and the actor-less rows stay");
        assertEquals(0, store.size(b));
    }

    @Test
    void storeCodecRoundTripsAndDropsMalformedLogs() {
        UUID id = UUID.randomUUID();
        SecurityAuditLog store = new SecurityAuditLog();
        store.append(id, entry(7L, SecurityEventType.CHEST_OPENED, UUID.randomUUID()), 100);
        JsonElement json = SecurityAuditLog.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        JsonObject root = json.getAsJsonObject().deepCopy();
        JsonObject bad = new JsonObject();
        bad.addProperty("installation", "not-a-uuid");
        bad.add("entries", new JsonArray());
        root.getAsJsonArray("logs").add(bad);
        SecurityAuditLog back = SecurityAuditLog.CODEC.parse(JsonOps.INSTANCE, root).getOrThrow();
        assertEquals(store.newestFirst(id, 10), back.newestFirst(id, 10));
    }

    @Test
    void entryNeverPrintsItsActor() {
        UUID actor = UUID.randomUUID();
        assertFalse(entry(1, SecurityEventType.DOOR_OPENED, actor).toString().contains(actor.toString()));
    }
}
