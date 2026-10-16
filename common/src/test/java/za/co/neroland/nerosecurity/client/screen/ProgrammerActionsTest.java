package za.co.neroland.nerosecurity.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.screen.ProgrammerActions.CardState;
import za.co.neroland.nerosecurity.menu.KeycardProgrammerMenu;

/** Every programmer button that is disabled says why, and the menu's slots sit where the screen draws them. */
class ProgrammerActionsTest {

    @Test
    void slotStateFromTheTwoFacts() {
        assertEquals(CardState.EMPTY, ProgrammerActions.state(false, false));
        assertEquals(CardState.EMPTY, ProgrammerActions.state(false, true));
        assertEquals(CardState.BLANK, ProgrammerActions.state(true, true));
        assertEquals(CardState.ENCODED, ProgrammerActions.state(true, false));
    }

    @Test
    void nothingIsEnabledBeforeTheFirstSnapshot() {
        assertEquals(ProgrammerActions.WAITING, ProgrammerActions.writeBlockedKey(false, true, CardState.BLANK));
        assertEquals(ProgrammerActions.WAITING, ProgrammerActions.cloneBlockedKey(false, CardState.ENCODED, CardState.BLANK));
        assertEquals(ProgrammerActions.WAITING, ProgrammerActions.wipeBlockedKey(false, CardState.ENCODED));
    }

    @Test
    void writeNeedsAWritableLevelAndABlankInTheCardSlot() {
        assertNull(ProgrammerActions.writeBlockedKey(true, true, CardState.BLANK));
        assertEquals(ProgrammerActions.NO_LEVEL, ProgrammerActions.writeBlockedKey(true, false, CardState.BLANK));
        assertEquals(ProgrammerActions.NO_CARD, ProgrammerActions.writeBlockedKey(true, true, CardState.EMPTY));
        assertEquals(ProgrammerActions.NOT_BLANK, ProgrammerActions.writeBlockedKey(true, true, CardState.ENCODED));
    }

    @Test
    void copyNeedsAWrittenCardAndABlank() {
        assertNull(ProgrammerActions.cloneBlockedKey(true, CardState.ENCODED, CardState.BLANK));
        assertEquals(ProgrammerActions.NO_CARD, ProgrammerActions.cloneBlockedKey(true, CardState.EMPTY, CardState.BLANK));
        assertEquals(ProgrammerActions.CARD_BLANK, ProgrammerActions.cloneBlockedKey(true, CardState.BLANK, CardState.BLANK));
        assertEquals(ProgrammerActions.NO_BLANK, ProgrammerActions.cloneBlockedKey(true, CardState.ENCODED, CardState.EMPTY));
        assertEquals(ProgrammerActions.NO_BLANK, ProgrammerActions.cloneBlockedKey(true, CardState.ENCODED, CardState.ENCODED));
    }

    @Test
    void wipeNeedsAWrittenCard() {
        assertNull(ProgrammerActions.wipeBlockedKey(true, CardState.ENCODED));
        assertEquals(ProgrammerActions.NO_CARD, ProgrammerActions.wipeBlockedKey(true, CardState.EMPTY));
        assertEquals(ProgrammerActions.CARD_BLANK, ProgrammerActions.wipeBlockedKey(true, CardState.BLANK));
    }

    /** The menu places the slots (server and client alike); the screen must draw its frames on the same pixels. */
    @Test
    void menuSlotsLineUpWithTheScreenLayout() {
        assertEquals(ProgrammerLayout.SLOT_X, KeycardProgrammerMenu.CARD_X);
        assertEquals(ProgrammerLayout.SLOT_X, KeycardProgrammerMenu.BLANK_X);
        assertEquals(ProgrammerLayout.SLOT_X, KeycardProgrammerMenu.MODULE_X);
        assertEquals(ProgrammerLayout.CARD_Y, KeycardProgrammerMenu.CARD_Y);
        assertEquals(ProgrammerLayout.BLANK_Y, KeycardProgrammerMenu.BLANK_Y);
        assertEquals(ProgrammerLayout.MODULE_Y, KeycardProgrammerMenu.MODULE_Y);
        assertEquals(ProgrammerLayout.INV_X, KeycardProgrammerMenu.INV_X);
        // The hull's inventory section: first row 14 px under the panel, hotbar 72 px under it (58 below row 1).
        assertEquals(ProgrammerLayout.PANEL_H + SecurityScreen.INVENTORY_ROWS_OFFSET, KeycardProgrammerMenu.INV_Y);
        assertEquals(ProgrammerLayout.PANEL_H + SecurityScreen.HOTBAR_OFFSET, KeycardProgrammerMenu.INV_Y + 58);
        // Slots and captions stay inside the slot box, and the band ends level with it.
        assertEquals(ProgrammerLayout.SLOT_BOX_Y1, ProgrammerLayout.BAND_Y + ProgrammerLayout.BAND_H);
        assertTrue(ProgrammerLayout.CARD_Y - 11 > ProgrammerLayout.SLOT_BOX_Y0);
        assertTrue(ProgrammerLayout.MODULE_Y + 17 < ProgrammerLayout.SLOT_BOX_Y1);
        assertTrue(ProgrammerLayout.buttonX(2) + ProgrammerLayout.BUTTON_W
                <= ProgrammerLayout.COL_X + ProgrammerLayout.COL_W);
        assertTrue(ProgrammerLayout.BUTTON_Y + ProgrammerLayout.BUTTON_H
                <= ProgrammerLayout.PANEL_H - SecurityScreen.FOOTER_HEIGHT);
    }
}
