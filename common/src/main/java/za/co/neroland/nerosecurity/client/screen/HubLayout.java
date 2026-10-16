package za.co.neroland.nerosecurity.client.screen;

/**
 * Every rectangle on the Security Hub screen, panel-relative, in one pure table so the layout is checked by a
 * unit test (boxes inside the panel, columns that do not overlap, button rows that add up) instead of by eye.
 *
 * <pre>
 *  y  30 ┌ STATUS ───────────┐ ┌ LOCKDOWN ─────────────┐
 *        │ status · devices  │ │  [ big danger button ] │
 *        │ You are [PILL] —  │ │  [ clear ] / explain   │
 *     90 └───────────────────┘ └────────────────────────┘
 *     94 ┌ MEMBERS (n) ──────┐ ┌ DEVICES (n) ───────────┐
 *        │ 4 rows            │ │ 4 rows                 │
 *    149 └───────────────────┘ └────────────────────────┘
 *    153 [+ Add] [▲ Promote] [▼ Demote] [– Remove]   (or the inline add row)
 *    171 ┌ LAST 5 EVENTS ───────────────────────────────┐
 *        │ 3 visible lines, wheel to scroll             │
 *    215 └──────────────────────────────────────────────┘
 *    216 footer
 * </pre>
 */
public final class HubLayout {

    /** A panel-relative rectangle, {@code x1}/{@code y1} exclusive. */
    public record Box(int x0, int y0, int x1, int y1) {

        public int width() {
            return x1 - x0;
        }

        public int height() {
            return y1 - y0;
        }

        public boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }

        public boolean overlaps(Box other) {
            return x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1;
        }

