package za.co.neroland.nerosecurity.client.widget;

import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * {@code [−] 5 [+]}, 60×16. Clicking − or + steps by 1 (Shift: 5); holding repeats after 400 ms; Left/Right
 * arrows step while focused. The value is clamped to {@link #setRange}. Changes are <b>debounced</b>: the
 * widget calls {@code onChange(value)} once, after {@value #DEBOUNCE_TICKS} quiet ticks, not on every step.
 * While a local edit is pending, {@link #setValue} from a snapshot is ignored so the number does not jump back.
 *
 * <pre>{@code
 * width = addRenderableWidget(new Stepper(leftPos + 40, topPos + 100, 1, 9, v -> sendIntent(new FieldConfigure(pos, settings.withWidth(v)))));
 * width.setRange(1, snap.maxWidth());
 * width.setValue(snap.settings().width());
 * }</pre>
 * CLIENT-ONLY.
 */
public class Stepper extends SecurityWidget {

    public static final int WIDTH = 60;
    public static final int HEIGHT = 16;
    public static final int DEBOUNCE_TICKS = 10;
    public static final long REPEAT_DELAY_MS = 400L;
    private static final int ZONE = 16;

    private final IntConsumer onChange;
    private int min;
    private int max;
    private int value;
    private boolean pending;
    private int quietTicks;
    /** -1 holding minus, +1 holding plus, 0 not holding. */
    private int holding;
    private long holdStart;

    public Stepper(int x, int y, int min, int max, IntConsumer onChange) {
        super(x, y, WIDTH, HEIGHT, Component.empty());
        this.onChange = onChange;
        setRange(min, max);
        this.value = this.min;
    }

    /** Sets the bounds (swapped if reversed) and re-clamps the value. */
    public final void setRange(int min, int max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.value = clamp(this.value);
    }

    /** Mirrors the snapshot value; ignored while a local edit has not been sent yet. */
    public final void setValue(int value) {
        if (!pending && holding == 0) {
            this.value = clamp(value);
        }
    }

    public final int value() {
        return value;
    }

    /** {@code true} while a stepped value is waiting for its debounced send. */
    public final boolean isPending() {
        return pending;
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    private void step(int delta) {
        int next = clamp(value + delta);
        if (next != value) {
            value = next;
            pending = true;
        }
        quietTicks = 0;
    }

    private static int amount(boolean shift) {
        return shift ? 5 : 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double local = event.x() - getX();
        int dir = local < ZONE ? -1 : (local >= getWidth() - ZONE ? 1 : 0);
        if (dir != 0) {
            step(dir * amount(event.hasShiftDown()));
            holding = dir;
            holdStart = System.currentTimeMillis();
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        holding = 0;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active || !this.visible) {
            return false;
        }
        if (event.isLeft() || event.isRight()) {
            step((event.isRight() ? 1 : -1) * amount(event.hasShiftDown()));
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (holding != 0) {
            if (!this.active || !Minecraft.getInstance().mouseHandler.isLeftPressed()) {
                holding = 0;
            } else if (System.currentTimeMillis() - holdStart >= REPEAT_DELAY_MS) {
                step(holding);
            }
        }
        if (pending && holding == 0 && ++quietTicks >= DEBOUNCE_TICKS) {
            pending = false;
            quietTicks = 0;
            onChange.accept(value);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean on = this.active;
        int edge = !on ? WidgetColours.DISABLED_EDGE : (isFocused() ? WidgetColours.EDGE_HOT : SecurityColours.BUTTON_EDGE);
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? SecurityColours.TROUGH : WidgetColours.DISABLED_BODY);
        boolean overMinus = on && isHovered() && mouseX < x + ZONE;
        boolean overPlus = on && isHovered() && mouseX >= x + w - ZONE;
        zone(g, x + 1, y + 1, ZONE - 1, h - 2, on && value > min, overMinus);
        zone(g, x + w - ZONE, y + 1, ZONE - 1, h - 2, on && value < max, overPlus);
        int ink = on ? WidgetColours.TEXT : WidgetColours.DISABLED_TEXT;
        // Minus and plus drawn as bars so they never depend on a font glyph.
        int cy = y + h / 2;
        int mx = x + ZONE / 2;
        g.fill(mx - 3, cy - 1, mx + 3, cy + 1, on && value > min ? ink : WidgetColours.DISABLED_TEXT);
        int px = x + w - ZONE / 2;
        int plusInk = on && value < max ? ink : WidgetColours.DISABLED_TEXT;
        g.fill(px - 3, cy - 1, px + 3, cy + 1, plusInk);
        g.fill(px - 1, cy - 3, px + 1, cy + 3, plusInk);
        g.centeredText(Minecraft.getInstance().font, Component.literal(Integer.toString(value)), x + w / 2, y + (h - 8) / 2, ink);
    }

    private static void zone(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean enabled, boolean hover) {
        int body = !enabled ? WidgetColours.DISABLED_BODY : (hover ? SecurityColours.BUTTON_HOVER : SecurityColours.BUTTON_BODY);
        g.fill(x, y, x + w, y + h, body);
    }
}
