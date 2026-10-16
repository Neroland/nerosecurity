package za.co.neroland.nerosecurity.block.chest;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;

/**
 * The secure chest's settings rule. The rule is shared with the security door and lives in
 * {@link DeviceSettingsRules}; this delegate only keeps the chest-side name.
 */
public final class ChestSettingsRules {

    private ChestSettingsRules() {
    }

    /** See {@link DeviceSettingsRules#mayApply}. */
    public static boolean mayApply(boolean ownerOrAdmin, AccessLevel current, AccessLevel wanted) {
        return DeviceSettingsRules.mayApply(ownerOrAdmin, current, wanted);
    }
}
