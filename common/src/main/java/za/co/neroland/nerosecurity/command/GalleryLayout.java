package za.co.neroland.nerosecurity.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldShape;

/**
 * The gallery's floor plan, as pure numbers: every exhibit position is an {@link Offset} from the invoking
 * operator's block position (north is {@code -z}, the floor layer is {@code dy = 0}), so the plan can be
 * checked in a plain JVM and the builder only has to place what it is told. Nothing here touches a level.
 *
 * <pre>
 *            dz
 *           -52  ################ ACCESS DENIED bay ################   second installation, owned by nobody
 *                #  cell: visitor, 2 beams, camera  |  hub plinth  |  members' room + keycard door  #
 *                #  (front sealed by a force field) | monitor alarm|  card rack on the east wall    #
 *           -39  ####          (open front)         chest           (open front)                ####
 *           -38  //////////////// red hazard threshold ////////////////
 *           -37  ####################################  ####################################
 *                #  FIELD BAY                       #  #  LASER CORRIDOR                  #
 *                #   emitter on back wall -> 5x5    #  #   3 beam pairs across x +6..+12  #
 *                #   wall at dz -32; 3x3 box at -28 #  #   camera on the back wall        #
 *           -26  ####          (open)            ####  ####          (open)            ####
 *           -24                       walkway behind the doors
 *           -22  ===== [door] ========= [blast] ======== [door] =====   door wall, camera
 *           -21  /////////////////// hazard threshold ///////////////////
 *                |rack|          alarm post (0,-17)                |rack|
 *           -13  |item|   programmer   [HUB on plinth]   monitor   |card|
 *            -8  |rack|               secure chest                 |rack|
 *            -4  |    |                                            |    |
 *             0                        operator (looking north)
 *                dx -16                          0                          +16
 * </pre>
 */
public final class GalleryLayout {

    /** The entity tag every gallery entity (label stand, item frame, the visitor) carries, so a clear removes only ours. */
    public static final String ENTITY_TAG = "nerosecurity_gallery";

    /** The demo installation's display name. */
    public static final String INSTALLATION_NAME = "Gallery";

    /** The access-denied bay's installation: owned by nobody, so no player is a member of it. */
    public static final String DENIED_INSTALLATION_NAME = "Access denied";

    /** The visitor's name tag (a passive, no-AI villager; never a player). */
    public static final String VISITOR_NAME = "Visitor — not a member";

    // --- footprint --------------------------------------------------------------------------------

    public static final int MIN_DX = -16;
    public static final int MAX_DX = 16;
    public static final int MIN_DZ = -52;
    public static final int MAX_DZ = -3;
    /** Highest layer above the floor that holds anything (walls reach 5, lanterns and light blocks sit at 6). */
    public static final int TOP_DY = 6;
    /** Layers the clear sweeps, floor included: a little above the top so a stray label is caught too. */
    public static final int CLEAR_DY = TOP_DY + 2;

    /** An integer offset from the operator's position. */
    public record Offset(int dx, int dy, int dz) {

        public BlockPos at(BlockPos origin) {
            return origin.offset(dx, dy, dz);
        }

        public Offset plus(int ddx, int ddy, int ddz) {
            return new Offset(dx + ddx, dy + ddy, dz + ddz);
        }

        public Offset toward(Direction direction, int steps) {
            return new Offset(dx + direction.getStepX() * steps, dy + direction.getStepY() * steps,
                    dz + direction.getStepZ() * steps);
        }
    }

    /** An inclusive box of offsets. */
    public record Box(int minDx, int minDy, int minDz, int maxDx, int maxDy, int maxDz) {

        public boolean contains(Offset o) {
            return o.dx() >= minDx && o.dx() <= maxDx && o.dy() >= minDy && o.dy() <= maxDy
                    && o.dz() >= minDz && o.dz() <= maxDz;
        }

        public boolean contains(Box other) {
            return other.minDx >= minDx && other.maxDx <= maxDx && other.minDy >= minDy && other.maxDy <= maxDy
                    && other.minDz >= minDz && other.maxDz <= maxDz;
        }

