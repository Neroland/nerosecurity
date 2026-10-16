package za.co.neroland.nerosecurity.compat.colonies;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocolonies.colony.Colony;
import za.co.neroland.nerocolonies.colony.ColonyApi;
import za.co.neroland.nerocolonies.colony.ColonyPermissions;
import za.co.neroland.nerocolonies.colony.ColonyState;
import za.co.neroland.nerocolonies.colony.Outpost;

/**
 * The <b>only</b> class in NeroSecurity that imports {@code za.co.neroland.nerocolonies}. NeroColonies is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link ColoniesCompat} only after {@code isModLoaded("nerocolonies")}, and left out of a build with
 * {@code nerocolonies_compat=false}.
 *
 * <p>Roles come from {@code ColonyPermissions.roleOf} (the stored role, no operator elevation: NeroSecurity has
 * its own operator rule). Every answer is neutral (false, STRANGER, null) for a null server and whenever
 * NeroColonies throws. Never logs a player id.
 */
public final class NeroColoniesBridge implements ColoniesBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroColoniesBridge() {
    }

    @Override
    public boolean exists(@Nullable MinecraftServer server, UUID colony) {
        if (server == null || colony == null) {
            return false;
        }
        try {
            return ColonyApi.exists(server, colony);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public Role roleOf(@Nullable MinecraftServer server, UUID colony, UUID player) {
        if (server == null || colony == null || player == null) {
            return Role.STRANGER;
        }
        try {
            Colony found = ColonyState.get(server).colony(colony);
            if (found == null) {
                return Role.STRANGER;
            }
            ColonyPermissions.Role role = ColonyPermissions.roleOf(server, found, player);
            return role == null ? Role.STRANGER : Role.valueOf(role.name());
        } catch (RuntimeException | LinkageError e) {
            return Role.STRANGER;
        }
    }

    @Override
    public @Nullable UUID colonyAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null) {
            return null;
        }
        try {
            Colony colony = ColonyApi.colonyAt(level, pos);
            if (colony != null) {
                return colony.colonyId();
            }
            // ColonyApi.colonyAt does not cover outposts: an outpost's claim counts as its parent colony.
            Outpost outpost = ColonyState.get(level.getServer()).outpostAt(level.dimension(), pos);
            return outpost == null ? null : outpost.parentColonyId();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
