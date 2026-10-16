package za.co.neroland.nerosecurity.block;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * Pure post-resolution rules shared by every device settings screen (doors and secure chests). The
 * resolver has already allowed CONFIGURE_DEVICE (OFFICER, never keycard filtered); these only refine
 * what that actor may change. Plain-JVM tested.
 */
public final class DeviceSettingsRules {

    private DeviceSettingsRules() {
    }

    /**
     * Whether an actor may change a device whose open level is {@code current} to {@code wanted}. Only
     * an owner (or an operator, reported as OWNER) may set a device to OWNER, and only an owner may
     * change anything at all on a device that is already OWNER (its level, keycard-only, a door's
     * redstone mode): otherwise an officer could lower an owner's device and then open it.
     *
     * @param ownerOrAdmin whether the resolver reported OWNER or an operator override for the actor
     */
    public static boolean mayApply(boolean ownerOrAdmin, AccessLevel current, AccessLevel wanted) {
        if (ownerOrAdmin) {
            return true;
        }
        return current != AccessLevel.OWNER && wanted != AccessLevel.OWNER;
    }
}
