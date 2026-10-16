package za.co.neroland.nerosecurity.lockdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;

/**
 * The lockdown threshold channel: one crossing per transition, opaque scope, gated by both flags.
 * Core's threshold bus has no unsubscribe, so one collecting listener is registered for the class and
 * filtered to this channel.
 */
class LockdownChannelTest {

    private static final List<ThresholdEvents.SourcedCrossing> SEEN = new CopyOnWriteArrayList<>();

    private final AtomicBoolean channelEnabled = new AtomicBoolean(true);
    private final AtomicBoolean lockdownEnabled = new AtomicBoolean(true);
    private SecurityEventBus.Subscription subscription;

    @BeforeAll
    static void listen() {
        ThresholdEvents.onSourcedCrossing(crossing -> {
            if (LockdownChannel.CHANNEL.equals(crossing.crossing().channel())) {
                SEEN.add(crossing);
            }
        });
    }

    @BeforeEach
    void subscribe() {
        SEEN.clear();
        subscription = SecurityEventBus.subscribe(LockdownChannel.listener(channelEnabled::get, lockdownEnabled::get));
    }

    @AfterEach
    void unsubscribe() {
        subscription.close();
    }

    private static void publish(SecurityEventType type, UUID installation, UUID actor) {
        SecurityEventBus.publish(SecurityEvent.of(type, installation, null, actor, 0L));
    }

    @Test
    void risingOnEnterFallingOnExitAndAdminUnlock() {
        UUID installation = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        publish(SecurityEventType.LOCKDOWN_ENTERED, installation, actor);
        publish(SecurityEventType.LOCKDOWN_EXITED, installation, actor);
        publish(SecurityEventType.ADMIN_UNLOCK, installation, actor);
        publish(SecurityEventType.MEMBER_ADDED, installation, actor);
        assertEquals(3, SEEN.size(), "one crossing per lockdown transition, nothing for other events");

        ThresholdEvents.ThresholdCrossing entered = SEEN.get(0).crossing();
        assertTrue(entered.rising());
        assertEquals(1L, entered.value());
        assertEquals(1L, entered.threshold());
        assertFalse(SEEN.get(1).crossing().rising());
        assertEquals(0L, SEEN.get(1).crossing().value());
        assertFalse(SEEN.get(2).crossing().rising());
        for (ThresholdEvents.SourcedCrossing crossing : SEEN) {
            assertEquals("nerosecurity", crossing.sourceModId());
            assertEquals("inst:" + installation, crossing.crossing().scope());
            assertFalse(crossing.crossing().scope().contains(actor.toString()), "the scope never names a player");
        }
    }

    @Test
    void nothingFiresWhenTheChannelIsOff() {
        channelEnabled.set(false);
        publish(SecurityEventType.LOCKDOWN_ENTERED, UUID.randomUUID(), null);
        assertTrue(SEEN.isEmpty());
    }

    @Test
    void nothingFiresWhenLockdownIsOff() {
        lockdownEnabled.set(false);
        publish(SecurityEventType.LOCKDOWN_ENTERED, UUID.randomUUID(), null);
        assertTrue(SEEN.isEmpty());
    }
}
