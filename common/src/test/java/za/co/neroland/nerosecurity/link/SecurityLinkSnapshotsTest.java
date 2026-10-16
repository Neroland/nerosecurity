package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;

class SecurityLinkSnapshotsTest {

    private static final Pattern UUID_SHAPE = Pattern.compile(
            "[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");
    private static final Set<String> POSITION_KEYS = Set.of("x", "y", "z", "pos", "position", "devicePos", "hub",
            "blockPos", "coords");

    private final FakeLinkContext ctx = new FakeLinkContext();
    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID intruder = UUID.randomUUID();
    private Installation lab;
    private DevicePos door;
    private DevicePos laser;

    private SecurityLinkSnapshots setup() {
        lab = ctx.install(owner, "Orbital Lab", 0);
        ctx.store.setMember(lab.id(), officer, AccessLevel.OFFICER, 64);
        ctx.store.setMember(lab.id(), member, AccessLevel.MEMBER, 64);
        door = ctx.link(lab, "security_door", 10, 64, 10);
        laser = ctx.link(lab, "laser_grid_node", 123, 64, -456);
        ctx.link(lab, "alarm", 12, 64, 10);
        ctx.link(lab, "force_field_emitter", 13, 64, 10);
        lab = ctx.fresh(lab);
        ctx.addAudit(lab, new AuditEntry(ctx.now - 60_000L, SecurityEventType.DOOR_DENIED, intruder, false, door, null));
        ctx.addAudit(lab, new AuditEntry(ctx.now - 30_000L, SecurityEventType.LASER_TRIPPED, intruder, false, laser, null));
        ctx.addAudit(lab, new AuditEntry(ctx.now - 10_000L, SecurityEventType.DOOR_OPENED, owner, false, door, null));
        ctx.addAudit(lab, new AuditEntry(ctx.now - 5_000L, SecurityEventType.MOTION_DETECTED, null, true, null, null));
        ctx.names.put(intruder, "Steve");
        ctx.status = (installation, pos, blockId, now) -> switch (DeviceKinds.kind(blockId)) {
            case "security_door" -> DeviceStatusSource.Status.of(true, "sealed");
            case "laser_grid_node" -> new DeviceStatusSource.Status(true, true, "armed", true, 0L);
            case "alarm" -> new DeviceStatusSource.Status(true, null, "silenced", false, now + 600_000L);
            default -> DeviceStatusSource.Status.UNLOADED;
        };
        ctx.raiseAlert(owner, LinkAlert.raise(PushCopy.alertId(lab.id(), PushKind.LASER_TRIPPED), "nerosecurity",
                LinkAlert.Severity.CRITICAL, "Laser grid tripped — Orbital Lab"));
        return new SecurityLinkSnapshots(ctx);
    }

    @Test
    void installationsForAnOwnerAreFullAndClean() {
        SecurityLinkSnapshots s = setup();
        JsonObject out = s.snapshot(owner, "installations", Map.of());
        assertEquals(1, out.get("schema_version").getAsInt());
        assertEquals("off", out.getAsJsonObject("actions").get("lockdown").getAsString(), "remote lockdown ships off");
        JsonObject row = out.getAsJsonArray("installations").get(0).getAsJsonObject();
        assertEquals("owner", row.get("role").getAsString());
        assertEquals("minecraft:overworld", row.get("dimension").getAsString());
        assertEquals(4, row.getAsJsonObject("deviceCounts").get("total").getAsInt());
        assertTrue(row.get("powered").getAsBoolean());
        assertEquals("motion_detected", row.getAsJsonObject("lastEvent").get("type").getAsString());
        assertEquals(1, row.get("unackedAlerts").getAsInt());
        assertClean(out, Set.of(lab.id().toString()));
    }

    @Test
    void aMemberRowHasExactlyFiveKeys() {
        SecurityLinkSnapshots s = setup();
        JsonObject row = s.snapshot(member, "installations", Map.of()).getAsJsonArray("installations").get(0)
                .getAsJsonObject();
        assertEquals(Set.of("id", "name", "dimension", "role", "lockdown"), row.keySet());
        assertEquals(Set.of("active", "source"), row.getAsJsonObject("lockdown").keySet());
        assertTrue(s.snapshot(intruder, "installations", Map.of()).getAsJsonArray("installations").isEmpty());
    }