        public int sizeX() {
            return maxDx - minDx + 1;
        }

        public int sizeZ() {
            return maxDz - minDz + 1;
        }
    }

    /** Everything the gallery may place: floor layer up to {@link #TOP_DY}. */
    public static final Box FOOTPRINT = new Box(MIN_DX, 0, MIN_DZ, MAX_DX, TOP_DY, MAX_DZ);

    /** What {@code gallery clear} wipes: the footprint plus two layers of headroom. */
    public static final Box CLEAR_BOX = new Box(MIN_DX, 0, MIN_DZ, MAX_DX, CLEAR_DY, MAX_DZ);

    // --- the plaza ------------------------------------------------------------------------------------

    public static final Offset PLINTH_CENTRE = new Offset(0, 1, -13);
    public static final Offset HUB = PLINTH_CENTRE.plus(0, 1, 0);
    public static final Offset PROGRAMMER = new Offset(-3, 1, -13);
    public static final Offset MONITOR = new Offset(3, 1, -13);
    public static final Offset CHEST = new Offset(0, 1, -8);
    /** The alarm sits on a three-high post behind the hub, on the axis. */
    public static final Offset ALARM_POST_BASE = new Offset(0, 1, -17);
    public static final int ALARM_POST_HEIGHT = 3;
    public static final Offset ALARM = ALARM_POST_BASE.plus(0, ALARM_POST_HEIGHT, 0);

    // --- the door wall ------------------------------------------------------------------------------

    /** The door wall is two blocks thick: devices mount on its south face, their batteries hide inside it. */
    public static final int DOOR_WALL_FRONT_DZ = -22;
    public static final int DOOR_WALL_BACK_DZ = -23;
    public static final int DOOR_WALL_MIN_DX = -14;
    public static final int DOOR_WALL_MAX_DX = 14;
    public static final int WALL_TOP_DY = 4;
    /** The hazard threshold strip runs in front of the door wall. */
    public static final int THRESHOLD_DZ = -21;

    /** A 1x2 Security Door, closed, at the member default. */
    public static final Offset SECURITY_DOOR_CLOSED = new Offset(-7, 1, DOOR_WALL_FRONT_DZ);
    /** A 2x2 Blast Door (controller = bottom-left for a south-facing door), OFFICER and keycard-only. */
    public static final Offset BLAST_DOOR = new Offset(-1, 1, DOOR_WALL_FRONT_DZ);
    /** A 1x2 Security Door held open by a lever (redstone mode "open while powered"). */
    public static final Offset SECURITY_DOOR_OPEN = new Offset(6, 1, DOOR_WALL_FRONT_DZ);
    /** The lever hangs on the wall beside the open door's upper part; the wall block carries the signal. */
    public static final Offset DOOR_LEVER = new Offset(5, 2, THRESHOLD_DZ);
    /** The camera that watches the plaza, mounted on the door wall's south face. */
    public static final Offset PLAZA_CAMERA = new Offset(10, 3, THRESHOLD_DZ);

    // --- the two pavilions ---------------------------------------------------------------------------

    public static final int PAVILION_BACK_DZ = -36;      // outer layer of the back wall (-36, -35)
    public static final int PAVILION_FRONT_DZ = -26;     // southernmost wall cell (open front)
    public static final int PAVILION_TOP_DY = 5;
    /** Interior x of the west (field) pavilion: -12..-6; walls at -14,-13 and -5,-4. */
    public static final int WEST_PAVILION_MIN_DX = -14;
    public static final int WEST_PAVILION_MAX_DX = -4;
    /** Interior x of the east (laser) pavilion: 6..12; walls at 4,5 and 13,14. */
    public static final int EAST_PAVILION_MIN_DX = 4;
    public static final int EAST_PAVILION_MAX_DX = 14;

