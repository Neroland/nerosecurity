package za.co.neroland.nerosecurity.block.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.compat.claims.ClaimsCompat;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;

/**
 * Places and removes an emitter's field blocks (server). The rules, every one of them a "skip", never a "make room":
 * <ul>
 *   <li>only into air, or into a replaceable plant-like block with no fluid and no block entity (short grass, a snow
 *       layer, {@link #isFree}): a field never replaces a real block, a fluid, a waterlogged block or another
 *       field;</li>
 *   <li>only into loaded chunks inside the world: a chunk is never loaded or force-loaded for a field;</li>
 *   <li>never into a block a living entity's box touches (an entity is never trapped or crushed);</li>
 *   <li>never into a chunk where no owner of the installation may build according to the claim mod
 *       ({@link ClaimsCompat#mayBuild}; true when no claim mod is active).</li>
 * </ul>
 * A skipped position is retried on the next reconcile; one skipped only because an entity stood in it is also retried
 * every {@link ForceFieldEmitterBlockEntity#HELD_RETRY_TICKS} ticks ({@link #retryHeld}), so the gap closes as soon as
 * the entity moves. Removal only ever touches a field block whose block entity points back at this emitter.
 *
 * <p><b>Cost.</b> One reconcile per emitter per {@code fieldReprojectIntervalTicks} (and right after a settings or
 * state change): at most {@code maxFieldArea} block reads, one entity query over the missing positions' bounds, one
 * claim lookup per chunk.
 */
public final class FieldProjector {

    private FieldProjector() {
    }

