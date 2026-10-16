package za.co.neroland.nerosecurity.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.client.ClientProgrammerState;
import za.co.neroland.nerosecurity.keycard.KeycardService;
import za.co.neroland.nerosecurity.menu.KeycardProgrammerMenu;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerClone;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerSnapshot;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerWipe;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerWrite;
import za.co.neroland.nerosecurity.menu.ProgrammerUiRules;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * A deliberately plain programmer screen (a later stage restyles it). Row 1: the four level buttons in their
 * level colours. Row 2: the "Encrypted" and "Bind to me" toggles. Then the <b>result band</b>: a full-width
 * bar in the chosen level's colour that states the level in words and what it means, with "ENCRYPTED" above
 * it when the card will be encrypted, so the level of the card about to be written is unmistakable. Then the
 * CARD / BLANK / MODULE slots, the Write / Copy / Wipe buttons, and the last outcome.
 *
 * <p>Everything drawn comes from the server's last snapshot and the synced slots. Each action button sends
 * an intent the server re-authorises; the buttons stay disabled until the next snapshot arrives.
 *
 * <p>CLIENT-ONLY.
 */
public class KeycardProgrammerScreen extends AbstractContainerScreen<KeycardProgrammerMenu> {

    private static final int W = 236;
    private static final int H = KeycardProgrammerMenu.INV_Y + 82;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int WELL = 0xFF0B1119;
    private static final int WELL_EDGE = 0xFF3A4C60;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int BAND_TEXT = 0xFF101010;
    private static final int NO_LEVEL = 0xFF505A66;
    private static final int BAND_X = 8;
    private static final int BAND_Y = 58;
    private static final int BAND_W = W - 16;
    private static final int BAND_H = 36;
    private static final int LEVEL_Y = 18;
    private static final int LEVEL_W = 53;
    private static final int TOGGLE_Y = 38;
    private static final int ACTION_Y = KeycardProgrammerMenu.SLOTS_Y - 1;
    private static final int STATUS_Y = 130;

    private final Button[] levelButtons = new Button[ProgrammerUiRules.LEVELS.length];
    private Button encryptButton;
    private Button bindButton;
    private Button writeButton;
    private Button cloneButton;
    private Button wipeButton;

    private @Nullable AccessLevel selected;
    private boolean encrypted;
    private boolean bind;
    /** The snapshot version a sent intent is waiting on; buttons stay disabled until it changes. */
    private long awaiting = -1L;