    /** Field wall: emitter on the west pavilion's back wall, facing south; a 5x5 plane two blocks out. */
    public static final Offset FIELD_WALL_EMITTER = new Offset(-9, 3, PAVILION_BACK_DZ + 2);
    public static final Direction FIELD_WALL_FACING = Direction.SOUTH;
    public static final FieldSettings FIELD_WALL_SETTINGS = FieldSettings.DEFAULT;
    /** Field box: emitter on the pavilion floor facing up; a 3x3x3 shell floats above it. */
    public static final Offset FIELD_BOX_EMITTER = new Offset(-9, 1, -28);
    public static final Direction FIELD_BOX_FACING = Direction.UP;
    public static final FieldSettings FIELD_BOX_SETTINGS =
            new FieldSettings(FieldShape.BOX, 2, 3, 3, 3, DeviceAccessSettings.DEFAULT, true);

    /** Laser corridor: the west node of each pair, facing east; its partner is {@link #LASER_SPAN} blocks away. */
    public static final int LASER_WEST_DX = 6;
    public static final int LASER_SPAN = 6;
    public static final int LASER_EAST_DX = LASER_WEST_DX + LASER_SPAN;
    public static final List<Offset> LASER_WEST_NODES = List.of(
            new Offset(LASER_WEST_DX, 1, -28),
            new Offset(LASER_WEST_DX, 3, -28),
            new Offset(LASER_WEST_DX, 2, -32));
    /** The camera at the end of the laser corridor, on the back wall, looking south down the beams. */
    public static final Offset CORRIDOR_CAMERA = new Offset(9, 4, PAVILION_BACK_DZ + 2);

    // --- the racks ------------------------------------------------------------------------------------

    public static final int RACK_MIN_DZ = -16;
    public static final int RACK_MAX_DZ = -4;
    public static final int RACK_TOP_DY = 3;
    public static final int RACK_FRAME_DY = 2;
    /** The keycard rack wall stands at dx +9; its frames hang on the west face, at dx +8. */
    public static final int CARD_RACK_DX = 9;
    /** The item rack wall stands at dx -9; its frames hang on the east face, at dx -8. */
    public static final int ITEM_RACK_DX = -9;
    public static final int RACK_SLOTS = 6;

    /** The one partner of each laser node is {@link #LASER_SPAN} blocks east of it, facing back. */
    public static Offset laserPartner(Offset westNode) {
        return westNode.plus(LASER_SPAN, 0, 0);
    }

    /** Frame positions of a rack: {@link #RACK_SLOTS} slots two blocks apart, hanging on the given wall face. */
    public static List<Offset> rackSlots(int wallDx, Direction frameFacing) {
        List<Offset> out = new ArrayList<>(RACK_SLOTS);
        int frameDx = wallDx + frameFacing.getStepX();
        for (int i = 0; i < RACK_SLOTS; i++) {
            out.add(new Offset(frameDx, RACK_FRAME_DY, RACK_MIN_DZ + 1 + i * 2));
        }
        return out;
    }

    /** The levels on the keycard rack, left to right, then the two extra slots (encrypted member, blank). */
    public static final List<AccessLevel> CARD_LEVELS =
            List.of(AccessLevel.PUBLIC, AccessLevel.MEMBER, AccessLevel.OFFICER, AccessLevel.OWNER);

    // --- the access-denied bay (the north end, behind the pavilions) ---------------------------------

    /**
     * The bay is a U of two-thick walls open to the south, like the pavilions but wider: walls at
     * {@code -12,-11} and {@code 11,12}, interior x {@code -10..10}, back wall at {@code -52,-51}, interior
     * dz {@code -50..-40}, side walls down to {@link #DENIED_FRONT_DZ}. It holds a second installation that
     * nobody is a member of, so every device in it refuses everyone but an operator.
     */
    public static final int DENIED_BACK_DZ = -52;        // outer layer of the back wall (-52, -51)
    public static final int DENIED_FRONT_DZ = -39;       // southernmost wall cell (open front)
    public static final int DENIED_MIN_DX = -12;
    public static final int DENIED_MAX_DX = 12;
    /** The red hazard strip across the bay's open front. */
    public static final int DENIED_THRESHOLD_DZ = -38;
    /** Where the bay's floating title hangs, over the threshold. */
    public static final Offset DENIED_TITLE = new Offset(0, 4, DENIED_THRESHOLD_DZ);
    /** The note for operators (who are always let through), just inside the bay. */
    public static final Offset DENIED_NOTE = new Offset(0, 3, -41);

