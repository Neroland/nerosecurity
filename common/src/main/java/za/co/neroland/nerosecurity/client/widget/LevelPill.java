package za.co.neroland.nerosecurity.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * A level word as a filled pill: 54×11 (wider if the translated word needs it), the level colour as the fill,
 * dark {@code INK} text. This is the <b>only</b> way a level word is drawn on a NeroSecurity panel: level
 * colours are never used as text on the panel (OWNER red on the panel fails WCAG AA). {@link #drawAdmin} is
 * the white operator-override pill, used only on the "You are" line. Read-only. CLIENT-ONLY.
 *
 * <pre>{@code
 * int w = LevelPill.draw(g, font, leftPos + 120, rowY, row.level());   // member list row
 * }</pre>
 */
public final class LevelPill {

    public static final int WIDTH = 54;
    public static final int HEIGHT = 11;
    private static final int PAD = 4;

    private LevelPill() {
    }

    /** The pill's word (the lang value, e.g. "OWNER"), uncoloured. */
    public static Component word(AccessLevel level) {
        return Component.translatable(level.translationKey());
    }

    public static Component adminWord() {
        return Component.translatable(AccessLevel.ADMIN_KEY);
    }

    /** Width the pill for {@code level} will take. */
    public static int width(Font font, AccessLevel level) {
        return Math.max(WIDTH, font.width(word(level)) + 2 * PAD);
    }

    public static int adminWidth(Font font) {
        return Math.max(WIDTH, font.width(adminWord()) + 2 * PAD);
    }

    /** Draws the pill with its top-left at ({@code x}, {@code y}); returns its width. */
    public static int draw(GuiGraphicsExtractor g, Font font, int x, int y, AccessLevel level) {
        AccessLevel l = level == null ? AccessLevel.PUBLIC : level;
        return drawPill(g, font, x, y, width(font, l), word(l), WidgetColours.pillFill(l), WidgetColours.pillText(l));
    }

    /** The white ADMIN pill; returns its width. */
    public static int drawAdmin(GuiGraphicsExtractor g, Font font, int x, int y) {
        return drawPill(g, font, x, y, adminWidth(font), adminWord(), WidgetColours.adminFill(), WidgetColours.adminText());
    }

    /** {@link #drawAdmin} when {@code admin}, otherwise {@link #draw}. */
    public static int drawViewer(GuiGraphicsExtractor g, Font font, int x, int y, AccessLevel level, boolean admin) {
        return admin ? drawAdmin(g, font, x, y) : draw(g, font, x, y, level);
    }

    /**
     * Any filled pill with ink text (e.g. an {@code ENCRYPTED} tag in {@code ENC} purple). Text colour is chosen
     * by contrast, so it is safe on any fill.
     */
    public static int drawTag(GuiGraphicsExtractor g, Font font, int x, int y, Component text, int fillRgb) {
        int w = Math.max(font.width(text) + 2 * PAD, 20);
        int fill = za.co.neroland.nerosecurity.client.SecurityColours.opaque(fillRgb);
        int ink = za.co.neroland.nerosecurity.client.SecurityColours.opaque(
                za.co.neroland.nerosecurity.client.SecurityColours.inkFor(fillRgb));
        return drawPill(g, font, x, y, w, text, fill, ink);
    }

    private static int drawPill(GuiGraphicsExtractor g, Font font, int x, int y, int w, Component text, int fill, int ink) {
        // Rounded by knocking out the four corner pixels.
        g.fill(x + 1, y, x + w - 1, y + HEIGHT, fill);
        g.fill(x, y + 1, x + 1, y + HEIGHT - 1, fill);
        g.fill(x + w - 1, y + 1, x + w, y + HEIGHT - 1, fill);
        WidgetText.drawCentredFitted(g, font, text, x + w / 2, y + 2, w - 2, ink);
        return w;
    }
}