    public KeycardProgrammerScreen(KeycardProgrammerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = KeycardProgrammerMenu.INV_X;
        this.inventoryLabelY = KeycardProgrammerMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        for (int i = 0; i < levelButtons.length; i++) {
            AccessLevel level = ProgrammerUiRules.LEVELS[i];
            levelButtons[i] = addRenderableWidget(Button.builder(level.displayName(), b -> select(level))
                    .bounds(x + 8 + i * (LEVEL_W + 2), y + LEVEL_Y, LEVEL_W, 16).build());
        }
        this.encryptButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleEncrypted())
                .bounds(x + 8, y + TOGGLE_Y, 108, 16).build());
        this.bindButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleBind())
                .bounds(x + 120, y + TOGGLE_Y, 108, 16).build());
        this.writeButton = addRenderableWidget(Button.builder(
                Component.translatable("programmer.nerosecurity.button.write"), b -> write())
                .bounds(x + 122, y + ACTION_Y, 34, 18).build());
        this.cloneButton = addRenderableWidget(Button.builder(
                Component.translatable("programmer.nerosecurity.button.clone"), b -> sendFor(true))
                .bounds(x + 158, y + ACTION_Y, 34, 18).build());
        this.wipeButton = addRenderableWidget(Button.builder(
                Component.translatable("programmer.nerosecurity.button.wipe"), b -> sendFor(false))
                .bounds(x + 194, y + ACTION_Y, 34, 18).build());
        refreshWidgets();
    }

    // --- state ----------------------------------------------------------------------------------

    private @Nullable ProgrammerSnapshot snapshot() {
        return ClientProgrammerState.current();
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientProgrammerState.version();
    }

    private AccessLevel viewer() {
        ProgrammerSnapshot snap = snapshot();
        return snap == null ? AccessLevel.PUBLIC : snap.viewerLevel();
    }

    private AccessLevel basicMax() {
        ProgrammerSnapshot snap = snapshot();
        return snap == null ? AccessLevel.OFFICER : snap.basicMaxLevel();
    }

    private Component reason(String key) {
        return "programmer.nerosecurity.encrypt_required".equals(key)
                ? Component.translatable(key, basicMax().displayName())
                : Component.translatable(key);
    }

    private void refreshWidgets() {
        ProgrammerSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        AccessLevel viewer = viewer();
        AccessLevel basicMax = basicMax();

        String encryptBlocked = ProgrammerUiRules.encryptBlockedKey(viewer, this.menu.hasModule());
        if (encryptBlocked != null) {
            this.encrypted = false;
        }
        this.encryptButton.active = ready && encryptBlocked == null;
        this.encryptButton.setMessage(toggleLabel("programmer.nerosecurity.toggle.encrypted", this.encrypted));
        this.encryptButton.setTooltip(encryptBlocked == null ? null : Tooltip.create(reason(encryptBlocked)));

        this.selected = ProgrammerUiRules.pick(viewer, this.encrypted, basicMax, this.selected);
        for (int i = 0; i < levelButtons.length; i++) {
            String blocked = ProgrammerUiRules.levelBlockedKey(viewer, ProgrammerUiRules.LEVELS[i], this.encrypted,
                    basicMax);
            levelButtons[i].active = ready && blocked == null;
            levelButtons[i].setTooltip(blocked == null ? null : Tooltip.create(reason(blocked)));
        }

        this.bindButton.active = ready && this.selected != null;
        this.bindButton.setMessage(toggleLabel("programmer.nerosecurity.toggle.bind", this.bind));

        ItemStack card = this.menu.programmerStack(KeycardProgrammerBlockEntity.SLOT_CARD);
        ItemStack blank = this.menu.programmerStack(KeycardProgrammerBlockEntity.SLOT_BLANK);
        boolean cardBlank = KeycardService.isBlank(card);
        boolean cardEncoded = KeycardService.isKeycard(card) && !cardBlank;
        this.writeButton.active = ready && this.selected != null && cardBlank;
        this.cloneButton.active = ready && cardEncoded && KeycardService.isBlank(blank);
        this.wipeButton.active = ready && cardEncoded;
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.translatable(key).append(": ").append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientProgrammerState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void select(AccessLevel level) {
        this.selected = level;
        refreshWidgets();
    }

    private void toggleEncrypted() {
        this.encrypted = !this.encrypted;
        refreshWidgets();
    }

    private void toggleBind() {
        this.bind = !this.bind;
        refreshWidgets();
    }

    private void send(CustomPacketPayload payload) {
        this.awaiting = ClientProgrammerState.version();
        Services.NETWORK.sendToServer(payload);
        refreshWidgets();
    }

    private void write() {
        ProgrammerSnapshot snap = snapshot();
        if (snap != null && this.selected != null) {
            send(new ProgrammerWrite(snap.pos(), this.selected, this.encrypted, this.bind));
        }
    }

    private void sendFor(boolean clone) {
        ProgrammerSnapshot snap = snapshot();
        if (snap != null) {
            send(clone ? new ProgrammerClone(snap.pos()) : new ProgrammerWipe(snap.pos()));
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
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, WELL_EDGE);
            g.fill(sx, sy, sx + 16, sy + 16, WELL);
        }
        drawBand(g, x, y);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawSelection(g, x, y);
    }

    /** A bar under the selected level button in that level's colour. */
    private void drawSelection(GuiGraphicsExtractor g, int x, int y) {
        if (this.selected == null) {
            return;
        }
        int i = this.selected.ordinal();
        int bx = x + 8 + i * (LEVEL_W + 2);
        g.fill(bx, y + LEVEL_Y + 17, bx + LEVEL_W, y + LEVEL_Y + 19, 0xFF000000 | this.selected.colour);
    }

    /**
     * The result band: the colour of the card's level, filled edge to edge, with the level and its meaning in
     * plain words (and "ENCRYPTED" first when it will be encrypted). Nothing writable gives a dark band that
     * says so.
     */
    private void drawBand(GuiGraphicsExtractor g, int x, int y) {
        int left = x + BAND_X;
        int top = y + BAND_Y;
        if (snapshot() == null) {
            g.fill(left, top, left + BAND_W, top + BAND_H, NO_LEVEL);
            return;
        }
        MutableComponent text;
        int colour;
        if (this.selected == null) {
            colour = NO_LEVEL;
            text = Component.translatable("programmer.nerosecurity.band.none");
        } else {
            colour = 0xFF000000 | this.selected.colour;
            MutableComponent band = Component.translatable("programmer.nerosecurity.band",
                    Component.translatable(this.selected.translationKey()),
                    Component.translatable(this.selected.meaningKey()));
            text = this.encrypted
                    ? Component.translatable("programmer.nerosecurity.band.encrypted").append(" · ").append(band)
                    : band;
        }
        g.fill(left, top, left + BAND_W, top + BAND_H, colour);
        int textColour = this.selected == null ? TITLE : BAND_TEXT;
        List<FormattedCharSequence> lines = this.font.split(text, BAND_W - 8);
        int lineY = top + 4;
        for (FormattedCharSequence line : lines) {
            if (lineY + this.font.lineHeight > top + BAND_H) {
                break;
            }
            g.text(this.font, line, left + 4, lineY, textColour, false);
            lineY += 10;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
        ProgrammerSnapshot snap = snapshot();
        if (snap != null) {
            Component cards = Component.translatable("programmer.nerosecurity.cards", snap.cardsIssued(), snap.cardCap());
            g.text(this.font, cards, W - 8 - this.font.width(cards), this.titleLabelY, SUBTLE, false);
        }
        int labelY = KeycardProgrammerMenu.SLOTS_Y - 11;
        g.text(this.font, Component.translatable("programmer.nerosecurity.slot.card"),
                KeycardProgrammerMenu.CARD_X - 2, labelY, SUBTLE, false);
        g.text(this.font, Component.translatable("programmer.nerosecurity.slot.blank"),
                KeycardProgrammerMenu.BLANK_X - 2, labelY, SUBTLE, false);
        g.text(this.font, Component.translatable("programmer.nerosecurity.slot.module"),
                KeycardProgrammerMenu.MODULE_X - 2, labelY, SUBTLE, false);
        if (snap != null && snap.message() != null) {
            List<FormattedCharSequence> lines = this.font.split(snap.message(), W - 16);
            if (!lines.isEmpty()) {
                g.text(this.font, lines.get(0), 8, STATUS_Y, TITLE, false);
            }
        }
    }
}
