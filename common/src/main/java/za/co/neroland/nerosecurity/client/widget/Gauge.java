package za.co.neroland.nerosecurity.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * A 6-px bar in {@code TROUGH} with a fill and a right-aligned white label (the exact number, e.g.
 * "14 000 / 20 000" or "18 %"), so the value is always readable as text. Read-only. CLIENT-ONLY.
 *
 * <pre>{@code
 * g.text(font, Component.translatable("field.nerosecurity.power"), x, y, WidgetColours.TEXT, false);
 * Gauge.draw(g, font, x + 40, y, 180, snap.energy() / (float) snap.capacity(), SecurityColours.FIELD,
 *         Component.literal(snap.energy() + " / " + snap.capacity()));
 * }</pre>
 */
public final class Gauge {

    public static final int BAR_HEIGHT = 6;
    private static final int LABEL_GAP = 6;

    private Gauge() {
    }

    /**
     * Draws the bar and label in a row {@code width} wide starting at ({@code x}, {@code y}); the bar is centred
     * on a 9-px text line. {@code fraction} is clamped to 0..1; {@code fillRgb} is {@code 0xRRGGBB}.
     */
    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int width, float fraction, int fillRgb,
                            @Nullable Component label) {
        int labelWidth = label == null ? 0 : font.width(label) + LABEL_GAP;
        int barWidth = Math.max(10, width - labelWidth);
        int by = y + 1;
        g.fill(x - 1, by - 1, x + barWidth + 1, by + BAR_HEIGHT + 1, SecurityColours.INK);
        g.fill(x, by, x + barWidth, by + BAR_HEIGHT, SecurityColours.TROUGH);
        float f = Float.isNaN(fraction) ? 0f : Math.max(0f, Math.min(1f, fraction));
        int fw = Math.round(barWidth * f);
        if (fw > 0) {
            g.fill(x, by, x + fw, by + BAR_HEIGHT, SecurityColours.opaque(fillRgb));
            g.fill(x, by, x + fw, by + 1, 0x55FFFFFF);
        }
        if (label != null) {
            g.text(font, label, x + width - font.width(label), y, WidgetColours.TEXT, false);
        }
    }
}
