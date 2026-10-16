package za.co.neroland.nerosecurity.client.screen.field;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldShape;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.menu.field.FieldMenu;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldConfigure;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldSnapshot;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * The force field settings screen, deliberately plain (a later stage restyles it): shape and on/off toggles, distance
 * and size steppers (clamped to the emitter's range and the area cap from the snapshot; the server re-validates), the
 * four "who may pass" level buttons, keycard-only, a live power / state / load line and the four module slots. It draws
 * only what the server sent; every button sends an intent the server re-authorises, and the buttons stay disabled until
 * the next snapshot.
 *
 * <p>CLIENT-ONLY.
 */
public class FieldSettingsScreen extends AbstractContainerScreen<FieldMenu> {

    private static final int W = 176;
    private static final int H = 246;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int GOOD = 0xFF6BE38A;
    private static final int WARN = 0xFFFFB454;
    private static final int BAD = 0xFFFF6B6B;

    private static final AccessLevel[] LEVELS = {
            AccessLevel.PUBLIC, AccessLevel.MEMBER, AccessLevel.OFFICER, AccessLevel.OWNER };

    /** What a stepper changes. */
    private enum Dim { OFFSET, WIDTH, HEIGHT, DEPTH }

    private Button shapeButton;
    private Button enabledButton;
    private Button keycardButton;
    private final Button[] levelButtons = new Button[LEVELS.length];
    private final Button[] minus = new Button[Dim.values().length];
    private final Button[] plus = new Button[Dim.values().length];
    /** The snapshot version a sent intent is waiting on; buttons stay disabled until it changes. */
    private long awaiting = -1L;

    public FieldSettingsScreen(FieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = FieldMenu.INVENTORY_X;
        this.inventoryLabelY = FieldMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        this.shapeButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleShape())
                .bounds(x + 8, y + 16, 78, 16).build());
        this.enabledButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleEnabled())
                .bounds(x + 90, y + 16, 78, 16).build());
        for (Dim dim : Dim.values()) {
            int column = dim.ordinal() % 2;
            int row = dim.ordinal() / 2;
            int bx = x + 8 + column * 82;
            int by = y + 36 + row * 18;
            this.minus[dim.ordinal()] = addRenderableWidget(Button.builder(Component.literal("-"), b -> step(dim, -1))
                    .bounds(bx, by, 14, 16).build());
            this.plus[dim.ordinal()] = addRenderableWidget(Button.builder(Component.literal("+"), b -> step(dim, 1))
                    .bounds(bx + 64, by, 14, 16).build());
        }
        for (int i = 0; i < LEVELS.length; i++) {
            AccessLevel level = LEVELS[i];
            this.levelButtons[i] = addRenderableWidget(Button.builder(level.displayName(), b -> setLevel(level))
                    .bounds(x + 8 + i * 40, y + 74, 38, 16).build());
        }
        this.keycardButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleKeycard())
                .bounds(x + 8, y + 92, 160, 16).build());
        refreshWidgets();
    }

    private FieldSnapshot snapshot() {
        return ClientFieldState.current();
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientFieldState.version();
    }

    private void refreshWidgets() {
        FieldSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        AccessLevel viewer = snap == null ? AccessLevel.PUBLIC : snap.viewerLevel();
        FieldSettings settings = snap == null ? FieldSettings.DEFAULT : snap.settings();
        boolean ownerLocked = viewer != AccessLevel.OWNER && settings.access().minLevel() == AccessLevel.OWNER;
        boolean editable = ready && !ownerLocked;

        this.shapeButton.setMessage(Component.translatable("field.nerosecurity.shape",
                Component.translatable("field.nerosecurity.shape." + settings.shape().jsonName())));
        this.shapeButton.active = editable && fits(snap, toggledShape(settings));
        this.enabledButton.setMessage(CommonComponents.optionStatus(
                Component.translatable("field.nerosecurity.enabled"), settings.enabled()));
        this.enabledButton.active = editable;
        for (Dim dim : Dim.values()) {
            boolean usable = dim != Dim.DEPTH || settings.shape() == FieldShape.BOX;
            this.minus[dim.ordinal()].active = editable && usable && fits(snap, stepped(settings, dim, -1));
            this.plus[dim.ordinal()].active = editable && usable && fits(snap, stepped(settings, dim, 1));
        }
        for (int i = 0; i < LEVELS.length; i++) {
            AccessLevel level = LEVELS[i];
            boolean selected = snap != null && settings.access().minLevel() == level;
            Button button = this.levelButtons[i];
            button.setMessage(selected
                    ? Component.literal("[").append(level.displayName()).append("]")
                    : level.displayName());
            boolean ownerOnly = viewer != AccessLevel.OWNER
                    && (level == AccessLevel.OWNER || settings.access().minLevel() == AccessLevel.OWNER);
            button.active = ready && !selected && !ownerOnly;
            button.setTooltip(ownerOnly ? Tooltip.create(Component.translatable("door.nerosecurity.owner_only_setting"))
                    : Tooltip.create(Component.translatable(level.meaningKey())));
        }
        this.keycardButton.setMessage(Component.translatable("chest.nerosecurity.toggle.keycard_only",
                CommonComponents.optionStatus(settings.access().keycardOnly())));
        this.keycardButton.active = editable;
    }

    /** Whether {@code s} fits the emitter as the snapshot describes it (the server checks again). */
    private static boolean fits(FieldSnapshot snap, FieldSettings s) {
        return snap != null && FieldGeometry.validate(s, snap.range(), snap.maxArea()).isEmpty();
    }

    private static FieldSettings toggledShape(FieldSettings s) {
        if (s.shape() == FieldShape.PLANE) {
            return s.withShape(FieldShape.BOX).withSize(s.width(), s.height(), Math.max(3, s.depth()));
        }
        return s.withShape(FieldShape.PLANE).withSize(s.width(), s.height(), 1);
    }

    private static FieldSettings stepped(FieldSettings s, Dim dim, int direction) {
        return switch (dim) {
            case OFFSET -> s.withOffset(s.offset() + direction);
            case WIDTH -> s.withSize(s.width() + 2 * direction, s.height(), s.depth());
            case HEIGHT -> s.withSize(s.width(), s.height() + 2 * direction, s.depth());
            case DEPTH -> s.withSize(s.width(), s.height(), s.depth() + 2 * direction);
        };
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientFieldState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void send(FieldSettings wanted) {
        FieldSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        this.awaiting = ClientFieldState.version();
        Services.NETWORK.sendToServer(new FieldConfigure(snap.pos(), wanted));
        refreshWidgets();
    }

    private void toggleShape() {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(toggledShape(snap.settings()));
        }
    }

    private void toggleEnabled() {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withEnabled(!snap.settings().enabled()));
        }
    }

    private void step(Dim dim, int direction) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(stepped(snap.settings(), dim, direction));
        }
    }

    private void setLevel(AccessLevel level) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withAccess(snap.settings().access().withMinLevel(level)));
        }
    }

    private void toggleKeycard() {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withAccess(snap.settings().access().withKeycardOnly(!snap.settings().access().keycardOnly())));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, EDGE);
        g.fill(x, y, x + W, y + H, PANEL);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, TROUGH);
            }
        }
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawSteppers(g);
        drawStatus(g);
    }

    private void drawSteppers(GuiGraphicsExtractor g) {
        FieldSettings settings = snapshot() == null ? FieldSettings.DEFAULT : snapshot().settings();
        for (Dim dim : Dim.values()) {
            int column = dim.ordinal() % 2;
            int row = dim.ordinal() / 2;
            int bx = this.leftPos + 8 + column * 82;
            int by = this.topPos + 36 + row * 18;
            Component label = switch (dim) {
                case OFFSET -> Component.translatable("field.nerosecurity.offset", settings.offset());
                case WIDTH -> Component.translatable("field.nerosecurity.width", settings.width());
                case HEIGHT -> Component.translatable("field.nerosecurity.height", settings.height());
                case DEPTH -> Component.translatable("field.nerosecurity.depth",
                        settings.shape() == FieldShape.BOX ? String.valueOf(settings.depth()) : "-");
            };
            int textWidth = this.font.width(label);
            g.text(this.font, label, bx + 14 + Math.max(0, (50 - textWidth) / 2), by + 4, SUBTLE, false);
        }
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        FieldSnapshot snap = snapshot();
        int x = this.leftPos + 8;
        int y = this.topPos + 112;
        g.text(this.font, Component.translatable("sensor.nerosecurity.power", this.menu.energy(), this.menu.capacity()),
                x, y, SUBTLE, false);
        Component line;
        int colour;
        if (snap != null && !snap.serverEnabled()) {
            line = Component.translatable("field.nerosecurity.disabled_server");
            colour = WARN;
        } else if (!this.menu.linked()) {
            line = Component.translatable("device.nerosecurity.unlinked");
            colour = SUBTLE;
        } else {
            FieldState state = this.menu.state();
            Component stateName = state == FieldState.OVERLOADED
                    ? Component.translatable(state.translationKey(), this.menu.cooldownSeconds())
                    : Component.translatable(state.translationKey());
            int maxStrength = snap == null ? this.menu.effectiveStrength() : snap.maxStrength();
            line = Component.translatable("field.nerosecurity.status", stateName, this.menu.effectiveStrength(),
                    maxStrength, this.menu.area(), this.menu.drawPerTick(), this.menu.loadPercent());
            colour = switch (state) {
                case PROJECTING -> GOOD;
                case OVERLOADED -> BAD;
                default -> WARN;
            };
        }
        List<FormattedCharSequence> lines = this.font.split(line, W - 16);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(this.font, lines.get(i), x, y + 10 + i * 9, colour, false);
        }
        g.text(this.font, Component.translatable("door.nerosecurity.modules"), x, FieldMenu.MODULE_Y + this.topPos + 4,
                SUBTLE, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }
}
