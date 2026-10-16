package za.co.neroland.nerosecurity.compat.claims;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * One land-claim mod, as NeroSecurity sees it. Implementations are the only classes that import their claim mod,
 * are listed in {@code META-INF/services/za.co.neroland.nerosecurity.compat.claims.ClaimProvider} and are loaded by
 * {@link ClaimsCompat} only when a known claim mod is installed. Open Parties and Claims is the one built today
 * ({@code OpacClaimProvider}, left out with {@code opac_compat=false}); FTB Chunks and Flan would plug in here.
 */
public interface ClaimProvider {

    /** Stable id for logs, e.g. {@code nerosecurity:opac}. */
    Identifier id();

    /**
     * True when {@code pos} is not claimed by anyone, or {@code actor} has full access to that claim. Must not
     * throw (a throwing provider counts as "no", which denies).
     */
    boolean mayInteract(MinecraftServer server, Identifier dimension, BlockPos pos, UUID actor);
}
