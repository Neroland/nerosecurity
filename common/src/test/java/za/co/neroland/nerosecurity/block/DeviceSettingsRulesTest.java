package za.co.neroland.nerosecurity.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.chest.ChestSettingsRules;

/** The settings rule shared by doors and secure chests. Plain JVM. */
class DeviceSettingsRulesTest {

    @Test
    void onlyAnOwnerTouchesAnOwnerDevice() {
        for (AccessLevel current : AccessLevel.values()) {
            for (AccessLevel wanted : AccessLevel.values()) {
                assertTrue(DeviceSettingsRules.mayApply(true, current, wanted), "an owner may set anything");
                boolean expected = current != AccessLevel.OWNER && wanted != AccessLevel.OWNER;
                assertEquals(expected, DeviceSettingsRules.mayApply(false, current, wanted),
                        current + " -> " + wanted + " below OWNER");
                assertEquals(DeviceSettingsRules.mayApply(false, current, wanted),
                        ChestSettingsRules.mayApply(false, current, wanted), "the chest uses the same rule");
            }
        }
    }

    @Test
    void anOfficerCannotLowerOrRetuneAnOwnerDoor() {
        // Lowering the level, or keeping OWNER while flipping keycard-only or the redstone mode: all refused.
        assertFalse(DeviceSettingsRules.mayApply(false, AccessLevel.OWNER, AccessLevel.OFFICER));
        assertFalse(DeviceSettingsRules.mayApply(false, AccessLevel.OWNER, AccessLevel.MEMBER));
        assertFalse(DeviceSettingsRules.mayApply(false, AccessLevel.OWNER, AccessLevel.PUBLIC));
        assertFalse(DeviceSettingsRules.mayApply(false, AccessLevel.OWNER, AccessLevel.OWNER));
        assertTrue(DeviceSettingsRules.mayApply(false, AccessLevel.OFFICER, AccessLevel.MEMBER),
                "an officer still manages non-owner doors");
    }
}
