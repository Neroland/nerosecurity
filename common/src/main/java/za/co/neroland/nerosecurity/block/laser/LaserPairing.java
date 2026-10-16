package za.co.neroland.nerosecurity.block.laser;

import java.util.function.IntFunction;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

/**
 * The laser grid node's geometry and pacing rules, kept apart from the block entity so they run in a
 * plain JVM.
 *
 * <ul>
 *   <li><b>Pairing</b>: walk {@code 1..range} blocks along the node's facing and stop at the first block
 *       that is neither air nor replaceable. If that block is a laser grid node facing back at this one,
 *       it is the partner. A block in the way breaks the pair, so the beam itself needs no raycast.
 *       A facing node only pairs when it is linked to the <b>same installation</b>; a stranger's (or an
 *       unlinked) node blocks, so nobody can extend an owner's beam out into public ground.</li>
 *   <li><b>One sweep per pair</b>: both nodes find each other, and only the one with the smaller
 *       {@link BlockPos#asLong()} sweeps, unless that one is not armed, in which case the other one
 *       does (so a pair never goes blind because one end lost power).</li>
 *   <li><b>Beam box</b>: 0.3 blocks wide, from this node's face centre to the partner's face centre.</li>
 *   <li><b>Staggering</b>: a node sweeps when {@code (gameTime + pos.asLong()) mod interval == 0}, so
 *       nodes placed together do not all scan on the same tick.</li>
 * </ul>
 */
public final class LaserPairing {

    /** Beam thickness in blocks. */
    public static final double BEAM_WIDTH = 0.3D;
    /** Blocks of range one range module adds. */
    public static final int RANGE_PER_MODULE = 4;
    /** Ticks between pairing re-checks (also re-run on a neighbour change). */
    public static final int PAIR_INTERVAL_TICKS = 20;

    /** What the walk finds at one step along the facing. */
    public enum Cell {
        /** Air or a replaceable block: the beam passes; keep walking. */
        OPEN,
        /** A laser grid node facing back toward the walking node: the partner. */
        FACING_NODE,
        /** Anything else (including a node facing another way, or an unloaded position): no partner. */
        BLOCKED
    }

    private LaserPairing() {
    }

    /** {@code min(max, base + 4 x modules)}, never below 1. */
    public static int range(int base, int max, int rangeModules) {
        long wanted = Math.max(0, base) + RANGE_PER_MODULE * (long) Math.max(0, rangeModules);
        return (int) Math.max(1L, Math.min(Math.max(1, max), wanted));
    }

    /**
     * The pure walk: the distance (1..range) of the partner, or 0 when there is none. Bounded by
     * {@code range}; {@code cellAt} is asked for each distance at most once.
     */
    public static int partnerDistance(int range, IntFunction<Cell> cellAt) {
        for (int distance = 1; distance <= range; distance++) {
            Cell cell = cellAt.apply(distance);
            if (cell == Cell.FACING_NODE) {
                return distance;
            }
            if (cell != Cell.OPEN) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * The partner of a node at {@code pos} facing {@code facing}, or null. Never loads a chunk: an
     * unloaded position counts as blocked. {@code sameInstallation} answers whether a node at a position
     * is linked to the walking node's installation; a facing node for which it is false blocks.
     */
    public static @Nullable BlockPos findPartner(Level level, BlockPos pos, Direction facing, int range,
            Predicate<BlockPos> sameInstallation) {
        int distance = partnerDistance(range, d -> cellAt(level, pos.relative(facing, d), facing, sameInstallation));
        return distance == 0 ? null : pos.relative(facing, distance).immutable();
    }

    private static Cell cellAt(Level level, BlockPos at, Direction facing, Predicate<BlockPos> sameInstallation) {
        if (!level.isLoaded(at)) {
            return Cell.BLOCKED;
        }
        BlockState state = level.getBlockState(at);
        boolean node = state.getBlock() instanceof LaserGridNodeBlock;
        Direction nodeFacing = node ? state.getValue(LaserGridNodeBlock.FACING) : null;
        // Only a node that would pair is asked about its installation (one store lookup per walk).
        boolean same = node && nodeFacing == facing.getOpposite() && sameInstallation.test(at);
        return cell(node, nodeFacing, facing, !node && (state.isAir() || state.canBeReplaced()), same);
    }

    /**
     * The pure cell rule without the installation check (both nodes taken as linked to the same
     * installation).
     */
    public static Cell cell(boolean node, @Nullable Direction nodeFacing, Direction walking, boolean openSpace) {
        return cell(node, nodeFacing, walking, openSpace, true);
    }

    /**
     * The pure cell rule: a node facing back ({@code walking.getOpposite()}) and linked to the same
     * installation is the partner; any other node blocks (a stranger's or an unlinked node included); air
     * or a replaceable block lets the beam through; anything else blocks.
     */
    public static Cell cell(boolean node, @Nullable Direction nodeFacing, Direction walking, boolean openSpace,
            boolean sameInstallation) {
        if (node) {
            return nodeFacing == walking.getOpposite() && sameInstallation ? Cell.FACING_NODE : Cell.BLOCKED;
        }
        return openSpace ? Cell.OPEN : Cell.BLOCKED;
    }

    /**
     * Whether the node at {@code self} sweeps the pair it forms with {@code partner}: the smaller
     * {@code asLong()} sweeps; the larger one only while the smaller one is not armed.
     */
    public static boolean sweeps(long self, long partner, boolean partnerArmed) {
        return self < partner || !partnerArmed;
    }

    /** Whether a sweep is due this tick ({@code interval} clamped to 1..20). */
    public static boolean scanDue(long gameTime, long posKey, int interval) {
        int every = Math.max(1, Math.min(20, interval));
        return Math.floorMod(gameTime + posKey, (long) every) == 0L;
    }

    /** Whether a pairing re-check is due this tick. */
    public static boolean pairDue(long gameTime, long posKey) {
        return Math.floorMod(gameTime + posKey, (long) PAIR_INTERVAL_TICKS) == 0L;
    }

    /**
     * The beam volume between a node at {@code from} and its partner at {@code to} (on one axis): a
     * {@value #BEAM_WIDTH}-wide box from this node's face centre to the partner's face centre. Adjacent
     * nodes give an empty-length box (the block entity does not sweep it).
     */
    public static AABB beamBox(BlockPos from, BlockPos to) {
        double half = BEAM_WIDTH / 2.0D;
        double fx = from.getX() + 0.5D;
        double fy = from.getY() + 0.5D;
        double fz = from.getZ() + 0.5D;
        double tx = to.getX() + 0.5D;
        double ty = to.getY() + 0.5D;
        double tz = to.getZ() + 0.5D;
        double minX = Math.min(fx, tx);
        double maxX = Math.max(fx, tx);
        double minY = Math.min(fy, ty);
        double maxY = Math.max(fy, ty);
        double minZ = Math.min(fz, tz);
        double maxZ = Math.max(fz, tz);
        // Along the beam axis: from face to face (half a block in from each centre); across it: the width.
        if (maxX > minX) {
            minX += 0.5D;
            maxX -= 0.5D;
        } else {
            minX -= half;
            maxX += half;
        }
        if (maxY > minY) {
            minY += 0.5D;
            maxY -= 0.5D;
        } else {
            minY -= half;
            maxY += half;
        }
        if (maxZ > minZ) {
            minZ += 0.5D;
            maxZ -= 0.5D;
        } else {
            minZ -= half;
            maxZ += half;
        }
        return new AABB(minX, minY, minZ, Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
    }
}
