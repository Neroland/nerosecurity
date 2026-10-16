package za.co.neroland.nerosecurity.compat.nerospace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.create;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.isAbstain;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.query;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The station source: the current station owner gets exactly OFFICER. Plain JVM, server null. */
class StationSourceTest {

    @Test
    void currentOwnerGetsOfficerOnly() {
        UUID owner = UUID.randomUUID();
        UUID captain = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation unbound = create(store, owner, 0);
        NerospaceBridge bridge = new NerospaceBridge() {
            @Override
            public boolean ownedBy(MinecraftServer server, int station, UUID player) {
                return station == 7 && captain.equals(player);
            }

            @Override
            public List<Integer> stationsOwnedBy(MinecraftServer server, UUID player) {
                return captain.equals(player) ? List.of(7) : List.of();
            }
        };
        StationSource source = new StationSource(() -> bridge, () -> true);
        assertTrue(isAbstain(source.vote(query(captain, unbound, AccessAction.OPEN))), "no binding: abstain");

        assertTrue(store.bindStation(unbound.id(), 7));
        Installation bound = store.byId(unbound.id()).orElseThrow();
        AccessVote grant = source.vote(query(captain, bound, AccessAction.OPEN));
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), grant, "station owner: OFFICER, never OWNER");
        assertTrue(isAbstain(source.vote(query(stranger, bound, AccessAction.OPEN))), "not the owner: abstain");
        assertTrue(isAbstain(new StationSource(() -> null, () -> true).vote(query(captain, bound, AccessAction.OPEN))),
                "Nerospace absent: abstain");
        assertTrue(isAbstain(new StationSource(() -> bridge, () -> false).vote(query(captain, bound, AccessAction.OPEN))),
                "integration off: abstain");
        assertEquals(Integer.valueOf(7), store.byId(bound.id()).orElseThrow().boundStation(), "binding preserved");
        assertTrue(store.unbindStation(bound.id()));
        assertTrue(isAbstain(source.vote(query(captain, store.byId(bound.id()).orElseThrow(), AccessAction.OPEN))));
    }
}
