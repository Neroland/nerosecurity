package za.co.neroland.nerosecurity.client.screen.sensor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads.SensorConfigure;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads.SensorSnapshot;
import za.co.neroland.nerosecurity.menu.sensor.SensorSettingsMenu;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.sensor.SensorEntityMode;
import za.co.neroland.nerosecurity.sensor.SensorSettings;

/**
 * The shared sensor settings screen (laser grid node and camera), deliberately plain (a later stage
 * restyles it): four level buttons ("who may pass unreported"), the entity-mode toggle, the camera's
 * field-of-view toggle (hidden for a laser), armed/power status and the range-module slots. It draws only
 * what the server sent; every button sends an intent the server re-authorises, and the buttons stay
 * disabled until the next snapshot.
 *
 * <p>CLIENT-ONLY.
 */
public class SensorSettingsScreen extends AbstractContainerScreen<SensorSettingsMenu> {

    private static final int W = 176;
    private static final int H = 200;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int GOOD = 0xFF6BE38A;
    private static final int WARN = 0xFFFFB454;

    private static final AccessLevel[] LEVELS = {
            AccessLevel.PUBLIC, AccessLevel.MEMBER, AccessLevel.OFFICER, AccessLevel.OWNER };

    private final Button[] levelButtons = new Button[LEVELS.length];
    private Button modeButton;
    private Button fovButton;
    /** The snapshot version a sent intent is waiting on; buttons stay disabled until it changes. */
    private long awaiting = -1L;

    public SensorSettingsScreen(SensorSettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = SensorSettingsMenu.INVENTORY_X;
        this.inventoryLabelY = SensorSettingsMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        for (int i = 0; i < LEVELS.length; i++) {
            AccessLevel level = LEVELS[i];
            this.levelButtons[i] = addRenderableWidget(Button.builder(level.displayName(), b -> setLevel(level))
                    .bounds(x + 8 + i * 40, y + 18, 38, 16).build());
        }
        this.modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleMode())
                .bounds(x + 8, y + 38, 160, 16).build());
        this.fovButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleFov())
                .bounds(x + 8, y + 58, 160, 16).build());
        refreshWidgets();
    }

    private SensorSnapshot snapshot() {
        return ClientSensorState.current();
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientSensorState.version();
    }

    private boolean isLaser(SensorSnapshot snap) {
        return snap != null ? snap.laser() : this.menu.laser();
    }

    private void refreshWidgets() {
        SensorSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        AccessLevel viewer = snap == null ? AccessLevel.PUBLIC : snap.viewerLevel();
        SensorSettings settings = snap == null ? SensorSettings.DEFAULT : snap.settings();
        for (int i = 0; i < LEVELS.length; i++) {
            AccessLevel level = LEVELS[i];
            boolean selected = snap != null && settings.minLevel() == level;
            Button button = this.levelButtons[i];
            button.setMessage(selected
                    ? Component.literal("[").append(level.displayName()).append("]")
                    : level.displayName());
            boolean ownerOnly = viewer != AccessLevel.OWNER
                    && (level == AccessLevel.OWNER || settings.minLevel() == AccessLevel.OWNER);
            button.active = ready && !selected && !ownerOnly;
            button.setTooltip(ownerOnly ? Tooltip.create(Component.translatable("door.nerosecurity.owner_only_setting"))
                    : Tooltip.create(Component.translatable(level.meaningKey())));
        }
        boolean ownerLocked = viewer != AccessLevel.OWNER && settings.minLevel() == AccessLevel.OWNER;
        this.modeButton.setMessage(Component.translatable("sensor.nerosecurity.mode",
                Component.translatable(settings.entityMode().translationKey())));
        this.modeButton.active = ready && !ownerLocked;
        boolean laser = isLaser(snap);
        this.fovButton.visible = !laser;
        this.fovButton.setMessage(Component.translatable("sensor.nerosecurity.fov", settings.fovDegrees()));
        this.fovButton.active = ready && !laser && !ownerLocked;
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientSensorState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void send(SensorSettings wanted) {
        SensorSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        this.awaiting = ClientSensorState.version();
        Services.NETWORK.sendToServer(new SensorConfigure(snap.pos(), wanted));
        refreshWidgets();
    }

    private void setLevel(AccessLevel level) {
        SensorSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withMinLevel(level));
        }
    }

    private void cycleMode() {
        SensorSnapshot snap = snapshot();
        if (snap != null) {
            SensorEntityMode[] modes = SensorEntityMode.values();
            SensorEntityMode next = modes[(snap.settings().entityMode().ordinal() + 1) % modes.length];
            send(snap.settings().withEntityMode(next));
        }
    }

    private void cycleFov() {
        SensorSnapshot snap = snapshot();
        if (snap == null || snap.laser()) {
            return;
        }
        int[] options = SensorSettings.FOV_OPTIONS;
        int current = snap.settings().fovDegrees();
        int next = options[0];
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) {
                next = options[(i + 1) % options.length];
                break;
            }
        }
        send(snap.settings().withFov(next));
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
        drawStatus(g);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        SensorSnapshot snap = snapshot();
        boolean laser = isLaser(snap);
        int x = this.leftPos + 8;
        int y = this.topPos + (laser ? 60 : 78);
        boolean linked = snap != null ? this.menu.linked() || snap.linked() : this.menu.linked();
        g.text(this.font, Component.translatable("sensor.nerosecurity.power", this.menu.energy(), this.menu.capacity()),
                x, y, SUBTLE, false);
        Component state;
        int colour;
        if (!linked) {
            state = Component.translatable("device.nerosecurity.unlinked");
            colour = SUBTLE;
        } else if (this.menu.armed()) {
            state = Component.translatable("sensor.nerosecurity.armed");
            colour = GOOD;
        } else if (laser && !this.menu.paired()) {
            state = Component.translatable("sensor.nerosecurity.unpaired");
            colour = WARN;
        } else {
            state = Component.translatable("sensor.nerosecurity.disarmed");
            colour = WARN;
        }
        g.text(this.font, state, x, y + 10, colour, false);
        if (this.menu.moduleSlots() > 0) {
            g.text(this.font, Component.translatable("door.nerosecurity.modules"), x,
                    SensorSettingsMenu.MODULE_Y + this.topPos + 4, SUBTLE, false);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }
}
