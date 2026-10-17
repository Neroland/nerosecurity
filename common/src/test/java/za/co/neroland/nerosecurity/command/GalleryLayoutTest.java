package za.co.neroland.nerosecurity.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.command.GalleryLayout.Offset;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;

/** The gallery floor plan: everything fits the footprint, the clear box covers it, and the devices can link and pair. */
class GalleryLayoutTest {

    /** The shipped defaults the plan is sized against ({@code maxDeviceDistance}, {@code laserBaseRange}, {@code fieldBaseRange}, {@code maxFieldArea}). */
    private static final int DEFAULT_MAX_DEVICE_DISTANCE = 128;
    private static final int DEFAULT_LASER_RANGE = 8;
    private static final int DEFAULT_FIELD_RANGE = 8;
    private static final int DEFAULT_MAX_FIELD_AREA = 400;

    @Test
    void footprintIsSymmetricAndNorthOfTheOperator() {
        assertEquals(-GalleryLayout.MIN_DX, GalleryLayout.MAX_DX, "symmetric about the axis");
        assertTrue(GalleryLayout.MAX_DZ < 0, "the whole exhibit is north of the operator");
        assertTrue(GalleryLayout.CLEAR_BOX.contains(GalleryLayout.FOOTPRINT), "the clear sweeps everything built");
        assertTrue(GalleryLayout.CLEAR_BOX.maxDy() > GalleryLayout.TOP_DY, "headroom above the tallest layer");
    }

    @Test
    void everyDeviceIsInsideTheFootprintAndWithinLinkRange() {
        Set<Offset> seen = new HashSet<>();
        for (Offset device : GalleryLayout.devices()) {
            assertTrue(GalleryLayout.FOOTPRINT.contains(device), device + " is inside the footprint");
            assertTrue(device.dy() >= 1, device + " stands above the floor layer");
            assertTrue(GalleryLayout.manhattanFromHub(device) <= DEFAULT_MAX_DEVICE_DISTANCE, device + " can link");
            assertTrue(seen.add(device), device + " is placed once");
        }
        assertTrue(GalleryLayout.FOOTPRINT.contains(GalleryLayout.HUB));
        assertFalse(seen.contains(GalleryLayout.HUB), "the hub is never a device");
    }

    @Test
    void laserPairsFaceEachOtherWithinDefaultRange() {
        for (Offset west : GalleryLayout.LASER_WEST_NODES) {
            Offset east = GalleryLayout.laserPartner(west);
            assertEquals(west.dy(), east.dy());
            assertEquals(west.dz(), east.dz());
            assertEquals(GalleryLayout.LASER_SPAN, east.dx() - west.dx());
            assertTrue(GalleryLayout.LASER_SPAN <= DEFAULT_LASER_RANGE, "a pair forms at the default range");
            assertTrue(GalleryLayout.LASER_SPAN > 1, "a gap between the lenses, so the beam has length");
            assertEquals(GalleryLayout.LASER_WEST_DX, west.dx());
            assertEquals(GalleryLayout.LASER_EAST_DX, east.dx());
            // Both ends sit against the corridor walls so their batteries hide inside them.
            assertEquals(GalleryLayout.EAST_PAVILION_MIN_DX + 2, west.dx());
            assertEquals(GalleryLayout.EAST_PAVILION_MAX_DX - 2, east.dx());
        }
    }

    @Test
    void fieldSettingsAreValidAndTheirBlocksFit() {
        assertTrue(FieldGeometry.validate(GalleryLayout.FIELD_WALL_SETTINGS, DEFAULT_FIELD_RANGE,
                DEFAULT_MAX_FIELD_AREA).isEmpty());
        assertTrue(FieldGeometry.validate(GalleryLayout.FIELD_BOX_SETTINGS, DEFAULT_FIELD_RANGE,
                DEFAULT_MAX_FIELD_AREA).isEmpty());
        assertFieldFits(GalleryLayout.FIELD_WALL_EMITTER, GalleryLayout.FIELD_WALL_FACING, GalleryLayout.FIELD_WALL_SETTINGS);
        assertFieldFits(GalleryLayout.FIELD_BOX_EMITTER, GalleryLayout.FIELD_BOX_FACING, GalleryLayout.FIELD_BOX_SETTINGS);
    }

