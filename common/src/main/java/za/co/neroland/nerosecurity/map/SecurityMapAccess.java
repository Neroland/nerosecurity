package za.co.neroland.nerosecurity.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The map's copy of the link module's visibility rule ({@code link/SecurityLinkAccess.visible}), re-implemented here
 * because that rule is package-private and the map package may not widen it. {@code SecurityMapParityTest} runs both
 * against the same stores for owner, officer, member, stranger, erased-owner and denying-source fixtures.
 *
 * <p>The rule: installations the viewer owns or is a stored member (member or officer) of; never the erased-owner
 * tombstone; hidden again when an access source denies the viewer outright; never widened for an operator. There is
 * no proximity path.
 */
public final class SecurityMapAccess {

    private SecurityMapAccess() {
    }

    /** Whether {@code player} holds a stored role (owner, officer or member) on {@code installation}. */
    public static boolean hasRole(@Nullable Installation installation, @Nullable UUID player) {
        if (installation == null || player == null || Installation.TOMBSTONE_OWNER.equals(player)) {
            return false;
        }
        if (installation.isOwner(player)) {
            return true;
        }
        AccessLevel level = installation.members().get(player);
        return level == AccessLevel.OFFICER || level == AccessLevel.MEMBER;
    }

    /** {@link #hasRole}, and no access source denies the player outright. A failing resolver hides. */
    public static boolean visible(@Nullable MinecraftServer server, AccessResolver resolver, Installation installation,
            UUID player) {
        if (!hasRole(installation, player)) {
            return false;
        }
        try {
            AccessDecision decision = resolver.resolve(
                    new AccessQuery(server, player, installation, AccessAction.OPEN, null), AccessLevel.PUBLIC);
            return decision.reason() != DenyReason.SOURCE_DENY;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Every installation {@code player} may see. */
    public static List<Installation> visible(@Nullable MinecraftServer server, AccessResolver resolver,
            @Nullable SecurityInstallations store, @Nullable UUID player) {
        List<Installation> out = new ArrayList<>();
        if (store == null || player == null || resolver == null) {
            return out;
        }
        for (Installation installation : store.visibleTo(player)) {
            if (visible(server, resolver, installation, player)) {
                out.add(installation);
            }
        }
        return out;
    }
}
