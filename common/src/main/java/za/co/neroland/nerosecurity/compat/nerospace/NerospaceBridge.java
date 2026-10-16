package za.co.neroland.nerosecurity.compat.nerospace;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * The seam between NeroSecurity and Nerospace, in NeroSecurity types only. The one implementation
 * ({@code NeroNerospaceBridge}) is the only class that imports Nerospace, listed in {@code META-INF/services},
 * found by {@link NerospaceCompat} only when Nerospace is loaded, and left out of a build with
 * {@code nerospace_compat=false}. Explicit station ids only: no position lookup.
 *
 * <p>Implementations never throw on purpose and return the neutral value (false, empty) for a null server.
 */
public interface NerospaceBridge {

    /** Whether {@code player} currently owns station {@code station}. */
    boolean ownedBy(@Nullable MinecraftServer server, int station, UUID player);

    /** Every station id {@code player} currently owns (for the bind command's suggestions). */
    List<Integer> stationsOwnedBy(@Nullable MinecraftServer server, UUID player);
}