    private static void assertFieldFits(Offset emitter, Direction facing, FieldSettings settings) {
        BlockPos origin = BlockPos.ZERO;
        List<BlockPos> positions = FieldGeometry.positions(emitter.at(origin), facing, settings);
        assertFalse(positions.isEmpty());
        for (BlockPos p : positions) {
            Offset o = new Offset(p.getX(), p.getY(), p.getZ());
            assertTrue(GalleryLayout.FOOTPRINT.contains(o), o + " is inside the footprint");
            assertTrue(o.dy() >= 1, o + " is above the floor");
            assertTrue(o.dy() <= GalleryLayout.PAVILION_TOP_DY, o + " is below the pavilion roof line");
            // Inside the west pavilion's interior, never in its walls.
            assertTrue(o.dx() > GalleryLayout.WEST_PAVILION_MIN_DX + 1 && o.dx() < GalleryLayout.WEST_PAVILION_MAX_DX - 1,
                    o + " is between the pavilion walls");
            assertTrue(o.dz() > GalleryLayout.PAVILION_BACK_DZ + 1 && o.dz() <= GalleryLayout.PAVILION_FRONT_DZ,
                    o + " is in front of the back wall");
        }
    }

    @Test
    void racksHaveSixEvenlySpacedFramesOnTheirWallFace() {
        List<Offset> cards = GalleryLayout.rackSlots(GalleryLayout.CARD_RACK_DX, Direction.WEST);
        List<Offset> items = GalleryLayout.rackSlots(GalleryLayout.ITEM_RACK_DX, Direction.EAST);
        assertEquals(GalleryLayout.RACK_SLOTS, cards.size());
        assertEquals(GalleryLayout.RACK_SLOTS, items.size());
        for (int i = 0; i < cards.size(); i++) {
            Offset card = cards.get(i);
            Offset item = items.get(i);
            assertEquals(GalleryLayout.CARD_RACK_DX - 1, card.dx(), "card frames hang on the rack's west face");
            assertEquals(GalleryLayout.ITEM_RACK_DX + 1, item.dx(), "item frames hang on the rack's east face");
            assertEquals(card.dz(), item.dz(), "the two racks mirror each other");
            assertTrue(card.dz() > GalleryLayout.RACK_MIN_DZ && card.dz() < GalleryLayout.RACK_MAX_DZ,
                    "every frame has wall behind it");
            if (i > 0) {
                assertEquals(2, card.dz() - cards.get(i - 1).dz(), "two blocks apart");
            }
            assertTrue(GalleryLayout.FOOTPRINT.contains(card));
            assertTrue(GalleryLayout.FOOTPRINT.contains(item));
        }
        assertEquals(GalleryLayout.RACK_SLOTS, GalleryLayout.CARD_LEVELS.size() + 2, "four levels, an encrypted card, a blank");
    }

    @Test
    void lightingSitsInsideTheFootprintAboveHeadHeight() {
        for (Offset lantern : GalleryLayout.lanterns()) {
            assertTrue(GalleryLayout.FOOTPRINT.contains(lantern), lantern + " lantern in the footprint");
            assertTrue(lantern.dy() >= 4, lantern + " lantern is up on a wall");
        }
        for (Offset light : GalleryLayout.lights()) {
            assertTrue(GalleryLayout.FOOTPRINT.contains(light), light + " light in the footprint");
            assertEquals(GalleryLayout.TOP_DY, light.dy(), "light blocks sit in the top layer");
        }
    }

