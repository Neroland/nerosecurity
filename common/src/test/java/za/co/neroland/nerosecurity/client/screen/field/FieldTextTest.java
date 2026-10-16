package za.co.neroland.nerosecurity.client.screen.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.screen.field.FieldText.Dim;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldShape;
import za.co.neroland.nerosecurity.field.FieldState;

/** Stepper bounds follow the geometry rules the server enforces; the status row has one glyph shape per state. */
class FieldTextTest {

    private static FieldSettings wall(int offset, int width, int height) {
        return FieldSettings.DEFAULT.withShape(FieldShape.PLANE).withOffset(offset).withSize(width, height, 1);
    }

    private static FieldSettings shell(int offset, int width, int height, int depth) {
        return FieldSettings.DEFAULT.withShape(FieldShape.BOX).withOffset(offset).withSize(width, height, depth);
    }

    @Test
    void statusPrecedence() {
        assertEquals(new FieldText.Row(Status.WARN, "field.nerosecurity.disabled_server", false, false),
                FieldText.state(false, true, true, FieldState.PROJECTING));
        assertEquals(new FieldText.Row(Status.OFF, "device.nerosecurity.unlinked", false, false),
                FieldText.state(true, false, true, FieldState.PROJECTING));
        assertEquals(new FieldText.Row(Status.BAD, "field.nerosecurity.state.lockdown", false, false),
                FieldText.state(true, true, true, FieldState.PROJECTING));
        assertEquals(new FieldText.Row(Status.OK, "field.nerosecurity.state.projecting", true, false),
                FieldText.state(true, true, false, FieldState.PROJECTING));
        assertEquals(new FieldText.Row(Status.WARN, "field.nerosecurity.state.brownout", false, false),
                FieldText.state(true, true, false, FieldState.BROWNOUT));
        assertEquals(new FieldText.Row(Status.BAD, "field.nerosecurity.state.overloaded", false, true),
                FieldText.state(true, true, false, FieldState.OVERLOADED));
        assertEquals(new FieldText.Row(Status.OFF, "field.nerosecurity.state.idle", false, false),
                FieldText.state(true, true, false, null));
    }

    @Test
    void lockReasons() {
        assertEquals("field.nerosecurity.disabled_server", FieldText.editWhy(false, true, AccessLevel.MEMBER, true));
        assertEquals("device.nerosecurity.unlinked", FieldText.editWhy(true, false, AccessLevel.MEMBER, true));
        assertEquals("door.nerosecurity.owner_only_setting", FieldText.editWhy(true, true, AccessLevel.OWNER, false));
        assertNull(FieldText.editWhy(true, true, AccessLevel.OWNER, true));
        assertNull(FieldText.editWhy(true, true, AccessLevel.MEMBER, false));
    }

    @Test
    void minimumsFollowTheShape() {
        assertEquals(1, FieldText.min(Dim.OFFSET, FieldShape.BOX));
        assertEquals(1, FieldText.min(Dim.WIDTH, FieldShape.BOX));
        assertEquals(1, FieldText.min(Dim.DEPTH, FieldShape.PLANE));
        assertEquals(3, FieldText.min(Dim.DEPTH, FieldShape.BOX));
    }

    @Test
    void maximumIsTheLargestValueThatStillValidates() {
        FieldSettings s = wall(2, 5, 5);
        int range = 8;
        int maxArea = 200;
        for (Dim dim : new Dim[] {Dim.OFFSET, Dim.WIDTH, Dim.HEIGHT}) {
            int max = FieldText.max(s, dim, range, maxArea);
            assertTrue(FieldGeometry.validate(FieldText.with(s, dim, max), range, maxArea).isEmpty(), dim + " max fits");
            int step = dim == Dim.OFFSET ? 1 : 2;
            assertTrue(FieldGeometry.validate(FieldText.with(s, dim, max + step), range, maxArea).isPresent(),
                    dim + " one step more does not");
        }
        // Width is bounded by the range: half-width ≤ 8 → 17.
        assertEquals(17, FieldText.max(s, Dim.WIDTH, range, maxArea));
        // Distance is bounded by the range as well.
        assertEquals(8, FieldText.max(s, Dim.OFFSET, range, maxArea));
        // The area cap binds before the range when it is small: 5 × h ≤ 40 → h = 7.
        assertEquals(7, FieldText.max(s, Dim.HEIGHT, range, 40));
    }

    @Test
    void maximumNeverDropsBelowTheMinimum() {
        assertEquals(3, FieldText.max(shell(1, 9, 9, 3), Dim.DEPTH, 1, 1));
        assertEquals(1, FieldText.max(wall(1, 1, 1), Dim.WIDTH, 0, 0));
    }

    @Test
    void sizesSnapToOddInTheDirectionTheyMoved() {
        assertEquals(7, FieldText.snap(Dim.WIDTH, 6, 5));
        assertEquals(5, FieldText.snap(Dim.WIDTH, 6, 7));
        assertEquals(5, FieldText.snap(Dim.HEIGHT, 5, 3));
        assertEquals(1, FieldText.snap(Dim.DEPTH, 0, 1));
        assertEquals(4, FieldText.snap(Dim.OFFSET, 4, 3));
        assertEquals(11, FieldText.snap(Dim.WIDTH, 10, 5));
    }

    @Test
    void validationArgumentsNameTheLimit() {
        assertEquals(12, FieldText.invalidArgument(FieldGeometry.INVALID_RANGE, 12, 300));
        assertEquals(300, FieldText.invalidArgument(FieldGeometry.INVALID_AREA, 12, 300));
        assertNull(FieldText.invalidArgument(FieldGeometry.INVALID_SHAPE, 12, 300));
    }

    @Test
    void valueAndWithRoundTrip() {
        FieldSettings s = shell(2, 5, 7, 3);
        for (Dim dim : Dim.values()) {
            assertEquals(FieldText.value(s, dim), FieldText.value(FieldText.with(s, dim, FieldText.value(s, dim)), dim));
        }
        assertEquals(9, FieldText.with(s, Dim.DEPTH, 9).depth());
        assertEquals(4, FieldText.with(s, Dim.OFFSET, 4).offset());
    }
}
