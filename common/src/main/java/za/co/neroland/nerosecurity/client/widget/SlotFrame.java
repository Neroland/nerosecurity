package za.co.neroland.nerosecurity.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * An 18×18 {@code TROUGH} box with a 1-px {@code EDGE} behind a menu slot, optionally with a small caption
 * above it ("Card", "Blank", "Module"). The screen hull already frames every slot of the menu; screens call
 * {@link #caption} for the labels. Coordinates are the slot's <b>item</b> origin in absolute pixels
 * ({@code leftPos + slot.x}, {@code topPos + slot.y}). Read-only. CLIENT-ONLY.
 */
public final class SlotFrame {

    public static final int SIZE = 18;

    private SlotFrame() {
    }

    /** The frame for a slot whose item draws at ({@code itemX}, {@code itemY}). */
    public static void draw(GuiGraphicsExtractor g, int itemX, int itemY) {
        g.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, SecurityColours.EDGE);
        g.fill(itemX, itemY, itemX + 16, itemY + 16, SecurityColours.TROUGH);
    }

    /** A {@code SUBTLE} caption centred above the slot. */
    public static void caption(GuiGraphicsExtractor g, Font font, int itemX, int itemY, Component text) {
        g.centeredText(font, text, itemX + 8, itemY - 11, SecurityColours.SUBTLE);
    }
}
