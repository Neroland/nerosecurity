package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * Erasure conformance for the link module's transient per-player state (coalescer windows, request dedup,
 * rate-limit buckets, the pending login summary), through Core's harness and NeroSecurity's composite
 * eraser. Plain JVM, {@code server = null}.
 */
class SecurityLinkErasureTest {

    private SecurityData.StoreEraser added;

    @AfterEach
    void cleanup() {
        if (added != null) {
            SecurityData.removeStore(added);
        }
        PlayerDataErasure.unregister(SecurityData.eraser());
    }

    @Test
    void theLinkEraserClearsEverythingHeldForTheSubject() {
        FakeLinkContext ctx = new FakeLinkContext();
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        Installation mine = ctx.install(subject, "Mine", 0);
        Installation theirs = ctx.install(bystander, "Theirs", 10);
        SecurityLinkEvents.Pipeline pipeline = new SecurityLinkEvents.Pipeline(ctx);
        LinkRequestDedup dedup = new LinkRequestDedup();
        LinkRateLimiter limiter = LinkRateLimiter.standard();

        pipeline.onEvent(SecurityEvent.of(SecurityEventType.LASER_TRIPPED, mine.id(), null, null, ctx.now));
        pipeline.onEvent(SecurityEvent.of(SecurityEventType.LASER_TRIPPED, theirs.id(), null, null, ctx.now));
        dedup.remember(subject, "ack_event", "r1", LinkActionResult.ok(null), ctx.now);
        limiter.tryAcquire(subject, "ack_event", ctx.now);
        LoginAlertSummary.schedule(subject);

        java.util.function.Predicate<UUID> holds = p -> pipeline.holdsAnyFor(p) || dedup.holdsAnyFor(p)
                || limiter.holdsAnyFor(p) || LoginAlertSummary.holdsAnyFor(p);
        assertTrue(holds.test(subject), "seeded");

        added = SecurityLinkEvents.eraser(pipeline, dedup, limiter);
        SecurityData.addStore(added);
        PlayerDataErasure.register(SecurityData.eraser());

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerosecurity:link.transient", holds::test)
                .run(null, subject);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(subject.toString()), "the report never carries the UUID");
        assertFalse(holds.test(subject));
        assertTrue(pipeline.holdsAnyFor(bystander), "erasure is per-subject");
    }

    @Test
    void alertsLiveInCoreNotHere() {
        FakeLinkContext ctx = new FakeLinkContext();
        UUID owner = UUID.randomUUID();
        Installation i = ctx.install(owner, "Lab", 0);
        new SecurityLinkEvents.Pipeline(ctx).onEvent(
                SecurityEvent.of(SecurityEventType.DOOR_FORCED, i.id(), null, null, ctx.now));
        LinkAlert alert = ctx.alerts(owner).get(0);
        assertTrue(alert.id().startsWith("nerosecurity." + i.id()));
        assertFalse(alert.text().contains(owner.toString()));
    }
}