    /** The bay's own hub, on a raised 3x3 plinth against the back wall. */
    public static final Offset DENIED_PLINTH_CENTRE = new Offset(0, 1, -48);
    public static final Offset DENIED_HUB = DENIED_PLINTH_CENTRE.plus(0, 1, 0);
    public static final Offset DENIED_MONITOR = new Offset(3, 1, -48);
    /** The bay's alarm on a two-high post; a redstone lamp on top blinks with its redstone pulse. */
    public static final Offset DENIED_ALARM_POST_BASE = new Offset(-3, 1, -49);
    public static final int DENIED_ALARM_POST_HEIGHT = 2;
    public static final Offset DENIED_ALARM = DENIED_ALARM_POST_BASE.plus(0, DENIED_ALARM_POST_HEIGHT, 0);
    public static final Offset DENIED_LAMP = DENIED_ALARM.plus(0, 1, 0);
    /** A MEMBER secure chest on the bay's open floor: it refuses anyone who is not a member (everyone). */
    public static final Offset DENIED_CHEST = new Offset(0, 1, -43);

    /**
     * The visitor cell (west): interior x {@code -10..-6}, dz {@code -50..-46}; the bay wall to its west, a
     * two-thick partition at {@code -5,-4} to its east, and its front at {@link #CELL_FIELD_DZ} sealed by a
     * 5x5 force-field plane. Inside stands the visitor, with two beams across it and a camera over it.
     */
    public static final int CELL_MIN_DX = -10;
    public static final int CELL_MAX_DX = -6;
    public static final int CELL_MIN_DZ = -50;
    public static final int CELL_MAX_DZ = -46;
    public static final int CELL_PARTITION_MIN_DX = -5;
    public static final int CELL_PARTITION_MAX_DX = -4;
    /** The plane that seals the cell's front (the partition runs down to it). */
    public static final int CELL_FIELD_DZ = -45;
    /** The visitor stands at the front of the cell, against the field, in both beams and in the camera's cone. */
    public static final Offset VISITOR = new Offset(-8, 1, -46);
    /** The cell's emitter, on the back wall's face; its plane is five blocks out at {@link #CELL_FIELD_DZ}. */
    public static final Offset CELL_FIELD_EMITTER = new Offset(-8, 3, DENIED_BACK_DZ + 2);
    public static final Direction CELL_FIELD_FACING = Direction.SOUTH;
    public static final FieldSettings CELL_FIELD_SETTINGS =
            new FieldSettings(FieldShape.PLANE, 5, 5, 5, 1, DeviceAccessSettings.DEFAULT, true);
    /** The cell camera, over the emitter, looking south at the visitor. */
    public static final Offset CELL_CAMERA = new Offset(-8, 4, DENIED_BACK_DZ + 2);
    /**
     * The cell camera's field of view: the narrowest, so its cone stays inside the cell's width out to the bay's
     * front and never reaches a floating label (label stands are living entities to a sensor set to every living
     * thing; those in front of the cell are behind the field as well, which blocks a non-player's line of sight).
     */
    public static final int CELL_CAMERA_FOV = 60;
    /** The west node of each cell beam, on the bay wall's face; the partner is {@link #CELL_LASER_SPAN} east. */
    public static final int CELL_LASER_WEST_DX = CELL_MIN_DX;
    public static final int CELL_LASER_SPAN = CELL_MAX_DX - CELL_MIN_DX;
    public static final List<Offset> CELL_LASER_WEST_NODES = List.of(
            new Offset(CELL_LASER_WEST_DX, 1, VISITOR.dz()),
            new Offset(CELL_LASER_WEST_DX, 2, VISITOR.dz()));

