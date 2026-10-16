package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.link.SecurityLinkAccess.Role;

class LinkVisibilityTest {

    private final FakeLinkContext ctx = new FakeLinkContext();
    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private Installation setup() {
        Installation i = ctx.install(owner, "Orbital Lab", 0);
        ctx.store.setMember(i.id(), officer, AccessLevel.OFFICER, 64);
        ctx.store.setMember(i.id(), member, AccessLevel.MEMBER, 64);
        return ctx.fresh(i);
    }

    @Test
    void rolesComeFromStoredMembershipOnly() {
        Installation i = setup();
        assertEquals(Role.OWNER, SecurityLinkAccess.roleOf(i, owner));
        assertEquals(Role.OFFICER, SecurityLinkAccess.roleOf(i, officer));
        assertEquals(Role.MEMBER, SecurityLinkAccess.roleOf(i, member));
        assertEquals(Role.NONE, SecurityLinkAccess.roleOf(i, stranger));
        assertEquals(Role.NONE, SecurityLinkAccess.roleOf(i, Installation.TOMBSTONE_OWNER));
        assertEquals(Role.NONE, SecurityLinkAccess.roleOf(i, null));
    }

    @Test
    void anOperatorGetsNothingExtra() {
        Installation i = setup();
        ctx.admin = (server, actor) -> true;
        assertEquals(Role.NONE, SecurityLinkAccess.visibleRole(ctx, i, stranger));
        assertTrue(SecurityLinkAccess.visible(ctx, ctx.store, stranger).isEmpty());
    }

    @Test
    void unknownAndNotVisibleAnswerTheSame() {
        Installation i = setup();
        assertNull(SecurityLinkAccess.param(ctx, ctx.store, stranger, i.id().toString()));
        assertNull(SecurityLinkAccess.param(ctx, ctx.store, stranger, UUID.randomUUID().toString()));
        assertNull(SecurityLinkAccess.param(ctx, ctx.store, owner, i.id().toString().substring(0, 8)), "no prefixes");
        assertNull(SecurityLinkAccess.param(ctx, ctx.store, owner, "Orbital Lab"), "no names");
        assertNull(SecurityLinkAccess.param(ctx, ctx.store, owner, (String) null));
        assertSame(i.id(), SecurityLinkAccess.param(ctx, ctx.store, member, i.id().toString()).id());

        SecurityLinkSnapshots snapshots = new SecurityLinkSnapshots(ctx);
        JsonObject unknown = snapshots.snapshot(stranger, "devices", Map.of("installation", UUID.randomUUID().toString()));
        JsonObject foreign = snapshots.snapshot(stranger, "devices", Map.of("installation", i.id().toString()));
        unknown.remove("asOf");
        foreign.remove("asOf");
        assertEquals(unknown, foreign, "a non-member cannot tell an existing installation from a missing one");
    }

    @Test
    void aDenyingSourceHidesTheInstallation() {
        Installation i = setup();
        ctx.sources.add(new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "test_deny");
            }

            @Override
            public AccessVote vote(za.co.neroland.nerosecurity.access.AccessQuery query) {
                return new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.source");
            }
        });
        assertEquals(Role.NONE, SecurityLinkAccess.visibleRole(ctx, i, owner));
    }

    @Test
    void recipientsAreStoredOwnersAndOptionallyOfficersNeverTheActor() {
        Installation i = setup();
        assertEquals(Set.of(owner), SecurityLinkAccess.recipients(i, null));
        ctx.store.setNotifyOfficers(i.id(), true);
        i = ctx.fresh(i);
        assertEquals(Set.of(owner, officer), SecurityLinkAccess.recipients(i, null));
        assertEquals(Set.of(owner), SecurityLinkAccess.recipients(i, officer), "the actor is not told about themselves");
        assertFalse(SecurityLinkAccess.recipients(i, null).contains(member));
    }

    @Test
    void theTombstoneOwnerIsNeverARecipient() {
        Installation i = setup();
        ctx.store.erase(owner);
        Installation orphan = ctx.fresh(i);
        assertTrue(orphan.owners().contains(Installation.TOMBSTONE_OWNER));
        assertFalse(SecurityLinkAccess.recipients(orphan, null).contains(Installation.TOMBSTONE_OWNER));
    }

    @Test
    void visibleIsSortedByName() {
        ctx.install(owner, "Zeta", 0);
        ctx.install(owner, "alpha", 10);
        ctx.install(owner, "Beta", 20);
        List<Installation> visible = SecurityLinkAccess.visible(ctx, ctx.store, owner);
        assertEquals(List.of("alpha", "Beta", "Zeta"), visible.stream().map(Installation::name).toList());
    }
}
