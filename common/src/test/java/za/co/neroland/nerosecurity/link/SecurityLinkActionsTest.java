package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig.LinkLockdownMode;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;

class SecurityLinkActionsTest {

    private FakeLinkContext ctx;
    private SecurityLinkActions actions;
    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private Installation lab;

    @BeforeEach
    void setup() {
        ctx = new FakeLinkContext();
        actions = new SecurityLinkActions(ctx, new LinkRequestDedup(), LinkRateLimiter.standard());
        lab = ctx.install(owner, "Orbital Lab", 0);
        ctx.store.setMember(lab.id(), officer, AccessLevel.OFFICER, 64);
        lab = ctx.fresh(lab);
        ctx.online.add(owner);
    }

    private JsonObject lockdown(boolean enabled, String requestId) {
        JsonObject p = new JsonObject();
        p.addProperty("installationId", lab.id().toString());
        p.addProperty("enabled", enabled);
        if (requestId != null) {
            p.addProperty("requestId", requestId);
        }
        return p;
    }

    private LinkActionResult.Error error(LinkActionResult r) {
        assertFalse(r.ok());
        return r.error();
    }

    @Test
    void exactlyThreeActions() {
        assertEquals(List.of("lockdown", "ack_event", "silence_alarm"), actions.actionIds());
    }

