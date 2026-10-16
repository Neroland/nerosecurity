package za.co.neroland.nerosecurity.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.screen.HubRules.LockdownButton;
import za.co.neroland.nerosecurity.installation.LockdownSource;

/** The hub screen's courtesy rules mirror the server's: never looser, and the arithmetic never goes negative. */
class HubRulesTest {

    @Test
    void lockdownButtonFollowsTheSnapshot() {
        assertEquals(LockdownButton.TURN_ON, HubRules.lockdownButton(false, LockdownSource.MANUAL));
        assertEquals(LockdownButton.TURN_ON, HubRules.lockdownButton(false, LockdownSource.ALARM));
        assertEquals(LockdownButton.TURN_ON, HubRules.lockdownButton(false, null));
        assertEquals(LockdownButton.TURN_OFF, HubRules.lockdownButton(true, LockdownSource.MANUAL));
        assertEquals(LockdownButton.TURN_OFF, HubRules.lockdownButton(true, null));
        assertEquals(LockdownButton.CONFIRM_ALARM, HubRules.lockdownButton(true, LockdownSource.ALARM));
    }

    @Test
    void lockdownNeedsOfficerToStartAndOwnerToClearOrKeep() {
        assertEquals(AccessLevel.OFFICER, LockdownButton.TURN_ON.needs());
        assertEquals(AccessLevel.OWNER, LockdownButton.TURN_OFF.needs());
        assertEquals(AccessLevel.OWNER, LockdownButton.CONFIRM_ALARM.needs());

        assertTrue(HubRules.mayLockdown(LockdownButton.TURN_ON, AccessLevel.OFFICER, false));
        assertTrue(HubRules.mayLockdown(LockdownButton.TURN_ON, AccessLevel.OWNER, false));
        assertFalse(HubRules.mayLockdown(LockdownButton.TURN_ON, AccessLevel.MEMBER, false));
        assertFalse(HubRules.mayLockdown(LockdownButton.TURN_OFF, AccessLevel.OFFICER, false));
        assertTrue(HubRules.mayLockdown(LockdownButton.TURN_OFF, AccessLevel.OWNER, false));
        assertFalse(HubRules.mayLockdown(LockdownButton.CONFIRM_ALARM, AccessLevel.OFFICER, false));
        // An operator override reports as OWNER, exactly as the server's decision does.
        assertTrue(HubRules.mayLockdown(LockdownButton.CONFIRM_ALARM, AccessLevel.PUBLIC, true));
    }

    @Test
    void statusKeysMatchTheLangTable() {
        assertEquals("hub.nerosecurity.status.normal", HubRules.statusKey(false, LockdownSource.ALARM));
        assertEquals("hub.nerosecurity.status.lockdown", HubRules.statusKey(true, LockdownSource.MANUAL));
        assertEquals("hub.nerosecurity.status.lockdown_alarm", HubRules.statusKey(true, LockdownSource.ALARM));
        assertEquals("hub.nerosecurity.lockdown.button.off", LockdownButton.TURN_ON.labelKey());
        assertEquals("hub.nerosecurity.lockdown.button.on", LockdownButton.TURN_OFF.labelKey());
        assertEquals("hub.nerosecurity.lockdown.button.alarm", LockdownButton.CONFIRM_ALARM.labelKey());
    }

    @Test
    void actorLevelTreatsAdminAsOwnerAndNullAsPublic() {
        assertEquals(AccessLevel.OWNER, HubRules.actorLevel(AccessLevel.MEMBER, true));
        assertEquals(AccessLevel.OFFICER, HubRules.actorLevel(AccessLevel.OFFICER, false));
        assertEquals(AccessLevel.PUBLIC, HubRules.actorLevel(null, false));
        assertTrue(HubRules.isOwnerOrAdmin(AccessLevel.OWNER, false));
        assertTrue(HubRules.isOwnerOrAdmin(AccessLevel.PUBLIC, true));
        assertFalse(HubRules.isOwnerOrAdmin(AccessLevel.OFFICER, false));
    }

    @Test
    void promoteAndDemoteStayBetweenMemberAndOfficer() {
        assertEquals(AccessLevel.OFFICER, HubRules.promoted(AccessLevel.MEMBER));
        assertNull(HubRules.promoted(AccessLevel.OFFICER), "OWNER is never offered");
        assertNull(HubRules.promoted(AccessLevel.OWNER));
        assertNull(HubRules.promoted(AccessLevel.PUBLIC));
        assertNull(HubRules.promoted(null));
        assertEquals(AccessLevel.MEMBER, HubRules.demoted(AccessLevel.OFFICER));
        assertNull(HubRules.demoted(AccessLevel.MEMBER), "below MEMBER is Remove, not Demote");
        assertNull(HubRules.demoted(AccessLevel.OWNER));
        assertNull(HubRules.demoted(null));
    }

