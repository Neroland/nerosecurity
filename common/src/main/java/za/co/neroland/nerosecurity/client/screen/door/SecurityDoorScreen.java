package za.co.neroland.nerosecurity.client.screen.door;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.door.RedstoneMode;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorConfigure;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorSnapshot;
import za.co.neroland.nerosecurity.menu.door.SecurityDoorMenu;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * A deliberately plain door settings screen (a later stage restyles it): four level buttons, the
 * keycard-only toggle, the redstone toggle (hidden when the server disallows door redstone; its tooltip
 * warns that redstone opens the door for anyone who can power it), the power
 * and lockdown status, and the two module slots. It draws only what the server sent; every button sends
 * an intent the server re-authorises, and the buttons stay disabled until the next snapshot.
 *
 * <p>CLIENT-ONLY.
 */
public class SecurityDoorScreen extends AbstractContainerScreen<SecurityDoorMenu> {

    private static final int W = 176;
    private static final int H = 200;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int WARN = 0xFFFFB454;
    private static final int ALERT = 0xFFFF5A5A;

    private static final AccessLevel[] LEVELS = {
            AccessLevel.PUBLIC, AccessLevel.MEMBER, AccessLevel.OFFICER, AccessLevel.OWNER };

    private final Button[] levelButtons = new Button[LEVELS.length];
    private Button keycardButton;
    private Button redstoneButton;
    /** The snapshot version a sent intent is waiting on; buttons stay disabled until it changes. */
    private long awaiting = -1L;

    public SecurityDoorScreen(SecurityDoorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = SecurityDoorMenu.INVENTORY_X;
        this.inventoryLabelY = SecurityDoorMenu.INVENTORY_Y - 10;
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
        this.keycardButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleKeycard())
                .bounds(x + 8, y + 38, 78, 16).build());
        this.redstoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleRedstone())
                .bounds(x + 90, y + 38, 78, 16).build());
        refreshWidgets();
    }

    private DoorSnapshot snapshot() {
        return ClientDoorState.current();
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientDoorState.version();
    }

    private void refreshWidgets() {
        DoorSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        AccessLevel viewer = snap == null ? AccessLevel.PUBLIC : snap.viewerLevel();
        for (int i = 0; i < LEVELS.length; i++) {
            AccessLevel level = LEVELS[i];
            boolean selected = snap != null && snap.minLevel() == level;
            Button button = this.levelButtons[i];
            button.setMessage(selected
                    ? Component.literal("[").append(level.displayName()).append("]")
                    : level.displayName());
            boolean ownerOnly = level == AccessLevel.OWNER && viewer != AccessLevel.OWNER;
            button.active = ready && !selected && !ownerOnly;
            button.setTooltip(ownerOnly ? Tooltip.create(Component.translatable("door.nerosecurity.owner_only_setting"))
                    : Tooltip.create(Component.translatable(level.meaningKey())));
        }
        boolean keycardOnly = snap != null && snap.keycardOnly();
        this.keycardButton.setMessage(Component.translatable("door.nerosecurity.toggle.keycard_only")
                .append(Component.literal(keycardOnly ? ": ON" : ": OFF")));
        this.keycardButton.active = ready;
        boolean redstoneAllowed = NeroSecurityConfig.doorRedstoneAllowed();
        this.redstoneButton.visible = redstoneAllowed;
        RedstoneMode mode = snap == null ? RedstoneMode.IGNORE : snap.mode();
        this.redstoneButton.setMessage(Component.translatable(mode.translationKey()));
        // Redstone is location-blind: say so where the owner turns it on.
        this.redstoneButton.setTooltip(Tooltip.create(Component.translatable("door.nerosecurity.redstone.warning")));
        this.redstoneButton.active = ready && redstoneAllowed;
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientDoorState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void send(AccessLevel level, boolean keycardOnly, RedstoneMode mode) {
        DoorSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        this.awaiting = ClientDoorState.version();
        Services.NETWORK.sendToServer(new DoorConfigure(snap.pos(), level, keycardOnly, mode));
        refreshWidgets();
    }

    private void setLevel(AccessLevel level) {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            send(level, snap.keycardOnly(), snap.mode());
        }
    }

    private void toggleKeycard() {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.minLevel(), !snap.keycardOnly(), snap.mode());
        }
    }

    private void toggleRedstone() {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.minLevel(), snap.keycardOnly(), snap.mode().next());
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
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, TROUGH);
        }
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawStatus(g);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        int x = this.leftPos + 8;
        int y = this.topPos + 60;
        DoorSnapshot snap = snapshot();
        boolean linked = snap != null ? this.menu.linked() || snap.linked() : this.menu.linked();
        g.text(this.font, Component.translatable("door.nerosecurity.power", this.menu.energy(), this.menu.capacity()),
                x, y, SUBTLE, false);
        if (this.menu.unpowered()) {
            g.text(this.font, Component.translatable("door.nerosecurity.unpowered"), x, y + 10, WARN, false);
        }
        if (this.menu.lockdown()) {
            g.text(this.font, Component.translatable("door.nerosecurity.sealed"), x, y + 20, ALERT, false);
        } else if (!linked) {
            g.text(this.font, Component.translatable("device.nerosecurity.unlinked"), x, y + 20, SUBTLE, false);
        }
        g.text(this.font, Component.translatable("door.nerosecurity.modules"), x, SecurityDoorMenu.MODULE_Y + this.topPos + 4,
                SUBTLE, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }
}
