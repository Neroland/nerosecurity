package za.co.neroland.nerosecurity.client.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * {@code n} equal buttons touching edge to edge, one selected (filled body, white text, bright edge). Used for
 * sensor entity mode, camera field of view and field shape. Display only: {@link #select} mirrors the
 * snapshot; pressing another segment calls {@code onSelect(index)}.
 *
 * <pre>{@code
 * fov = addGroup(new Segmented(leftPos + 90, topPos + 82, 120, 16,
 *         List.of(Component.literal("60°"), Component.literal("90°"), Component.literal("120°")),
 *         i -> sendIntent(new SensorConfigure(pos, settings.withFov(FOVS[i])))));
 * fov.select(indexOf(snap.fov()));
 * }</pre>
 * CLIENT-ONLY.
 */
public final class Segmented implements WidgetGroup {

    private final List<SecurityButton> segments = new ArrayList<>();
    private int selected = -1;

    public Segmented(int x, int y, int width, int height, List<Component> labels, IntConsumer onSelect) {
        int n = Math.max(1, labels.size());
        int base = width / n;
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            int w = i == labels.size() - 1 ? x + width - cx : base;
            final int index = i;
            SecurityButton b = SecurityButton.of(cx, y, w, height, labels.get(i), btn -> {
                if (index != selected) {
                    onSelect.accept(index);
                }
            });
            segments.add(b);
            cx += w;
        }
    }

    @Override
    public List<? extends SecurityWidget> widgets() {
        return Collections.unmodifiableList(segments);
    }

    /** Shows segment {@code index} as selected; -1 for none. */
    public void select(int index) {
        this.selected = index;
        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).setSelected(i == index);
        }
    }

    public int selected() {
        return selected;
    }

    public void disable(int index, Component why) {
        SecurityButton b = segment(index);
        if (b != null) {
            b.disable(why);
        }
    }

    public void enable(int index) {
        SecurityButton b = segment(index);
        if (b != null) {
            b.enable();
        }
    }

    public void disableAll(Component why) {
        segments.forEach(b -> b.disable(why));
    }

    public void enableAll() {
        segments.forEach(SecurityWidget::enable);
    }

    /** Tooltip for one segment while enabled. */
    public void setHelp(int index, @Nullable Component help) {
        SecurityButton b = segment(index);
        if (b != null) {
            b.setHelp(help);
        }
    }

    /** Hides or shows the whole control (e.g. camera-only rows). */
    public void setVisible(boolean visible) {
        segments.forEach(b -> b.visible = visible);
    }

    public @Nullable SecurityButton segment(int index) {
        return index >= 0 && index < segments.size() ? segments.get(index) : null;
    }
}
