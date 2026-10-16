package za.co.neroland.nerosecurity.compat.colonies;

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
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge.Role;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * {@code nerosecurity:colony}: a member of the installation's explicitly bound colony is granted by their role
 * ({@link ColonyRoleMapping}), capped by {@code colonyMaxLevel} and never above OFFICER. Strangers and enemies get
 * an abstention, never a deny. Only the bound colony id is consulted; where the device stands never matters.
 *
 * <p>Abstains when: no installation, the integration is off or NeroColonies absent, no colony bound, the binding
 * is <b>suspended</b> (no current owner is still a member; {@code COLONY_SUSPENDED} once per session), or the role
 * maps to nothing. A failing bridge abstains.
 */
public final class ColonySource implements AccessSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "colony");

    private final Supplier<ColoniesBridge> bridge;
    private final BooleanSupplier enabled;
    private final SuspensionNotices suspensions;

    public ColonySource(Supplier<ColoniesBridge> bridge, BooleanSupplier enabled) {
        this(bridge, enabled, System::currentTimeMillis);
    }

    public ColonySource(Supplier<ColoniesBridge> bridge, BooleanSupplier enabled, LongSupplier clock) {
        this.bridge = bridge;
        this.enabled = enabled;
        this.suspensions = new SuspensionNotices(SecurityEventType.COLONY_SUSPENDED, clock);
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
        UUID colony = installation.boundColony();
        if (colony == null || !enabled.getAsBoolean()) {
            return AccessVote.Abstain.INSTANCE;
        }
        ColoniesBridge b = bridge.get();
        if (b == null) {
            return AccessVote.Abstain.INSTANCE;
        }
        MinecraftServer server = query.server();
        if (!anyOwnerIsMember(b, server, installation, colony)) {
            suspensions.suspended(installation.id(), colony.toString());
            return AccessVote.Abstain.INSTANCE;
        }
        AccessLevel level = ColonyRoleMapping.level(b.roleOf(server, colony, actor), installation.colonyMaxLevel());
        return level == null ? AccessVote.Abstain.INSTANCE : new AccessVote.Grant(level);
    }

    /** Whether the installation's colony binding is suspended right now (bound, and no owner is a member). */
    public boolean isSuspended(@Nullable MinecraftServer server, Installation installation) {
        UUID colony = installation == null ? null : installation.boundColony();
        ColoniesBridge b = colony == null || !enabled.getAsBoolean() ? null : bridge.get();
        if (b == null) {
            return false;
        }
        try {
            return !anyOwnerIsMember(b, server, installation, colony);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** A new server session: suspension notices may be published again. */
    public void clearSession() {
        suspensions.clear();
    }

    private static boolean anyOwnerIsMember(ColoniesBridge b, @Nullable MinecraftServer server, Installation installation,
            UUID colony) {
        for (UUID owner : installation.owners()) {
            if (Installation.TOMBSTONE_OWNER.equals(owner)) {
                continue;
            }
            Role role = b.roleOf(server, colony, owner);
            if (role != null && role.member()) {
                return true;
            }
        }
        return false;
    }
}
