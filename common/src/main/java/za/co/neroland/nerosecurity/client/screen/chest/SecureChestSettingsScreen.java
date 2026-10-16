package za.co.neroland.nerosecurity.client.screen.chest;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestConfigure;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestSnapshot;
import za.co.neroland.nerosecurity.menu.chest.ChestSettingsMenu;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * A deliberately plain settings screen for the secure chest: four level buttons (who can open it), a
 * keycard-only toggle, and status lines for the automation policy, lockdown and linking. It draws only
 * what the server's last snapshot says; every button sends an intent the server re-authorises, and the
 * buttons stay disabled until the next snapshot arrives.
 *
 * <p>CLIENT-ONLY.
 */
public class SecureChestSettingsScreen extends AbstractContainerScreen<ChestSettingsMenu> {

    private static final int W = 240;
    private static final int H = 140;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int WARN = 0xFFE0A526;
    private static final int LEVEL_Y = 34;
    private static final int LEVEL_W = 53;
    private static final int LEVEL_GAP = 4;

    private final Button[] levelButtons = new Button[AccessLevel.values().length];
    private Button keycardButton;

    /** The snapshot version a sent intent is waiting on; buttons stay disabled until it changes. */
    private long awaiting = -1L;

    public SecureChestSettingsScreen(ChestSettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        AccessLevel[] levels = AccessLevel.values();
        for (int i = 0; i < levels.length; i++) {
            AccessLevel level = levels[i];
            this.levelButtons[i] = addRenderableWidget(Button.builder(level.displayName(), b -> setLevel(level))
                    .bounds(x + 8 + i * (LEVEL_W + LEVEL_GAP), y + LEVEL_Y, LEVEL_W, 16).build());
        }
        this.keycardButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleKeycardOnly())
                .bounds(x + 8, y + LEVEL_Y + 24, W - 16, 16).build());
        refreshWidgets();
    }

    private static ChestSnapshot snapshot() {
        return ClientChestState.current();
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientChestState.version();
    }

    private void refreshWidgets() {
        ChestSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        boolean owner = snap != null && snap.viewerLevel() == AccessLevel.OWNER;
        boolean ownerLocked = snap != null && snap.minLevel() == AccessLevel.OWNER && !owner;
        AccessLevel[] levels = AccessLevel.values();
        for (int i = 0; i < levels.length; i++) {
            AccessLevel level = levels[i];
            boolean selected = snap != null && snap.minLevel() == level;
            Button button = this.levelButtons[i];
            button.active = ready && !selected && !ownerLocked && (level != AccessLevel.OWNER || owner);
            button.setMessage(selected
                    ? Component.literal("[").append(level.displayName()).append("]")
                    : level.displayName());
        }
        boolean keycardOnly = snap != null && snap.keycardOnly();
        this.keycardButton.active = ready && !ownerLocked;
        this.keycardButton.setMessage(Component.translatable("chest.nerosecurity.toggle.keycard_only",
                Component.translatable(keycardOnly ? "chest.nerosecurity.on" : "chest.nerosecurity.off")));
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientChestState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void send(CustomPacketPayload payload) {
        this.awaiting = ClientChestState.version();
        Services.NETWORK.sendToServer(payload);
        refreshWidgets();
    }

    private void setLevel(AccessLevel level) {
        ChestSnapshot snap = snapshot();
        if (snap != null) {
            send(new ChestConfigure(snap.pos(), level, snap.keycardOnly()));
        }
    }

    private void toggleKeycardOnly() {
        ChestSnapshot snap = snapshot();
        if (snap != null) {
            send(new ChestConfigure(snap.pos(), snap.minLevel(), !snap.keycardOnly()));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, EDGE);
        g.fill(x, y, x + W, y + H, PANEL);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawBody(g);
    }

    private void drawBody(GuiGraphicsExtractor g) {
        int x = this.leftPos + 8;
        int y = this.topPos;
        ChestSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        g.text(this.font, Component.translatable("chest.nerosecurity.label.level"), x, y + 22, SUBTLE, false);
        int line = y + LEVEL_Y + 48;
        if (!snap.linked()) {
            g.text(this.font, Component.translatable("device.nerosecurity.unlinked"), x, line, WARN, false);
            return;
        }
        Component policy = snap.automation() == null
                ? Component.translatable("chest.nerosecurity.automation.off")
                : snap.automation().displayName();
        g.text(this.font, Component.translatable("chest.nerosecurity.automation", policy), x, line, TITLE, false);
        line += 11;
        if (snap.automation() != null && !snap.automationAllowed()) {
            g.text(this.font, Component.translatable("chest.nerosecurity.automation.blocked"), x, line, WARN, false);
            line += 11;
        }
        if (snap.lockdown()) {
            g.text(this.font, Component.translatable("chest.nerosecurity.sealed"), x, line, WARN, false);
            line += 11;
        }
        g.text(this.font, Component.translatable("chest.nerosecurity.automation.hint"), x, line, SUBTLE, false);
        g.text(this.font, Component.translatable("chest.nerosecurity.automation.hint_command"), x, line + 11, SUBTLE,
                false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
    }
}
