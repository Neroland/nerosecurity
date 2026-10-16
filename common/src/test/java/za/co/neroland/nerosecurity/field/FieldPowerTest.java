package za.co.neroland.nerosecurity.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.DeviceAccessSettings;

/** The emitter's arithmetic and the geometry caps. Pure. */
class FieldPowerTest {

    private static FieldSettings plane(int offset, int w, int h) {
        return new FieldSettings(FieldShape.PLANE, offset, w, h, 1, DeviceAccessSettings.DEFAULT, true);
    }

    private static FieldSettings box(int offset, int w, int h, int d) {
        return new FieldSettings(FieldShape.BOX, offset, w, h, d, DeviceAccessSettings.DEFAULT, true);
    }

    @Test
    void formulaAndCaps() {
        assertEquals(505, FieldPower.drawPerTick(400, 5, 5, 0.25));
        assertEquals(6, FieldPower.drawPerTick(1, 1, 5, 0.25), "the variable part rounds up");
        assertEquals(6900L, FieldPower.restartThreshold(69, 100));

        assertEquals(1, FieldPower.strength(0, 5));
        assertEquals(5, FieldPower.strength(4, 5));
        assertEquals(5, FieldPower.strength(9, 5), "strength clamps to maxFieldStrength");
        assertEquals(1, FieldPower.strength(3, 0), "never below 1");
        assertEquals(8, FieldPower.range(0, 8, 24));
        assertEquals(24, FieldPower.range(4, 8, 24));
        assertEquals(24, FieldPower.range(9, 8, 24), "range clamps to fieldMaxRange");
        assertEquals(200.0, FieldPower.loadCapacity(40.0, 5));

        // Area cap: a 21x21 wall is 441 blocks.
        assertEquals(Optional.of(FieldGeometry.INVALID_AREA), FieldGeometry.validate(plane(2, 21, 21), 24, 400));
        assertEquals(Optional.empty(), FieldGeometry.validate(plane(2, 19, 21), 24, 400));
        // Range cap: offset 9 is beyond range 8; a wide wall's edge is too.
        assertEquals(Optional.of(FieldGeometry.INVALID_RANGE), FieldGeometry.validate(plane(9, 1, 1), 8, 400));
        assertEquals(Optional.of(FieldGeometry.INVALID_RANGE), FieldGeometry.validate(plane(2, 19, 1), 8, 400));
        assertEquals(Optional.empty(), FieldGeometry.validate(plane(8, 17, 17), 8, 400));
        // A box reaches offset + half its depth.
        assertEquals(Optional.of(FieldGeometry.INVALID_RANGE), FieldGeometry.validate(box(7, 3, 3, 5), 8, 400));
        assertEquals(Optional.empty(), FieldGeometry.validate(box(6, 3, 3, 5), 8, 400));
        // Shape rules.
        assertEquals(Optional.of(FieldGeometry.INVALID_SHAPE), FieldGeometry.validate(plane(2, 4, 5), 24, 400), "even");
        assertEquals(Optional.of(FieldGeometry.INVALID_SHAPE), FieldGeometry.validate(plane(0, 5, 5), 24, 400), "offset 0");
        assertEquals(Optional.of(FieldGeometry.INVALID_SHAPE), FieldGeometry.validate(box(2, 5, 5, 1), 24, 400),
                "a shell needs depth 3");
        assertEquals(98, FieldGeometry.count(box(2, 5, 5, 5)));

        // Every projected block lies within range, for every facing.
        FieldSettings wide = plane(8, 17, 17);
        for (Direction facing : Direction.values()) {
            for (BlockPos p : FieldGeometry.positions(BlockPos.ZERO, facing, wide)) {
                assertTrue(FieldGeometry.chebyshev(BlockPos.ZERO, p) <= 8, facing + " " + p);
            }
            assertEquals(FieldGeometry.count(wide), FieldGeometry.positions(BlockPos.ZERO, facing, wide).size());
        }
    }
}
