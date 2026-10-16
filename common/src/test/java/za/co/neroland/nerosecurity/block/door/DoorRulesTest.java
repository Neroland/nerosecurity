package za.co.neroland.nerosecurity.block.door;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.DenyReason;

/** The security door's pure rules: animation length, blast resistance, the power gate and redstone. */
class DoorRulesTest {

    private static AccessDecision allowed(AccessLevel effective) {
        return new AccessDecision(true, effective, AccessLevel.MEMBER, DenyReason.NONE, List.of(), false);
    }

    private static AccessDecision denied(AccessLevel effective) {
        return new AccessDecision(false, effective, AccessLevel.OWNER, DenyReason.INSUFFICIENT_LEVEL, List.of(), false);
    }

    @Test
    void openDurationScalesWithModulesAndFloor() {
        assertEquals(20, DoorRules.openDuration(20, 0));
        assertEquals(13, DoorRules.openDuration(20, 1)); // 20 / 1.5 = 13.33
        assertEquals(10, DoorRules.openDuration(20, 2)); // 20 / 2
        assertEquals(DoorRules.MIN_OPEN_TICKS, DoorRules.openDuration(5, 2));
        assertEquals(DoorRules.MIN_OPEN_TICKS, DoorRules.openDuration(6, 2));
        assertEquals(20, DoorRules.openDuration(20, -3), "negative module counts read as none");
        for (int base = 5; base <= 100; base++) {
            for (int modules = 0; modules < 2; modules++) {
                assertTrue(DoorRules.openDuration(base, modules + 1) <= DoorRules.openDuration(base, modules));
            }
        }
    }

    @Test
    void blastResistanceIsCapped() {
        assertEquals(600.0F, DoorRules.blastResistance(600.0F, 0, 3600.0D), 1.0E-3F);
        assertEquals(900.0F, DoorRules.blastResistance(600.0F, 1, 3600.0D), 1.0E-3F);
        assertEquals(1350.0F, DoorRules.blastResistance(600.0F, 2, 3600.0D), 1.0E-3F);
        // The blast door: 1200 x 1.5^2 = 2700 is under the default cap, but a low cap clamps it.
        assertEquals(2700.0F, DoorRules.blastResistance(1200.0F, 2, 3600.0D), 1.0E-3F);
        assertEquals(1500.0F, DoorRules.blastResistance(1200.0F, 2, 1500.0D), 1.0E-3F);
        // A cap below the base clamps the base too, and modules never weaken a door.
        assertEquals(100.0F, DoorRules.blastResistance(1200.0F, 0, 100.0D), 1.0E-3F);
        for (int modules = 0; modules <= 10; modules++) {
            assertTrue(DoorRules.blastResistance(1200.0F, modules, 3600.0D) <= 3600.0F);
        }
        // Breaking slows by the same capped factor and never stops (nothing is unbreakable).
        float progress = DoorRules.destroyProgress(0.01F, 1200.0F, 10, 3600.0D);
        assertEquals(0.01F / 3.0F, progress, 1.0E-6F);
        assertTrue(progress > 0.0F);
        assertEquals(0.01F, DoorRules.destroyProgress(0.01F, 1200.0F, 0, 3600.0D), 1.0E-6F);
    }

    @Test
    void unpoweredDoorAdmitsOnlyOwnerWhenRequirePower() {
        boolean noPower = DoorRules.hasPowerToOpen(true, 199L, 200L);
        assertFalse(noPower);
        assertTrue(DoorRules.hasPowerToOpen(true, 200L, 200L));
        assertTrue(DoorRules.hasPowerToOpen(false, 0L, 200L), "doorsRequirePower=false never needs power");

        assertFalse(DoorRules.passesPowerGate(allowed(AccessLevel.MEMBER), noPower));
        assertFalse(DoorRules.passesPowerGate(allowed(AccessLevel.OFFICER), noPower));
        assertFalse(DoorRules.passesPowerGate(allowed(AccessLevel.PUBLIC), noPower));
        assertTrue(DoorRules.passesPowerGate(allowed(AccessLevel.OWNER), noPower));
        assertTrue(DoorRules.passesPowerGate(AccessDecision.adminOverride(AccessLevel.MEMBER), noPower));

        assertTrue(DoorRules.passesPowerGate(allowed(AccessLevel.MEMBER), true));
        // The gate only refines an allowed decision: a denied OWNER-level decision never passes.
        assertFalse(DoorRules.passesPowerGate(denied(AccessLevel.OWNER), true));
        assertFalse(DoorRules.isPrivileged(denied(AccessLevel.OWNER)));
        assertTrue(DoorRules.isPrivileged(allowed(AccessLevel.OWNER)));
        assertFalse(DoorRules.isPrivileged(allowed(AccessLevel.OFFICER)));
    }

    @Test
    void redstoneNeverOpensDuringLockdown() {
        RedstoneMode open = RedstoneMode.OPEN_WHILE_POWERED;
        assertTrue(DoorRules.redstoneMayOpen(open, true, true, false, true));
        assertFalse(DoorRules.redstoneMayOpen(open, true, true, true, true), "lockdown");
        assertFalse(DoorRules.redstoneMayOpen(open, false, true, false, true), "server disallows door redstone");
        assertFalse(DoorRules.redstoneMayOpen(open, true, false, false, true), "no signal");
        assertFalse(DoorRules.redstoneMayOpen(open, true, true, false, false), "unpowered door");
        assertFalse(DoorRules.redstoneMayOpen(RedstoneMode.IGNORE, true, true, false, true), "mode ignores redstone");
    }

    @Test
    void lockdownClosesUnlessPrivilegedOpened() {
        assertTrue(DoorRules.lockdownCloses(true, false));
        assertFalse(DoorRules.lockdownCloses(true, true));
        assertFalse(DoorRules.lockdownCloses(false, false));
    }

    @Test
    void deniedEventCooldown() {
        assertTrue(DoorRules.deniedEventDue(Long.MIN_VALUE, 10L, 40));
        assertFalse(DoorRules.deniedEventDue(10L, 49L, 40));
        assertTrue(DoorRules.deniedEventDue(10L, 50L, 40));
        assertTrue(DoorRules.deniedEventDue(10L, 10L, 0));
    }

    @Test
    void redstoneModeParsingFallsBackToIgnore() {
        assertEquals(RedstoneMode.OPEN_WHILE_POWERED, RedstoneMode.byName("open"));
        assertEquals(RedstoneMode.IGNORE, RedstoneMode.byName("junk"));
        assertEquals(RedstoneMode.IGNORE, RedstoneMode.byOrdinal(99));
        assertEquals(RedstoneMode.IGNORE, RedstoneMode.byOrdinal(-1));
    }
}
