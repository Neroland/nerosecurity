package za.co.neroland.nerosecurity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerosecurity.audit.RetentionPolicy;
import za.co.neroland.nerosecurity.block.camera.CameraRules;

/**
 * Config compliance: retention defaults err short, the 90-day hard cap is a key with a loud comment that
 * nothing can exceed, every key except the two client-local ones is server-authoritative, keys are flat camelCase
 * (Core config has no sections), and the five per-feature kill switches exist, default on, server-authoritative.
 */
class ComplianceConfigTest {

    /** The only keys a client decides for itself: crash-report opt-out and its own animations. */
    private static final Set<String> CLIENT_LOCAL = Set.of("telemetryEnabled", "renderAnimationsEnabled");

    /** Spec name to the shipped key (existing keys are not renamed). */
    static final Map<String, String> KILL_SWITCHES = Map.of(
            "cameras.enabled", "camerasEnabled",
            "auditLog.enabled", "auditLogEnabled",
            "forceFields.enabled", "forceFieldsEnabled",
            "lockdown.enabled", "lockdownEnabled",
            "pushNotifications.enabled", "pushNotificationsEnabled");

    private static ConfigValue<?> key(String name) {
        ConfigValue<?> value = NeroSecurityConfig.SCHEMA.value(name);
        assertTrue(value != null, "missing config key " + name);
        return value;
    }

    @Test
    void retentionDefaultsErShort() {
        assertEquals("7", key("privacyAuditRetentionDays").defaultAsString());
        assertEquals("60", key("privacyCameraEventRetentionMinutes").defaultAsString());
        assertEquals("500", key("privacyAuditMaxEntriesPerInstallation").defaultAsString());
        assertEquals("90", key("privacyRetentionHardCapDays").defaultAsString());
        assertEquals("60", key("privacyPurgeIntervalMinutes").defaultAsString(), "the sweep runs hourly");
        assertEquals("false", key("privacyCameraLogsMembers").defaultAsString(), "members are not logged by default");
        assertEquals("60", key("privacyExportCooldownSeconds").defaultAsString());
    }

    @Test
    void theHardCapIsLoudAndCannotBeExceeded() {
        assertTrue(key("privacyRetentionHardCapDays").comment().contains("LOUD"), "the cap's comment is loud");
        assertTrue(key("privacyRetentionHardCapDays").comment().contains("90"));
        for (int requested = 1; requested <= 400; requested += 13) {
            for (int cap = 1; cap <= 400; cap += 29) {
                RetentionPolicy p = RetentionPolicy.of(requested, 60, 500, cap, null);
                assertTrue(p.auditDays() <= Math.min(cap, 90), "never above the cap or 90");
                assertTrue(p.auditDays() >= 1);
                if (requested <= Math.min(cap, 90)) {
                    assertEquals(requested, p.auditDays(), "an admin may lengthen up to the cap");
                }
            }
        }
        assertEquals(7, NeroSecurityConfig.privacyAuditRetentionDays(), "the default reads back as 7");
    }

    @Test
    void everyKeyIsServerAuthoritativeExceptTheTwoClientLocalOnes() {
        int checked = 0;
        for (ConfigValue<?> value : NeroSecurityConfig.SCHEMA.values()) {
            assertTrue(value.key().matches("[a-z][A-Za-z0-9]*"), "flat camelCase key: " + value.key());
            if (CLIENT_LOCAL.contains(value.key())) {
                assertFalse(value.serverAuthoritative(), value.key() + " is client-local");
            } else {
                assertTrue(value.serverAuthoritative(), value.key() + " must be serverAuthoritative");
            }
            checked++;
        }
        assertTrue(checked > 50, "the whole schema was walked");
    }

    @Test
    void killSwitchesExistDefaultOnAndAreServerAuthoritative() {
        KILL_SWITCHES.values().forEach(name -> {
            assertEquals("true", key(name).defaultAsString(), name);
            assertTrue(key(name).serverAuthoritative(), name);
        });
        assertTrue(NeroSecurityConfig.isCamerasEnabled() && NeroSecurityConfig.isAuditLogEnabled()
                && NeroSecurityConfig.isForceFieldsEnabled() && NeroSecurityConfig.isLockdownEnabled()
                && NeroSecurityConfig.isPushNotificationsEnabled(), "accessors read the defaults");
        assertFalse(CameraRules.armed(true, false, 1_000_000L, 0), "cameras off: no camera senses anything");
    }
}