    /**
     * Brings the world in line with what the emitter should project now.
     *
     * @return whether the emitter's set of owned positions changed (the caller marks it changed)
     */
    public static boolean reconcile(ServerLevel level, ForceFieldEmitterBlockEntity emitter, @Nullable Installation installation) {
        Set<BlockPos> projected = emitter.projectedMutable();
        emitter.heldMutable().clear(); // recomputed below from this pass's entity query
        Set<BlockPos> wanted = emitter.fieldState() == FieldState.PROJECTING && installation != null
                ? new LinkedHashSet<>(FieldGeometry.positions(emitter.getBlockPos(), emitter.facing(), emitter.settings()))
                : Set.of();
        boolean changed = false;
        // Drop what is no longer wanted, and forget loaded positions that no longer hold our block (an orphan that
        // removed itself, a creative break): those are re-placed below if they are wanted and free.
        for (Iterator<BlockPos> it = projected.iterator(); it.hasNext();) {
            BlockPos p = it.next();
            boolean loaded = level.isLoaded(p);
            if (!wanted.contains(p)) {
                if (loaded && ownedBy(level, p, emitter)) {
                    level.removeBlock(p, false);
                }
                it.remove();
                changed = true;
            } else if (loaded && !ownedBy(level, p, emitter)) {
                it.remove();
                changed = true;
            }
        }
        if (wanted.isEmpty()) {
            return changed;
        }
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos p : wanted) {
            if (!projected.contains(p)) {
                missing.add(p);
            }
        }
        if (missing.isEmpty()) {
            return changed;
        }
        return place(level, emitter, installation, missing) || changed;
    }

    /**
     * Retries only the positions the last reconcile skipped because an entity stood in them (one small entity query
     * over those positions). Nothing when the field is not up.
     *
     * @return whether the emitter's set of owned positions changed
     */
    public static boolean retryHeld(ServerLevel level, ForceFieldEmitterBlockEntity emitter, @Nullable Installation installation) {
        Set<BlockPos> held = emitter.heldMutable();
        if (held.isEmpty()) {
            return false;
        }
        if (emitter.fieldState() != FieldState.PROJECTING || installation == null) {
            held.clear();
            return false;
        }
        Set<BlockPos> projected = emitter.projectedMutable();
        List<BlockPos> candidates = new ArrayList<>(held.size());
        for (BlockPos p : held) {
            if (!projected.contains(p)) {
                candidates.add(p);
            }
        }
        held.clear();
        return !candidates.isEmpty() && place(level, emitter, installation, candidates);
    }

    /**
     * Whether a field may be placed into {@code state}: air, or a replaceable block (short grass, a snow layer) with no
     * fluid and no block entity. Never a fluid, a waterlogged block, a real block or another field.
     */
    static boolean isFree(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        return state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && !(state.getBlock() instanceof ForceFieldBlock);
    }

    /** Places field blocks at the free, claim-allowed, unoccupied {@code candidates}; records entity-held ones. */
    private static boolean place(ServerLevel level, ForceFieldEmitterBlockEntity emitter, Installation installation,
            List<BlockPos> candidates) {
        Set<BlockPos> projected = emitter.projectedMutable();
        Set<BlockPos> held = emitter.heldMutable();
        boolean changed = false;
        List<AABB> occupied = occupants(level, candidates);
        Identifier dimension = level.dimension().identifier();
        Map<Long, Boolean> claimOk = new HashMap<>();
        BlockState fieldState = FieldRegistrations.FIELD_BLOCK.get().defaultBlockState()
                .setValue(ForceFieldBlock.LOCKDOWN, emitter.lockdownBoost());
        BlockPos emitterPos = emitter.getBlockPos().immutable();
        for (BlockPos p : candidates) {
            if (!level.isInWorldBounds(p) || !level.isLoaded(p) || !isFree(level.getBlockState(p))) {
                continue;
            }
            int chunkX = p.getX() >> 4;
            int chunkZ = p.getZ() >> 4;
            long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
            Boolean ok = claimOk.get(chunkKey);
            if (ok == null) {
                ok = mayBuild(level, dimension, chunkX, chunkZ, installation);
                claimOk.put(chunkKey, ok);
            }
            if (!ok) {
                continue;
            }
            if (touchesEntity(occupied, p)) {
                held.add(p.immutable());
                continue;
            }
            if (!level.setBlock(p, fieldState, Block.UPDATE_ALL)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof ForceFieldBlockEntity field) {
                field.setEmitterPos(emitterPos);
                projected.add(p.immutable());
                changed = true;
            } else {
                level.removeBlock(p, false); // no block entity: never leave an anonymous field behind
            }
        }
        return changed;
    }

    /** Removes every loaded block this emitter owns and forgets them all (unloaded ones become orphans). */
    public static void collapse(ServerLevel level, ForceFieldEmitterBlockEntity emitter) {
        emitter.heldMutable().clear();
        Set<BlockPos> projected = emitter.projectedMutable();
        if (projected.isEmpty()) {
            return;
        }
        List<BlockPos> owned = new ArrayList<>(projected);
        projected.clear();
        for (BlockPos p : owned) {
            if (level.isLoaded(p) && ownedBy(level, p, emitter)) {
                level.removeBlock(p, false);
            }
        }
    }

    /** Repaints the lockdown look on every loaded block this emitter owns. */
    public static void repaint(ServerLevel level, ForceFieldEmitterBlockEntity emitter, boolean lockdown) {
        BlockPos emitterPos = emitter.getBlockPos().immutable();
        for (BlockPos p : emitter.projectedMutable()) {
            if (!level.isLoaded(p) || !ownedBy(level, p, emitter)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.getValue(ForceFieldBlock.LOCKDOWN) != lockdown) {
                level.setBlock(p, state.setValue(ForceFieldBlock.LOCKDOWN, lockdown), Block.UPDATE_CLIENTS);
                if (level.getBlockEntity(p) instanceof ForceFieldBlockEntity field && !emitterPos.equals(field.emitterPos())) {
                    field.setEmitterPos(emitterPos);
                }
            }
        }
    }

    /**
     * The emitter that owns the field block at {@code fieldPos}, or null when the block is an orphan: no field block
     * entity, no emitter position, the emitter's chunk is not loaded (never loaded from here), no emitter block entity
     * there, or that emitter no longer projects this position.
     */
    public static @Nullable ForceFieldEmitterBlockEntity emitterFor(ServerLevel level, BlockPos fieldPos) {
        if (!(level.getBlockEntity(fieldPos) instanceof ForceFieldBlockEntity field)) {
            return null;
        }
        BlockPos emitterPos = field.emitterPos();
        if (emitterPos == null || !level.isLoaded(emitterPos)) {
            return null;
        }
        if (level.getBlockEntity(emitterPos) instanceof ForceFieldEmitterBlockEntity emitter
                && !emitter.isRemoved() && emitter.projects(fieldPos)) {
            return emitter;
        }
        return null;
    }

    /** Whether the field block at a loaded {@code p} belongs to {@code emitter}. */
    static boolean ownedBy(ServerLevel level, BlockPos p, ForceFieldEmitterBlockEntity emitter) {
        return level.getBlockState(p).getBlock() instanceof ForceFieldBlock
                && level.getBlockEntity(p) instanceof ForceFieldBlockEntity field
                && emitter.getBlockPos().equals(field.emitterPos());
    }

    private static boolean mayBuild(ServerLevel level, Identifier dimension, int chunkX, int chunkZ, Installation installation) {
        try {
            return ClaimsCompat.mayBuild(level.getServer(), dimension, chunkX, chunkZ, installation.owners());
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] claim check failed ({}); field position skipped",
                    e.getClass().getName());
            return false; // fail closed: never project where we cannot tell
        }
    }

    /** Boxes of living, non-spectator entities touching the missing positions (one query per reconcile). */
    private static List<AABB> occupants(ServerLevel level, List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos p : positions) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D).inflate(0.5D);
        List<Entity> found = level.getEntities((Entity) null, bounds,
                e -> e instanceof LivingEntity && !e.isSpectator());
        List<AABB> boxes = new ArrayList<>(found.size());
        for (Entity e : found) {
            boxes.add(e.getBoundingBox());
        }
        return boxes;
    }

    private static boolean touchesEntity(List<AABB> occupied, BlockPos p) {
        if (occupied.isEmpty()) {
            return false;
        }
        AABB cell = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0D, p.getY() + 1.0D, p.getZ() + 1.0D);
        for (AABB box : occupied) {
            if (box.intersects(cell)) {
                return true;
            }
        }
        return false;
    }
}
