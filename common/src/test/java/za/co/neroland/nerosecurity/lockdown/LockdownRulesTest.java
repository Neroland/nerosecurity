package za.co.neroland.nerosecurity.lockdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.lockdown.LockdownRules.Verdict;
import za.co.neroland.nerosecurity.sensor.SensorPermission;
import za.co.neroland.nerosecurity.sensor.SensorSettings;

/** The lockdown state machine's guards and the owner-is-never-locked-out guarantee. Plain JVM. */
class LockdownRulesTest {

    private static final long MIN = 60_000L;
    private static final long NOW = 100 * 24 * 60 * MIN;
    private static final long COOLDOWN = 10 * MIN;
    private static final DevicePos HUB = new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(0, 64, 0));

    private record Row(String name, Lockdown current, long lastEndedAt, SecuritySeverity severity, SecuritySeverity minimum,
            boolean lockdownEnabled, boolean alarmLockdownEnabled, Verdict expected) {
    }

    /** 20. Every row of the transition table that an alarm can hit. */
    @Test
    void transitionTable() {
        Lockdown manual = Lockdown.manual(NOW - MIN, true);
        Lockdown alarm = Lockdown.alarm(NOW - MIN, NOW + 4 * MIN);
        List<Row> rows = List.of(
                new Row("normal, critical, never locked", Lockdown.NONE, 0L, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, true, true, Verdict.ENTER),
                new Row("normal, cooldown elapsed", Lockdown.NONE, NOW - COOLDOWN, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, true, true, Verdict.ENTER),
                new Row("normal, inside cooldown", Lockdown.NONE, NOW - COOLDOWN + 1, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, true, true, Verdict.COOLDOWN),
                new Row("warn below critical gate", Lockdown.NONE, 0L, SecuritySeverity.WARN,
                        SecuritySeverity.CRITICAL, true, true, Verdict.BELOW_SEVERITY),
                new Row("warn at warn gate", Lockdown.NONE, 0L, SecuritySeverity.WARN,
                        SecuritySeverity.WARN, true, true, Verdict.ENTER),
                new Row("info never", Lockdown.NONE, 0L, SecuritySeverity.INFO,
                        SecuritySeverity.WARN, true, true, Verdict.BELOW_SEVERITY),
                new Row("manual lockdown in force", manual, 0L, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, true, true, Verdict.ALREADY_ACTIVE),
                new Row("alarm lockdown in force", alarm, 0L, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, true, true, Verdict.ALREADY_ACTIVE),
                new Row("lockdown switched off", Lockdown.NONE, 0L, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, false, true, Verdict.DISABLED),
                new Row("alarm lockdown switched off", Lockdown.NONE, 0L, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, true, false, Verdict.DISABLED),
                new Row("switched off beats in force", alarm, 0L, SecuritySeverity.CRITICAL,
                        SecuritySeverity.CRITICAL, false, true, Verdict.DISABLED));
        for (Row row : rows) {
            assertEquals(row.expected(), LockdownRules.alarmEntry(row.current(), row.lastEndedAt(), NOW, row.severity(),
                    row.minimum(), row.lockdownEnabled(), row.alarmLockdownEnabled(), COOLDOWN), row.name());
        }
    }

    /** 21. */
    @Test
    void alarmLockdownNeverExtendsAndAutoReleases() {
        long releaseAt = NOW + 5 * MIN;
        Lockdown alarm = Lockdown.alarm(NOW, releaseAt);
        assertTrue(alarm.isAlarm());
        assertFalse(alarm.enteredByOwner(), "an alarm is not an owner");
        assertFalse(LockdownRules.dueForAutoRelease(alarm, releaseAt - 1));
        assertTrue(LockdownRules.dueForAutoRelease(alarm, releaseAt));
        assertEquals(Verdict.ALREADY_ACTIVE, LockdownRules.alarmEntry(alarm, 0L, NOW + MIN, SecuritySeverity.CRITICAL,
                SecuritySeverity.CRITICAL, true, true, COOLDOWN));
        assertEquals(releaseAt, alarm.autoReleaseAt(), "a re-trip changes nothing");

        Lockdown confirmed = alarm.confirmed();
        assertEquals(LockdownSource.MANUAL, confirmed.source());
        assertEquals(0L, confirmed.autoReleaseAt());
        assertTrue(confirmed.active());
        assertEquals(alarm.since(), confirmed.since());
        assertFalse(LockdownRules.dueForAutoRelease(confirmed, Long.MAX_VALUE), "a confirmed lockdown never clears itself");

        assertFalse(LockdownRules.dueForAutoRelease(Lockdown.manual(NOW, true), Long.MAX_VALUE));
        assertFalse(LockdownRules.dueForAutoRelease(Lockdown.NONE, Long.MAX_VALUE));
        assertEquals(0L, new Lockdown(false, NOW, false, LockdownSource.ALARM, releaseAt).autoReleaseAt(),
                "an inactive lockdown has no release time");
        assertTrue(LockdownRules.dueForAutoRelease(new Lockdown(true, NOW, false, LockdownSource.ALARM, 0L), NOW),
                "an alarm lockdown with a missing release time is due at once (always time-boxed)");
    }

    /** 22. */
    @Test
    void drillOnlyForOwnerManual() {
        assertTrue(LockdownRules.countsForDrill(Lockdown.manual(NOW, true), true));
        assertFalse(LockdownRules.countsForDrill(Lockdown.alarm(NOW, NOW + MIN), true));
        assertFalse(LockdownRules.countsForDrill(Lockdown.manual(NOW, false), true),
                "entered by an officer, cleared by an owner");
        assertFalse(LockdownRules.countsForDrill(Lockdown.manual(NOW, true), false));
    }

    /** 23. */
    @Test
    void memberCannotLockOutOwner() {
        UUID owner = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Vault", HUB, 0L, 8).installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE), AdminCheck.NONE, () -> true);
        DevicePos device = new DevicePos(HUB.dimension(), new BlockPos(2, 64, 0));

        // Before any lockdown, a member is permitted at a default sensor, so cannot produce the trip that starts one,
        // cannot enter one manually, and cannot arm an alarm's lockdown response.
        assertEquals(SensorPermission.Classification.PERMITTED, SensorPermission.classify(resolver, null, installation,
                device, SensorPermission.Sensed.player(member), SensorSettings.DEFAULT));
        AccessDecision toggle = resolver.resolve(new AccessQuery(null, member, installation, AccessAction.LOCKDOWN_TOGGLE,
                null), AccessAction.LOCKDOWN_TOGGLE.defaultMinimum);
        assertFalse(toggle.allowed());
        assertEquals(DenyReason.INSUFFICIENT_LEVEL, toggle.reason());

        store.setLockdown(installation.id(), Lockdown.alarm(NOW, NOW + 5 * MIN));
        for (AccessLevel level : AccessLevel.values()) {
            AccessDecision ownerOpen = resolver.resolve(new AccessQuery(null, owner, installation, AccessAction.OPEN, device),
                    level);
            assertTrue(ownerOpen.allowed(), "the owner opens a " + level + " device during an alarm lockdown");
        }
        AccessDecision officerOpen = resolver.resolve(new AccessQuery(null, officer, installation, AccessAction.OPEN, device),
                AccessLevel.MEMBER);
        assertFalse(officerOpen.allowed());
        assertEquals(DenyReason.LOCKDOWN, officerOpen.reason());

        AccessDecision configure = resolver.resolve(new AccessQuery(null, officer, installation,
                AccessAction.CONFIGURE_ALARM_LOCKDOWN, device), AccessAction.CONFIGURE_ALARM_LOCKDOWN.defaultMinimum);
        assertFalse(configure.allowed());
        assertEquals(DenyReason.INSUFFICIENT_LEVEL, configure.reason());
        AccessDecision memberToggle = resolver.resolve(new AccessQuery(null, member, installation,
                AccessAction.LOCKDOWN_TOGGLE, null), AccessAction.LOCKDOWN_TOGGLE.defaultMinimum);
        assertEquals(DenyReason.INSUFFICIENT_LEVEL, memberToggle.reason());
        AccessDecision ownerToggle = resolver.resolve(new AccessQuery(null, owner, installation,
                AccessAction.LOCKDOWN_TOGGLE, null), AccessAction.LOCKDOWN_TOGGLE.defaultMinimum);
        assertTrue(ownerToggle.allowed(), "the owner can always confirm or clear an alarm lockdown");
    }

    @Test
    void storePersistsAlarmStateAndSchedulesRelease() {
        UUID owner = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "A", HUB, 0L, 8).installation();
        store.setLockdown(installation.id(), Lockdown.alarm(NOW, NOW + 5 * MIN));
        store.setLastAlarmLockdownEndedAt(installation.id(), NOW - COOLDOWN * 2);
        assertTrue(store.dueForAutoRelease(NOW).isEmpty());
        assertEquals(List.of(installation.id()), store.dueForAutoRelease(NOW + 5 * MIN).stream().map(Installation::id).toList());

        JsonElement json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        SecurityInstallations back = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        Installation loaded = back.byId(installation.id()).orElseThrow();
        assertEquals(installation.lockdown(), loaded.lockdown());
        assertEquals(NOW - COOLDOWN * 2, loaded.lastAlarmLockdownEndedAt());
        assertEquals(1, back.dueForAutoRelease(NOW + 5 * MIN).size(), "the release schedule is rebuilt on load");

        back.setLockdown(installation.id(), loaded.lockdown().confirmed());
        assertTrue(back.dueForAutoRelease(Long.MAX_VALUE).isEmpty(), "a confirmed lockdown leaves the schedule");
    }

    @Test
    void autoReleaseFallsOnTheChannelAndConfirmIsNotATransition() {
        UUID id = UUID.randomUUID();
        var released = LockdownChannel.crossingFor(za.co.neroland.nerosecurity.event.SecurityEvent.of(
                za.co.neroland.nerosecurity.event.SecurityEventType.LOCKDOWN_AUTO_RELEASED, id, null, null, NOW));
        assertTrue(released.isPresent());
        assertFalse(released.get().rising());
        assertTrue(LockdownChannel.crossingFor(za.co.neroland.nerosecurity.event.SecurityEvent.of(
                za.co.neroland.nerosecurity.event.SecurityEventType.LOCKDOWN_CONFIRMED, id, null, null, NOW)).isEmpty());
    }

    @Test
    void junkLockdownRowsLoadSafely() {
        UUID id = UUID.randomUUID();
        String json = "{\"schema_version\":1,\"installations\":[{\"id\":\"" + id + "\",\"owners\":[\""
                + UUID.randomUUID() + "\"],\"hub\":{\"dim\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0},"
                + "\"lockdown\":{\"active\":false,\"since\":5,\"source\":\"alarm\",\"auto_release_at\":99},"
                + "\"last_alarm_lockdown_ended_at\":-4}]}";
        SecurityInstallations store = SecurityInstallations.CODEC
                .parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json)).getOrThrow();
        Installation loaded = store.byId(id).orElseThrow();
        assertEquals(0L, loaded.lockdown().autoReleaseAt(), "auto_release_at on an inactive lockdown reads as 0");
        assertEquals(0L, loaded.lastAlarmLockdownEndedAt());
        assertEquals(LockdownSource.MANUAL, LockdownSource.byName("junk"));
    }
}