    @Test
    void eventsCarryActorKindsNotActors() {
        SecurityLinkSnapshots s = setup();
        JsonObject out = s.snapshot(officer, "events", Map.of("installation", lab.id().toString()));
        assertFalse(out.get("actorsIncluded").getAsBoolean());
        JsonArray rows = out.getAsJsonArray("events");
        assertEquals(4, rows.size());
        JsonObject newest = rows.get(0).getAsJsonObject();
        assertEquals("withheld", newest.get("actorKind").getAsString());
        JsonObject tripped = rows.get(2).getAsJsonObject();
        assertEquals("laser_tripped", tripped.get("type").getAsString());
        assertEquals("Laser grid 1", tripped.getAsJsonObject("device").get("label").getAsString());
        assertEquals("player", tripped.get("actorKind").getAsString());
        assertFalse(tripped.has("actor"));
        assertFalse(tripped.has("alertId"), "the officer has no alert of their own");
        assertClean(out, Set.of(lab.id().toString()));
        assertTrue(ctx.published.isEmpty(), "a plain read is not a log read");

        JsonObject ownerView = s.snapshot(owner, "events", Map.of("installation", lab.id().toString()));
        JsonObject ownTrip = ownerView.getAsJsonArray("events").get(2).getAsJsonObject();
        assertEquals(PushCopy.alertId(lab.id(), PushKind.LASER_TRIPPED), ownTrip.get("alertId").getAsString());
        assertEquals("you", ownerView.getAsJsonArray("events").get(1).getAsJsonObject().get("actorKind").getAsString());
    }

    @Test
    void theFeedCoversOfficerInstallationsOnly() {
        SecurityLinkSnapshots s = setup();
        assertEquals(4, s.snapshot(owner, "events", Map.of()).getAsJsonArray("events").size());
        assertEquals(0, s.snapshot(member, "events", Map.of()).getAsJsonArray("events").size());
        assertTrue(ctx.published.isEmpty());
    }

    @Test
    void actorsAsMemberAddsNothingAndIsNotALogRead() {
        SecurityLinkSnapshots s = setup();
        JsonObject out = s.snapshot(member, "events", Map.of("installation", lab.id().toString(), "actors", "1"));
        assertTrue(out.get("restricted").getAsBoolean());
        assertTrue(out.getAsJsonArray("events").isEmpty());
        assertTrue(ctx.published.isEmpty());
        JsonObject stranger = s.snapshot(intruder, "events", Map.of("installation", lab.id().toString(), "actors", "1"));
        assertEquals("unknown installation", stranger.get("error").getAsString());
        assertTrue(ctx.published.isEmpty());
    }

    @Test
    void actorsAsOfficerPublishesExactlyOneLogReadFirstAndShowsNamesNeverIds() {
        SecurityLinkSnapshots s = setup();
        JsonObject out = s.snapshot(officer, "events", Map.of("installation", lab.id().toString(), "actors", "1"));
        assertEquals(1, ctx.published.size());
        assertEquals(SecurityEventType.LOG_READ, ctx.published.get(0).type());
        assertEquals(officer, ctx.published.get(0).actor());
        assertEquals(null, ctx.published.get(0).devicePos());
        assertTrue(out.get("actorsIncluded").getAsBoolean());
        JsonObject tripped = out.getAsJsonArray("events").get(2).getAsJsonObject();
        assertEquals("Steve", tripped.getAsJsonObject("actor").get("name").getAsString());
        assertFalse(out.getAsJsonArray("events").get(0).getAsJsonObject().has("actor"), "withheld stays anonymous");
        assertClean(out, Set.of(lab.id().toString()));
    }

    @Test
    void actorsDuringLockdownFollowTheInGameLogRule() {
        SecurityLinkSnapshots s = setup();
        ctx.store.setLockdown(lab.id(), Lockdown.manual(ctx.now, true));
        JsonObject out = s.snapshot(officer, "events", Map.of("installation", lab.id().toString(), "actors", "1"));
        assertFalse(out.get("actorsIncluded").getAsBoolean(), "lockdown raises READ_LOG to OWNER");
        assertTrue(ctx.published.isEmpty());
        assertEquals(4, out.getAsJsonArray("events").size(), "officers still see the identity-free rows");
    }

