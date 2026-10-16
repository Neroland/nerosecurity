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

    /** A lockdown edge rescales the meter and never zeroes it, so cycling lockdown cannot drain it. */
    @Test
    void lockdownCyclingNeverDrainsTheMeter() {
        double normal = FieldPower.loadCapacity(40.0, FieldStrength.effective(1, false, true, 5));
        double boosted = FieldPower.loadCapacity(40.0, FieldStrength.effective(1, true, true, 5));
        assertEquals(150.0, FieldStrength.rescaleLoad(30.0, normal, boosted), 1.0E-9, "entry keeps the fraction");
        assertEquals(30.0, FieldStrength.rescaleLoad(150.0, boosted, normal), 1.0E-9, "exit hands it back");
        assertTrue(FieldStrength.rescaleLoad(30.0, normal, boosted) > 0.0, "never reset to zero");
        assertEquals(0.0, FieldStrength.rescaleLoad(Double.NaN, normal, boosted));
        assertEquals(12.0, FieldStrength.rescaleLoad(12.0, 0.0, boosted), "no capacity: left as it is");

        double decayPerTick = 0.01;
        double start = 30.0;
        int cycles = 50;
        for (int ticksPerHalf : new int[] {0, 1, 20}) {
            double load = start;
            for (int cycle = 0; cycle < cycles; cycle++) {
                load = FieldStrength.rescaleLoad(load, normal, boosted);
                for (int t = 0; t < ticksPerHalf; t++) {
                    load = Math.max(0.0, load - decayPerTick);
                }
                load = FieldStrength.rescaleLoad(load, boosted, normal);
                for (int t = 0; t < ticksPerHalf; t++) {
                    load = Math.max(0.0, load - decayPerTick);
                }
            }
            double decayAlone = Math.max(0.0, start - decayPerTick * 2.0 * ticksPerHalf * cycles);
            assertTrue(load >= decayAlone - 1.0E-9,
                    cycles + " lockdown cycles (" + ticksPerHalf + " ticks each half) left " + load
                            + ", decay alone would leave " + decayAlone);
        }
    }
}
