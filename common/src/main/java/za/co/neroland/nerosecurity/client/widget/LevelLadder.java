package za.co.neroland.nerosecurity.client.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.SecurityColours;

/**
 * The access ladder as four coloured buttons in a row — PUBLIC · MEMBER · OFFICER · OWNER, 54×20 each, 4 px
 * apart (228 px total). Each unselected button has a 3-px band in its level colour along the top and its word
 * in white; the <b>selected</b> one is filled with the level colour with dark ink text (filled vs outlined, so
 * the selection is not shown by hue alone). ADMIN is never a button. Hovering an enabled button shows that
 * level's meaning; a disabled one shows its reason.
 *
 * <p>Display only: {@link #select} mirrors the snapshot; pressing a different level calls {@code onSelect},
 * and the screen sends the intent (normally via {@code sendIntent}, which holds the ladder until the answer).
 * Pressing the already-selected level does nothing.
 *
 * <pre>{@code
 * ladder = addGroup(new LevelLadder(leftPos + 8, topPos + 30, level -> sendIntent(new DoorConfigure(pos, level, ...))));
 * // in refreshWidgets():
 * ladder.select(snap.minLevel());
 * ladder.restrictOwner(snap.viewerLevel(), snap.admin(), Component.translatable("door.nerosecurity.owner_only_setting"));
 * // in extractPanel():
 * LevelMeaning.draw(g, font, leftPos + 8, topPos + 52, LevelLadder.WIDTH, ladder.selected());
 * }</pre>
 * CLIENT-ONLY.
 */
public final class LevelLadder implements WidgetGroup {

    public static final int BUTTON_WIDTH = 54;
    public static final int BUTTON_HEIGHT = 20;
    public static final int GAP = 4;
    /** Width of the standard four-button ladder. */
    public static final int WIDTH = 4 * BUTTON_WIDTH + 3 * GAP;
    private static final int BAND = 3;

    private static final List<AccessLevel> FOUR = List.of(
            AccessLevel.PUBLIC, AccessLevel.MEMBER, AccessLevel.OFFICER, AccessLevel.OWNER);

    private final List<Rung> rungs = new ArrayList<>();
    private final Consumer<AccessLevel> onSelect;
    private @Nullable AccessLevel selected;

    /** The standard four-level ladder at ({@code x}, {@code y}). */
    public LevelLadder(int x, int y, Consumer<AccessLevel> onSelect) {
        this(x, y, FOUR, BUTTON_WIDTH, BUTTON_HEIGHT, onSelect);
    }

    /**
     * A ladder over a subset (e.g. the hub's MEMBER/OFFICER add-row mini ladder), lowest first, with a custom
     * button size.
     */
    public LevelLadder(int x, int y, List<AccessLevel> levels, int buttonWidth, int buttonHeight,
                       Consumer<AccessLevel> onSelect) {
        this.onSelect = onSelect;
        int cx = x;
        for (AccessLevel level : levels) {
            Rung rung = new Rung(cx, y, buttonWidth, buttonHeight, level);
            rungs.add(rung);
            cx += buttonWidth + GAP;
        }
    }

    @Override
    public List<? extends SecurityWidget> widgets() {
        return Collections.unmodifiableList(rungs);
    }

    /** Shows {@code level} as the current setting ({@code null}: nothing selected yet). */
    public void select(@Nullable AccessLevel level) {
        this.selected = level;
    }

    public @Nullable AccessLevel selected() {
        return selected;
    }

    /** Disables one rung with a reason tooltip. */
    public void disable(AccessLevel level, Component why) {
        Rung rung = rung(level);
        if (rung != null) {
            rung.disable(why);
        }
    }

    public void enable(AccessLevel level) {
        Rung rung = rung(level);
        if (rung != null) {
            rung.enable();
        }
    }

    public void enableAll() {
        rungs.forEach(SecurityWidget::enable);
    }

    /** Disables every rung with the same reason (e.g. the viewer cannot configure this device at all). */
    public void disableAll(Component why) {
        rungs.forEach(r -> r.disable(why));
    }

    /**
     * The shared screen rule (display mirror only; the server re-checks): OWNER is selectable only when the
     * viewer is OWNER or an operator. Enables OWNER otherwise-unchanged when allowed.
     */
    public void restrictOwner(AccessLevel viewerLevel, boolean admin, Component why) {
        boolean allowed = admin || viewerLevel == AccessLevel.OWNER;
        Rung owner = rung(AccessLevel.OWNER);
        if (owner != null) {
            if (allowed) {
                owner.enable();
            } else {
                owner.disable(why);
            }
        }
    }

    /** The button for {@code level}, or {@code null} if this ladder does not show it. */
    public @Nullable SecurityWidget button(AccessLevel level) {
        return rung(level);
    }

    private @Nullable Rung rung(AccessLevel level) {
        for (Rung rung : rungs) {
            if (rung.level == level) {
                return rung;
            }
        }
        return null;
    }

    private final class Rung extends SecurityWidget {

        private final AccessLevel level;

        Rung(int x, int y, int w, int h, AccessLevel level) {
            super(x, y, w, h, LevelPill.word(level));
            this.level = level;
            setHelp(Component.translatable(level.translationKey()).append(" — ")
                    .append(Component.translatable(level.meaningKey())));
        }

        private void press() {
            if (this.active && selected != level) {
                onSelect.accept(level);
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
            boolean isSelected = selected == level;
            boolean hot = hot();
            if (isSelected) {
                // Filled with the level colour, ink text. A disabled selected rung stays filled: it is the
                // current setting, the viewer just cannot change away from it.
                int fill = WidgetColours.pillFill(level);
                g.fill(x, y, x + w, y + h, hot ? WidgetColours.BUTTON_TEXT_HOT : fill);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
                WidgetText.drawCentredFitted(g, Minecraft.getInstance().font, getMessage(), x + w / 2,
                        y + (h - 8) / 2 + 1, w - 4, WidgetColours.pillText(level));
                return;
            }
            int edge = !this.active ? WidgetColours.DISABLED_EDGE : (hot ? WidgetColours.EDGE_HOT : SecurityColours.EDGE);
            int body = !this.active ? WidgetColours.DISABLED_BODY
                    : (hot ? SecurityColours.BUTTON_HOVER : SecurityColours.BUTTON_BODY);
            g.fill(x, y, x + w, y + h, edge);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
            g.fill(x + 1, y + 1, x + w - 1, y + 1 + BAND, WidgetColours.ladderBand(level, this.active));
            int text = this.active ? (hot ? WidgetColours.BUTTON_TEXT_HOT : WidgetColours.TEXT) : WidgetColours.DISABLED_TEXT;
            WidgetText.drawCentredFitted(g, Minecraft.getInstance().font, getMessage(), x + w / 2,
                    y + BAND + (h - BAND - 8) / 2 + 1, w - 4, text);
        }
    }
}
