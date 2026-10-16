package za.co.neroland.nerosecurity.block.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.access.AccessLevel.MEMBER;
import static za.co.neroland.nerosecurity.access.AccessLevel.OFFICER;
import static za.co.neroland.nerosecurity.access.AccessLevel.OWNER;
import static za.co.neroland.nerosecurity.access.AccessLevel.PUBLIC;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;

/** The secure chest's automation gate and the settings-screen refinements, as pure rules. */
class ChestAutomationPolicyTest {

    @Test
    void automationAllowedMatrix() {
        List<AccessLevel> policies = Arrays.asList(null, MEMBER, OFFICER); // null = off
        for (AccessLevel policy : policies) {
            for (AccessLevel minLevel : List.of(MEMBER, OFFICER)) {
                for (boolean keycardOnly : new boolean[] {false, true}) {
                    for (boolean lockdown : new boolean[] {false, true}) {
                        DeviceAccessSettings settings = new DeviceAccessSettings(minLevel, keycardOnly);
                        boolean expected = policy != null && policy.ordinal() >= minLevel.ordinal()
                                && !keycardOnly && !lockdown;
                        assertEquals(expected,
                                GatedChestAccess.automationAllowed(true, policy, settings, lockdown),
                                "policy=" + policy + " min=" + minLevel + " keycardOnly=" + keycardOnly
                                        + " lockdown=" + lockdown);
                        assertTrue(GatedChestAccess.automationAllowed(false, policy, settings, lockdown),
                                "an unlinked chest is a plain chest");
                    }
                }
            }
        }
    }

    @Test
    void onlyTheThreeDocumentedCellsAreOpen() {
        int open = 0;
        for (AccessLevel policy : Arrays.asList(null, MEMBER, OFFICER)) {
            for (AccessLevel minLevel : List.of(MEMBER, OFFICER)) {
                for (boolean keycardOnly : new boolean[] {false, true}) {
                    for (boolean lockdown : new boolean[] {false, true}) {
                        if (GatedChestAccess.automationAllowed(true, policy,
                                new DeviceAccessSettings(minLevel, keycardOnly), lockdown)) {
                            open++;
                        }
                    }
                }
            }
        }
        // MEMBER policy @ MEMBER chest, OFFICER policy @ MEMBER chest, OFFICER policy @ OFFICER chest.
        assertEquals(3, open);
    }

    @Test
    void automationIsCappedAtOfficer() {
        assertNull(GatedChestAccess.cappedPolicy(null));
        assertNull(GatedChestAccess.cappedPolicy(PUBLIC), "PUBLIC is not a policy: off");
        assertEquals(MEMBER, GatedChestAccess.cappedPolicy(MEMBER));
        assertEquals(OFFICER, GatedChestAccess.cappedPolicy(OFFICER));
        assertEquals(OFFICER, GatedChestAccess.cappedPolicy(OWNER), "never above OFFICER");
        // Even a corrupt OWNER policy never reaches an OWNER chest, and never beats lockdown.
        assertFalse(GatedChestAccess.automationAllowed(true, OWNER, new DeviceAccessSettings(OWNER, false), false));
        assertFalse(GatedChestAccess.automationAllowed(true, OWNER, new DeviceAccessSettings(MEMBER, false), true));
        assertFalse(GatedChestAccess.automationAllowed(true, PUBLIC, new DeviceAccessSettings(PUBLIC, false), false),
                "PUBLIC policy is off, even for a PUBLIC chest");
        assertTrue(GatedChestAccess.automationAllowed(true, MEMBER, new DeviceAccessSettings(PUBLIC, false), false));
        assertFalse(GatedChestAccess.automationAllowed(true, OFFICER, null, false), "no settings: closed");
    }

    @Test
    void capabilityGateRefusesAnythingButASecureChest() {
        assertFalse(GatedChestAccess.allows(null));
        assertFalse(GatedChestAccess.allows(new Object()));
    }

    @Test
    void cooldowns() {
        assertTrue(GatedChestAccess.cooldownElapsed(Long.MIN_VALUE, 0L, 1200L), "never raised");
        assertFalse(GatedChestAccess.cooldownElapsed(100L, 1299L, 1200L));
        assertTrue(GatedChestAccess.cooldownElapsed(100L, 1300L, 1200L));
        assertTrue(GatedChestAccess.cooldownElapsed(100L, 50L, 1200L), "clock went backwards");
        assertTrue(GatedChestAccess.cooldownElapsed(100L, 100L, 0L), "no cooldown configured");
    }

    @Test
    void settingsScreenOwnerRule() {
        for (AccessLevel current : AccessLevel.values()) {
            for (AccessLevel wanted : AccessLevel.values()) {
                assertTrue(ChestSettingsRules.mayApply(true, current, wanted), "an owner may set anything");
                assertEquals(current != OWNER && wanted != OWNER, ChestSettingsRules.mayApply(false, current, wanted),
                        "an officer may not set OWNER or change an OWNER chest: " + current + " -> " + wanted);
            }
        }
    }
}
