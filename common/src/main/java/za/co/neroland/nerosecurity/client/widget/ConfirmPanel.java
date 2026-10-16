package za.co.neroland.nerosecurity.client.widget;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;

/**
 * The confirm step for every destructive action (lockdown on/off/keep, remove member, erase data): a modal
 * panel, 220 px wide and at least 72 tall, centred on the screen over a dim. A title line, the consequence
 * (wrapped, never cut), then {@code [Yes, …]} (danger) and {@code [Cancel]}.
 *
 * <ul>
 *   <li>While open, the screen underneath gets no clicks, keys or hover tooltips.</li>
 *   <li>Keyboard focus is trapped in the panel and starts on <b>Cancel</b>; Tab / arrows switch, Enter or Space
 *       presses, Esc cancels.</li>
 *   <li>Yes closes the panel first, then runs {@code onYes} (normally a {@code sendIntent}).</li>
 * </ul>
 *
 * <pre>{@code
 * ConfirmPanel.show(this,
 *         Component.translatable("hub.nerosecurity.lockdown.confirm.on.title", snap.name()),
 *         Component.translatable("hub.nerosecurity.lockdown.confirm.on.body"),
 *         Component.translatable("hub.nerosecurity.lockdown.confirm.on.yes"),
 *         () -> sendIntent(new HubLockdown(snap.installationId(), true)));
 * }</pre>
 * CLIENT-ONLY.
 */
public final class ConfirmPanel {

    public static final int WIDTH = 220;
    public static final int MIN_HEIGHT = 72;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int BUTTON_H = 16;

    private final SecurityScreen<?> screen;
    private final Component title;
    private final Component body;
    private final Runnable onYes;
    private final SecurityButton yes;
    private final SecurityButton cancel;
    private int x;
    private int y;
    private int height = MIN_HEIGHT;
    private List<FormattedCharSequence> bodyLines = List.of();
    private List<FormattedCharSequence> titleLines = List.of();
    /** 0 = yes, 1 = cancel. */
    private int focus = 1;

    private ConfirmPanel(SecurityScreen<?> screen, Component title, Component body, Component yesLabel, Runnable onYes) {
        this.screen = screen;
        this.title = title;
        this.body = body;
        this.onYes = onYes;
        this.yes = SecurityButton.danger(0, 0, 100, BUTTON_H, yesLabel, b -> confirm());
        this.cancel = SecurityButton.of(0, 0, 100, BUTTON_H, Component.translatable("ui.nerosecurity.cancel"), b -> cancel());
        this.yes.setIntent(false);
        this.cancel.setIntent(false);
    }

    /** Opens a confirm panel on {@code screen} (replacing any open one) and returns it. */
    public static ConfirmPanel show(SecurityScreen<?> screen, Component title, Component body, Component yesLabel,
                                    Runnable onYes) {
        ConfirmPanel panel = new ConfirmPanel(screen, title, body, yesLabel, onYes);
        screen.openConfirm(panel);
        return panel;
    }

    /** Closes without acting. */
    public void cancel() {
        screen.closeConfirm(this);
    }

    private void confirm() {
        screen.closeConfirm(this);
        onYes.run();
    }

    /** Lays the panel out centred in a {@code screenWidth × screenHeight} screen (the hull calls this). */
    public void layout(Font font, int screenWidth, int screenHeight) {
        int inner = WIDTH - 2 * PAD;
        titleLines = WidgetText.wrap(font, title, inner);
        bodyLines = WidgetText.wrap(font, body, inner);
        int content = PAD + titleLines.size() * LINE + 4 + bodyLines.size() * LINE + 8 + BUTTON_H + PAD;
        height = Math.max(MIN_HEIGHT, content);
        x = (screenWidth - WIDTH) / 2;
        y = (screenHeight - height) / 2;
        int bw = (inner - 6) / 2;
        int by = y + height - PAD - BUTTON_H;
        yes.setX(x + PAD);
        yes.setY(by);
        yes.setWidth(bw);
        cancel.setX(x + PAD + bw + 6);
        cancel.setY(by);
        cancel.setWidth(inner - bw - 6);
        applyFocus();
    }

    private void applyFocus() {
        yes.setFocused(focus == 0);
        cancel.setFocused(focus == 1);
    }

    /** Draws dim + panel + buttons (the hull calls this on a new stratum, after everything else). */
    public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, screen.width, screen.height, WidgetColours.MODAL_DIM);
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, SecurityColours.DANGER_EDGE);
        g.fill(x, y, x + WIDTH, y + height, SecurityColours.INK);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + height - 1, SecurityColours.PANEL);
        int ty = y + PAD;
        for (FormattedCharSequence line : titleLines) {
            g.text(font, line, x + PAD, ty, SecurityColours.TITLE, false);
            ty += LINE;
        }
        ty += 4;
        for (FormattedCharSequence line : bodyLines) {
            g.text(font, line, x + PAD, ty, WidgetColours.TEXT, false);
            ty += LINE;
        }
        yes.extractRenderState(g, mouseX, mouseY, partialTick);
        cancel.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    /** Routes a click; the screen beneath never sees it. */
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (yes.mouseClicked(event, doubleClick)) {
            return true;
        }
        cancel.mouseClicked(event, doubleClick);
        return true;
    }

    /** Esc cancels, Tab/arrows move focus, Enter/Space presses the focused button. Always consumes the key. */
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            cancel();
        } else if (event.isCycleFocus() || event.isLeft() || event.isRight()) {
            focus = 1 - focus;
            applyFocus();
        } else if (event.isSelection()) {
            (focus == 0 ? yes : cancel).keyPressed(event);
        }
        return true;
    }

    public Component title() {
        return title;
    }
}
