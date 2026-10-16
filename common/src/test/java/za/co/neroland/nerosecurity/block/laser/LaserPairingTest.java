package za.co.neroland.nerosecurity.block.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.laser.LaserPairing.Cell;

/** Pairing, range, one sweep per pair and staggering. Plain JVM. */
class LaserPairingTest {

    /** A line of cells along the facing; index 0 is distance 1. Beyond the array: open space. */
    private static IntFunction<Cell> line(Cell... cells) {
        return d -> d - 1 < cells.length ? cells[d - 1] : Cell.OPEN;
    }

    /** 15. */
    @Test
    void pairsOnlyWithOppositeFacingWithinRange() {
        // A node facing back at distance 3 across open space pairs.
        assertEquals(3, LaserPairing.partnerDistance(8, line(Cell.OPEN, Cell.OPEN, Cell.FACING_NODE)));
        // A node facing the same way blocks, so there is no partner.
        Cell sameFacing = LaserPairing.cell(true, Direction.EAST, Direction.EAST, false);
        assertEquals(Cell.BLOCKED, sameFacing);
        assertEquals(0, LaserPairing.partnerDistance(8, line(Cell.OPEN, sameFacing)));
        // A node facing sideways blocks too; only the exact opposite facing pairs.
        assertEquals(Cell.BLOCKED, LaserPairing.cell(true, Direction.NORTH, Direction.EAST, false));
        assertEquals(Cell.FACING_NODE, LaserPairing.cell(true, Direction.WEST, Direction.EAST, false));
        // Stone in the way breaks the pair.
        Cell stone = LaserPairing.cell(false, null, Direction.EAST, false);
        assertEquals(Cell.BLOCKED, stone);
        assertEquals(0, LaserPairing.partnerDistance(8, line(Cell.OPEN, stone, Cell.FACING_NODE)));
        // Air or a replaceable block lets the beam through.
        assertEquals(Cell.OPEN, LaserPairing.cell(false, null, Direction.EAST, true));
        // Beyond range: none; exactly at range: paired.
        Cell[] nine = new Cell[9];
        java.util.Arrays.fill(nine, Cell.OPEN);
        nine[8] = Cell.FACING_NODE;
        assertEquals(0, LaserPairing.partnerDistance(8, line(nine)));
        assertEquals(9, LaserPairing.partnerDistance(9, line(nine)));
        // Only the smaller asLong() node of an armed pair sweeps.
        long a = new BlockPos(0, 64, 0).asLong();
        long b = new BlockPos(5, 64, 0).asLong();
        assertNotEquals(LaserPairing.sweeps(a, b, true), LaserPairing.sweeps(b, a, true));
        assertTrue(LaserPairing.sweeps(Math.min(a, b), Math.max(a, b), true));
        assertFalse(LaserPairing.sweeps(Math.max(a, b), Math.min(a, b), true));
        // ... unless the smaller one is not armed: then the other end covers the beam.
        assertTrue(LaserPairing.sweeps(Math.max(a, b), Math.min(a, b), false));
    }

    @Test
    void walkIsBoundedByRange() {
        AtomicInteger asked = new AtomicInteger();
        assertEquals(0, LaserPairing.partnerDistance(32, d -> {
            asked.incrementAndGet();
            return Cell.OPEN;
        }));
        assertEquals(32, asked.get());
    }

    @Test
    void rangeAddsFourPerModuleUpToTheCap() {
        assertEquals(8, LaserPairing.range(8, 32, 0));
        assertEquals(16, LaserPairing.range(8, 32, 2));
        assertEquals(32, LaserPairing.range(8, 32, 10));
        assertEquals(12, LaserPairing.range(8, 12, 2));
        assertEquals(1, LaserPairing.range(0, 32, 0));
    }

    @Test
    void scansAreStaggeredAndOncePerInterval() {
        long pos = new BlockPos(3, 70, -9).asLong();
        int due = 0;
        for (long t = 0; t < 10; t++) {
            if (LaserPairing.scanDue(t, pos, 10)) {
                due++;
            }
        }
        assertEquals(1, due);
        // Interval clamped to 1..20.
        assertTrue(LaserPairing.scanDue(7, pos, 0));
        int dueClamped = 0;
        for (long t = 0; t < 20; t++) {
            if (LaserPairing.scanDue(t, pos, 500)) {
                dueClamped++;
            }
        }
        assertEquals(1, dueClamped);
        // Neighbouring nodes do not all scan on the same tick.
        long other = new BlockPos(3, 71, -9).asLong();
        boolean differ = false;
        for (long t = 0; t < 2; t++) {
            differ |= LaserPairing.scanDue(t, pos, 2) != LaserPairing.scanDue(t, other, 2);
        }
        assertTrue(differ);
    }
}
