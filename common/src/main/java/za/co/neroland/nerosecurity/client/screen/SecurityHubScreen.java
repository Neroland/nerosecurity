package za.co.neroland.nerosecurity.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.ClientHubState;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubAddMember;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubLockdown;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubRename;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSetMember;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSnapshot;
import za.co.neroland.nerosecurity.menu.HubPayloads.MemberRow;
import za.co.neroland.nerosecurity.menu.SecurityHubMenu;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * A deliberately plain hub screen: the installation name, lockdown status, the member list (owners
 * first) and buttons to add, remove, promote and demote members, toggle lockdown (with a confirm press)
 * and rename. It draws only what the server's last snapshot says, and every button sends an intent the
 * server re-authorises; a button disables itself until the next snapshot arrives.
 *
 * <p>CLIENT-ONLY.
 */
public class SecurityHubScreen extends AbstractContainerScreen<SecurityHubMenu> {

    private static final int W = 248;
    private static final int H = 206;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int SELECTED = 0x553A6EA5;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 32;
    private static final int LIST_W = 150;
    private static final int ROW_H = 11;
    private static final int ROWS = 9;
    private static final int SIDE_X = 164;
    private static final int SIDE_W = 76;

    private EditBox nameField;
    private EditBox renameField;
    private Button addLevelButton;
    private Button addButton;
    private Button removeButton;
    private Button promoteButton;
    private Button demoteButton;
    private Button lockdownButton;
    private Button renameButton;
    private Button upButton;
    private Button downButton;

    private AccessLevel addLevel = AccessLevel.MEMBER;
    private int scroll;
    private int selected = -1;
    private boolean lockdownArmed;
    /** The snapshot version a sent intent is waiting on; buttons stay disabled until it changes. */
    private long awaiting = -1L;

