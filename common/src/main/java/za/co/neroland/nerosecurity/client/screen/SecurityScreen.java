package za.co.neroland.nerosecurity.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.widget.ConfirmPanel;
import za.co.neroland.nerosecurity.client.widget.Feedback;
import za.co.neroland.nerosecurity.client.widget.ScrollRegion;
import za.co.neroland.nerosecurity.client.widget.SecurityButton;
import za.co.neroland.nerosecurity.client.widget.SecurityWidget;
import za.co.neroland.nerosecurity.client.widget.SlotFrame;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetGroup;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * The procedural hull every NeroSecurity screen sits on (ported from Nerospace's {@code TexturedContainerScreen}
 * palette, with no GUI texture at all):
 *
 * <pre>
 *  ┌────────────────────────────────────────────┐  1-px EDGE border, 1-px INK inner line, PANEL body
 *  │ Title — one line, a question or sentence ✕ │  y+6   TITLE    {@link #setTitleLine}
 *  │ Subtitle — what this screen controls.      │  y+17  SUBTLE   {@link #setSubtitle}
 *  │                                            │  y+30  body     {@link #extractPanel}  ({@link #BODY_Y})
 *  │ ■ footer: the last action's result         │  last 14 px, TROUGH strip   {@link #feedbackOk} …
 *  ├────────────────────────────────────────────┤
 *  │ Inventory                                  │  optional player inventory (+{@link #INVENTORY_HEIGHT})
 *  └────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Lifecycle for subclasses</h2>
 * <ol>
 *   <li>Constructor: {@code super(menu, inv, title, W, H, withInventory, ClientXState::version)}, then
 *       {@link #setTitleLine} / {@link #setSubtitle}.</li>
 *   <li>{@link #initWidgets()} (instead of {@code init()}): add widgets with {@code addRenderableWidget},
 *       {@link #addGroup} and {@link #addScrollRegion}, using absolute coords {@code leftPos + dx}.</li>
 *   <li>{@link #refreshWidgets()} runs after init, after every new snapshot and every tick: mirror the snapshot
 *       into {@code select/setChecked/setValue/enable/disable(why)}.</li>
 *   <li>{@link #onSnapshot(boolean)} runs once per new snapshot: usually
 *       {@code feedbackFromSnapshot(snap.ok(), snap.message(), answered)}.</li>
 *   <li>{@link #extractPanel} draws the body (text, pills, status dots, gauges, sections). Widgets draw
 *       themselves on top.</li>
 * </ol>
 *
 * <h2>Intents</h2>
 * {@link #sendIntent} sends a payload and calls {@link #awaitSnapshot()}: every intent widget (a
 * {@link SecurityWidget} with {@code isIntent()}) is held inactive, tooltip "Waiting for the server…", until the
 * version supplier changes. If no answer comes within {@link #AWAIT_TIMEOUT_TICKS} the hold is released with a
 * hint. The screen never computes a permission; it mirrors {@code viewerLevel}/{@code admin} and the server
 * re-authorises every payload.
 *
 * <h2>Disabled controls</h2>
 * Every disabled widget states why ({@link SecurityWidget#disable(Component)}). For read-only areas use
 * {@link #explain} to attach a hover sentence.
 *
 * CLIENT-ONLY.
 */
public abstract class SecurityScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    public static final int PAD = 8;
    public static final int TITLE_Y = 6;
    public static final int SUBTITLE_Y = 17;
    /** First body row (panel-relative). */
    public static final int BODY_Y = 30;
    public static final int FOOTER_HEIGHT = 14;
    /** Height added below the panel when the player inventory is shown. */
    public static final int INVENTORY_HEIGHT = 96;
    /** Panel-relative offsets inside the inventory section: label, first main row, hotbar. */
    public static final int INVENTORY_LABEL_OFFSET = 4;
    public static final int INVENTORY_ROWS_OFFSET = 14;
    public static final int HOTBAR_OFFSET = 72;
    /** Ticks a held screen waits for an answer before releasing the hold. */
    public static final int AWAIT_TIMEOUT_TICKS = 100;
    private static final int CLOSE_SIZE = 11;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int OFFSCREEN = -100_000;

    private final int panelWidth;
    private final int panelHeight;
    private final boolean showInventory;
    private final @Nullable LongSupplier versionSource;
    private final List<ScrollRegion> scrollRegions = new ArrayList<>();

    private Component titleLine;
    private @Nullable Component subtitle;
    private int titleRightInset;
    private @Nullable Feedback feedback;
    private boolean frameSlots = true;

    private long seenVersion = Long.MIN_VALUE;
    private long awaitingVersion = Long.MIN_VALUE;
    private int awaitTicks;
    private @Nullable ConfirmPanel confirm;
    /** The mouse position for this frame as widgets see it (off-screen while a confirm panel is open). */
    private int frameMouseX;
    private int frameMouseY;

    /**
     * @param panelWidth     panel width in px (e.g. 248)
     * @param panelHeight    panel height in px, footer included, inventory excluded
     * @param showInventory  draw the player inventory section below the panel; the menu's player slots must be
     *                       placed at {@code panelHeight + INVENTORY_ROWS_OFFSET} / {@code + HOTBAR_OFFSET}
     * @param versionSource  the client state's snapshot counter (e.g. {@code ClientDoorState::version}); a screen
     *                       with two snapshots passes their sum. {@code null}: no hold, no snapshot callbacks.
     */
    protected SecurityScreen(T menu, Inventory inventory, Component title, int panelWidth, int panelHeight,
                             boolean showInventory, @Nullable LongSupplier versionSource) {
        super(menu, inventory, title, panelWidth, panelHeight + (showInventory ? INVENTORY_HEIGHT : 0));
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.showInventory = showInventory;
        this.versionSource = versionSource;
        this.titleLine = title;
        this.inventoryLabelX = PAD;
        this.inventoryLabelY = panelHeight + INVENTORY_LABEL_OFFSET;
    }

    // --- layout accessors -----------------------------------------------------------------------

    public final int panelWidth() {
        return panelWidth;
    }

    public final int panelHeight() {
        return panelHeight;
    }

    /** Absolute x of the panel's left edge. */
    public final int left() {
        return leftPos;
    }

    /** Absolute y of the panel's top edge. */
    public final int top() {
        return topPos;
    }

    /** Absolute y of the footer strip. */
    public final int footerTop() {
        return topPos + panelHeight - FOOTER_HEIGHT;
    }

    /** Panel-relative y of the first inventory row, for menus that place player slots. */
    public static int inventoryRowsY(int panelHeight) {
        return panelHeight + INVENTORY_ROWS_OFFSET;
    }

    /** Panel-relative y of the hotbar, for menus that place player slots. */
    public static int hotbarY(int panelHeight) {
        return panelHeight + HOTBAR_OFFSET;
    }

    public final Font font() {
        return this.font;
    }

    // --- header ---------------------------------------------------------------------------------

    /** The one-line title (a question or plain sentence). Defaults to the menu title. */
    public final void setTitleLine(Component title) {
        this.titleLine = title == null ? this.title : title;
    }

    /** The plain sentence under the title saying what this screen controls. Every screen sets one. */
    public final void setSubtitle(@Nullable Component subtitle) {
        this.subtitle = subtitle;
    }

    /** Pixels reserved at the right of the title row (for a {@code TabBar}); the close button is extra. */
    public final void setTitleRightInset(int px) {
        this.titleRightInset = Math.max(0, px);
    }

    /** Whether the hull draws a frame behind every active menu slot (default {@code true}). */
    protected final void setFrameSlots(boolean frameSlots) {
        this.frameSlots = frameSlots;
    }

    /** Override to {@code false} to hide the top-right close button. */
    protected boolean showCloseButton() {
        return true;
    }

    // --- footer feedback ------------------------------------------------------------------------

    public final void feedbackOk(Component text) {
        this.feedback = Feedback.ok(text);
    }

    public final void feedbackDeny(Component text) {
        this.feedback = Feedback.deny(text);
    }

    public final void feedbackHint(Component text) {
        this.feedback = Feedback.hint(text);
    }

    public final void clearFeedback() {
        this.feedback = null;
    }

    public final @Nullable Feedback feedback() {
        return feedback;
    }

    /**
     * Routes a snapshot's {@code (ok, message)} tail to the footer: a message shows green when {@code ok}, red
     * otherwise; no message after an answered intent shows "Saved." (ok) or clears the waiting hint.
     */
    public final void feedbackFromSnapshot(boolean ok, @Nullable Component message, boolean answeredIntent) {
        if (message != null && !message.getString().isEmpty()) {
            this.feedback = ok ? Feedback.ok(message) : Feedback.deny(message);
        } else if (answeredIntent) {
            this.feedback = ok ? Feedback.ok(Component.translatable("ui.nerosecurity.saved")) : null;
        }
    }

    // --- intents and the snapshot hold ----------------------------------------------------------

    /** Sends {@code payload} to the server and holds intent widgets until the next snapshot. */
    protected final void sendIntent(CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
        awaitSnapshot();
    }

    /** Holds every intent widget until the version supplier changes. No-op without a supplier. */
    protected final void awaitSnapshot() {
        if (versionSource == null) {
            return;
        }
        this.awaitingVersion = versionSource.getAsLong();
        this.awaitTicks = 0;
        feedbackHint(Component.translatable("ui.nerosecurity.waiting"));
        applyHold();
    }

    /** {@code true} while an intent is waiting for its answer. */
    public final boolean isAwaiting() {
        return awaitingVersion != Long.MIN_VALUE;
    }

    /** Mirror the snapshot into the widgets. Runs after init, on every new snapshot and every tick. */
    protected void refreshWidgets() {
    }

    /**
     * Runs once per new snapshot, before {@link #refreshWidgets()}. {@code answeredIntent} is {@code true} when
     * this snapshot ended an {@link #awaitSnapshot()} hold. Typical body:
     * {@code feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent)}.
     */
    protected void onSnapshot(boolean answeredIntent) {
    }

    private void applyHold() {
        boolean held = isAwaiting();
        Component why = held ? Component.translatable("ui.nerosecurity.waiting") : null;
        for (GuiEventListener child : children()) {
            if (child instanceof SecurityWidget widget) {
                widget.setHeld(held, why);
            }
        }
    }

    private void pollSnapshot() {
        if (versionSource == null) {
            return;
        }
        long version = versionSource.getAsLong();
        if (version != seenVersion) {
            boolean first = seenVersion == Long.MIN_VALUE;
            seenVersion = version;
            boolean answered = isAwaiting() && !first;
            awaitingVersion = Long.MIN_VALUE;
            applyHold();
            onSnapshot(answered);
        } else if (isAwaiting() && ++awaitTicks >= AWAIT_TIMEOUT_TICKS) {
            awaitingVersion = Long.MIN_VALUE;
            applyHold();
            feedbackHint(Component.translatable("ui.nerosecurity.no_answer"));
        }
    }

    // --- widget registration --------------------------------------------------------------------

    /** Adds every child of a {@link WidgetGroup} (ladder, segmented control, tab bar) and returns the group. */
    protected final <G extends WidgetGroup> G addGroup(G group) {
        for (AbstractWidget widget : group.widgets()) {
            addRenderableWidget(widget);
        }
        return group;
    }

    /** Registers a mouse-wheel region (e.g. a {@code SentenceList}) and returns it. */
    protected final <S extends ScrollRegion> S addScrollRegion(S region) {
        scrollRegions.add(region);
        return region;
    }

    // --- confirm panel --------------------------------------------------------------------------

    /** Opens {@code panel} as the modal on this screen. Use {@link ConfirmPanel#show} rather than calling this. */
    public final void openConfirm(ConfirmPanel panel) {
        this.confirm = panel;
        setFocused(null);
        panel.layout(this.font, this.width, this.height);
    }

    /** Closes {@code panel} if it is the open modal. */
    public final void closeConfirm(ConfirmPanel panel) {
        if (this.confirm == panel) {
            this.confirm = null;
        }
    }

    public final boolean hasConfirm() {
        return confirm != null;
    }

    // --- lifecycle ------------------------------------------------------------------------------

    @Override
    protected final void init() {
        super.init();
        scrollRegions.clear();
        if (showCloseButton()) {
            addRenderableWidget(new CloseButton(leftPos + panelWidth - CLOSE_SIZE - 4, topPos + 4,
                    b -> onClose()));
        }
        initWidgets();
        if (confirm != null) {
            confirm.layout(this.font, this.width, this.height);
        }
        applyHold();
        refreshWidgets();
    }

    /** Add this screen's widgets (absolute coordinates). Called on open and on every resize. */
    protected abstract void initWidgets();

    @Override
    protected void containerTick() {
        super.containerTick();
        pollSnapshot();
        applyHold();
        for (GuiEventListener child : children()) {
            if (child instanceof SecurityWidget widget) {
                widget.tick();
            }
        }
        refreshWidgets();
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    public final void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ConfirmPanel modalPanel = confirm;
        boolean modal = modalPanel != null;
        this.frameMouseX = modal ? OFFSCREEN : mouseX;
        this.frameMouseY = modal ? OFFSCREEN : mouseY;
        drawHull(g);
        extractPanel(g, frameMouseX, frameMouseY, partialTick);
        super.extractContents(g, frameMouseX, frameMouseY, partialTick);
        extractOverlay(g, frameMouseX, frameMouseY, partialTick);
        if (modalPanel != null) {
            g.nextStratum();
            modalPanel.extract(g, this.font, mouseX, mouseY, partialTick);
        }
    }

    /**
     * The screen body, drawn on the panel before the widgets (so section boxes and troughs sit behind them).
     * {@code mouseX/Y} are off-screen while a confirm panel is open.
     */
    protected abstract void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick);

    /** Optional drawing on top of the widgets (rarely needed). */
    protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    private void drawHull(GuiGraphicsExtractor g) {
        int x = leftPos;
        int y = topPos;
        int w = panelWidth;
        int h = imageHeight;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SecurityColours.EDGE);
        g.fill(x, y, x + w, y + h, SecurityColours.INK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SecurityColours.PANEL);

        int titleRoom = w - 2 * PAD - titleRightInset - (showCloseButton() ? CLOSE_SIZE + 6 : 0);
        WidgetText.drawFitted(g, font, titleLine, x + PAD, y + TITLE_Y, titleRoom, SecurityColours.TITLE);
        if (WidgetText.overflows(font, titleLine, titleRoom)) {
            explain(g, x + PAD, y + TITLE_Y, titleRoom, 9, titleLine);
        }
        if (subtitle != null) {
            int room = w - 2 * PAD;
            WidgetText.drawFitted(g, font, subtitle, x + PAD, y + SUBTITLE_Y, room, SecurityColours.SUBTLE);
            if (WidgetText.overflows(font, subtitle, room)) {
                explain(g, x + PAD, y + SUBTITLE_Y, room, 9, subtitle);
            }
        }

        int fy = y + panelHeight - FOOTER_HEIGHT;
        g.fill(x + 1, fy, x + w - 1, y + panelHeight - 1, SecurityColours.TROUGH);
        g.fill(x + 1, fy, x + w - 1, fy + 1, SecurityColours.EDGE);
        if (feedback != null) {
            int tx = x + PAD;
            switch (feedback.kind()) {
                case OK -> {
                    StatusDot.drawGlyph(g, tx, fy + 4, Status.OK);
                    tx += StatusDot.SIZE + 4;
                }
                case DENY -> {
                    StatusDot.drawShape(g, tx, fy + 4, Status.Shape.CROSS, SecurityColours.DENY_TEXT);
                    tx += StatusDot.SIZE + 4;
                }
                case HINT -> {
                }
            }
            int room = x + w - PAD - tx;
            WidgetText.drawFitted(g, font, feedback.text(), tx, fy + 3, room, WidgetColours.footerText(feedback.kind()));
            if (WidgetText.overflows(font, feedback.text(), room)) {
                explain(g, tx, fy + 3, room, 9, feedback.text());
            }
        }

        if (showInventory) {
            g.fill(x + 1, y + panelHeight - 1, x + w - 1, y + panelHeight, SecurityColours.EDGE);
        }
        if (frameSlots) {
            for (Slot slot : menu.slots) {
                if (slot.isActive()) {
                    SlotFrame.draw(g, x + slot.x, y + slot.y);
                }
            }
        }
    }

    /** Hull labels: only the inventory caption (in {@code SUBTLE}); the title row is drawn by the hull. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (showInventory) {
            g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                    SecurityColours.SUBTLE, false);
        }
    }

    /**
     * Shows {@code text} (wrapped) as a tooltip while the mouse is over the given absolute rectangle. Call it from
     * {@link #extractPanel} for read-only things that need a longer sentence (status rows, pills, truncated
     * lines). Suppressed while a confirm panel is open.
     */
    protected final void explain(GuiGraphicsExtractor g, int x, int y, int w, int h, @Nullable Component text) {
        if (text == null) {
            return;
        }
        int mx = frameMouseX;
        int my = frameMouseY;
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            List<FormattedCharSequence> lines = font.split(text, TOOLTIP_WIDTH);
            g.setTooltipForNextFrame(font, lines, mx, my);
        }
    }

    // --- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (confirm != null) {
            return confirm.mouseClicked(event, doubleClick);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (confirm != null) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (confirm != null) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (confirm != null) {
            return true;
        }
        for (ScrollRegion region : scrollRegions) {
            if (region.mouseScrolled(x, y, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (confirm != null) {
            return confirm.keyPressed(event);
        }
        // A focused text box keeps every key except Esc and Tab, so typing "e" never closes the screen.
        if (getFocused() instanceof EditBox box && box.canConsumeInput() && !event.isEscape() && !event.isCycleFocus()) {
            box.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (confirm != null) {
            return true;
        }
        return super.charTyped(event);
    }

    /** The ✕ in the top-right corner, drawn as two diagonals so it needs no glyph. Navigation, never held. */
    private static final class CloseButton extends SecurityButton {

        CloseButton(int x, int y, java.util.function.Consumer<SecurityButton> onPress) {
            super(x, y, CLOSE_SIZE, CLOSE_SIZE, Component.translatable("ui.nerosecurity.close"), onPress);
            setIntent(false);
            setHelp(Component.translatable("ui.nerosecurity.close"));
        }

        @Override
        protected void extractLabel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int colour) {
            int s = 5;
            int ox = x + (w - s) / 2;
            int oy = y + (h - s) / 2;
            for (int i = 0; i < s; i++) {
                g.fill(ox + i, oy + i, ox + i + 1, oy + i + 1, colour);
                g.fill(ox + s - 1 - i, oy + i, ox + s - i, oy + i + 1, colour);
            }
        }
    }
}
