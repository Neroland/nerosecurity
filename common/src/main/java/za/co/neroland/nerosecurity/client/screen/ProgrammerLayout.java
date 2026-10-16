package za.co.neroland.nerosecurity.client.screen;

/**
 * Where everything sits on the Keycard Programmer screen, in panel-relative pixels. One pure table so the
 * menu's slot coordinates (which the server also builds) can be checked against the screen's layout in a
 * plain-JVM test, and so nothing in the screen is a magic number.
 *
 * <pre>
 *  x: 8      48  60                                      288 300
 *  30  ┌ slots ┐  LEVEL ON THE CARD            Cards issued: 12 / 512
 *  40  │ [▢]   │  [PUBLIC][MEMBER][OFFICER][OWNER]              ← ladder, 228 wide
 *  62  │ card  │  [OFFICER] — can manage members, link devices…  ← meaning (two lines of room)
 *  86  │ [▢]   │  [ ] Encrypted      [ ] Bind to me
 * 104  │ blank │  ┌──────────────────────────────────────────┐
 *      │ [▢]   │  │ OFFICER card — can manage …    ENCRYPTED │  ← result band 228×22
 * 126  └ module┘  └──────────────────────────────────────────┘
 * 130             [ Write ] [ Copy ] [ Wipe ]
 * 148  ▸ footer
 * 162  ─ player inventory ─
 * </pre>
 */
public final class ProgrammerLayout {

    public static final int PANEL_W = 300;
    /** Footer included, inventory excluded. */
    public static final int PANEL_H = 162;

    /** The slot column: a box down the left with the three machine slots and their captions. */
    public static final int SLOT_BOX_X0 = 8;
    public static final int SLOT_BOX_X1 = 48;
    public static final int SLOT_BOX_Y0 = 30;
    public static final int SLOT_BOX_Y1 = 126;
    /** Item origin x of every machine slot (centred in the slot box). */
    public static final int SLOT_X = 20;
    public static final int CARD_Y = 46;
    public static final int BLANK_Y = 76;
    public static final int MODULE_Y = 106;

    /** The control column to the right of the slots. */
    public static final int COL_X = 60;
    public static final int COL_W = 228;
    public static final int CAPTION_Y = 30;
    public static final int LADDER_Y = 40;
    public static final int MEANING_Y = 62;
    public static final int TOGGLE_Y = 86;
    public static final int TOGGLE_BIND_X = COL_X + 90;
    public static final int BAND_Y = 104;
    public static final int BAND_H = 22;
    public static final int BUTTON_Y = 130;
    public static final int BUTTON_W = 60;
    public static final int BUTTON_H = 16;
    public static final int BUTTON_GAP = 4;

    /** Player inventory origin (the standard 9×3 grid is 162 px wide). */
    public static final int INVENTORY_WIDTH = 162;
    public static final int INV_X = (PANEL_W - INVENTORY_WIDTH) / 2;

    private ProgrammerLayout() {
    }

    /** x of the {@code i}th action button (Write, Copy, Wipe). */
    public static int buttonX(int i) {
        return COL_X + i * (BUTTON_W + BUTTON_GAP);
    }
}
