package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The link alert retention sweep: per-installation override, former recipients, module filter. */
class AlertRetentionSweepTest {

    private static final long DAY = 86_400_000L;

    private static SecurityInstallations storeWith(UUID shortId, UUID longId, UUID owner) {
        String json = "{\"installations\":["
                + row(shortId, owner, 0, ",\"audit_retention_days\":3") + ","
                + row(longId, owner, 10, ",\"audit_retention_days\":60") + "]}";
        return SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static String row(UUID id, UUID owner, int x, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":\"T\",\"owners\":[\"" + owner + "\"],\"members\":[],"
                + "\"hub\":{\"dim\":\"minecraft:overworld\",\"x\":" + x + ",\"y\":64,\"z\":0}" + extra + "}";
    }

    private static LinkAlert alert(String id, String module, long createdAt) {
        return new LinkAlert(id, module, LinkAlert.Severity.WARN, "t", createdAt, false, 0L);
    }

    @Test
    void theInstallationOverrideShortensButNeverLengthensRetention() {
        FakeLinkContext ctx = new FakeLinkContext();
        UUID owner = UUID.randomUUID();
        UUID shortId = UUID.randomUUID();
        UUID longId = UUID.randomUUID();
        ctx.store = storeWith(shortId, longId, owner);
        String shortAlert = PushCopy.alertId(shortId, PushKind.DOOR_FORCED);
        String longAlert = PushCopy.alertId(longId, PushKind.DOOR_FORCED);
        String goneAlert = PushCopy.alertId(UUID.randomUUID(), PushKind.DOOR_FORCED);
        ctx.raiseAlert(owner, alert(shortAlert, "nerosecurity", ctx.now - 5 * DAY));
        ctx.raiseAlert(owner, alert(longAlert, "nerosecurity", ctx.now - 45 * DAY));
        ctx.raiseAlert(owner, alert(goneAlert, "nerosecurity", ctx.now - 20 * DAY));
        ctx.raiseAlert(owner, alert("nerocolonies.x.y", "nerocolonies", ctx.now - 80 * DAY));

        SecurityLinkEvents.Pipeline pipeline = new SecurityLinkEvents.Pipeline(ctx);
        assertEquals(2, pipeline.sweepAlerts(ctx.now, 30));

        assertFalse(ctx.alertIds(owner).contains(shortAlert), "3-day override: gone after 5 days");
        assertFalse(ctx.alertIds(owner).contains(longAlert), "a 60-day override cannot outlast the global 30");
        assertTrue(ctx.alertIds(owner).contains(goneAlert), "gone installation: global 30 days applies");
        assertTrue(ctx.alertIds(owner).contains("nerocolonies.x.y"), "never another module's alert");
        assertEquals(3, SecurityLinkEvents.Pipeline.retentionDays(ctx.store, shortAlert, 30));
        assertEquals(30, SecurityLinkEvents.Pipeline.retentionDays(ctx.store, "nerosecurity.junk.door_forced", 30));
    }

    @Test
    void aFormerRecipientAlertedThisSessionIsStillSwept() {
        FakeLinkContext ctx = new FakeLinkContext();
        UUID owner = UUID.randomUUID();
        Installation lab = ctx.install(owner, "Lab", 0);
        SecurityLinkEvents.Pipeline pipeline = new SecurityLinkEvents.Pipeline(ctx);
        pipeline.onEvent(SecurityEvent.of(SecurityEventType.DOOR_FORCED, lab.id(), null, null, ctx.now));
        assertEquals(1, ctx.alertIds(owner).size());
        ctx.store.dissolve(lab.id());
        assertTrue(ctx.store.visibleTo(owner).isEmpty(), "no stored relationship any more");
        assertEquals(1, pipeline.sweepAlerts(System.currentTimeMillis() + 31 * DAY, 30));
        assertTrue(ctx.alertIds(owner).isEmpty());
        pipeline.forget(owner);
        assertFalse(pipeline.holdsAnyFor(owner), "erasure forgets the session recipient set too");
    }
}