    @Test
    void floorPatternsAreWhereThePlanSaysAndStripesAlternate() {
        assertTrue(GalleryLayout.onPlinth(0, -13));
        assertTrue(GalleryLayout.onPlinth(1, -12));
        assertFalse(GalleryLayout.onPlinth(2, -13));
        assertTrue(GalleryLayout.onPlinthRing(2, -13));
        assertTrue(GalleryLayout.onPlinthRing(-2, -15));
        assertFalse(GalleryLayout.onPlinthRing(3, -13));
        assertTrue(GalleryLayout.onSpine(0, -4));
        assertTrue(GalleryLayout.onSpine(-1, -23));
        assertFalse(GalleryLayout.onSpine(2, -10));
        assertFalse(GalleryLayout.onSpine(0, -3), "the border row is not spine");
        assertTrue(GalleryLayout.onThreshold(0, GalleryLayout.THRESHOLD_DZ));
        assertFalse(GalleryLayout.onThreshold(GalleryLayout.DOOR_WALL_MIN_DX, GalleryLayout.THRESHOLD_DZ));
        assertTrue(GalleryLayout.onBorder(GalleryLayout.MIN_DX, -10));
        assertTrue(GalleryLayout.onBorder(0, GalleryLayout.MIN_DZ));
        assertFalse(GalleryLayout.onBorder(0, -10));
        for (int dx = -5; dx < 5; dx++) {
            assertNotEquals(GalleryLayout.hazardYellow(dx, 7), GalleryLayout.hazardYellow(dx + 1, 7), "alternates along x");
            assertNotEquals(GalleryLayout.hazardYellow(7, dx), GalleryLayout.hazardYellow(7, dx + 1), "alternates along z");
        }
    }

    @Test
    void doorsAndTheirFurnitureSitInTheDoorWall() {
        for (Offset door : List.of(GalleryLayout.SECURITY_DOOR_CLOSED, GalleryLayout.BLAST_DOOR,
                GalleryLayout.SECURITY_DOOR_OPEN)) {
            assertEquals(GalleryLayout.DOOR_WALL_FRONT_DZ, door.dz());
            assertEquals(1, door.dy(), "controllers stand on the floor");
            assertTrue(door.dx() > GalleryLayout.DOOR_WALL_MIN_DX && door.dx() < GalleryLayout.DOOR_WALL_MAX_DX);
        }
        assertTrue(GalleryLayout.BLAST_DOOR.dx() + 1 < GalleryLayout.SECURITY_DOOR_OPEN.dx() - 1, "a wall pier between doors");
        assertEquals(GalleryLayout.SECURITY_DOOR_OPEN.dx() - 1, GalleryLayout.DOOR_LEVER.dx(), "the lever is beside the open door");
        assertEquals(GalleryLayout.THRESHOLD_DZ, GalleryLayout.DOOR_LEVER.dz(), "the lever hangs on the wall's south face");
        assertEquals(GalleryLayout.THRESHOLD_DZ, GalleryLayout.PLAZA_CAMERA.dz(), "the plaza camera hangs on the same face");
        assertTrue(GalleryLayout.PLAZA_CAMERA.dy() <= GalleryLayout.WALL_TOP_DY, "the camera's battery has wall behind it");
    }

    // --- the access-denied bay -------------------------------------------------------------------------

    /** The bay's walls and interior. */
    private static final GalleryLayout.Box BAY = new GalleryLayout.Box(GalleryLayout.DENIED_MIN_DX, 0,
            GalleryLayout.DENIED_BACK_DZ, GalleryLayout.DENIED_MAX_DX, GalleryLayout.TOP_DY, GalleryLayout.DENIED_FRONT_DZ);

