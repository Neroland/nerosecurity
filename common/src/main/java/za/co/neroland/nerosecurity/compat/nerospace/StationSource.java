package za.co.neroland.nerosecurity.compat.nerospace;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * {@code nerosecurity:station}: whoever Nerospace says <b>currently</b> owns the installation's bound station gets
 * OFFICER (never OWNER). A transferred station follows its new owner by design; the binder is not stored. Only ever
 * grants or abstains; a failing bridge abstains.
 */
public final class StationSource implements AccessSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "station");

    private final Supplier<NerospaceBridge> bridge;
    private final BooleanSupplier enabled;

    public StationSource(Supplier<NerospaceBridge> bridge, BooleanSupplier enabled) {
        this.bridge = bridge;
        this.enabled = enabled;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public AccessVote vote(AccessQuery query) {
        try {
            Installation installation = query.installation();
            UUID actor = query.actor();
            if (installation == null || actor == null || Installation.TOMBSTONE_OWNER.equals(actor)) {
                return AccessVote.Abstain.INSTANCE;
            }
            Integer station = installation.boundStation();
            if (station == null || !enabled.getAsBoolean()) {
                return AccessVote.Abstain.INSTANCE;
            }
            NerospaceBridge b = bridge.get();
            if (b == null) {
                return AccessVote.Abstain.INSTANCE;
            }
            return b.ownedBy(query.server(), station.intValue(), actor)
                    ? new AccessVote.Grant(AccessLevel.OFFICER) : AccessVote.Abstain.INSTANCE;
        } catch (RuntimeException | LinkageError e) {
            return AccessVote.Abstain.INSTANCE;
        }
    }
}
