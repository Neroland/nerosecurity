package za.co.neroland.nerosecurity.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The built-in membership source: owners, members, strangers and the erasure tombstone. */
class MembershipSourceTest {

    @Test
    void votesFollowTheOwnerSetAndMemberList() {
        UUID owner = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Test",
                new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO), 0L, 8)
                .installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);

        assertEquals(new AccessVote.Grant(AccessLevel.OWNER), vote(owner, installation));
        assertEquals(new AccessVote.Grant(AccessLevel.OFFICER), vote(officer, installation));
        assertEquals(new AccessVote.Grant(AccessLevel.MEMBER), vote(member, installation));
        assertEquals(AccessVote.Abstain.INSTANCE, vote(stranger, installation));
        assertEquals(AccessVote.Abstain.INSTANCE, vote(Installation.TOMBSTONE_OWNER, installation));
    }

    @Test
    void tombstonedInstallationGrantsNothingToTheTombstoneId() {
        UUID owner = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Test",
                new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO), 0L, 8)
                .installation();
        store.erase(owner);
        assertEquals(AccessVote.Abstain.INSTANCE, vote(Installation.TOMBSTONE_OWNER, installation));
        assertEquals(AccessVote.Abstain.INSTANCE, vote(owner, installation));
    }

    private static AccessVote vote(UUID actor, Installation installation) {
        return MembershipSource.INSTANCE.vote(new AccessQuery(null, actor, installation, AccessAction.OPEN, null));
    }
}
