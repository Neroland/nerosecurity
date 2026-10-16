package za.co.neroland.nerosecurity.client.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * A 14×14 check box plus its label. Checked = a filled white box with a 6×6 ink square in it; unchecked = an
 * empty box. The widget is as wide as box + label. Display only: a press calls {@code onChange(!checked)};
 * the box changes when the screen calls {@link #setChecked} from the next snapshot.
 *
 * <pre>{@code
 * keycardOnly = addRenderableWidget(new Toggle(leftPos + 8, topPos + 68,
 *         Component.translatable("door.nerosecurity.toggle.keycard_only"), on -> sendIntent(...)));
 * keycardOnly.setHelp(Component.translatable("door.nerosecurity.toggle.keycard_only.hint"));
 * keycardOnly.setChecked(snap.keycardOnly());
 * }</pre>
 * CLIENT-ONLY.
 */
public class Toggle extends SecurityWidget {

    public static final int BOX = 14;
    private static final int GAP = 5;

    private final Consumer<Boolean> onChange;
    private boolean checked;

    public Toggle(int x, int y, Component label, Consumer<Boolean> onChange) {
        super(x, y, BOX + GAP + Minecraft.getInstance().font.width(label), BOX, label);
        this.onChange = onChange;
    }

    public final void setChecked(boolean checked) {
        this.checked = checked;
    }

    public final boolean checked() {
        return checked;
    }

    private void press() {
        if (this.active) {
            onChange.accept(!checked);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.active && this.visible && event.isSelection()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            press();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hot = hot();
        int edge = !this.active ? WidgetColours.DISABLED_EDGE : (hot ? WidgetColours.EDGE_HOT : SecurityColours.BUTTON_EDGE);
        g.fill(x, y, x + BOX, y + BOX, edge);
        if (checked) {
            int fill = this.active ? SecurityColours.TITLE : WidgetColours.DISABLED_TEXT;
            g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, fill);
            g.fill(x + 4, y + 4, x + 10, y + 10, SecurityColours.INK);
        } else {
            g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, this.active ? SecurityColours.TROUGH : WidgetColours.DISABLED_BODY);
        }
        int text = this.active ? (hot ? WidgetColours.BUTTON_TEXT_HOT : WidgetColours.TEXT) : WidgetColours.DISABLED_TEXT;
        g.text(Minecraft.getInstance().font, getMessage(), x + BOX + GAP, y + 3, text, false);
    }
}
