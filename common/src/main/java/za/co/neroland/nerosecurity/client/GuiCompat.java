package za.co.neroland.nerosecurity.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * GUI drawing calls whose name differs between the Minecraft lines NeroSecurity builds for, in one place so the
 * screens and widgets stay one copy. A straight pass-through on 26.x.
 */
public final class GuiCompat {

    private GuiCompat() {
    }

    /** A one-pixel rectangle outline. */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int argb) {
        //? if >=26.1 {
        g.outline(x, y, width, height, argb);
        //?} else if >=1.21.10 && <1.21.11 {
        /*g.submitOutline(x, y, width, height, argb);
        *///?} else {
        /*g.renderOutline(x, y, width, height, argb);
        *///?}
    }
}