    public SecurityHubScreen(SecurityHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        this.upButton = addRenderableWidget(Button.builder(Component.literal("^"), b -> scrollBy(-1))
                .bounds(x + LIST_X + LIST_W - 14, y + LIST_Y - 12, 14, 11).build());
        this.downButton = addRenderableWidget(Button.builder(Component.literal("v"), b -> scrollBy(1))
                .bounds(x + LIST_X + LIST_W - 14, y + LIST_Y + ROWS * ROW_H + 1, 14, 11).build());

        this.removeButton = addRenderableWidget(Button.builder(Component.translatable("hub.nerosecurity.button.remove"),
                b -> changeSelected(AccessLevel.PUBLIC)).bounds(x + SIDE_X, y + 32, SIDE_W, 16).build());
        this.promoteButton = addRenderableWidget(Button.builder(Component.translatable("hub.nerosecurity.button.promote"),
                b -> changeSelected(AccessLevel.OFFICER)).bounds(x + SIDE_X, y + 52, SIDE_W, 16).build());
        this.demoteButton = addRenderableWidget(Button.builder(Component.translatable("hub.nerosecurity.button.demote"),
                b -> changeSelected(AccessLevel.MEMBER)).bounds(x + SIDE_X, y + 72, SIDE_W, 16).build());
        this.lockdownButton = addRenderableWidget(Button.builder(Component.empty(), b -> pressLockdown())
                .bounds(x + SIDE_X, y + 100, SIDE_W, 16).build());

        this.nameField = new EditBox(this.font, x + 8, y + 150, 104, 16, Component.translatable("hub.nerosecurity.button.add"));
        this.nameField.setMaxLength(PlayerNames.MAX_NAME);
        addRenderableWidget(this.nameField);
        this.addLevelButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleAddLevel())
                .bounds(x + 116, y + 150, 60, 16).build());
        this.addButton = addRenderableWidget(Button.builder(Component.translatable("hub.nerosecurity.button.add"),
                b -> addMember()).bounds(x + 180, y + 150, 60, 16).build());

        this.renameField = new EditBox(this.font, x + 8, y + 176, 168, 16, Component.translatable("hub.nerosecurity.button.rename"));
        this.renameField.setMaxLength(Installation.NAME_MAX);
        addRenderableWidget(this.renameField);
        this.renameButton = addRenderableWidget(Button.builder(Component.translatable("hub.nerosecurity.button.rename"),
                b -> rename()).bounds(x + 180, y + 176, 60, 16).build());
        refreshWidgets();
    }

    // --- state ----------------------------------------------------------------------------------

    private HubSnapshot snapshot() {
        return ClientHubState.current();
    }

    private List<MemberRow> rows() {
        HubSnapshot snap = snapshot();
        List<MemberRow> rows = new ArrayList<>();
        if (snap != null) {
            rows.addAll(snap.owners());
            rows.addAll(snap.members());
        }
        return rows;
    }

    private MemberRow selectedRow() {
        List<MemberRow> rows = rows();
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientHubState.version();
    }

    private void refreshWidgets() {
        HubSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        AccessLevel viewer = snap == null ? AccessLevel.PUBLIC : snap.viewerLevel();
        boolean owner = viewer == AccessLevel.OWNER;
        MemberRow row = selectedRow();
        AccessLevel rowLevel = row == null ? null : row.level();
        boolean editable = rowLevel != null && rowLevel != AccessLevel.OWNER;
        this.removeButton.active = ready && editable;
        this.promoteButton.active = ready && editable && rowLevel == AccessLevel.MEMBER && owner;
        this.demoteButton.active = ready && editable && rowLevel == AccessLevel.OFFICER && owner;
        this.addButton.active = ready && !this.nameField.getValue().isBlank();
        this.addLevelButton.active = ready && owner;
        if (!owner) {
            this.addLevel = AccessLevel.MEMBER;
        }
        this.addLevelButton.setMessage(this.addLevel.displayName());
        this.renameButton.active = ready && owner && !this.renameField.getValue().isBlank();
        this.renameField.setEditable(owner);
        boolean locked = snap != null && snap.lockdown();
        this.lockdownButton.active = ready;
        this.lockdownButton.setMessage(Component.translatable(this.lockdownArmed ? "hub.nerosecurity.button.confirm"
                : locked ? "hub.nerosecurity.button.lockdown_off" : "hub.nerosecurity.button.lockdown_on"));
        int max = Math.max(0, rows().size() - ROWS);
        this.scroll = Math.min(this.scroll, max);
        this.upButton.active = this.scroll > 0;
        this.downButton.active = this.scroll < max;
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientHubState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        this.awaiting = ClientHubState.version();
        Services.NETWORK.sendToServer(payload);
        refreshWidgets();
    }

    private void changeSelected(AccessLevel level) {
        HubSnapshot snap = snapshot();
        MemberRow row = selectedRow();
        if (snap != null && row != null) {
            send(new HubSetMember(snap.installationId(), row.uuid(), level));
            if (level == AccessLevel.PUBLIC) {
                this.selected = -1;
            }
        }
    }

    private void addMember() {
        HubSnapshot snap = snapshot();
        String name = this.nameField.getValue().trim();
        if (snap != null && !name.isEmpty()) {
            send(new HubAddMember(snap.installationId(), name, this.addLevel));
            this.nameField.setValue("");
        }
    }

    private void toggleAddLevel() {
        this.addLevel = this.addLevel == AccessLevel.MEMBER ? AccessLevel.OFFICER : AccessLevel.MEMBER;
        refreshWidgets();
    }

    private void pressLockdown() {
        HubSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        if (!this.lockdownArmed) {
            this.lockdownArmed = true;
            refreshWidgets();
            return;
        }
        this.lockdownArmed = false;
        send(new HubLockdown(snap.installationId(), !snap.lockdown()));
    }

    private void rename() {
        HubSnapshot snap = snapshot();
        String name = this.renameField.getValue().trim();
        if (snap != null && !name.isEmpty()) {
            send(new HubRename(snap.installationId(), name));
            this.renameField.setValue("");
        }
    }

    private void scrollBy(int delta) {
        this.scroll = Math.max(0, this.scroll + delta);
        refreshWidgets();
    }

    // --- input ----------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (getFocused() instanceof EditBox box && box.canConsumeInput() && !event.isEscape()) {
            box.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double localX = event.x() - this.leftPos;
        double localY = event.y() - this.topPos;
        if (localX >= LIST_X && localX < LIST_X + LIST_W - 16 && localY >= LIST_Y && localY < LIST_Y + ROWS * ROW_H) {
            int index = this.scroll + (int) ((localY - LIST_Y) / ROW_H);
            this.selected = index < rows().size() ? index : -1;
            this.lockdownArmed = false;
            refreshWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, EDGE);
        g.fill(x, y, x + W, y + H, PANEL);
        g.fill(x + LIST_X - 1, y + LIST_Y - 1, x + LIST_X + LIST_W - 15, y + LIST_Y + ROWS * ROW_H + 1, TROUGH);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawBody(g);
    }

    private void drawBody(GuiGraphicsExtractor g) {
        int x = this.leftPos;
        int y = this.topPos;
        HubSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        Component status = Component.translatable(snap.lockdown() ? "hub.nerosecurity.status.locked"
                : "hub.nerosecurity.status.open", snap.deviceCount());
        g.text(this.font, status, x + 8, y + 18, SUBTLE, false);
        List<MemberRow> rows = rows();
        for (int i = 0; i < ROWS && this.scroll + i < rows.size(); i++) {
            int index = this.scroll + i;
            MemberRow row = rows.get(index);
            int rowY = y + LIST_Y + i * ROW_H;
            if (index == this.selected) {
                g.fill(x + LIST_X, rowY, x + LIST_X + LIST_W - 16, rowY + ROW_H, SELECTED);
            }
            Component line = Component.literal(row.displayName() + " - ").withColor(0xD6ECFF)
                    .append(row.level().displayName());
            g.text(this.font, line, x + LIST_X + 2, rowY + 2, TITLE, false);
        }
        g.text(this.font, Component.translatable("hub.nerosecurity.label.add"), x + 8, y + 140, SUBTLE, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        HubSnapshot snap = snapshot();
        Component title = snap == null ? this.title : Component.literal(snap.name());
        g.text(this.font, title, this.titleLabelX, this.titleLabelY, TITLE, false);
    }
}