    @Test
    void memberEditsMirrorTheServerRule() {
        // An officer may add and remove members but not touch officers or create one.
        assertTrue(HubRules.mayEdit(AccessLevel.OFFICER, false, AccessLevel.MEMBER, AccessLevel.PUBLIC));
        assertTrue(HubRules.mayAddAt(AccessLevel.OFFICER, false, AccessLevel.MEMBER));
        assertFalse(HubRules.mayAddAt(AccessLevel.OFFICER, false, AccessLevel.OFFICER));
        assertFalse(HubRules.mayEdit(AccessLevel.OFFICER, false, AccessLevel.MEMBER, AccessLevel.OFFICER));
        assertFalse(HubRules.mayEdit(AccessLevel.OFFICER, false, AccessLevel.OFFICER, AccessLevel.MEMBER));
        assertFalse(HubRules.mayEdit(AccessLevel.OFFICER, false, AccessLevel.OFFICER, AccessLevel.PUBLIC));
        // An owner may do everything up to OFFICER; nobody manages owners here.
        assertTrue(HubRules.mayEdit(AccessLevel.OWNER, false, AccessLevel.MEMBER, AccessLevel.OFFICER));
        assertTrue(HubRules.mayEdit(AccessLevel.OWNER, false, AccessLevel.OFFICER, AccessLevel.MEMBER));
        assertTrue(HubRules.mayEdit(AccessLevel.OWNER, false, AccessLevel.OFFICER, AccessLevel.PUBLIC));
        assertTrue(HubRules.mayAddAt(AccessLevel.OWNER, false, AccessLevel.OFFICER));
        assertFalse(HubRules.mayEdit(AccessLevel.OWNER, false, AccessLevel.MEMBER, AccessLevel.OWNER));
        assertFalse(HubRules.mayEdit(AccessLevel.OWNER, false, AccessLevel.OWNER, AccessLevel.PUBLIC));
        // Admin acts as OWNER; a member or lower may do nothing.
        assertTrue(HubRules.mayEdit(AccessLevel.MEMBER, true, AccessLevel.OFFICER, AccessLevel.MEMBER));
        assertFalse(HubRules.mayEdit(AccessLevel.MEMBER, false, AccessLevel.MEMBER, AccessLevel.PUBLIC));
        assertFalse(HubRules.mayEdit(null, false, AccessLevel.MEMBER, AccessLevel.PUBLIC));
    }

    @Test
    void scrollClampsToTheWindow() {
        assertEquals(0, HubRules.clampScroll(-3, 10, 4));
        assertEquals(0, HubRules.clampScroll(5, 3, 4), "everything fits: no scroll");
        assertEquals(6, HubRules.clampScroll(99, 10, 4));
        assertEquals(2, HubRules.clampScroll(2, 10, 4));
        assertEquals(0, HubRules.clampScroll(4, 0, 4));
        assertEquals(9, HubRules.clampScroll(50, 10, 0), "a zero window still shows one row");
    }

    @Test
    void rowHitTestRespectsScrollAndTheVisibleWindow() {
        assertEquals(0, HubRules.rowAt(0, 11, 0, 10, 4));
        assertEquals(0, HubRules.rowAt(10.9, 11, 0, 10, 4));
        assertEquals(1, HubRules.rowAt(11, 11, 0, 10, 4));
        assertEquals(5, HubRules.rowAt(11, 11, 4, 10, 4), "scrolled by four");
        assertEquals(-1, HubRules.rowAt(44, 11, 0, 10, 4), "below the fourth visible row");
        assertEquals(-1, HubRules.rowAt(22, 11, 0, 2, 4), "no row there");
        assertEquals(-1, HubRules.rowAt(-1, 11, 0, 10, 4));
        assertEquals(-1, HubRules.rowAt(5, 0, 0, 10, 4));
    }

    @Test
    void nameRoomNeverGoesNegative() {
        assertEquals(60, HubRules.nameRoom(118, 54, 4));
        assertEquals(0, HubRules.nameRoom(50, 54, 4));
    }

    @Test
    void agesGrowAndCountdownsShrinkWhileTheScreenIsOpen() {
        assertEquals(130L, HubRules.aged(100, 30));
        assertEquals(100L, HubRules.aged(100, -5));
        assertEquals(0L, HubRules.aged(-7, 0));
        assertEquals(70L, HubRules.remaining(100, 30));
        assertEquals(0L, HubRules.remaining(20, 30), "never below zero");
        assertEquals(100L, HubRules.remaining(100, -4));
    }
}