    @Test
    void devicesAreLabelledWithoutCoordinates() {
        SecurityLinkSnapshots s = setup();
        JsonObject out = s.snapshot(owner, "devices", Map.of("installation", lab.id().toString()));
        JsonArray rows = out.getAsJsonArray("devices");
        assertEquals(4, rows.size());
        assertEquals("Door 1", rows.get(0).getAsJsonObject().get("label").getAsString());
        assertEquals("sealed", rows.get(0).getAsJsonObject().get("state").getAsString());
        assertTrue(rows.get(1).getAsJsonObject().get("tripped").getAsBoolean());
        assertTrue(rows.get(2).getAsJsonObject().has("silencedUntil"));
        assertEquals(Set.of("kind", "label", "loaded"), rows.get(3).getAsJsonObject().keySet());
        assertClean(out, Set.of(lab.id().toString()));
        assertTrue(s.snapshot(member, "devices", Map.of("installation", lab.id().toString())).get("restricted")
                .getAsBoolean());
        assertNotNull(s.snapshot(owner, "devices", Map.of()).get("error"));
    }

    @Test
    void theLegacyLogKeepsTheSummaryDocShape() {
        SecurityLinkSnapshots s = setup();
        JsonObject out = s.snapshot(owner, "log", Map.of());
        assertTrue(out.get("headline").getAsString().startsWith("1 installation"));
        JsonArray sections = out.getAsJsonArray("sections");
        assertEquals("Installations", sections.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals(4, sections.get(1).getAsJsonObject().getAsJsonArray("items").size());
        assertClean(out, Set.of());
        JsonObject memberLog = s.snapshot(member, "log", Map.of());
        assertEquals(0, memberLog.getAsJsonArray("sections").get(1).getAsJsonObject().getAsJsonArray("items").size(),
                "a member sees names and lockdown only");
    }

    @Test
    void noServerAnswersEmpty() {
        SecurityLinkSnapshots s = new SecurityLinkSnapshots(new FakeLinkContext() {
            @Override
            public za.co.neroland.nerosecurity.installation.SecurityInstallations installations() {
                return null;
            }
        });
        assertTrue(s.snapshot(owner, "installations", Map.of()).getAsJsonArray("installations").isEmpty());
        assertTrue(s.snapshot(owner, "events", Map.of()).getAsJsonArray("events").isEmpty());
    }

    @Test
    void wikiIndexIsServedAndTraversalRefused() {
        JsonObject index = SecurityLinkSnapshots.wiki(Map.of());
        assertEquals("nerosecurity", index.get("mod").getAsString());
        assertTrue(index.getAsJsonArray("pages").size() >= 1);
        assertEquals("unknown page", SecurityLinkSnapshots.wiki(Map.of("page", "../secret")).get("error").getAsString());
    }

    // --- the JSON walk ----------------------------------------------------------------------------

    /** No position-shaped key anywhere; no UUID-shaped string other than the allowed installation ids. */
    private void assertClean(JsonElement root, Set<String> allowedIds) {
        Set<String> seen = new HashSet<>();
        walk(root, "", seen, allowedIds);
        String text = root.toString();
        for (UUID person : List.of(owner, officer, member, intruder)) {
            assertFalse(text.contains(person.toString()), "a player id leaked");
        }
        for (String id : allowedIds) {
            text = text.replace(id, "");
        }
        if (lab != null) {
            text = text.replace(lab.id().toString(), "");
        }
        for (String coordinate : List.of("123", "-456")) {
            assertFalse(text.contains(coordinate), "a coordinate leaked: " + coordinate);
        }
    }

    private void walk(JsonElement e, String path, Set<String> seen, Set<String> allowedIds) {
        if (e == null || e.isJsonNull()) {
            return;
        }
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> member : e.getAsJsonObject().entrySet()) {
                assertFalse(POSITION_KEYS.contains(member.getKey()), "position key at " + path + "." + member.getKey());
                if ("dimension".equals(member.getKey())) {
                    assertTrue(path.endsWith("installations[]"), "dimension only on an installation row: " + path);
                }
                walk(member.getValue(), path + "." + member.getKey(), seen, allowedIds);
            }
        } else if (e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) {
                walk(item, path + "[]", seen, allowedIds);
            }
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            Matcher m = UUID_SHAPE.matcher(e.getAsString());
            while (m.find()) {
                assertTrue(allowedIds.contains(m.group()), "UUID-shaped string at " + path + ": not an installation id");
            }
        }
    }
}
