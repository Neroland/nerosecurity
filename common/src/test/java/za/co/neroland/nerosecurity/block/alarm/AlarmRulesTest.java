package za.co.neroland.nerosecurity.block.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.alarm.AlarmDispatcher;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules.LockdownChange;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.lockdown.LockdownRules;
import za.co.neroland.nerosecurity.lockdown.LockdownRules.Verdict;

/** The alarm's pure rules: defaults, arming, silence bounds, and the alarm-lockdown gating matrix. Plain JVM. */
class AlarmRulesTest {

    private static final long MIN = 60_000L;
    private static final long NOW = 100 * 24 * 60 * MIN;
    private static final long COOLDOWN = 10 * MIN;

    @Test
    void defaultsListenToEverythingAndNeverLockDown() {
        assertEquals(EnumSet.of(SecurityEventType.LASER_TRIPPED, SecurityEventType.MOTION_DETECTED,
                SecurityEventType.DOOR_FORCED, SecurityEventType.CHEST_FORCED, SecurityEventType.DEVICE_FORCED,
                SecurityEventType.FIELD_OVERLOADED, SecurityEventType.FIELD_BREACH_ATTEMPT,
                SecurityEventType.DOOR_DENIED, SecurityEventType.CHEST_DENIED, SecurityEventType.KEYCARD_REJECTED,
                SecurityEventType.POWER_LOST), AlarmRules.defaultSubscribed());
        assertEquals(EnumSet.of(AlarmResponse.REDSTONE, AlarmResponse.SOUND, AlarmResponse.HIGHLIGHT,
                AlarmResponse.CHAT), AlarmRules.defaultResponses());
        assertFalse(AlarmRules.attemptsLockdown(AlarmRules.defaultResponses()), "lockdown is opt-in");
    }

    @Test
    void dispatcherIgnoresBookkeepingAndMemberSightings() {
        for (SecurityEventType type : List.of(SecurityEventType.ALARM_TRIGGERED, SecurityEventType.ALARM_SILENCED,
                SecurityEventType.ALARM_LOCKDOWN_SUPPRESSED, SecurityEventType.LOCKDOWN_ENTERED,
                SecurityEventType.LOCKDOWN_EXITED, SecurityEventType.LOCKDOWN_AUTO_RELEASED,
                SecurityEventType.LOCKDOWN_CONFIRMED, SecurityEventType.ADMIN_UNLOCK, SecurityEventType.LOG_READ,
                SecurityEventType.INSTALLATION_CREATED, SecurityEventType.MEMBER_ADDED,
                SecurityEventType.LEVEL_CHANGED, SecurityEventType.DEVICE_LINKED, SecurityEventType.DEVICE_CONFIGURED,
                SecurityEventType.AUTOMATION_POLICY_CHANGED, SecurityEventType.KEYCARD_WRITTEN,
                SecurityEventType.CAMERA_MEMBER_SEEN, SecurityEventType.DOOR_OPENED, SecurityEventType.CHEST_OPENED,
                SecurityEventType.FIELD_COLLAPSED, SecurityEventType.FIELD_RESTORED, SecurityEventType.FACTION_BOUND,
                SecurityEventType.FACTION_UNBOUND, SecurityEventType.FACTION_SUSPENDED, SecurityEventType.COLONY_BOUND,
                SecurityEventType.COLONY_UNBOUND, SecurityEventType.COLONY_SUSPENDED, SecurityEventType.STATION_BOUND,
                SecurityEventType.STATION_UNBOUND)) {
            assertFalse(AlarmDispatcher.feeds(type), type + " must never reach an alarm");
        }
        assertTrue(AlarmDispatcher.feeds(SecurityEventType.LASER_TRIPPED));
        assertTrue(AlarmDispatcher.feeds(SecurityEventType.DEVICE_FORCED));
        assertTrue(AlarmDispatcher.feeds(SecurityEventType.FIELD_OVERLOADED));
        assertTrue(AlarmDispatcher.feeds(SecurityEventType.FIELD_BREACH_ATTEMPT));
        assertTrue(AlarmRules.BREACHES.contains(SecurityEventType.FIELD_OVERLOADED), "field alarms are breaches");
        assertTrue(AlarmRules.BREACHES.contains(SecurityEventType.FIELD_BREACH_ATTEMPT), "field alarms are breaches");
    }

    @Test
    void lockdownOverridesSilenceAndUnlinkedNeverFires() {
        assertTrue(AlarmRules.armed(true, NOW, 0L, true, false));
        assertFalse(AlarmRules.armed(true, NOW, NOW + MIN, true, false), "silenced");
        assertTrue(AlarmRules.armed(true, NOW, NOW + MIN, true, true), "a lockdown overrides a silence");
        assertFalse(AlarmRules.armed(true, NOW, NOW + MIN, false, true), "unless lockdown is off on the server");
        assertFalse(AlarmRules.armed(false, NOW, 0L, true, true), "an unlinked alarm reacts to nothing");
    }

