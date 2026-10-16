package za.co.neroland.nerosecurity.link;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig.LinkLockdownMode;

/**
 * One read of every config value the link module consults, taken per call so a {@code /neroland config
 * reload} applies at once. Tests build their own.
 */
record LinkSettings(boolean moduleEnabled, boolean pushEnabled, long cooldownMs, int deniedThreshold,
        long deniedWindowMs, LinkLockdownMode lockdownMode, boolean lockdownOffline, boolean ackEnabled,
        boolean silenceEnabled, boolean loginSummaryEnabled, boolean lockdownEnabled, boolean auditEnabled,
        int silenceMaxMinutes, int maxDevices) {

    static LinkSettings fromConfig() {
        return new LinkSettings(NeroSecurityConfig.isLinkModuleEnabled(), NeroSecurityConfig.isPushNotificationsEnabled(),
                NeroSecurityConfig.pushCooldownSeconds() * 1000L, NeroSecurityConfig.pushDeniedThreshold(),
                NeroSecurityConfig.pushDeniedWindowSeconds() * 1000L, NeroSecurityConfig.linkLockdownMode(),
                NeroSecurityConfig.isLinkLockdownAllowOffline(), NeroSecurityConfig.isLinkActionAckEnabled(),
                NeroSecurityConfig.isLinkActionSilenceEnabled(), NeroSecurityConfig.isLoginAlertSummaryEnabled(),
                NeroSecurityConfig.isLockdownEnabled(), NeroSecurityConfig.isAuditLogEnabled(),
                NeroSecurityConfig.alarmSilenceMaxMinutes(), NeroSecurityConfig.maxDevicesPerInstallation());
    }

    /** The shipped defaults (remote lockdown off). */
    static LinkSettings defaults() {
        return new LinkSettings(true, true, 60_000L, 3, 60_000L, LinkLockdownMode.OFF, false, true, true, true,
                true, true, 60, 256);
    }

    LinkSettings withLockdown(LinkLockdownMode mode, boolean offline) {
        return new LinkSettings(moduleEnabled, pushEnabled, cooldownMs, deniedThreshold, deniedWindowMs, mode, offline,
                ackEnabled, silenceEnabled, loginSummaryEnabled, lockdownEnabled, auditEnabled, silenceMaxMinutes,
                maxDevices);
    }

    LinkSettings withSwitches(boolean module, boolean push, boolean ack, boolean silence, boolean loginSummary) {
        return new LinkSettings(module, push, cooldownMs, deniedThreshold, deniedWindowMs, lockdownMode, lockdownOffline,
                ack, silence, loginSummary, lockdownEnabled, auditEnabled, silenceMaxMinutes, maxDevices);
    }
}