    /**
     * The members' room (east): interior x {@code 6..10}, dz {@code -50..-46}, behind a two-thick partition
     * at {@code 4,5} and a one-thick front wall at {@link #ROOM_FRONT_DZ} that holds the keycard-only door.
     */
    public static final int ROOM_MIN_DX = 6;
    public static final int ROOM_MAX_DX = 10;
    public static final int ROOM_PARTITION_MIN_DX = 4;
    public static final int ROOM_PARTITION_MAX_DX = 5;
    public static final int ROOM_FRONT_DZ = -45;
    /** A 1x2 Security Door in the room's front wall: MEMBER, keycard only. */
    public static final Offset DENIED_DOOR = new Offset(8, 1, ROOM_FRONT_DZ);
    /** What a visitor who got in would find. */
    public static final Offset ROOM_LABEL = new Offset(8, 3, -48);

    /** The bay's card rack: three glow frames on the east wall's inner face (wall at +11, frames at +10). */
    public static final int DENIED_RACK_DX = 11;
    public static final int DENIED_RACK_SLOTS = 3;
    public static final int DENIED_RACK_MIN_DZ = -44;

    /** Frame positions of the bay's rack, south to north: visitor card, foreign Gallery card, this bay's OFFICER card. */
    public static List<Offset> deniedRackSlots() {
        List<Offset> out = new ArrayList<>(DENIED_RACK_SLOTS);
        for (int i = 0; i < DENIED_RACK_SLOTS; i++) {
            out.add(new Offset(DENIED_RACK_DX - 1, RACK_FRAME_DY, DENIED_RACK_MIN_DZ + i * 2));
        }
        return out;
    }

    /** The partner of a cell beam node is {@link #CELL_LASER_SPAN} blocks east of it, facing back. */
    public static Offset cellLaserPartner(Offset westNode) {
        return westNode.plus(CELL_LASER_SPAN, 0, 0);
    }

    /** Whether a floor cell is inside the visitor cell (between its walls, behind its field). */
    public static boolean inVisitorCell(int dx, int dz) {
        return dx >= CELL_MIN_DX && dx <= CELL_MAX_DX && dz >= CELL_MIN_DZ && dz <= CELL_MAX_DZ;
    }

    /** Whether a floor cell is on the red hazard strip across the bay's front. */
    public static boolean onDeniedThreshold(int dx, int dz) {
        return dz == DENIED_THRESHOLD_DZ && dx > DENIED_MIN_DX + 1 && dx < DENIED_MAX_DX - 1;
    }

    // --- surfaces -------------------------------------------------------------------------------------

    /** Whether a floor cell is on the hazard ring two blocks out from the plinth centre. */
    public static boolean onPlinthRing(int dx, int dz) {
        return Math.max(Math.abs(dx - PLINTH_CENTRE.dx()), Math.abs(dz - PLINTH_CENTRE.dz())) == 2;
    }

    /** Whether a floor cell is part of the raised plinth (the 3x3 under the hub). */
    public static boolean onPlinth(int dx, int dz) {
        return Math.max(Math.abs(dx - PLINTH_CENTRE.dx()), Math.abs(dz - PLINTH_CENTRE.dz())) <= 1;
    }

    /** Whether a floor cell is on the central spine (three wide, from the operator up to the door wall). */
    public static boolean onSpine(int dx, int dz) {
        return Math.abs(dx) <= 1 && dz <= MAX_DZ - 1 && dz >= DOOR_WALL_BACK_DZ;
    }

    /** Whether a floor cell is on the hazard threshold in front of the door wall. */
    public static boolean onThreshold(int dx, int dz) {
        return dz == THRESHOLD_DZ && dx >= DOOR_WALL_MIN_DX + 1 && dx <= DOOR_WALL_MAX_DX - 1;
    }

    /** Whether a floor cell is on the outermost ring of the footprint. */
    public static boolean onBorder(int dx, int dz) {
        return dx == MIN_DX || dx == MAX_DX || dz == MIN_DZ || dz == MAX_DZ;
    }

    /** Hazard stripes alternate on cell parity, so a strip reads as yellow-black chevrons from any side. */
    public static boolean hazardYellow(int dx, int dz) {
        return Math.floorMod(dx + dz, 2) == 0;
    }

