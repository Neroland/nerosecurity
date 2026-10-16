package za.co.neroland.nerosecurity.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.client.ClientProgrammerState;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.ProgrammerActions.CardState;
import za.co.neroland.nerosecurity.client.widget.LevelLadder;
import za.co.neroland.nerosecurity.client.widget.LevelMeaning;
import za.co.neroland.nerosecurity.client.widget.LevelPill;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.SecurityButton;
import za.co.neroland.nerosecurity.client.widget.SlotFrame;
import za.co.neroland.nerosecurity.client.widget.Toggle;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.keycard.KeycardService;
import za.co.neroland.nerosecurity.menu.KeycardProgrammerMenu;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerClone;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerSnapshot;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerWipe;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerWrite;
import za.co.neroland.nerosecurity.menu.ProgrammerUiRules;

/**
 * "Write a keycard": the three machine slots down the left (Card, Blank, Module), and on the right the level
 * ladder, the meaning of the chosen level, the Encrypted and Bind toggles, the <b>result band</b> (a bar filled
 * with the level colour that says in ink what card is about to be written, with an ENCRYPTED tag when it will
 * be encrypted) and the Write / Copy / Wipe buttons. The footer shows the server's answer. Layout in
 * {@link ProgrammerLayout}.
 *
 * <p>Everything drawn comes from the server's last {@link ProgrammerSnapshot} and the synced slots. The
 * ladder and toggles only change what the screen is <em>about to ask for</em>; nothing is written until Write
 * sends {@link ProgrammerWrite}, and the server re-checks every field. Each action holds the controls until
 * the next snapshot. A disabled control always says why ({@link ProgrammerUiRules}, {@link ProgrammerActions}).
 *
 * <p>CLIENT-ONLY.
 */
public class KeycardProgrammerScreen extends SecurityScreen<KeycardProgrammerMenu> {

    private static final int BAND_LINES = 2;
    private static final int BAND_LINE_HEIGHT = 11;
    private static final int TAG_PAD = 4;

    private LevelLadder ladder;
    private Toggle encryptedToggle;
    private Toggle bindToggle;
    private SecurityButton writeButton;
    private SecurityButton cloneButton;
    private SecurityButton wipeButton;

    private @Nullable AccessLevel selected;
    private boolean encrypted;
    private boolean bind;

