package za.co.neroland.nerosecurity.compat.colonies;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

/**
 * The seam between NeroSecurity and NeroColonies, in NeroSecurity types only. The one implementation
 * ({@code NeroColoniesBridge}) is the only class that imports NeroColonies, listed in {@code META-INF/services},
 * found by {@link ColoniesCompat} only when NeroColonies is loaded, and left out of a build with
 * {@code nerocolonies_compat=false}.
 *
 * <p>Implementations never throw on purpose and return the neutral value (false, STRANGER, null) for a null
 * server, which only plain-JVM tests pass.
 */
public interface ColoniesBridge {

    /** A player's role in a colony (names match NeroColonies' roles). The stored role, never an operator elevation. */
    enum Role {
        STRANGER, ENEMY, ALLY, CHIEF, OWNER;

        /** Whether this role counts as belonging to the colony (ally, chief or owner). */
        public boolean member() {
            return this == ALLY || this == CHIEF || this == OWNER;
        }
    }

    /** Whether a colony with this id exists. */
    boolean exists(@Nullable MinecraftServer server, UUID colony);

    /** {@code player}'s role in {@code colony}; STRANGER when the colony is gone. */
    Role roleOf(@Nullable MinecraftServer server, UUID colony, UUID player);

    /** The colony at {@code pos} (an outpost counts as its parent colony), or null. Bind-time convenience only. */
    @Nullable UUID colonyAt(ServerLevel level, BlockPos pos);
}
