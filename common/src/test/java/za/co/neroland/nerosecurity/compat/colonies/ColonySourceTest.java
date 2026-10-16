package za.co.neroland.nerosecurity.compat.colonies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.create;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.isAbstain;
import static za.co.neroland.nerosecurity.compat.CompatFixtures.query;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge.Role;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The colony source: role-mapped grants capped at OFFICER; enemies abstain, never deny. Plain JVM, server null. */
class ColonySourceTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID colony = UUID.randomUUID();
    private SecurityInstallations store;
    private Installation installation;

    /** Roles by player for {@link #colony}; anyone unlisted is a stranger. */
    private final class FakeColonies implements ColoniesBridge {
        final Map<UUID, Role> roles = new HashMap<>();

        @Override
        public boolean exists(MinecraftServer server, UUID id) {
            return colony.equals(id);
        }

        @Override
        public Role roleOf(MinecraftServer server, UUID id, UUID player) {
            return colony.equals(id) ? roles.getOrDefault(player, Role.STRANGER) : Role.STRANGER;
        }

        @Override
        public UUID colonyAt(ServerLevel level, BlockPos pos) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = create(store, owner, 0);
    }

    private Installation bound(AccessLevel max) {
        assertTrue(store.bindColony(installation.id(), colony, max));
        return store.byId(installation.id()).orElseThrow();
    }

    @Test
    void roleMapping() {
        for (Role role : new Role[] {Role.STRANGER, Role.ENEMY}) {
            assertNull(ColonyRoleMapping.level(role, AccessLevel.OFFICER), role.name());
        }
        assertEquals(AccessLevel.MEMBER, ColonyRoleMapping.level(Role.ALLY, AccessLevel.OFFICER));
        assertEquals(AccessLevel.OFFICER, ColonyRoleMapping.level(Role.CHIEF, AccessLevel.OFFICER));
        assertEquals(AccessLevel.OFFICER, ColonyRoleMapping.level(Role.OWNER, AccessLevel.OFFICER));
        assertEquals(AccessLevel.OFFICER, ColonyRoleMapping.level(Role.OWNER, AccessLevel.OWNER), "never above OFFICER");
        assertFalse(store.bindColony(installation.id(), colony, AccessLevel.OWNER), "the store refuses an OWNER max");

        FakeColonies fake = new FakeColonies();
        fake.roles.put(owner, Role.OWNER);
        Installation capped = bound(AccessLevel.MEMBER);
        ColonySource source = new ColonySource(() -> fake, () -> true);
        Map<Role, AccessVote> expected = Map.of(
                Role.STRANGER, AccessVote.Abstain.INSTANCE,
                Role.ENEMY, AccessVote.Abstain.INSTANCE,
                Role.ALLY, new AccessVote.Grant(AccessLevel.MEMBER),
                Role.CHIEF, new AccessVote.Grant(AccessLevel.MEMBER),
                Role.OWNER, new AccessVote.Grant(AccessLevel.MEMBER));
        for (Role role : Role.values()) {
            fake.roles.put(actor, role);
            AccessVote v = source.vote(query(actor, capped, AccessAction.OPEN));
            assertEquals(expected.get(role), v, role + " capped by colonyMaxLevel=MEMBER");
            assertFalse(v instanceof AccessVote.Deny, "colonies never deny");
        }
    }

    @Test
    void absentColoniesAbstainAndBindingPreserved() {
        Installation i = bound(AccessLevel.OFFICER);
        assertTrue(isAbstain(new ColonySource(() -> null, () -> true).vote(query(actor, i, AccessAction.OPEN))));
        assertEquals(colony, store.byId(i.id()).orElseThrow().boundColony(), "the binding is kept, inert");

        // ... and it survives a save and load untouched.
        SecurityInstallations reloaded = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE,
                SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow()).getOrThrow();
        Installation back = reloaded.byId(i.id()).orElseThrow();
        assertEquals(colony, back.boundColony());
        assertEquals(AccessLevel.OFFICER, back.colonyMaxLevel());

        FakeColonies fake = new FakeColonies();
        fake.roles.put(actor, Role.CHIEF);
        ColonySource source = new ColonySource(() -> fake, () -> true);
        assertTrue(isAbstain(source.vote(query(actor, i, AccessAction.OPEN))), "owner is no member: suspended");
        assertTrue(source.isSuspended(null, i));
        fake.roles.put(owner, Role.ALLY);
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), source.vote(query(actor, i, AccessAction.OPEN)));
        assertTrue(store.unbindColony(i.id()));
        assertNull(store.byId(i.id()).orElseThrow().boundColony());
    }
}
