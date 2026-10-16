package za.co.neroland.nerosecurity.compat.factions;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.compat.SuspensionNotices;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * {@code nerosecurity:faction}: a member of the installation's bound faction is granted by their tier
 * ({@link FactionTierMapping}), capped by the installation's {@code factionMaxLevel} and never above OFFICER.
 * Only ever grants or abstains; never denies.
 *
 * <p>Abstains when: no installation, the integration is off or NeroFactions absent, no faction bound, the binding
 * is <b>suspended</b> (no current owner is still a member of the bound faction; published once per session as
 * {@code FACTION_SUSPENDED}), the actor is not a member, or the tier maps to nothing. A failing bridge also
 * abstains, so a broken sibling can only take grants away.
 */
public final class FactionSource implements AccessSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "faction");

    private final Supplier<FactionsBridge> bridge;
    private final BooleanSupplier enabled;
    private final SuspensionNotices suspensions;

    public FactionSource(Supplier<FactionsBridge> bridge, BooleanSupplier enabled) {
        this(bridge, enabled, System::currentTimeMillis);
    }

    public FactionSource(Supplier<FactionsBridge> bridge, BooleanSupplier enabled, LongSupplier clock) {
        this.bridge = bridge;
        this.enabled = enabled;
        this.suspensions = new SuspensionNotices(SecurityEventType.FACTION_SUSPENDED, clock);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public AccessVote vote(AccessQuery query) {
        try {
            return decide(query);
        } catch (RuntimeException | LinkageError e) {
            return AccessVote.Abstain.INSTANCE;
        }
    }

    private AccessVote decide(AccessQuery query) {
        Installation installation = query.installation();
        UUID actor = query.actor();
        if (installation == null || actor == null || Installation.TOMBSTONE_OWNER.equals(actor)) {
            return AccessVote.Abstain.INSTANCE;
        }
        Identifier bound = installation.boundFaction();
        if (bound == null || !enabled.getAsBoolean()) {
            return AccessVote.Abstain.INSTANCE;
        }
        FactionsBridge b = bridge.get();
        if (b == null) {
            return AccessVote.Abstain.INSTANCE;
        }
        MinecraftServer server = query.server();
        if (!anyOwnerIsMember(b, server, installation, bound)) {
            suspensions.suspended(installation.id(), bound.toString());
            return AccessVote.Abstain.INSTANCE;
        }
        if (!b.isMember(server, actor, bound)) {
            return AccessVote.Abstain.INSTANCE;
        }
        AccessLevel level = FactionTierMapping.level(b.tierOf(server, actor, bound), installation.factionMaxLevel());
        return level == null ? AccessVote.Abstain.INSTANCE : new AccessVote.Grant(level);
    }

    /**
     * Whether the installation's faction binding is suspended right now (a binding exists and no current owner is
     * a member of it). False when there is no binding or the integration is inactive.
     */
    public boolean isSuspended(@Nullable MinecraftServer server, Installation installation) {
        Identifier bound = installation == null ? null : installation.boundFaction();
        FactionsBridge b = bound == null || !enabled.getAsBoolean() ? null : bridge.get();
        if (b == null) {
            return false;
        }
        try {
            return !anyOwnerIsMember(b, server, installation, bound);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** A new server session: suspension notices may be published again. */
    public void clearSession() {
        suspensions.clear();
    }

    private static boolean anyOwnerIsMember(FactionsBridge b, @Nullable MinecraftServer server, Installation installation,
            Identifier faction) {
        for (UUID owner : installation.owners()) {
            if (!Installation.TOMBSTONE_OWNER.equals(owner) && b.isMember(server, owner, faction)) {
                return true;
            }
        }
        return false;
    }
}