    @Test
    void accessDeniedBaySitsAtTheNorthEndInsideTheFootprint() {
        assertTrue(GalleryLayout.FOOTPRINT.contains(BAY), "the bay is inside the footprint");
        assertEquals(GalleryLayout.MIN_DZ, GalleryLayout.DENIED_BACK_DZ, "the bay's back wall is the footprint's north edge");
        assertTrue(GalleryLayout.DENIED_FRONT_DZ < GalleryLayout.PAVILION_BACK_DZ, "the bay stands behind the pavilions");
        assertEquals(GalleryLayout.DENIED_FRONT_DZ + 1, GalleryLayout.DENIED_THRESHOLD_DZ, "the red strip is right at the open front");
        assertTrue(GalleryLayout.DENIED_THRESHOLD_DZ < GalleryLayout.PAVILION_BACK_DZ, "a walkway separates strip and pavilions");
        assertTrue(GalleryLayout.onDeniedThreshold(0, GalleryLayout.DENIED_THRESHOLD_DZ));
        assertFalse(GalleryLayout.onDeniedThreshold(GalleryLayout.DENIED_MIN_DX + 1, GalleryLayout.DENIED_THRESHOLD_DZ),
                "the strip stops at the walls");
        assertFalse(GalleryLayout.onThreshold(0, GalleryLayout.DENIED_THRESHOLD_DZ), "the two strips are different strips");
        assertTrue(GalleryLayout.FOOTPRINT.contains(GalleryLayout.DENIED_TITLE));
        assertTrue(GalleryLayout.FOOTPRINT.contains(GalleryLayout.DENIED_NOTE));
        assertTrue(GalleryLayout.FOOTPRINT.contains(GalleryLayout.DENIED_HUB));
        assertTrue(GalleryLayout.FOOTPRINT.contains(GalleryLayout.DENIED_LAMP));
        assertEquals(GalleryLayout.DENIED_ALARM.plus(0, 1, 0), GalleryLayout.DENIED_LAMP, "the lamp sits on the alarm");
        assertNotEquals(GalleryLayout.INSTALLATION_NAME, GalleryLayout.DENIED_INSTALLATION_NAME);
    }

    @Test
    void deniedDevicesAreInsideTheBayAndLinkFromItsHub() {
        Set<Offset> seen = new HashSet<>(GalleryLayout.devices());
        seen.add(GalleryLayout.HUB);
        for (Offset device : GalleryLayout.deniedDevices()) {
            assertTrue(BAY.contains(device), device + " is inside the bay");
            assertTrue(device.dy() >= 1, device + " stands above the floor layer");
            assertTrue(GalleryLayout.manhattanFromDeniedHub(device) <= DEFAULT_MAX_DEVICE_DISTANCE, device + " can link");
            assertTrue(seen.add(device), device + " is placed once, and not where the Gallery put something");
        }
        assertTrue(BAY.contains(GalleryLayout.DENIED_HUB));
        assertFalse(seen.contains(GalleryLayout.DENIED_HUB), "the bay's hub is never a device");
        assertTrue(GalleryLayout.manhattanFromHub(GalleryLayout.DENIED_HUB) > 0, "two hubs, two installations");
    }

