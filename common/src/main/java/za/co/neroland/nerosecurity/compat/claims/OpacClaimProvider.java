package za.co.neroland.nerosecurity.compat.claims;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * Open Parties and Claims, as a {@link ClaimProvider}. The <b>only</b> class in NeroSecurity that imports
 * {@code xaero.pac}; compiled {@code compileOnly} against OPAC's common API jar, listed in {@code META-INF/services},
 * loaded by {@link ClaimsCompat} only when {@code openpartiesandclaims} is installed, and left out of a build with
 * {@code opac_compat=false}.
 *
 * <p>Wilderness and the claim's own owner are allowed; anyone else is allowed only when OPAC says they have chunk
 * access. Claims only ever take access away ({@link ClaimSource} never grants). A null server (plain-JVM tests
 * only) has no claims to consult and answers "allowed"; an OPAC failure answers "not allowed" (fail closed) and is
 * warned about once, by exception class only.
 */
public final class OpacClaimProvider implements ClaimProvider {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "opac");

    private static volatile boolean warned;

    /** For {@link java.util.ServiceLoader}. */
    public OpacClaimProvider() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean mayInteract(MinecraftServer server, Identifier dimension, BlockPos pos, UUID actor) {
        if (server == null) {
            return true;
        }
        if (dimension == null || pos == null || actor == null) {
            return false;
        }
        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(server);
            IPlayerChunkClaimAPI claim = api.getServerClaimsManager().get(dimension, pos);
            if (claim == null || actor.equals(claim.getPlayerId())) {
                return true;
            }
            return api.getChunkProtection().hasChunkAccess(actor, dimension, pos.getX() >> 4, pos.getZ() >> 4);
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Open Parties and Claims check failed ({}); treating "
                        + "the position as claimed against the player.", e.getClass().getSimpleName());
            }
            return false;
        }
    }
}
