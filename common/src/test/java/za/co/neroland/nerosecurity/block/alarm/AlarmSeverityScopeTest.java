package za.co.neroland.nerosecurity.block.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.alarm.AlarmCoalescer;
import za.co.neroland.nerosecurity.alarm.AlarmFiring;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules.LockdownChange;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.lockdown.LockdownRules;
import za.co.neroland.nerosecurity.lockdown.LockdownRules.Verdict;

/**
 * An alarm acts on its own view of a coalesced firing: severity and type over the window's types it
 * subscribes to, never over the whole installation window; and an alarm that can lock down is owner-only
 * to edit. Plain JVM.
 */
class AlarmSeverityScopeTest {

    private static final long WINDOW = 10_000L;
    private static final long T0 = 1_000_000L;
    private static final long COOLDOWN = 600_000L;

    /** A WARN power loss opens the window, then an unsubscribed CRITICAL break lands inside it. */
    private static AlarmFiring powerLossThenForcedBreak() {
        AlarmCoalescer c = new AlarmCoalescer(() -> WINDOW, 16);
        UUID id = UUID.randomUUID();
        assertNotNull(c.offer(id, SecurityEventType.POWER_LOST, SecuritySeverity.WARN, T0));
        assertNull(c.offer(id, SecurityEventType.DEVICE_FORCED, SecuritySeverity.CRITICAL, T0 + 1_000L));
        List<AlarmCoalescer.Emit> drained = c.drain(T0 + WINDOW);
        assertEquals(1, drained.size());
        return drained.get(0).firing();
    }

    private static Verdict gate(AlarmFiring mine) {
        return LockdownRules.alarmEntry(Lockdown.NONE, 0L, T0 + WINDOW, mine.severity(), SecuritySeverity.CRITICAL,
                true, true, COOLDOWN);
    }

    @Test
    void unsubscribedCriticalNeverLocksDown() {
        AlarmFiring window = powerLossThenForcedBreak();
        assertEquals(SecuritySeverity.CRITICAL, window.severity(), "the installation window is CRITICAL (chat keeps it)");
        assertEquals(SecurityEventType.DEVICE_FORCED, window.type());

        // An alarm subscribed only to POWER_LOST, with LOCKDOWN on.
        Set<AlarmResponse> responses = EnumSet.of(AlarmResponse.LOCKDOWN);
        assertTrue(AlarmRules.attemptsLockdown(responses));
        AlarmFiring mine = AlarmRules.effective(window, Set.of(SecurityEventType.POWER_LOST));
        assertNotNull(mine, "the alarm still reacts to the power loss");
        assertEquals(SecurityEventType.POWER_LOST, mine.type());
        assertEquals(SecuritySeverity.WARN, mine.severity());
        assertEquals(Set.of(SecurityEventType.POWER_LOST), mine.types());
        assertEquals(Verdict.BELOW_SEVERITY, gate(mine), "an unsubscribed CRITICAL must not push it over the gate");

        // An alarm that does subscribe to breaks locks down on the same window.
        AlarmFiring breaches = AlarmRules.effective(window,
                Set.of(SecurityEventType.POWER_LOST, SecurityEventType.DEVICE_FORCED));
        assertNotNull(breaches);
        assertEquals(SecurityEventType.DEVICE_FORCED, breaches.type());
        assertEquals(Verdict.ENTER, gate(breaches));

        // An alarm subscribed to neither does not react at all.
        assertNull(AlarmRules.effective(window, Set.of(SecurityEventType.DOOR_DENIED)));
        assertFalse(window.matches(Set.of(SecurityEventType.DOOR_DENIED)));
    }

    @Test
    void perTypeSeverityIsKeptAndLeadingEdgeIsUnchanged() {
        AlarmFiring single = AlarmFiring.single(SecurityEventType.LASER_TRIPPED, SecuritySeverity.CRITICAL);
        AlarmFiring mine = AlarmRules.effective(single, Set.of(SecurityEventType.LASER_TRIPPED));
        assertNotNull(mine);
        assertEquals(SecuritySeverity.CRITICAL, mine.severity());
        assertEquals(SecuritySeverity.WARN, powerLossThenForcedBreak().severityOf(SecurityEventType.POWER_LOST));
        assertNull(AlarmRules.effective(single, Set.of()));
    }

    @Test
    void lockdownArmedAlarmIsOwnerOnlyToEdit() {
        Set<SecurityEventType> types = EnumSet.of(SecurityEventType.LASER_TRIPPED);
        Set<SecurityEventType> wider = EnumSet.of(SecurityEventType.LASER_TRIPPED, SecurityEventType.DOOR_DENIED);
        Set<AlarmResponse> armed = EnumSet.of(AlarmResponse.SOUND, AlarmResponse.LOCKDOWN);
        Set<AlarmResponse> armedQuiet = EnumSet.of(AlarmResponse.LOCKDOWN);
        Set<AlarmResponse> plain = EnumSet.of(AlarmResponse.SOUND);

        // An officer (not an owner) cannot change what a lockdown alarm listens to, or its other responses.
        assertTrue(AlarmRules.editNeedsOwner(types, armed, wider, armed));
        assertEquals(LockdownChange.LOCKDOWN_ARMED_OWNER_ONLY,
                AlarmRules.configureChange(types, armed, wider, armed, false, true));
        assertEquals(LockdownChange.LOCKDOWN_ARMED_OWNER_ONLY,
                AlarmRules.configureChange(types, armed, types, armedQuiet, false, true));
        // Even with the server's alarm lockdown switched off, the stored flag still guards the alarm.
        assertEquals(LockdownChange.LOCKDOWN_ARMED_OWNER_ONLY,
                AlarmRules.configureChange(types, armed, wider, armed, false, false));
        // The owner may.
        assertEquals(LockdownChange.UNCHANGED, AlarmRules.configureChange(types, armed, wider, armed, true, true));
        // Switching the flag itself stays OWNER_ONLY for an officer.
        assertEquals(LockdownChange.OWNER_ONLY, AlarmRules.configureChange(types, armed, types, plain, false, true));
        // A no-op resend needs nothing.
        assertFalse(AlarmRules.editNeedsOwner(types, armed, types, armed));
        assertEquals(LockdownChange.UNCHANGED, AlarmRules.configureChange(types, armed, types, armed, false, true));
        // Without the lockdown response, officers edit freely.
        assertFalse(AlarmRules.editNeedsOwner(types, plain, wider, plain));
        assertEquals(LockdownChange.UNCHANGED, AlarmRules.configureChange(types, plain, wider, plain, false, true));
    }
}