    @Test
    void cellBeamsCrossTheVisitorAndTheFieldSealsTheCell() {
        Offset visitor = GalleryLayout.VISITOR;
        assertTrue(GalleryLayout.inVisitorCell(visitor.dx(), visitor.dz()), "the visitor stands in the cell");
        assertEquals(1, visitor.dy(), "on the floor");
        assertFalse(GalleryLayout.CELL_LASER_WEST_NODES.isEmpty());
        for (Offset west : GalleryLayout.CELL_LASER_WEST_NODES) {
            Offset east = GalleryLayout.cellLaserPartner(west);
            assertEquals(west.dy(), east.dy());
            assertEquals(west.dz(), east.dz());
            assertEquals(GalleryLayout.CELL_MIN_DX, west.dx(), "the west node sits against the bay wall");
            assertEquals(GalleryLayout.CELL_MAX_DX, east.dx(), "the east node sits against the partition");
            assertTrue(GalleryLayout.CELL_LASER_SPAN <= DEFAULT_LASER_RANGE && GalleryLayout.CELL_LASER_SPAN > 1);
            assertEquals(visitor.dz(), west.dz(), "the beam crosses the visitor's cell row");
            assertTrue(visitor.dx() > west.dx() && visitor.dx() < east.dx(), "the visitor stands between the lenses");
            assertTrue(west.dy() >= visitor.dy() && west.dy() <= visitor.dy() + 1, "the beam crosses the visitor's body");
        }
        // The field: valid at the default range, a plane exactly across the cell's front, from floor to wall top.
        assertTrue(FieldGeometry.validate(GalleryLayout.CELL_FIELD_SETTINGS, DEFAULT_FIELD_RANGE, DEFAULT_MAX_FIELD_AREA).isEmpty());
        List<BlockPos> plane = FieldGeometry.positions(GalleryLayout.CELL_FIELD_EMITTER.at(BlockPos.ZERO),
                GalleryLayout.CELL_FIELD_FACING, GalleryLayout.CELL_FIELD_SETTINGS);
        assertEquals((GalleryLayout.CELL_MAX_DX - GalleryLayout.CELL_MIN_DX + 1) * GalleryLayout.PAVILION_TOP_DY, plane.size());
        for (BlockPos p : plane) {
            Offset o = new Offset(p.getX(), p.getY(), p.getZ());
            assertEquals(GalleryLayout.CELL_FIELD_DZ, o.dz(), o + " is on the field line");
            assertTrue(o.dx() >= GalleryLayout.CELL_MIN_DX && o.dx() <= GalleryLayout.CELL_MAX_DX, o + " spans the cell");
            assertTrue(o.dy() >= 1 && o.dy() <= GalleryLayout.PAVILION_TOP_DY, o + " runs floor to wall top");
            assertNotEquals(visitor, o, "the visitor never stands in the plane");
        }
        assertTrue(visitor.dz() < GalleryLayout.CELL_FIELD_DZ, "the visitor is behind the field");
        assertEquals(GalleryLayout.CELL_FIELD_DZ, GalleryLayout.ROOM_FRONT_DZ, "field line and room front align");
        // The camera: behind the visitor, and the visitor's eyes inside its (narrow) cone.
        Offset camera = GalleryLayout.CELL_CAMERA;
        assertTrue(GalleryLayout.inVisitorCell(camera.dx(), camera.dz()) && camera.dz() < visitor.dz());
        double drop = (camera.dy() + 0.5) - (visitor.dy() + 1.62);
        double reach = visitor.dz() - camera.dz();
        assertTrue(Math.toDegrees(Math.atan2(drop, reach)) <= GalleryLayout.CELL_CAMERA_FOV / 2.0, "the visitor is in the cone");
        assertEquals(60, GalleryLayout.CELL_CAMERA_FOV, "the narrowest cone, so no label is ever in it");
    }

    @Test
    void deniedRackHasThreeFramesOnTheEastWall() {
        List<Offset> slots = GalleryLayout.deniedRackSlots();
        assertEquals(GalleryLayout.DENIED_RACK_SLOTS, slots.size());
        assertEquals(3, slots.size(), "the right card, the foreign card, this bay's card for the blast door");
        for (int i = 0; i < slots.size(); i++) {
            Offset slot = slots.get(i);
            assertEquals(GalleryLayout.DENIED_RACK_DX - 1, slot.dx(), "frames hang on the east wall's inner face");
            assertEquals(GalleryLayout.DENIED_MAX_DX - 1, GalleryLayout.DENIED_RACK_DX, "that wall is the bay's inner east layer");
            assertTrue(slot.dz() > GalleryLayout.ROOM_FRONT_DZ && slot.dz() < GalleryLayout.DENIED_THRESHOLD_DZ,
                    "south of the room, inside the bay");
            assertTrue(BAY.contains(slot));
            if (i > 0) {
                assertEquals(2, slot.dz() - slots.get(i - 1).dz(), "two blocks apart");
            }
        }
    }

    @Test
    void offsetsResolveAgainstAnyOrigin() {
        BlockPos origin = new BlockPos(100, 64, -200);
        assertEquals(new BlockPos(100, 66, -213), GalleryLayout.HUB.at(origin));
        assertEquals(new Offset(1, 2, 3).plus(1, 1, 1), new Offset(2, 3, 4));
        assertEquals(new Offset(0, 0, 0).toward(Direction.NORTH, 2), new Offset(0, 0, -2));
    }
}