    /** Lantern seats: on the door wall top, the rack tops, the pavilion outer walls and the bay's outer walls. */
    public static List<Offset> lanterns() {
        List<Offset> out = new ArrayList<>();
        for (int dx : new int[] {-13, -10, -3, 3, 10, 13}) {
            out.add(new Offset(dx, WALL_TOP_DY + 1, DOOR_WALL_FRONT_DZ));
        }
        for (int dz : new int[] {RACK_MIN_DZ, -10, RACK_MAX_DZ}) {
            out.add(new Offset(CARD_RACK_DX, RACK_TOP_DY + 1, dz));
            out.add(new Offset(ITEM_RACK_DX, RACK_TOP_DY + 1, dz));
        }
        for (int dz : new int[] {-35, -31, -27}) {
            out.add(new Offset(WEST_PAVILION_MIN_DX, PAVILION_TOP_DY + 1, dz));
            out.add(new Offset(WEST_PAVILION_MAX_DX, PAVILION_TOP_DY + 1, dz));
            out.add(new Offset(EAST_PAVILION_MIN_DX, PAVILION_TOP_DY + 1, dz));
            out.add(new Offset(EAST_PAVILION_MAX_DX, PAVILION_TOP_DY + 1, dz));
        }
        for (int dz : new int[] {-50, -45, -40}) {
            out.add(new Offset(DENIED_MIN_DX, PAVILION_TOP_DY + 1, dz));
            out.add(new Offset(DENIED_MAX_DX, PAVILION_TOP_DY + 1, dz));
        }
        return out;
    }

    /** Invisible full-brightness light blocks that fill the exhibit evenly, all above head height. */
    public static List<Offset> lights() {
        List<Offset> out = new ArrayList<>();
        for (int dx : new int[] {-6, 0, 6}) {
            for (int dz : new int[] {-19, -13, -7}) {
                out.add(new Offset(dx, TOP_DY, dz));
            }
        }
        out.add(new Offset(-9, TOP_DY, -30));
        out.add(new Offset(9, TOP_DY, -30));
        out.add(new Offset(0, TOP_DY, -25));
        out.add(new Offset(0, TOP_DY, -45));
        out.add(new Offset(-8, TOP_DY, -43));
        out.add(new Offset(8, TOP_DY, -43));
        return out;
    }

    /** Every device position the demo installation links, for bounds and distance checks. */
    public static List<Offset> devices() {
        List<Offset> out = new ArrayList<>();
        out.add(PROGRAMMER);
        out.add(MONITOR);
        out.add(CHEST);
        out.add(ALARM);
        out.add(SECURITY_DOOR_CLOSED);
        out.add(BLAST_DOOR);
        out.add(SECURITY_DOOR_OPEN);
        out.add(PLAZA_CAMERA);
        out.add(CORRIDOR_CAMERA);
        out.add(FIELD_WALL_EMITTER);
        out.add(FIELD_BOX_EMITTER);
        for (Offset node : LASER_WEST_NODES) {
            out.add(node);
            out.add(laserPartner(node));
        }
        return out;
    }

    /** Every device position the access-denied installation links. */
    public static List<Offset> deniedDevices() {
        List<Offset> out = new ArrayList<>();
        out.add(DENIED_MONITOR);
        out.add(DENIED_ALARM);
        out.add(DENIED_CHEST);
        out.add(DENIED_DOOR);
        out.add(CELL_FIELD_EMITTER);
        out.add(CELL_CAMERA);
        for (Offset node : CELL_LASER_WEST_NODES) {
            out.add(node);
            out.add(cellLaserPartner(node));
        }
        return out;
    }

    /** Manhattan distance from the hub to an offset (the link-distance rule). */
    public static int manhattanFromHub(Offset o) {
        return manhattan(HUB, o);
    }

    /** Manhattan distance from the access-denied bay's hub to an offset. */
    public static int manhattanFromDeniedHub(Offset o) {
        return manhattan(DENIED_HUB, o);
    }

    private static int manhattan(Offset a, Offset b) {
        return Math.abs(b.dx() - a.dx()) + Math.abs(b.dy() - a.dy()) + Math.abs(b.dz() - a.dz());
    }

    private GalleryLayout() {
    }
}