    public KeycardProgrammerScreen(KeycardProgrammerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ProgrammerLayout.PANEL_W, ProgrammerLayout.PANEL_H, true,
                ClientProgrammerState::version);
        setTitleLine(Component.translatable("programmer.nerosecurity.title"));
        setSubtitle(Component.translatable("programmer.nerosecurity.subtitle"));
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int y = top();
        this.ladder = addGroup(new LevelLadder(x + ProgrammerLayout.COL_X, y + ProgrammerLayout.LADDER_Y, this::select));
        this.encryptedToggle = addRenderableWidget(new Toggle(x + ProgrammerLayout.COL_X, y + ProgrammerLayout.TOGGLE_Y,
                Component.translatable("programmer.nerosecurity.toggle.encrypted"), this::setEncrypted));
        this.bindToggle = addRenderableWidget(new Toggle(x + ProgrammerLayout.TOGGLE_BIND_X, y + ProgrammerLayout.TOGGLE_Y,
                Component.translatable("programmer.nerosecurity.toggle.bind"), this::setBind));
        this.bindToggle.setHelp(Component.translatable("programmer.nerosecurity.toggle.bind.hint"));
        this.writeButton = addRenderableWidget(SecurityButton.of(x + ProgrammerLayout.buttonX(0),
                y + ProgrammerLayout.BUTTON_Y, ProgrammerLayout.BUTTON_W, ProgrammerLayout.BUTTON_H,
                Component.translatable("programmer.nerosecurity.button.write"), b -> write()));
        this.cloneButton = addRenderableWidget(SecurityButton.of(x + ProgrammerLayout.buttonX(1),
                y + ProgrammerLayout.BUTTON_Y, ProgrammerLayout.BUTTON_W, ProgrammerLayout.BUTTON_H,
                Component.translatable("programmer.nerosecurity.button.clone"), b -> sendFor(true)));
        this.wipeButton = addRenderableWidget(SecurityButton.of(x + ProgrammerLayout.buttonX(2),
                y + ProgrammerLayout.BUTTON_Y, ProgrammerLayout.BUTTON_W, ProgrammerLayout.BUTTON_H,
                Component.translatable("programmer.nerosecurity.button.wipe"), b -> sendFor(false)));
    }

    // --- state ----------------------------------------------------------------------------------

    private static @Nullable ProgrammerSnapshot snapshot() {
        return ClientProgrammerState.current();
    }

    private AccessLevel viewer() {
        ProgrammerSnapshot snap = snapshot();
        return snap == null ? AccessLevel.PUBLIC : snap.viewerLevel();
    }

    private AccessLevel basicMax() {
        ProgrammerSnapshot snap = snapshot();
        return snap == null ? AccessLevel.OFFICER : snap.basicMaxLevel();
    }

    /** A rule key as a sentence; {@code encrypt_required} names the basic maximum level. */
    private Component reason(String key) {
        return "programmer.nerosecurity.encrypt_required".equals(key)
                ? Component.translatable(key, basicMax().displayName())
                : Component.translatable(key);
    }

    private CardState slotState(int slot) {
        ItemStack stack = this.menu.programmerStack(slot);
        return ProgrammerActions.state(KeycardService.isKeycard(stack), KeycardService.isBlank(stack));
    }

    @Override
    protected void refreshWidgets() {
        ProgrammerSnapshot snap = snapshot();
        boolean ready = snap != null;
        AccessLevel viewer = viewer();
        AccessLevel basicMax = basicMax();
        Component waiting = Component.translatable(ProgrammerActions.WAITING);

        String encryptBlocked = ProgrammerUiRules.encryptBlockedKey(viewer, this.menu.hasModule());
        if (encryptBlocked != null) {
            this.encrypted = false;
        }
        this.encryptedToggle.setChecked(this.encrypted);
        if (!ready) {
            this.encryptedToggle.disable(waiting);
        } else {
            this.encryptedToggle.setEnabled(encryptBlocked == null, encryptBlocked == null ? waiting : reason(encryptBlocked));
        }

        this.selected = ProgrammerUiRules.pick(viewer, this.encrypted, basicMax, this.selected);
        this.ladder.select(this.selected);
        for (AccessLevel level : ProgrammerUiRules.LEVELS) {
            String blocked = ProgrammerUiRules.levelBlockedKey(viewer, level, this.encrypted, basicMax);
            if (!ready) {
                this.ladder.disable(level, waiting);
            } else if (blocked == null) {
                this.ladder.enable(level);
            } else {
                this.ladder.disable(level, reason(blocked));
            }
        }

        this.bindToggle.setChecked(this.bind);
        if (!ready) {
            this.bindToggle.disable(waiting);
        } else {
            this.bindToggle.setEnabled(this.selected != null, Component.translatable(ProgrammerActions.NO_LEVEL));
        }

        CardState card = slotState(KeycardProgrammerBlockEntity.SLOT_CARD);
        CardState blank = slotState(KeycardProgrammerBlockEntity.SLOT_BLANK);
        apply(this.writeButton, ProgrammerActions.writeBlockedKey(ready, this.selected != null, card));
        apply(this.cloneButton, ProgrammerActions.cloneBlockedKey(ready, card, blank));
        apply(this.wipeButton, ProgrammerActions.wipeBlockedKey(ready, card));
    }

    private static void apply(SecurityButton button, @Nullable String blockedKey) {
        if (blockedKey == null) {
            button.enable();
        } else {
            button.disable(Component.translatable(blockedKey));
        }
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        ProgrammerSnapshot snap = snapshot();
        if (snap != null) {
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
        }
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientProgrammerState.clear();
        super.removed();
    }

    // --- local choices and intents --------------------------------------------------------------

    private void select(AccessLevel level) {
        this.selected = level;
        refreshWidgets();
    }

    private void setEncrypted(boolean value) {
        this.encrypted = value;
        refreshWidgets();
    }

    private void setBind(boolean value) {
        this.bind = value;
        refreshWidgets();
    }

    private void write() {
        ProgrammerSnapshot snap = snapshot();
        if (snap != null && this.selected != null) {
            sendIntent(new ProgrammerWrite(snap.pos(), this.selected, this.encrypted, this.bind));
        }
    }

    private void sendFor(boolean clone) {
        ProgrammerSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(clone ? new ProgrammerClone(snap.pos()) : new ProgrammerWipe(snap.pos()));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        ProgrammerSnapshot snap = snapshot();

        SectionBox.draw(g, font(), x + ProgrammerLayout.SLOT_BOX_X0, y + ProgrammerLayout.SLOT_BOX_Y0,
                x + ProgrammerLayout.SLOT_BOX_X1, y + ProgrammerLayout.SLOT_BOX_Y1, null);
        SlotFrame.caption(g, font(), x + ProgrammerLayout.SLOT_X, y + ProgrammerLayout.CARD_Y,
                Component.translatable("programmer.nerosecurity.slot.card"));
        SlotFrame.caption(g, font(), x + ProgrammerLayout.SLOT_X, y + ProgrammerLayout.BLANK_Y,
                Component.translatable("programmer.nerosecurity.slot.blank"));
        SlotFrame.caption(g, font(), x + ProgrammerLayout.SLOT_X, y + ProgrammerLayout.MODULE_Y,
                Component.translatable("programmer.nerosecurity.slot.module"));

        int colX = x + ProgrammerLayout.COL_X;
        int colRight = colX + ProgrammerLayout.COL_W;
        g.text(font(), Component.translatable("programmer.nerosecurity.section.level"), colX,
                y + ProgrammerLayout.CAPTION_Y, SecurityColours.SUBTLE, false);
        if (snap != null) {
            Component cards = Component.translatable("programmer.nerosecurity.cards", snap.cardsIssued(), snap.cardCap());
            g.text(font(), cards, colRight - font().width(cards), y + ProgrammerLayout.CAPTION_Y,
                    SecurityColours.SUBTLE, false);
        }
        LevelMeaning.draw(g, font(), colX, y + ProgrammerLayout.MEANING_Y, ProgrammerLayout.COL_W, this.selected);
        drawBand(g, colX, y + ProgrammerLayout.BAND_Y, snap);
    }

    /**
     * The result band: filled edge to edge with the level colour, ink text saying "<LEVEL> card — meaning", and
     * an ENCRYPTED tag at the right when the card will be encrypted (the word, not only the colour). When the
     * viewer can write nothing the band is a dark trough that says so.
     */
    private void drawBand(GuiGraphicsExtractor g, int left, int top, @Nullable ProgrammerSnapshot snap) {
        int w = ProgrammerLayout.COL_W;
        int h = ProgrammerLayout.BAND_H;
        if (snap == null || this.selected == null) {
            g.fill(left, top, left + w, top + h, SecurityColours.TROUGH);
            g.outline(left, top, w, h, SecurityColours.EDGE);
            if (snap != null) {
                drawBandText(g, Component.translatable("programmer.nerosecurity.band.none"), left, top, w - 8,
                        WidgetColours.TEXT);
            }
            return;
        }
        g.fill(left, top, left + w, top + h, WidgetColours.pillFill(this.selected));
        int textRoom = w - 8;
        if (this.encrypted) {
            Component tag = Component.translatable("programmer.nerosecurity.band.encrypted");
            int tagW = Math.max(font().width(tag) + 2 * TAG_PAD, 20);
            LevelPill.drawTag(g, font(), left + w - 4 - tagW, top + (h - LevelPill.HEIGHT) / 2, tag, SecurityColours.ENC);
            textRoom -= tagW + 6;
        }
        Component text = Component.translatable("programmer.nerosecurity.band", LevelPill.word(this.selected),
                LevelMeaning.meaning(this.selected));
        drawBandText(g, text, left, top, textRoom, WidgetColours.pillText(this.selected));
    }

    private void drawBandText(GuiGraphicsExtractor g, Component text, int left, int top, int room, int colour) {
        List<FormattedCharSequence> lines = WidgetText.wrap(font(), text, room);
        int shown = Math.min(BAND_LINES, lines.size());
        int lineY = top + (ProgrammerLayout.BAND_H - shown * BAND_LINE_HEIGHT) / 2 + 1;
        for (int i = 0; i < shown; i++) {
            g.text(font(), lines.get(i), left + 4, lineY + i * BAND_LINE_HEIGHT, colour, false);
        }
        if (lines.size() > BAND_LINES) {
            explain(g, left, top, ProgrammerLayout.COL_W, ProgrammerLayout.BAND_H, text);
        }
    }
}
