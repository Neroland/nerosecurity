package za.co.neroland.nerosecurity.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.screen.HubLayout.Box;

/** The hub's rectangles fit the 300×230 panel, sit in the body, and never overlap. */
class HubLayoutTest {

    private static final List<Box> OVERVIEW = List.of(HubLayout.STATUS, HubLayout.LOCKDOWN, HubLayout.MEMBERS,
            HubLayout.DEVICES, HubLayout.EVENTS);
    private static final List<Box> PRIVACY = List.of(HubLayout.KEPT, HubLayout.YOURS);

    private static void assertDisjoint(List<Box> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).overlaps(boxes.get(j)), boxes.get(i) + " overlaps " + boxes.get(j));
            }
        }
    }

    @Test
    void panelMatchesTheSpecifiedSize() {
        assertEquals(300, HubLayout.PANEL_W);
        assertEquals(230, HubLayout.PANEL_H);
        assertEquals(new Box(0, 30, 300, 216), HubLayout.BODY);
    }

    @Test
    void sectionBoxesStayInsideTheBodyAndApart() {
        for (Box box : OVERVIEW) {
            assertTrue(box.within(HubLayout.BODY), box + " leaves the body");
        }
        for (Box box : PRIVACY) {
            assertTrue(box.within(HubLayout.BODY), box + " leaves the body");
        }
        assertDisjoint(OVERVIEW);
        assertDisjoint(PRIVACY);
    }

    @Test
    void buttonRowsSitBetweenTheListsAndTheEvents() {
        Box row = new Box(8, HubLayout.BUTTON_ROW_Y, 292, HubLayout.BUTTON_ROW_Y + HubLayout.BUTTON_ROW_H);
        assertTrue(row.y0() >= HubLayout.MEMBERS.y1());
        assertTrue(row.y1() <= HubLayout.EVENTS.y0());
        for (int i = 0; i < 4; i++) {
            assertTrue(HubLayout.memberButton(i).within(row), "member button " + i);
        }
        assertEquals(292, HubLayout.memberButton(3).x1(), "the row ends on the right margin");
        for (int i = 0; i < 3; i++) {
            assertTrue(HubLayout.memberButton(i).x1() + HubLayout.MEMBER_BUTTON_GAP <= HubLayout.memberButton(i + 1).x0());
        }
        // The inline add row replaces it exactly.
        List<Box> add = List.of(HubLayout.ADD_NAME,
                new Box(HubLayout.ADD_LADDER_X, HubLayout.BUTTON_ROW_Y,
                        HubLayout.ADD_LADDER_X + 2 * HubLayout.ADD_LADDER_BUTTON_W + 4,
                        HubLayout.BUTTON_ROW_Y + HubLayout.BUTTON_ROW_H),
                HubLayout.ADD_GO, HubLayout.ADD_CANCEL);
        for (Box box : add) {
            assertTrue(box.within(row), box + " leaves the add row");
        }
        assertDisjoint(add);
        assertEquals(292, HubLayout.ADD_CANCEL.x1());
    }

    @Test
    void lockdownButtonsFitTheirBox() {
        assertTrue(HubLayout.BIG_BUTTON.within(HubLayout.LOCKDOWN));
        assertTrue(HubLayout.CLEAR_BUTTON.within(HubLayout.LOCKDOWN));
        assertFalse(HubLayout.BIG_BUTTON.overlaps(HubLayout.CLEAR_BUTTON));
        assertEquals(26, HubLayout.BIG_BUTTON.height(), "two lines of text fit");
    }

    @Test
    void listRowsAndArrowsFitTheirBoxes() {
        for (Box list : List.of(HubLayout.MEMBERS, HubLayout.DEVICES)) {
            Box rows = HubLayout.listRows(list);
            assertTrue(rows.within(list), rows + " leaves " + list);
            assertEquals(HubLayout.LIST_ROWS * HubLayout.ROW_H, rows.height());
            Box up = new Box(HubLayout.listArrowX(list), HubLayout.ARROW_UP_Y,
                    HubLayout.listArrowX(list) + HubLayout.ARROW, HubLayout.ARROW_UP_Y + HubLayout.ARROW);
            Box down = new Box(HubLayout.listArrowX(list), HubLayout.ARROW_DOWN_Y,
                    HubLayout.listArrowX(list) + HubLayout.ARROW, HubLayout.ARROW_DOWN_Y + HubLayout.ARROW);
            assertTrue(up.within(list));
            assertTrue(down.within(list));
            assertFalse(up.overlaps(rows));
            assertFalse(down.overlaps(rows));
            assertFalse(up.overlaps(down));
            assertEquals(HubLayout.listTextX(list) + HubLayout.listTextWidth(list) + HubLayout.LIST_ARROW_GAP,
                    HubLayout.listArrowX(list), "text column ends at the arrow column");
        }
    }

    @Test
    void eventListSitsInsideItsBox() {
        assertTrue(HubLayout.EVENT_LIST.within(HubLayout.EVENTS));
        assertEquals(3, HubLayout.EVENT_LIST.height() / 11, "three visible lines");
    }

    @Test
    void privacyButtonsFitTheirBoxAndTextStartsBelowThem() {
        for (int i = 0; i < 3; i++) {
            assertTrue(HubLayout.yoursButton(i).within(HubLayout.YOURS), "privacy button " + i);
        }
        assertFalse(HubLayout.yoursButton(0).overlaps(HubLayout.yoursButton(1)));
        assertFalse(HubLayout.yoursButton(1).overlaps(HubLayout.yoursButton(2)));
        assertTrue(HubLayout.YOURS_TEXT_Y >= HubLayout.yoursButton(0).y1());
        assertTrue(HubLayout.YOURS_TEXT_X + HubLayout.YOURS_TEXT_W <= HubLayout.YOURS.x1());
        assertTrue(HubLayout.KEPT_VALUE_X + HubLayout.KEPT_VALUE_W <= HubLayout.KEPT.x1());
    }

    @Test
    void titleRowLeavesRoomForPencilTabsAndClose() {
        int tabsEnd = HubLayout.TABS_X + 2 * 64;
        assertTrue(HubLayout.PENCIL_X + HubLayout.PENCIL_SIZE <= HubLayout.TABS_X);
        assertTrue(HubLayout.RENAME_SAVE_X + HubLayout.RENAME_SAVE_W <= HubLayout.PENCIL_X);
        assertTrue(HubLayout.RENAME_X + HubLayout.RENAME_W < HubLayout.RENAME_SAVE_X);
        assertTrue(tabsEnd <= HubLayout.PANEL_W - 11 - 4 - 2, "tabs stop before the close button");
        assertEquals(HubLayout.PANEL_W - 8 - HubLayout.PENCIL_X, HubLayout.TITLE_INSET);
    }

    @Test
    void boxArithmetic() {
        Box box = new Box(10, 20, 30, 50);
        assertEquals(20, box.width());
        assertEquals(30, box.height());
        assertTrue(box.contains(10, 20));
        assertFalse(box.contains(30, 20));
        assertFalse(box.contains(10, 50));
        assertTrue(box.overlaps(new Box(29, 49, 40, 60)));
        assertFalse(box.overlaps(new Box(30, 20, 40, 60)));
        assertTrue(new Box(11, 21, 29, 49).within(box));
        assertFalse(new Box(0, 21, 29, 49).within(box));
    }
}
