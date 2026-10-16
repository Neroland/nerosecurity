package za.co.neroland.nerosecurity.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.MembershipSource;

/**
 * The member edit rule. Together with the lockdown tests in the resolver test this is the proof that a
 * MEMBER can never lock out the OWNER: no path lowers an owner's level, no path removes an owner, and
 * lockdown never binds an owner.
 */
class InstallationServiceRulesTest {

    private static final AccessLevel PUBLIC = AccessLevel.PUBLIC;
    private static final AccessLevel MEMBER = AccessLevel.MEMBER;
    private static final AccessLevel OFFICER = AccessLevel.OFFICER;
    private static final AccessLevel OWNER = AccessLevel.OWNER;

    @Test
    void memberCannotReachTheEditPathAtAll() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Test",
                new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO), 0L, 8)
                .installation();
        store.setMember(installation.id(), member, MEMBER, 64);
        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE), (s, a) -> false, () -> true);
        assertFalse(resolver.resolve(new AccessQuery(null, member, installation, AccessAction.EDIT_MEMBERS, null),
                AccessAction.EDIT_MEMBERS.defaultMinimum).allowed());
        // Even if a member reached the rule, it would refuse every real edit; the only pair it passes
        // is the no-op "non-member stays a non-member".
        for (AccessLevel current : AccessLevel.values()) {
            for (AccessLevel next : AccessLevel.values()) {
                boolean noOp = current == PUBLIC && next == PUBLIC;
                assertEquals(noOp, InstallationService.canEdit(MEMBER, current, next), current + " -> " + next);
            }
        }
    }

    @Test
    void officerManagesMemberRowsOnly() {
        assertTrue(InstallationService.canEdit(OFFICER, PUBLIC, MEMBER), "add a member");
        assertTrue(InstallationService.canEdit(OFFICER, MEMBER, PUBLIC), "remove a member");
        assertFalse(InstallationService.canEdit(OFFICER, PUBLIC, OFFICER), "cannot add an officer");
        assertFalse(InstallationService.canEdit(OFFICER, MEMBER, OFFICER), "cannot promote to officer");
        assertFalse(InstallationService.canEdit(OFFICER, OFFICER, PUBLIC), "cannot remove an officer");
        assertFalse(InstallationService.canEdit(OFFICER, OFFICER, MEMBER), "cannot demote an officer");
        assertFalse(InstallationService.canEdit(OFFICER, OWNER, PUBLIC), "cannot touch an owner");
    }

    @Test
    void ownerDoesEverythingButCreateOrTouchOwners() {
        assertTrue(InstallationService.canEdit(OWNER, PUBLIC, OFFICER));
        assertTrue(InstallationService.canEdit(OWNER, MEMBER, OFFICER));
        assertTrue(InstallationService.canEdit(OWNER, OFFICER, MEMBER));
        assertTrue(InstallationService.canEdit(OWNER, OFFICER, PUBLIC));
        assertFalse(InstallationService.canEdit(OWNER, MEMBER, OWNER), "owner-set edits are not a member edit");
        assertFalse(InstallationService.canEdit(OWNER, OWNER, MEMBER), "no edit lowers an owner");
        assertFalse(InstallationService.canEdit(OWNER, OWNER, PUBLIC), "no edit removes an owner");
    }

    @Test
    void noLevelCanLowerOrRemoveAnOwner() {
        for (AccessLevel actor : AccessLevel.values()) {
            for (AccessLevel next : AccessLevel.values()) {
                assertFalse(InstallationService.canEdit(actor, OWNER, next));
            }
        }
    }

    @Test
    void levelChangePayloadMayOnlyEditExistingMemberRows() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Test",
                new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO), 0L, 8)
                .installation();
        store.setMember(installation.id(), member, MEMBER, 64);
        assertTrue(InstallationService.isExistingMemberRow(installation, member));
        assertFalse(InstallationService.isExistingMemberRow(installation, UUID.randomUUID()),
                "a client-chosen id that is not already a member is refused (no insertion by id)");
        assertFalse(InstallationService.isExistingMemberRow(installation, owner), "owners are not member rows");
        assertFalse(InstallationService.isExistingMemberRow(installation, null));
    }

    @Test
    void storeRefusesOwnerRowsAndSelfEditIsAServiceRule() {
        UUID owner = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Test",
                new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO), 0L, 8)
                .installation();
        assertEquals(SecurityInstallations.MemberResult.IS_OWNER,
                store.setMember(installation.id(), owner, MEMBER, 64), "an owner never becomes a member row");
        assertEquals(SecurityInstallations.MemberResult.IS_OWNER,
                store.setMember(installation.id(), owner, PUBLIC, 64), "an owner cannot be removed as a member");
        assertTrue(installation.isOwner(owner));
    }
}
