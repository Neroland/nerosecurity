package za.co.neroland.nerosecurity.block;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The few world-visible facts the renderers show, derived the same way everywhere. Everything here is
 * about the device, never about a person: whether its installation is in lockdown, and whether it is
 * recording (the in-world notice that audit logging is on).
 *
 * <p>Devices without a ticker of their own (hub, monitor, chest) re-derive these on a staggered
 * {@link #REFRESH_TICKS} cadence, so a dissolved installation, an unlink the device did not hear about, or a
 * config reload still reaches the model within two seconds. The lockdown hook is only an accelerator.
 */
public final class VisualState {

    /** How often an untickered device re-derives its visual state, in ticks. */
    public static final int REFRESH_TICKS = 40;

    private VisualState() {
    }

    /**
     * Whether a device at {@code posKey} (a {@code BlockPos.asLong()}) refreshes on {@code gameTime}: once every
     * {@link #REFRESH_TICKS}, phase-shifted by position so a base full of devices does not refresh on one tick.
     */
    public static boolean refreshDue(long gameTime, long posKey) {
        long mixed = posKey ^ (posKey >>> 33); // murmur3 fmix64: every coordinate bit reaches the phase
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        mixed *= 0xC4CEB9FE1A85EC53L;
        mixed ^= mixed >>> 33;
        return Math.floorMod(gameTime + Math.floorMod(mixed, REFRESH_TICKS), REFRESH_TICKS) == 0;
    }

    /** The recording notice: linked and the audit log is on. */
    public static boolean recording(boolean linked, boolean auditLogEnabled) {
        return linked && auditLogEnabled;
    }

    /** Whether the lockdown look is shown: linked, lockdown enabled on this server, and in force. */
    public static boolean lockdownShown(boolean linked, boolean lockdownEnabled, boolean lockdownActive) {
        return linked && lockdownEnabled && lockdownActive;
    }

    /** {@link #recording(boolean, boolean)} for {@code installation} under the live config. */
    public static boolean recording(@Nullable Installation installation) {
        return recording(installation != null, NeroSecurityConfig.isAuditLogEnabled());
    }

    /** {@link #lockdownShown(boolean, boolean, boolean)} for {@code installation} under the live config. */
    public static boolean lockdownShown(@Nullable Installation installation) {
        return installation != null && lockdownShown(true, NeroSecurityConfig.isLockdownEnabled(),
                installation.lockdown().active());
    }
}
