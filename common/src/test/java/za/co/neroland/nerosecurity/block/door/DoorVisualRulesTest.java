package za.co.neroland.nerosecurity.block.door;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The door's two renderer-only derived facts. */
class DoorVisualRulesTest {

    @Test
    void animatingFromStartUntilShutAndStill() {
        assertFalse(DoorRules.animating(false, 0), "shut and still: the static model");
        assertTrue(DoorRules.animating(true, 0), "the tick it starts opening");
        assertTrue(DoorRules.animating(true, 10), "fully open: the renderer draws retracted panels");
        assertTrue(DoorRules.animating(false, 3), "still closing");
    }

    @Test
    void powerLowOnlyForALinkedDoorThatNeedsPower() {
        assertTrue(DoorRules.powerLow(true, true, 10, 100));
        assertFalse(DoorRules.powerLow(true, true, 100, 100), "exactly enough to open");
        assertFalse(DoorRules.powerLow(false, true, 0, 100), "an unlinked door shows no warning");
        assertFalse(DoorRules.powerLow(true, false, 0, 100), "power not required on this server");
    }
}
