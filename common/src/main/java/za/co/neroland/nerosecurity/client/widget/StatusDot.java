package za.co.neroland.nerosecurity.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * A 7×7 status glyph plus its word. The glyph's <b>shape</b> differs per {@link Status} (filled square /
 * hollow square with a dot / outline / cross), so colour is never the only signal; the word is white.
 * Give the row a long-sentence tooltip with {@code SecurityScreen.explain(...)}. Read-only. CLIENT-ONLY.
 *
 * <pre>{@code
 * int w = StatusDot.draw(g, font, x, y, Status.of(row.state()), Component.translatable(row.state().translationKey()));
 * explain(g, x, y, w, 9, Component.translatable("device.nerosecurity.tooltip", name, pos, stateWord));
 * }</pre>
 */
public final class StatusDot {

    public static final int SIZE = 7;
    private static final int GAP = 4;

    private StatusDot() {
    }

    /**
     * Draws the glyph at ({@code x}, {@code y + 1}) and the word after it (baseline aligned with 9-px text at
     * {@code y}). Returns the total width. {@code word} may be {@code null} or empty for the glyph alone.
     */
    public static int draw(GuiGraphicsExtractor g, Font font, int x, int y, Status status, @Nullable Component word) {
        drawGlyph(g, x, y + 1, status);
        if (word == null || word.getString().isEmpty()) {
            return SIZE;
        }
        g.text(font, word, x + SIZE + GAP, y, WidgetColours.TEXT, false);
        return SIZE + GAP + font.width(word);
    }

    /** Just the 7×7 glyph with its top-left at ({@code x}, {@code y}). */
    public static void drawGlyph(GuiGraphicsExtractor g, int x, int y, Status status) {
        Status s = status == null ? Status.OFF : status;
        drawShape(g, x, y, s.shape(), s.argb());
    }

    /** Draws one of the four shapes in {@code argb}; shared with event rows and the footer. */
    public static void drawShape(GuiGraphicsExtractor g, int x, int y, Status.Shape shape, int argb) {
        switch (shape) {
            case FILLED_SQUARE -> g.fill(x, y, x + SIZE, y + SIZE, argb);
            case HOLLOW_WITH_DOT -> {
                g.outline(x, y, SIZE, SIZE, argb);
                g.fill(x + 2, y + 2, x + 5, y + 5, argb);
            }
            case OUTLINE -> g.outline(x, y, SIZE, SIZE, argb);
            case CROSS -> {
                for (int i = 0; i < SIZE; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, argb);
                    g.fill(x + SIZE - 1 - i, y + i, x + SIZE - i, y + i + 1, argb);
                    if (i < SIZE - 1) {
                        // Thicken to 2 px so the cross reads at GUI scale 1.
                        g.fill(x + i + 1, y + i, x + i + 2, y + i + 1, argb);
                        g.fill(x + SIZE - 2 - i, y + i, x + SIZE - 1 - i, y + i + 1, argb);
                    }
                }
            }
        }
    }
}
