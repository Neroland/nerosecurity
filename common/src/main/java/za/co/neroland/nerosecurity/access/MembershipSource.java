package za.co.neroland.nerosecurity.access;

import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The built-in source: an installation's own owner set and member list. Grants OWNER to an owner and
 * the stored level to a member; abstains for everyone else. Never denies.
 *
 * <p>The tombstone owner left behind by an erasure is not a real player, so it grants nothing to
 * anyone, even an actor that somehow carries the same all-zero UUID.
 */
public final class MembershipSource implements AccessSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "membership");

    public static final MembershipSource INSTANCE = new MembershipSource();

    private MembershipSource() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public AccessVote vote(AccessQuery query) {
        Installation installation = query.installation();
        UUID actor = query.actor();
        if (installation == null || actor == null || Installation.TOMBSTONE_OWNER.equals(actor)) {
            return AccessVote.Abstain.INSTANCE;
        }
        if (installation.isOwner(actor)) {
            return new AccessVote.Grant(AccessLevel.OWNER);
        }
        AccessLevel level = installation.memberLevel(actor);
        return level == AccessLevel.PUBLIC ? AccessVote.Abstain.INSTANCE : new AccessVote.Grant(level);
    }
}
