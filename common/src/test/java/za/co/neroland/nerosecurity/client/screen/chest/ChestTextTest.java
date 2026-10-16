package za.co.neroland.nerosecurity.client.screen.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.chest.AutomationWhy;
import za.co.neroland.nerosecurity.client.widget.Status;

/** The chest screen's automation row and lock reasons mirror the server-computed facts, never recompute them. */
class ChestTextTest {

    @Test
    void automationRowShowsTheServersReason() {
        ChestText.Automation allowed = ChestText.automation(true, AccessLevel.MEMBER, true, AutomationWhy.OK);
        assertEquals(new ChestText.Automation(Status.OK, AccessLevel.MEMBER, "chest.nerosecurity.automation.why.ok"), allowed);

        ChestText.Automation blocked = ChestText.automation(true, AccessLevel.MEMBER, false, AutomationWhy.LEVEL);
        assertEquals(new ChestText.Automation(Status.OFF, AccessLevel.MEMBER, "chest.nerosecurity.automation.why.level"), blocked);

        ChestText.Automation off = ChestText.automation(true, null, false, AutomationWhy.OFF);
        assertEquals(new ChestText.Automation(Status.OFF, null, "chest.nerosecurity.automation.why.off"), off);

        ChestText.Automation lockdown = ChestText.automation(true, AccessLevel.OFFICER, false, AutomationWhy.LOCKDOWN);
        assertEquals("chest.nerosecurity.automation.why.lockdown", lockdown.whyKey());
    }

    @Test
    void aMissingReasonFallsBackToTheAllowedFlag() {
        assertEquals("chest.nerosecurity.automation.why.ok", ChestText.automation(true, AccessLevel.MEMBER, true, null).whyKey());
        assertEquals("chest.nerosecurity.automation.why.off", ChestText.automation(true, null, false, null).whyKey());
    }

    @Test
    void unlinkedChestsHaveNoAutomationRow() {
        assertNull(ChestText.automation(false, AccessLevel.MEMBER, true, AutomationWhy.OK));
    }

    @Test
    void sealedRowOnlyDuringLockdown() {
        assertEquals("chest.nerosecurity.sealed", ChestText.sealedKey(true));
        assertNull(ChestText.sealedKey(false));
    }

    @Test
    void unlinkedChestsRefuseSettingsAndOwnerChestsNeedAnOwner() {
        assertEquals("device.nerosecurity.unlinked", ChestText.editWhy(false, AccessLevel.MEMBER, true));
        assertEquals("chest.nerosecurity.owner_only_setting", ChestText.editWhy(true, AccessLevel.OWNER, false));
        assertNull(ChestText.editWhy(true, AccessLevel.OWNER, true));
        assertNull(ChestText.editWhy(true, AccessLevel.MEMBER, false));
    }
}
