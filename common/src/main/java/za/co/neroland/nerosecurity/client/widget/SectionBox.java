package za.co.neroland.nerosecurity.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * A titled group box — {@code ┌ STATUS ────┐} — a 1-px {@code EDGE} outline with its caption in {@code SUBTLE}
 * cut into the top edge. Content starts at {@link #contentTop}. Read-only. CLIENT-ONLY.
 *
 * <pre>{@code
 * SectionBox.draw(g, font, leftPos + 8, topPos + 30, leftPos + 150, topPos + 76,
 *         Component.translatable("hub.nerosecurity.section.status"));
 * }</pre>
 */
public final class SectionBox {

    private SectionBox() {
    }

    /** Outline from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) exclusive, optional caption. */
    public static void draw(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1, @Nullable Component caption) {
        g.outline(x0, y0, x1 - x0, y1 - y0, SecurityColours.EDGE);
        if (caption != null) {
            int w = Math.min(font.width(caption), x1 - x0 - 12);
            g.fill(x0 + 4, y0, x0 + 8 + w, y0 + 1, SecurityColours.PANEL);
            WidgetText.drawFitted(g, font, caption, x0 + 6, y0 - 4, x1 - x0 - 12, SecurityColours.SUBTLE);
        }
    }

    /** The first content row inside a box drawn at {@code y0}. */
    public static int contentTop(int y0) {
        return y0 + 7;
    }
}
