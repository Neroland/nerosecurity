package za.co.neroland.nerosecurity.block.field;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldPassabilityUpdate;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * Tells nearby players whether <b>they</b> may pass one emitter's field, so their client predicts collision the way the
 * server will decide it (no rubber-banding). Run by the emitter every {@code fieldSyncIntervalTicks} and at once on a
 * lockdown or state flip.
 *
 * <p>Players within {@code range + 16} (Chebyshev) of the emitter, spectators excluded, get their own verdict,
 * computed by the emitter's verdict cache (the access resolver); a payload is sent only when that player's verdict
 * changed, or the player is a new entity (rejoined, respawned) whose client has forgotten it. A player who leaves the
 * radius (or the server) is told "solid" once and forgotten. This is sync, not authorisation: the server never reads
 * the client's answer back. The payload carries the emitter position and one boolean about the receiver only: never
 * another player's verdict, level, name or UUID.
 *
 * <p>Cost: one pass over {@code level.players()} per emitter per sync interval; at most one resolve per nearby player.
 */
public final class FieldSync {

    /** Margin beyond the emitter's range within which players are kept up to date. */
    public static final int RADIUS_MARGIN = 16;

    /** What was last sent to one player about this emitter. */
    record Sent(boolean passable, int entityId) {
    }

    private FieldSync() {
    }

    public static void pass(ServerLevel level, ForceFieldEmitterBlockEntity emitter) {
        Map<UUID, Sent> lastSent = emitter.lastSent();
        BlockPos origin = emitter.getBlockPos();
        DevicePos at = emitter.devicePos(level);
        int radius = emitter.range() + RADIUS_MARGIN;
        boolean holding = emitter.holdsBlocks();
        Set<UUID> near = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos p = player.blockPosition();
            if (Math.abs(p.getX() - origin.getX()) > radius || Math.abs(p.getY() - origin.getY()) > radius
                    || Math.abs(p.getZ() - origin.getZ()) > radius) {
                continue;
            }
            UUID id = player.getUUID();
            near.add(id);
            // No blocks, nothing to predict: skip the resolve and say "solid".
            boolean passable = holding && emitter.isPassableFor(level, id);
            Sent previous = lastSent.get(id);
            if (previous != null && previous.passable() == passable && previous.entityId() == player.getId()) {
                continue;
            }
            lastSent.put(id, new Sent(passable, player.getId()));
            if (previous == null && !passable) {
                continue; // the client already treats an unknown emitter as solid
            }
            Services.NETWORK.sendToPlayer(player, new FieldPassabilityUpdate(at, passable));
        }
        for (Iterator<Map.Entry<UUID, Sent>> it = lastSent.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Sent> entry = it.next();
            if (near.contains(entry.getKey())) {
                continue;
            }
            it.remove();
            if (entry.getValue().passable()) {
                ServerPlayer gone = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (gone != null) {
                    Services.NETWORK.sendToPlayer(gone, new FieldPassabilityUpdate(at, false));
                }
            }
        }
        emitter.breachCooldowns().prune(level.getGameTime(), NeroSecurityConfig.fieldBreachEventCooldownTicks());
    }
}
