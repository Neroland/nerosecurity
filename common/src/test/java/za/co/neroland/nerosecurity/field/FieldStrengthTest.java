package za.co.neroland.nerosecurity.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lockdown raises the effective strength to the cap, never the power draw (test 16). Pure. */
class FieldStrengthTest {

    @Test
    void lockdownRaisesToMax() {
        assertEquals(5, FieldStrength.effective(1, true, true, 5));
        assertEquals(1, FieldStrength.effective(1, false, true, 5));
        assertEquals(1, FieldStrength.effective(1, true, false, 5), "the server can turn the boost off");
        assertEquals(5, FieldStrength.effective(7, false, true, 5), "module strength is capped too");
        assertEquals(3, FieldStrength.effective(1, true, true, 3), "the boost never exceeds maxFieldStrength");
        assertEquals(1, FieldStrength.effective(0, false, true, 5), "never below 1");

        // Power is computed from module strength: the boost adds load capacity, not draw.
        int area = 25;
        int moduleStrength = FieldPower.strength(0, 5);
        int effective = FieldStrength.effective(moduleStrength, true, true, 5);
        int draw = FieldPower.drawPerTick(area, moduleStrength, 5, 0.25);
        assertEquals(FieldPower.drawPerTick(area, 1, 5, 0.25), draw);
        assertTrue(FieldPower.drawPerTick(area, effective, 5, 0.25) > draw, "boosted draw would be higher; it is not used");
        assertTrue(FieldPower.loadCapacity(40.0, effective) > FieldPower.loadCapacity(40.0, moduleStrength));
    }
}