    @Test
    void lockdownShipsOff() {
        assertEquals(LinkLockdownMode.OFF, NeroSecurityConfig.linkLockdownMode(), "the shipped default");
        assertEquals(LinkLockdownMode.OFF, NeroSecurityConfig.parseLinkLockdownMode("junk"));
        assertEquals(LinkLockdownMode.FULL, NeroSecurityConfig.parseLinkLockdownMode(" Full "));
        assertFalse(NeroSecurityConfig.isLinkLockdownAllowOffline());
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "lockdown", lockdown(true, "a"))));
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "lockdown", lockdown(false, "b"))));
        assertFalse(ctx.fresh(lab).lockdown().active());
    }

    @Test
    void releaseCannotStartOrConfirm() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.RELEASE, false);
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "lockdown", lockdown(true, "a"))));
        ctx.store.setLockdown(lab.id(), Lockdown.alarm(ctx.now, ctx.now + 300_000L));
        ctx.now += 20_000L;
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "lockdown", lockdown(true, "b"))));
        assertTrue(ctx.fresh(lab).lockdown().isAlarm(), "not confirmed");
        ctx.now += 20_000L;
        assertTrue(actions.execute(owner, "lockdown", lockdown(false, "c")).ok());
        assertFalse(ctx.fresh(lab).lockdown().active());
        assertEquals(ctx.now, ctx.fresh(lab).lastAlarmLockdownEndedAt(), "leaving an alarm lockdown starts the cooldown");
    }

    @Test
    void fullEntersConfirmsAndIsIdempotent() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        LinkActionResult entered = actions.execute(owner, "lockdown", lockdown(true, "a"));
        assertTrue(entered.ok());
        Lockdown state = ctx.fresh(lab).lockdown();
        assertTrue(state.active() && state.enteredByOwner());
        assertEquals(LockdownSource.MANUAL, state.source());
        assertTrue(entered.state().getAsJsonObject("lockdown").get("active").getAsBoolean());
        ctx.now += 20_000L;
        assertTrue(actions.execute(owner, "lockdown", lockdown(true, "b")).ok(), "already manual: ok, unchanged");
        assertEquals(state, ctx.fresh(lab).lockdown());

        ctx.store.setLockdown(lab.id(), Lockdown.alarm(ctx.now, ctx.now + 300_000L));
        ctx.now += 20_000L;
        assertTrue(actions.execute(owner, "lockdown", lockdown(true, "c")).ok());
        assertFalse(ctx.fresh(lab).lockdown().isAlarm(), "confirmed: manual, no auto-release");
        assertTrue(ctx.fresh(lab).lockdown().active());
    }

    @Test
    void notOwnerUnknownAndOfficerAreOneAnswer() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        assertEquals(LinkActionResult.Error.NOT_OWNER, error(actions.execute(officer, "lockdown", lockdown(true, "a"))));
        assertEquals(LinkActionResult.Error.NOT_OWNER,
                error(actions.execute(UUID.randomUUID(), "lockdown", lockdown(true, "b"))));
        JsonObject unknown = lockdown(true, "c");
        unknown.addProperty("installationId", UUID.randomUUID().toString());
        assertEquals(LinkActionResult.Error.NOT_OWNER, error(actions.execute(owner, "lockdown", unknown)));
    }

    @Test
    void anOperatorOverrideIsRefused() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        UUID operator = UUID.randomUUID();
        ctx.admin = (server, actor) -> true;
        assertEquals(LinkActionResult.Error.NOT_OWNER, error(actions.execute(operator, "lockdown", lockdown(true, "a"))));
        assertFalse(ctx.fresh(lab).lockdown().active());
    }

    @Test
    void anOfflineOwnerIsRefusedUnlessTheServerAllowsIt() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        ctx.online.remove(owner);
        assertEquals(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                error(actions.execute(owner, "lockdown", lockdown(true, "a"))));
        assertFalse(ctx.fresh(lab).lockdown().active());
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, true);
        ctx.now += 20_000L;
        assertTrue(actions.execute(owner, "lockdown", lockdown(true, "b")).ok());
        assertTrue(ctx.fresh(lab).lockdown().active());
    }

    @Test
    void malformedRequestsDoNotSpendARateLimitSlot() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        JsonObject noEnabled = new JsonObject();
        noEnabled.addProperty("installationId", lab.id().toString());
        for (int i = 0; i < 5; i++) {
            assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(owner, "lockdown", noEnabled)));
            JsonObject badId = lockdown(true, null);
            badId.addProperty("installationId", "nope");
            assertEquals(LinkActionResult.Error.NOT_OWNER, error(actions.execute(owner, "lockdown", badId)));
        }
        assertTrue(actions.execute(owner, "lockdown", lockdown(true, "ok")).ok(), "the slot was still free");
    }

    @Test
    void allowOfflineFollowsConfig() {
        assertFalse(actions.allowOffline("lockdown"));
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.RELEASE, true);
        assertTrue(actions.allowOffline("lockdown"));
        assertTrue(actions.allowOffline("ack_event"));
        assertTrue(actions.allowOffline("silence_alarm"));
    }

    @Test
    void aReplayedRequestIdDoesNotTransitionTwice() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        LinkActionResult first = actions.execute(owner, "lockdown", lockdown(true, "same"));
        assertTrue(first.ok());
        ctx.store.setLockdown(lab.id(), Lockdown.NONE);
        ctx.now += 30_000L;
        LinkActionResult replay = actions.execute(owner, "lockdown", lockdown(true, "same"));
        assertSame(first, replay);
        assertFalse(ctx.fresh(lab).lockdown().active(), "the replay did not enter a second time");
    }

    @Test
    void theSecondLockdownWithinTenSecondsIsRateLimited() {
        ctx.settings = ctx.settings.withLockdown(LinkLockdownMode.FULL, false);
        assertTrue(actions.execute(owner, "lockdown", lockdown(true, "a")).ok());
        ctx.now += 2_000L;
        LinkActionResult limited = actions.execute(owner, "lockdown", lockdown(false, "b"));
        assertEquals(LinkActionResult.Error.VALIDATION, error(limited));
        assertTrue(limited.message().startsWith("Too many requests"));
        assertTrue(ctx.fresh(lab).lockdown().active());
        ctx.now += 9_000L;
        assertTrue(actions.execute(owner, "lockdown", lockdown(false, "c")).ok());
    }

    @Test
    void ackOnlyOwnAlertsWithThisModulesPrefix() {
        String id = PushCopy.alertId(lab.id(), PushKind.LASER_TRIPPED);
        ctx.raiseAlert(owner, LinkAlert.raise(id, "nerosecurity", LinkAlert.Severity.CRITICAL, "t"));
        JsonObject foreign = new JsonObject();
        foreign.addProperty("eventId", "nerocolonies.x.y");
        assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(owner, "ack_event", foreign)));
        JsonObject p = new JsonObject();
        p.addProperty("eventId", id);
        assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(officer, "ack_event", p)),
                "someone else's alert is unknown to you");
        LinkActionResult ok = actions.execute(owner, "ack_event", p);
        assertTrue(ok.ok());
        assertTrue(ctx.alerts(owner).get(0).acked());
    }

    @Test
    void silenceIsBoundedOwnerOnlyAndAudited() {
        JsonObject p = new JsonObject();
        p.addProperty("installationId", lab.id().toString());
        p.addProperty("minutes", 0);
        assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(owner, "silence_alarm", p)));
        p.addProperty("minutes", 61);
        assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(owner, "silence_alarm", p)));
        p.addProperty("minutes", 1.5);
        assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(owner, "silence_alarm", p)));
        p.addProperty("minutes", 30);
        assertEquals(LinkActionResult.Error.NOT_OWNER, error(actions.execute(officer, "silence_alarm", p)));
        LinkActionResult ok = actions.execute(owner, "silence_alarm", p);
        assertTrue(ok.ok());
        assertEquals(ctx.now + 30 * 60_000L, ctx.fresh(lab).alarmSilencedUntil());
        assertEquals(ctx.now + 30 * 60_000L, ok.state().get("silencedUntil").getAsLong());
        assertEquals(SecurityEventType.ALARM_SILENCED, ctx.published.get(ctx.published.size() - 1).type());
        assertEquals(owner, ctx.published.get(ctx.published.size() - 1).actor());
    }

    @Test
    void perActionSwitchesAndModuleSwitch() {
        ctx.settings = ctx.settings.withSwitches(true, true, false, false, true);
        JsonObject p = new JsonObject();
        p.addProperty("eventId", "nerosecurity.x");
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "ack_event", p)));
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "silence_alarm", p)));
        ctx.settings = ctx.settings.withSwitches(false, true, true, true, true);
        assertEquals(LinkActionResult.Error.ACTION_DISABLED, error(actions.execute(owner, "ack_event", p)));
        assertEquals(LinkActionResult.Error.VALIDATION, error(actions.execute(owner, "dissolve", p)), "no such action");
    }

    @Test
    void rateLimiterAndDedupForgetAPlayer() {
        LinkRateLimiter limiter = LinkRateLimiter.standard();
        LinkRequestDedup dedup = new LinkRequestDedup();
        UUID p = UUID.randomUUID();
        assertEquals(0L, limiter.tryAcquire(p, "lockdown", 0L));
        assertTrue(limiter.tryAcquire(p, "lockdown", 1_000L) > 0L);
        dedup.remember(p, "lockdown", "r", LinkActionResult.ok(null), 0L);
        assertTrue(dedup.holdsAnyFor(p) && limiter.holdsAnyFor(p));
        assertEquals(null, dedup.lookup(p, "lockdown", "r", LinkRequestDedup.TTL_MS), "expires after the TTL");
        limiter.forget(p);
        dedup.forget(p);
        assertFalse(dedup.holdsAnyFor(p) || limiter.holdsAnyFor(p));
        for (int i = 0; i < 6; i++) {
            assertEquals(0L, limiter.tryAcquire(p, "lockdown", i * 10_000L));
        }
        assertTrue(limiter.tryAcquire(p, "lockdown", 60_000L) == 0L, "the oldest of six has aged out");
        assertTrue(limiter.tryAcquire(p, "lockdown", 70_000L) == 0L);
    }
}
