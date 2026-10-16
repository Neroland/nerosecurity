package za.co.neroland.nerosecurity.audit;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;

/**
 * How long one installation's audit rows live. The hard cap (never above 90 days) wins over the
 * server setting and over any per-installation override; presence rows (motion, member seen) expire on
 * a shorter clock that can never outlast the audit retention.
 *
 * @param auditDays       effective audit retention in days, 1..hardCapDays
 * @param presenceMinutes effective presence retention in minutes, at most auditDays x 1440
 * @param maxEntries      ring buffer size
 * @param hardCapDays     the ceiling that produced this policy
 */
public record RetentionPolicy(int auditDays, int presenceMinutes, int maxEntries, int hardCapDays) {

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 86_400_000L;

    /** The policy for an installation, from the server config and its optional override (days). */
    public static RetentionPolicy fromConfig(@Nullable Integer installationOverrideDays) {
        return of(NeroSecurityConfig.privacyAuditRetentionDays(), NeroSecurityConfig.privacyCameraEventRetentionMinutes(),
                NeroSecurityConfig.privacyAuditMaxEntriesPerInstallation(), NeroSecurityConfig.privacyRetentionHardCapDays(),
                installationOverrideDays);
    }

    /**
     * The pure rule: {@code effectiveDays = min(hardCap, max(1, override ?? auditDays))} with the cap
     * itself clamped to 1..90, and {@code presence = min(presenceMinutes, effectiveDays x 1440)}.
     */
    public static RetentionPolicy of(int auditDays, int presenceMinutes, int maxEntries, int hardCapDays,
            @Nullable Integer installationOverrideDays) {
        int cap = Math.max(1, Math.min(NeroSecurityConfig.MAX_RETENTION_DAYS, hardCapDays));
        int requested = installationOverrideDays != null ? installationOverrideDays : auditDays;
        int days = Math.min(cap, Math.max(1, requested));
        int presence = (int) Math.min(Math.max(1, presenceMinutes), days * 1440L);
        return new RetentionPolicy(days, presence, Math.max(1, maxEntries), cap);
    }

    public long auditMs() {
        return auditDays * DAY_MS;
    }

    public long presenceMs() {
        return presenceMinutes * MINUTE_MS;
    }
}
