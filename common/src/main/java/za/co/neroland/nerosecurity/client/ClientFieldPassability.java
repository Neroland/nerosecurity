package za.co.neroland.nerosecurity.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import za.co.neroland.nerosecurity.installation.DevicePos;

/**
 * The client's copy of the server's verdict "may <b>I</b> pass the field of the emitter at X". Holds booleans about
 * the local player only, keyed by emitter position and dimension (so a dimension change needs no hook), filled by
 * the server's passability payload and cleared on disconnect. Consulted only to predict the local player's
 * collision; the server never reads it and decides for itself. Unknown emitters are solid.
 *
 * <p>Common package on purpose: no client-only imports, so the field block's collision shape can call it.
 */
public final class ClientFieldPassability {

    private static final Map<DevicePos, Boolean> PASSABLE = new ConcurrentHashMap<>();

    private ClientFieldPassability() {
    }

    /** Applies one update from the server. Only passable emitters are kept; false removes the entry. */
    public static void set(DevicePos emitter, boolean passable) {
        if (emitter == null) {
            return;
        }
        if (passable) {
            PASSABLE.put(emitter, Boolean.TRUE);
        } else {
            PASSABLE.remove(emitter);
        }
    }

    /** Whether the server last said the local player may pass {@code emitter}; false when it never said. */
    public static boolean isPassable(DevicePos emitter) {
        return emitter != null && PASSABLE.getOrDefault(emitter, Boolean.FALSE);
    }

    /** {@link #isPassable(DevicePos)} for an emitter at {@code emitterPos} in {@code level}'s dimension. */
    public static boolean isPassable(Level level, BlockPos emitterPos) {
        if (level == null || emitterPos == null) {
            return false;
        }
        return isPassable(new DevicePos(level.dimension().identifier(), emitterPos));
    }

    /** Disconnect: forget everything. */
    public static void clear() {
        PASSABLE.clear();
    }

    /** How many emitters the local player may currently pass (tests and debug). */
    public static int size() {
        return PASSABLE.size();
    }
}
