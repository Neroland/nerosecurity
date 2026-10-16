package za.co.neroland.nerosecurity.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;

/** The retention hard cap wins over every setting. Plain JVM. */
class RetentionPolicyTest {

    /** 5. */
    @Test
    void hardCapWins() {
        assertEquals(90, RetentionPolicy.of(365, 60, 500, 90, null).auditDays(), "server setting above the cap");
        assertEquals(90, RetentionPolicy.of(7, 60, 500, 90, 120).auditDays(), "installation override above the cap");
        assertEquals(3, RetentionPolicy.of(7, 60, 500, 90, 3).auditDays(), "a shorter override applies");
        assertEquals(7, RetentionPolicy.of(7, 60, 500, 90, null).auditDays(), "the default");
        assertEquals(90, RetentionPolicy.of(365, 60, 500, 365, null).auditDays(), "the cap itself never exceeds 90");
        assertEquals(1, RetentionPolicy.of(7, 60, 500, 90, 0).auditDays(), "never below one day");
        for (int days = 1; days <= 120; days += 7) {
            RetentionPolicy p = RetentionPolicy.of(days, 1440, 500, 90, null);
            assertTrue(p.presenceMinutes() <= p.auditDays() * 1440, "presence never outlasts the audit retention");
            assertTrue(p.presenceMs() <= p.auditMs());
        }
        assertEquals(60, RetentionPolicy.of(7, 60, 500, 90, null).presenceMinutes());
    }

    @Test
    void configClampAndSchemaBound() {
        assertEquals(90, NeroSecurityConfig.MAX_RETENTION_DAYS);
        assertEquals(90, NeroSecurityConfig.clampRetentionDays(365, 90));
        assertEquals(30, NeroSecurityConfig.clampRetentionDays(60, 30));
        assertEquals(90, NeroSecurityConfig.clampRetentionDays(365, 500), "a cap above 90 still clamps to 90");
        assertEquals(1, NeroSecurityConfig.clampRetentionDays(0, 90));
        assertEquals("90", NeroSecurityConfig.SCHEMA.value("privacyRetentionHardCapDays").defaultAsString());
        assertEquals("7", NeroSecurityConfig.SCHEMA.value("privacyAuditRetentionDays").defaultAsString());
        assertEquals(7, NeroSecurityConfig.privacyAuditRetentionDays(), "default read");
        assertEquals(90, NeroSecurityConfig.privacyRetentionHardCapDays());
    }
}
