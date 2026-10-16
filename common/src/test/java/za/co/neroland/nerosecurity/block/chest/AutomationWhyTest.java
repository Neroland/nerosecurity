package za.co.neroland.nerosecurity.block.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;

/** The settings screen's automation reason agrees with the gate itself, everywhere. */
class AutomationWhyTest {

    @Test
    void reasonIsOkExactlyWhenTheGateAllows() {
        List<AccessLevel> policies = new ArrayList<>(Arrays.asList(AccessLevel.values()));
        policies.add(null);
        for (boolean linked : new boolean[] {true, false}) {
            for (AccessLevel policy : policies) {
                for (AccessLevel min : AccessLevel.values()) {
                    for (boolean keycard : new boolean[] {true, false}) {
                        for (boolean lockdown : new boolean[] {true, false}) {
                            DeviceAccessSettings settings = new DeviceAccessSettings(min, keycard);
                            boolean allowed = GatedChestAccess.automationAllowed(linked, policy, settings, lockdown);
                            AutomationWhy why = GatedChestAccess.why(linked, policy, settings, lockdown);
                            assertEquals(allowed, why == AutomationWhy.OK,
                                    linked + " " + policy + " " + min + " " + keycard + " " + lockdown);
                        }
                    }
                }
            }
        }
    }

    @Test
    void reasonsNameTheRuleThatRefused() {
        DeviceAccessSettings member = new DeviceAccessSettings(AccessLevel.MEMBER, false);
        assertEquals(AutomationWhy.OFF, GatedChestAccess.why(true, null, member, false));
        assertEquals(AutomationWhy.OFF, GatedChestAccess.why(true, AccessLevel.PUBLIC, member, false));
        assertEquals(AutomationWhy.KEYCARD, GatedChestAccess.why(true, AccessLevel.OFFICER,
                new DeviceAccessSettings(AccessLevel.MEMBER, true), false));
        assertEquals(AutomationWhy.LOCKDOWN, GatedChestAccess.why(true, AccessLevel.OFFICER, member, true));
        assertEquals(AutomationWhy.LEVEL, GatedChestAccess.why(true, AccessLevel.MEMBER,
                new DeviceAccessSettings(AccessLevel.OFFICER, false), false));
        assertEquals("chest.nerosecurity.automation.why.keycard", AutomationWhy.KEYCARD.translationKey());
    }
}