        public boolean within(Box outer) {
            return x0 >= outer.x0 && y0 >= outer.y0 && x1 <= outer.x1 && y1 <= outer.y1;
        }
    }

    public static final int PANEL_W = 300;
    public static final int PANEL_H = 230;
    public static final Box PANEL = new Box(0, 0, PANEL_W, PANEL_H);
    /** The body between the subtitle and the footer (the hull's BODY_Y and FOOTER_HEIGHT, repeated as numbers so this table stays free of client classes). */
    public static final int BODY_Y = 30;
    public static final int FOOTER_H = 14;
    public static final int PAD = 8;
    public static final Box BODY = new Box(0, BODY_Y, PANEL_W, PANEL_H - FOOTER_H);

    // --- title row ------------------------------------------------------------------------------

    public static final int TABS_X = 150;
    public static final int TABS_Y = 4;
    public static final int PENCIL_X = 136;
    public static final int PENCIL_Y = 4;
    public static final int PENCIL_SIZE = 11;
    public static final int RENAME_X = 8;
    public static final int RENAME_Y = 3;
    public static final int RENAME_W = 90;
    public static final int RENAME_H = 13;
    public static final int RENAME_SAVE_X = 102;
    public static final int RENAME_SAVE_W = 32;
    /** Pixels the hull keeps clear at the right of the title (pencil + tabs). */
    public static final int TITLE_INSET = PANEL_W - PAD - PENCIL_X;

    // --- overview: status and lockdown ----------------------------------------------------------

    public static final Box STATUS = new Box(8, 30, 140, 90);
    public static final Box LOCKDOWN = new Box(144, 30, 292, 90);
    public static final int STATUS_LINE_1 = 37;
    public static final int STATUS_LINE_2 = 47;
    public static final int STATUS_YOU_ARE = 57;
    public static final int STATUS_MEANING = 69;
    public static final int TEXT_LINE = 10;
    public static final Box BIG_BUTTON = new Box(148, 38, 288, 64);
    public static final Box CLEAR_BUTTON = new Box(148, 67, 288, 81);
    public static final int EXPLAIN_Y = 67;

    // --- overview: lists ------------------------------------------------------------------------

    public static final int ROW_H = 11;
    public static final int LIST_ROWS = 4;
    public static final int LIST_TOP = 101;
    public static final Box MEMBERS = new Box(8, 94, 140, 149);
    public static final Box DEVICES = new Box(144, 94, 292, 149);
    public static final int ARROW = 9;
    public static final int ARROW_UP_Y = LIST_TOP;
    public static final int ARROW_DOWN_Y = LIST_TOP + LIST_ROWS * ROW_H - ARROW;
    /** The text column of a list: from the box's inner left to the arrow column. */
    public static final int LIST_INSET = 3;
    public static final int LIST_ARROW_GAP = 2;

    public static final int BUTTON_ROW_Y = 153;
    public static final int BUTTON_ROW_H = 14;
    public static final int MEMBER_BUTTON_W = 68;
    public static final int MEMBER_BUTTON_GAP = 4;
    public static final int[] MEMBER_BUTTON_X = {8, 80, 152, 224};

    public static final Box ADD_NAME = new Box(8, BUTTON_ROW_Y, 110, BUTTON_ROW_Y + BUTTON_ROW_H);
    public static final int ADD_LADDER_X = 114;
    public static final int ADD_LADDER_BUTTON_W = 46;
    public static final Box ADD_GO = new Box(214, BUTTON_ROW_Y, 252, BUTTON_ROW_Y + BUTTON_ROW_H);
    public static final Box ADD_CANCEL = new Box(256, BUTTON_ROW_Y, 292, BUTTON_ROW_Y + BUTTON_ROW_H);

    // --- overview: events -----------------------------------------------------------------------

    public static final Box EVENTS = new Box(8, 171, 292, 215);
    public static final Box EVENT_LIST = new Box(11, 178, 289, 211);

    // --- privacy --------------------------------------------------------------------------------

    public static final Box KEPT = new Box(8, 30, 292, 118);
    public static final int KEPT_LABEL_X = 12;
    public static final int KEPT_VALUE_X = 84;
    public static final int KEPT_VALUE_W = 204;
    public static final int KEPT_FIRST_Y = 38;
    public static final int KEPT_ROW_GAP = 3;
    public static final Box YOURS = new Box(8, 122, 292, 214);
    public static final int YOURS_BUTTON_Y = 130;
    public static final int YOURS_BUTTON_H = 14;
    public static final int YOURS_BUTTON_W = 88;
    public static final int[] YOURS_BUTTON_X = {12, 106, 200};
    public static final int YOURS_TEXT_Y = 149;
    public static final int YOURS_TEXT_X = 12;
    public static final int YOURS_TEXT_W = 276;
    public static final int YOURS_PARAGRAPH_GAP = 2;

    private HubLayout() {
    }

    /** The text column of {@code list} (left inset to the arrow column). */
    public static int listTextX(Box list) {
        return list.x0() + LIST_INSET;
    }

    public static int listTextWidth(Box list) {
        return list.width() - 2 * LIST_INSET - ARROW - LIST_ARROW_GAP;
    }

    public static int listArrowX(Box list) {
        return list.x1() - LIST_INSET - ARROW;
    }

    /** The row window of a list, for hit tests. */
    public static Box listRows(Box list) {
        return new Box(list.x0() + 1, LIST_TOP, listArrowX(list) - LIST_ARROW_GAP, LIST_TOP + LIST_ROWS * ROW_H);
    }

    /** The member button at {@code index} (0 add, 1 promote, 2 demote, 3 remove). */
    public static Box memberButton(int index) {
        int x = MEMBER_BUTTON_X[index];
        return new Box(x, BUTTON_ROW_Y, x + MEMBER_BUTTON_W, BUTTON_ROW_Y + BUTTON_ROW_H);
    }

    /** The privacy action button at {@code index} (0 export, 1 opt out / in, 2 erase). */
    public static Box yoursButton(int index) {
        int x = YOURS_BUTTON_X[index];
        return new Box(x, YOURS_BUTTON_Y, x + YOURS_BUTTON_W, YOURS_BUTTON_Y + YOURS_BUTTON_H);
    }
}
