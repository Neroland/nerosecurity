package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The push privacy test: a named actor at a known position trips a laser, and nothing about the actor,
 * the owner, the position or the dimension reaches the notification, the alert text or the deep link.
 */
class PushCopyTest {

    private static final UUID ACTOR = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID OWNER = UUID.fromString("11112222-3333-4444-8555-666677778888");

    @Test
    void theFullPathCarriesNoPersonalDataOrPosition() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.names.put(ACTOR, "Steve");
        ctx.names.put(OWNER, "Alex");
        Installation installation = ctx.install(OWNER, "Orbital Lab", 0);
        DevicePos laser = ctx.link(installation, "laser_grid_node", 123, 64, -456);
        SecurityLinkEvents.Pipeline pipeline = new SecurityLinkEvents.Pipeline(ctx);

        pipeline.onEvent(SecurityEvent.of(SecurityEventType.LASER_TRIPPED, installation.id(), laser, ACTOR, ctx.now));

        assertEquals(1, ctx.linkEvents.size(), "one live row for the one owner");
        LinkEvent event = ctx.linkEvents.get(0);
        assertEquals(OWNER, event.playerId());
        JsonObject notify = event.payload().getAsJsonObject(PushCopy.FIELD);
        assertNotNull(notify, "the leading event is notified at once");
        String title = notify.get("title").getAsString();
        String body = notify.get("body").getAsString();
        String link = notify.get("deepLink").getAsString();
        List<LinkAlert> alerts = ctx.alerts(OWNER);
        assertEquals(1, alerts.size());
        String alertText = alerts.get(0).text();

        for (String text : List.of(title, body, alertText)) {
            assertClean(text);
        }
        assertEquals("nerolink://server/{server}/security/" + installation.id(), link,
                "the deep link names the installation id and nothing else");
        assertTrue(PushCopy.DEEP_LINK.matcher(link.replace(PushCopy.SERVER_PLACEHOLDER, "abc")).matches());
        assertTrue(link.length() <= PushCopy.LINK_MAX);
        assertTrue(title.length() <= PushCopy.TITLE_MAX);
        assertTrue(body.length() <= PushCopy.BODY_MAX);
        assertEquals("Orbital Lab", body);
        assertTrue(alertText.contains("Orbital Lab"));

        // The live row itself: no actor, no position, no owner.
        String row = event.payload().toString();
        assertFalse(row.contains(ACTOR.toString()) || row.contains(OWNER.toString()));
        assertFalse(row.contains("Steve") || row.contains("Alex"));
        assertFalse(row.contains("-456") || row.contains("\"x\"") || row.contains("overworld"));
        assertEquals("player", event.payload().get("actorKind").getAsString());
    }

    private static void assertClean(String text) {
        for (String banned : List.of("Steve", "Alex", ACTOR.toString(), ACTOR.toString().replace("-", ""),
                OWNER.toString(), OWNER.toString().replace("-", ""), "123", "64", "-456", "overworld", "minecraft")) {
            assertFalse(text.contains(banned), () -> "'" + banned + "' leaked into: " + text);
        }
    }

    @Test
    void namesAreCleanedAndCapped() {
        String dirty = "§cEvil\u0007 §lBase​ name that is far too long to keep";
        JsonObject notify = PushCopy.notify(dirty, PushKind.DOOR_FORCED, 0, UUID.randomUUID());
        String body = notify.get("body").getAsString();
        assertFalse(body.contains("§") || body.contains("\u0007") || body.contains("​"));
        assertTrue(body.length() <= Installation.NAME_MAX);
        assertEquals("Installation", PushCopy.notify("\u0001\u0002", PushKind.DOOR_FORCED, 0, UUID.randomUUID())
                .get("body").getAsString(), "nothing usable left reads as a generic word");
    }

    @Test
    void extraRendersAsMore() {
        JsonObject notify = PushCopy.notify("Orbital Lab", PushKind.LASER_TRIPPED, 4, UUID.randomUUID());
        assertTrue(notify.get("body").getAsString().endsWith("(+4 more)"));
        assertTrue(PushCopy.alertText("Orbital Lab", PushKind.LASER_TRIPPED, 4).endsWith("(+4 more)"));
        JsonObject summary = PushCopy.notify("Orbital Lab", PushKind.SUMMARY, 2, UUID.randomUUID());
        assertEquals("Security events", summary.get("title").getAsString());
        assertEquals("Orbital Lab (+2 more)", summary.get("body").getAsString());
        assertEquals("Repeated access denied", PushCopy.phrase(PushKind.REPEATED_DENIED));
    }

    @Test
    void everyKindHasAPhraseAndFitsTheCaps() {
        UUID id = UUID.randomUUID();
        String longName = "x".repeat(200);
        for (PushKind kind : PushKind.values()) {
            JsonObject notify = PushCopy.notify(longName, kind, 9999, id);
            assertFalse(notify.get("title").getAsString().isBlank(), kind.name());
            assertTrue(notify.get("title").getAsString().length() <= PushCopy.TITLE_MAX);
            assertTrue(notify.get("body").getAsString().length() <= PushCopy.BODY_MAX);
        }
        assertEquals(null, PushCopy.deepLink(null));
        assertFalse(PushCopy.DEEP_LINK.matcher("nerolink://server/{server}/security/" + id).matches(),
                "the placeholder itself is not a valid server id; the bridge must substitute it");
    }
}
