package za.co.neroland.nerosecurity.link;

import java.util.UUID;
import java.util.function.Predicate;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * Test-only seam so the shared erasure conformance tests (other packages) can seed and probe the link module's
 * transient per-player state (coalescer windows, request dedup, rate-limit buckets, the pending login summary),
 * store {@code "link"}.
 */
public final class LinkErasureFixture {

    private LinkErasureFixture() {
    }

    /** A seeded link store: its eraser and the probe over every per-player map it holds. */
    public record Seeded(SecurityData.StoreEraser eraser, Predicate<UUID> holds) {
    }

    /** Seeds link state for {@code subject} and {@code bystander}, each owning one installation. */
    public static Seeded seed(UUID subject, UUID bystander) {
        FakeLinkContext ctx = new FakeLinkContext();
        Installation mine = ctx.install(subject, "Mine", 0);
        Installation theirs = ctx.install(bystander, "Theirs", 10);
        SecurityLinkEvents.Pipeline pipeline = new SecurityLinkEvents.Pipeline(ctx);
        LinkRequestDedup dedup = new LinkRequestDedup();
        LinkRateLimiter limiter = LinkRateLimiter.standard();
        pipeline.onEvent(SecurityEvent.of(SecurityEventType.LASER_TRIPPED, mine.id(), null, null, ctx.now));
        pipeline.onEvent(SecurityEvent.of(SecurityEventType.LASER_TRIPPED, theirs.id(), null, null, ctx.now));
        dedup.remember(subject, "ack_event", "r1", LinkActionResult.ok(null), ctx.now);
        dedup.remember(bystander, "ack_event", "r2", LinkActionResult.ok(null), ctx.now);
        limiter.tryAcquire(subject, "ack_event", ctx.now);
        LoginAlertSummary.schedule(subject);
        Predicate<UUID> holds = p -> pipeline.holdsAnyFor(p) || dedup.holdsAnyFor(p) || limiter.holdsAnyFor(p)
                || LoginAlertSummary.holdsAnyFor(p);
        return new Seeded(SecurityLinkEvents.eraser(pipeline, dedup, limiter), holds);
    }
}