    @Test
    void silenceIsClampedAndSaturates() {
        assertEquals(10, AlarmRules.clampSilenceMinutes(10, 60));
        assertEquals(60, AlarmRules.clampSilenceMinutes(100_000, 60));
        assertEquals(1, AlarmRules.clampSilenceMinutes(-5, 60));
        assertEquals(NOW + 10 * MIN, AlarmRules.silencedUntil(NOW, 10));
        assertEquals(Long.MAX_VALUE, AlarmRules.silencedUntil(Long.MAX_VALUE - 5, 10));
        assertEquals(600, AlarmRules.silencedForSeconds(NOW, NOW + 10 * MIN));
        assertEquals(0, AlarmRules.silencedForSeconds(NOW, NOW - 1));
    }

    @Test
    void lockdownResponseIsOwnerGatedBothWays() {
        Set<AlarmResponse> off = AlarmRules.defaultResponses();
        Set<AlarmResponse> on = EnumSet.copyOf(off);
        on.add(AlarmResponse.LOCKDOWN);
        assertEquals(LockdownChange.UNCHANGED, AlarmRules.lockdownChange(off, off, false, true));
        assertEquals(LockdownChange.UNCHANGED, AlarmRules.lockdownChange(on, on, false, false),
                "an officer may still edit other settings of an owner-armed alarm");
        assertEquals(LockdownChange.OWNER_ONLY, AlarmRules.lockdownChange(off, on, false, true), "officer arming");
        assertEquals(LockdownChange.OWNER_ONLY, AlarmRules.lockdownChange(on, off, false, true), "officer disarming");
        assertEquals(LockdownChange.ALLOWED, AlarmRules.lockdownChange(off, on, true, true));
        assertEquals(LockdownChange.ALLOWED, AlarmRules.lockdownChange(on, off, true, false),
                "an owner can always switch it off");
        assertEquals(LockdownChange.SERVER_DISABLED, AlarmRules.lockdownChange(off, on, true, false));
    }

    private record Case(String name, boolean responseOn, boolean lockdownEnabled, boolean alarmLockdownEnabled,
            SecuritySeverity severity, Lockdown current, long lastEndedAt, boolean enters) {
    }

    /**
     * The whole gate an alarm firing passes before it can lock down: this alarm's opt-in (the block
     * entity's check) and then the service's guards (the same {@link LockdownRules} the service applies).
     */
    @Test
    void lockdownGatingMatrix() {
        Lockdown none = Lockdown.NONE;
        Lockdown active = Lockdown.alarm(NOW - MIN, NOW + 4 * MIN);
        List<Case> cases = List.of(
                new Case("all gates open", true, true, true, SecuritySeverity.CRITICAL, none, 0L, true),
                new Case("alarm not opted in", false, true, true, SecuritySeverity.CRITICAL, none, 0L, false),
                new Case("server alarm-lockdown off", true, true, false, SecuritySeverity.CRITICAL, none, 0L, false),
                new Case("server lockdown off", true, false, true, SecuritySeverity.CRITICAL, none, 0L, false),
                new Case("below severity", true, true, true, SecuritySeverity.WARN, none, 0L, false),
                new Case("inside cooldown", true, true, true, SecuritySeverity.CRITICAL, none, NOW - COOLDOWN + 1, false),
                new Case("cooldown elapsed", true, true, true, SecuritySeverity.CRITICAL, none, NOW - COOLDOWN, true),
                new Case("never extends an active lockdown", true, true, true, SecuritySeverity.CRITICAL, active, 0L,
                        false));
        for (Case c : cases) {
            Set<AlarmResponse> responses = EnumSet.copyOf(AlarmRules.defaultResponses());
            if (c.responseOn()) {
                responses.add(AlarmResponse.LOCKDOWN);
            }
            boolean enters = AlarmRules.attemptsLockdown(responses)
                    && LockdownRules.alarmEntry(c.current(), c.lastEndedAt(), NOW, c.severity(), SecuritySeverity.CRITICAL,
                            c.lockdownEnabled(), c.alarmLockdownEnabled(), COOLDOWN) == Verdict.ENTER;
            assertEquals(c.enters(), enters, c.name());
        }
    }

    @Test
    void storedNamesRoundTripAndJunkDrops() {
        assertEquals(EnumSet.of(SecurityEventType.LASER_TRIPPED),
                AlarmRules.typesFromNames(List.of("laser_tripped", "nonsense", "door_opened", "log_read")),
                "unknown and non-subscribable names are dropped");
        assertEquals(EnumSet.of(AlarmResponse.CHAT, AlarmResponse.LOCKDOWN),
                AlarmRules.responsesFromNames(List.of("chat", "lockdown", "LOCKDOWN", "explode")));
        for (SecurityEventType type : AlarmRules.subscribable()) {
            assertEquals(type, AlarmRules.typeByKey(AlarmRules.typeKey(type)));
        }
        for (AlarmResponse response : AlarmResponse.values()) {
            assertEquals(response, AlarmResponse.byKey(response.key()).orElseThrow());
        }
    }
}
