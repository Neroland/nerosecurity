package za.co.neroland.nerosecurity.compat.claims;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.installation.DevicePos;

/**
 * {@code nerosecurity:claim}: refuses when the device stands on land claimed against the actor. <b>Never
 * grants</b> — a claim owner gets nothing from the claim — so it only ever adds restrictions, and because a deny is
 * final it beats owner membership, faction grants and keycards alike. The position checked is the device's own
 * ({@link AccessQuery#at()}); a query with no position, inactive claims or no provider abstains. A provider that
 * throws denies (fail closed).
 */
public final class ClaimSource implements AccessSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "claim");

    /** The deny's detail key. */
    public static final String DENY_KEY = "access.nerosecurity.deny.claim";

    private final Supplier<List<ClaimProvider>> providers;
    private final BooleanSupplier enabled;

    public ClaimSource(Supplier<List<ClaimProvider>> providers, BooleanSupplier enabled) {
        this.providers = providers;
        this.enabled = enabled;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public AccessVote vote(AccessQuery query) {
        DevicePos at = query.at();
        if (at == null || query.installation() == null || query.actor() == null || !enabled.getAsBoolean()) {
            return AccessVote.Abstain.INSTANCE;
        }
        List<ClaimProvider> list;
        try {
            list = providers.get();
        } catch (RuntimeException | LinkageError e) {
            return new AccessVote.Deny(DenyReason.SOURCE_DENY, DENY_KEY);
        }
        if (list == null || list.isEmpty()) {
            return AccessVote.Abstain.INSTANCE;
        }
        return ClaimsCompat.mayInteract(list, query.server(), at.dimension(), at.pos(), query.actor())
                ? AccessVote.Abstain.INSTANCE
                : new AccessVote.Deny(DenyReason.SOURCE_DENY, DENY_KEY);
    }
}
