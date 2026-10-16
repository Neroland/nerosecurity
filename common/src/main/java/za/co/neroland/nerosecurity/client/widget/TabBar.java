package za.co.neroland.nerosecurity.client.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * Two or more 64×14 tabs in a row. The active tab's word is white with a 2-px underline; the others are
 * {@code SUBTLE} with no underline (shape, not only colour). Tabs are navigation, so the hull never holds them
 * while waiting for the server. Pressing a tab calls {@code onSelect(index)}; the screen switches its body and
 * calls {@link #select}.
 *
 * <pre>{@code
 * tabs = addGroup(new TabBar(leftPos + 160, topPos + 4, List.of(
 *         Component.translatable("hub.nerosecurity.tab.overview"), Component.translatable("hub.nerosecurity.tab.privacy")),
 *         this::showTab));
 * setTitleRightInset(TabBar.TAB_WIDTH * 2 + 20);
 * }</pre>
 * CLIENT-ONLY.
 */
public final class TabBar implements WidgetGroup {

    public static final int TAB_WIDTH = 64;
    public static final int TAB_HEIGHT = 14;

    private final List<Tab> tabs = new ArrayList<>();
    private final IntConsumer onSelect;
    private int selected;

    public TabBar(int x, int y, List<Component> labels, IntConsumer onSelect) {
        this(x, y, TAB_WIDTH, labels, onSelect);
    }

    public TabBar(int x, int y, int tabWidth, List<Component> labels, IntConsumer onSelect) {
        this.onSelect = onSelect;
        for (int i = 0; i < labels.size(); i++) {
            Tab tab = new Tab(x + i * tabWidth, y, tabWidth, i, labels.get(i));
            tab.setIntent(false);
            tabs.add(tab);
        }
    }

    @Override
    public List<? extends SecurityWidget> widgets() {
        return Collections.unmodifiableList(tabs);
    }

    public void select(int index) {
        this.selected = index;
    }

    public int selected() {
        return selected;
    }

    public void disable(int index, Component why) {
        Tab tab = tab(index);
        if (tab != null) {
            tab.disable(why);
        }
    }

    public void enable(int index) {
        Tab tab = tab(index);
        if (tab != null) {
            tab.enable();
        }
    }

    private @Nullable Tab tab(int index) {
        return index >= 0 && index < tabs.size() ? tabs.get(index) : null;
    }

    private final class Tab extends SecurityWidget {

        private final int index;

        Tab(int x, int y, int w, int index, Component label) {
            super(x, y, w, TAB_HEIGHT, label);
            this.index = index;
        }

        private void press() {
            if (this.active && index != selected) {
                onSelect.accept(index);
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
            boolean isSelected = index == selected;
            boolean hot = hot();
            if (hot && !isSelected) {
                g.fill(x, y, x + w, y + h, SecurityColours.BUTTON_HOVER);
            }
            int text = !this.active ? WidgetColours.DISABLED_TEXT
                    : (isSelected || hot ? SecurityColours.TITLE : SecurityColours.SUBTLE);
            WidgetText.drawCentredFitted(g, Minecraft.getInstance().font, getMessage(), x + w / 2, y + 3, w - 4, text);
            if (isSelected) {
                g.fill(x + 4, y + h - 2, x + w - 4, y + h, SecurityColours.TITLE);
            }
        }
    }
}
