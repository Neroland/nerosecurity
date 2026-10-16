package za.co.neroland.nerosecurity.client.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * The NeroSecurity button, ported from Nerospace's {@code SpaceButton}: a dark body, an edge that brightens on
 * hover or keyboard focus, a 1-px top sheen and centred text. Drawn entirely with fills, no texture.
 *
 * <ul>
 *   <li>{@link #danger} variant (lockdown, remove, erase): red edge, dark red body.</li>
 *   <li>{@link #setSelected selected}: brighter body and white text (segment / tab style).</li>
 *   <li>Disabled: grey text and the reason as a tooltip ({@link SecurityWidget#disable}).</li>
 * </ul>
 * The label is cut with "…" only if it cannot fit; give such a button a {@link #setHelp help} tooltip with the
 * full text. CLIENT-ONLY.
 *
 * <pre>{@code
 * SecurityButton write = addRenderableWidget(SecurityButton.of(leftPos + 60, topPos + 124, 70, 16,
 *         Component.translatable("programmer.nerosecurity.button.write"), b -> sendIntent(new ProgrammerWrite(...))));
 * write.setEnabled(hasBlank, Component.translatable("programmer.nerosecurity.no_blank"));
 * }</pre>
 */
public class SecurityButton extends SecurityWidget {

    private final Consumer<SecurityButton> onPress;
    private boolean danger;
    private boolean selected;

    public SecurityButton(int x, int y, int width, int height, Component label, Consumer<SecurityButton> onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    /** A normal button. */
    public static SecurityButton of(int x, int y, int width, int height, Component label, Consumer<SecurityButton> onPress) {
        return new SecurityButton(x, y, width, height, label, onPress);
    }

    /** A danger button (red edge). Destructive actions should still go through a {@link ConfirmPanel}. */
    public static SecurityButton danger(int x, int y, int width, int height, Component label,
                                        Consumer<SecurityButton> onPress) {
        SecurityButton button = new SecurityButton(x, y, width, height, label, onPress);
        button.danger = true;
        return button;
    }

    public final SecurityButton setDanger(boolean danger) {
        this.danger = danger;
        return this;
    }

    public final boolean isDanger() {
        return danger;
    }

    public final void setSelected(boolean selected) {
        this.selected = selected;
    }

    public final boolean isSelected() {
        return selected;
    }

    /** Runs the press action (also used by keyboard activation and by {@link ConfirmPanel}). */
    public void press() {
        if (this.active) {
            onPress.accept(this);
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
        int w = getWidth();
        int h = getHeight();
        boolean hot = hot();
        int edge;
        int body;
        if (!this.active) {
            edge = WidgetColours.DISABLED_EDGE;
            body = WidgetColours.DISABLED_BODY;
        } else if (danger) {
            edge = SecurityColours.DANGER_EDGE;
            body = hot ? WidgetColours.DANGER_HOT : SecurityColours.DANGER_BODY;
        } else {
            edge = hot || selected ? WidgetColours.EDGE_HOT : SecurityColours.BUTTON_EDGE;
            body = selected ? WidgetColours.SELECTED_BODY : (hot ? SecurityColours.BUTTON_HOVER : SecurityColours.BUTTON_BODY);
        }
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        if (danger && this.active && hot) {
            // A second edge line so a focused danger button reads as focused without relying on hue.
            g.outline(x + 1, y + 1, w - 2, h - 2, SecurityColours.DANGER_EDGE);
        }
        int text = !this.active ? WidgetColours.DISABLED_TEXT
                : (hot || selected ? WidgetColours.BUTTON_TEXT_HOT : WidgetColours.BUTTON_TEXT);
        extractLabel(g, Minecraft.getInstance().font, x, y, w, h, text);
    }

    /** Draws the label; override for a glyph button. */
    protected void extractLabel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int colour) {
        WidgetText.drawCentredFitted(g, font, getMessage(), x + w / 2, y + (h - 8) / 2, w - 4, colour);
    }
}
