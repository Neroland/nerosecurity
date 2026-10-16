package za.co.neroland.nerosecurity.block.field;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The server's answer to "may this player walk through the field blocks of the emitter at {@code emitterPos}".
 * Every answer comes from the emitter's own per-tick verdict cache, which asks {@code field.FieldVerdict} and so
 * the access resolver, by the player's UUID. Anything missing (no block entity at the position, a block entity
 * that is not an emitter) is solid: fail closed.
 *
 * <p>{@link #GENERATION} is a "something changed" counter. Emitters drop their verdict cache when it moves, so
 * a member edit, a lockdown write or a faction tier crossing takes effect in the tick it happens instead of the
 * next one. Bumping it never grants anything.
 */
public final class FieldPassability {

    /** Bumped on member edits, lockdown writes and faction tier crossings. Read by every emitter's cache. */
    public static volatile long GENERATION;

    private FieldPassability() {
    }

    /** What a force field emitter block entity implements so this lookup can reach its verdict cache. */
    public interface Emitter {
        /** This emitter's verdict for {@code actor} (cached for at most one game tick and one generation). */
        boolean isPassableFor(ServerLevel level, UUID actor);
    }

    /** Marks every emitter's verdict cache stale. */
    public static void bump() {
        GENERATION++;
    }

    /** Whether {@code actor} may pass the field blocks of the emitter at {@code emitterPos}; false when in doubt. */
    public static boolean isPassable(ServerLevel level, BlockPos emitterPos, UUID actor) {
        if (level == null || emitterPos == null || actor == null) {
            return false;
        }
        try {
            return level.getBlockEntity(emitterPos) instanceof Emitter emitter && emitter.isPassableFor(level, actor);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
