package za.co.neroland.nerosecurity.client.screen.chest;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;
import za.co.neroland.nerosecurity.client.widget.LevelLadder;
import za.co.neroland.nerosecurity.client.widget.LevelMeaning;
import za.co.neroland.nerosecurity.client.widget.LevelPill;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.Toggle;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestConfigure;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloads.ChestSnapshot;
import za.co.neroland.nerosecurity.menu.chest.ChestSettingsMenu;

/**
 * "Who can open this chest?" — the secure chest settings screen on the {@link SecurityScreen} hull: the level
 * ladder with its meaning line, the keycard-only toggle, the lockdown / link row, and the automation row
 * ("Automation: [MEMBER]" with an OK or OFF glyph and the server's one-line reason) over the policy hint. It
 * draws only what the server's last snapshot says; every control sends a {@link ChestConfigure} the server
 * re-authorises, and the controls are held until the next snapshot. Disabled controls always say why.
 *
 * <p>CLIENT-ONLY.
 */
public class SecureChestSettingsScreen extends SecurityScreen<ChestSettingsMenu> {

    public static final int W = 248;
    public static final int H = 150;

    private static final int LADDER_Y = 30;
    private static final int MEANING_Y = 52;
    private static final int KEYCARD_Y = 76;
    /** The lockdown / link row shares the keycard row, right of the toggle. */
    private static final int STATE_X = 140;
    private static final int AUTOMATION_Y = 94;
    private static final int WHY_Y = 105;
    private static final int HINT_Y = 116;
    private static final int COMMAND_Y = 126;

    private LevelLadder ladder;
    private Toggle keycardOnly;

    public SecureChestSettingsScreen(ChestSettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, false, ClientChestState::version);
        setTitleLine(Component.translatable("chest.nerosecurity.title"));
        setSubtitle(Component.translatable("chest.nerosecurity.subtitle"));
    }

    private static @Nullable ChestSnapshot snapshot() {
        return ClientChestState.current();
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int y = top();
        ladder = addGroup(new LevelLadder(x + PAD, y + LADDER_Y, this::setLevel));
        keycardOnly = addRenderableWidget(new Toggle(x + PAD, y + KEYCARD_Y,
                Component.translatable("door.nerosecurity.toggle.keycard_only"), this::setKeycardOnly));
        keycardOnly.setHelp(Component.translatable("door.nerosecurity.toggle.keycard_only.hint"));
    }

    @Override
    protected void refreshWidgets() {
        ChestSnapshot snap = snapshot();
        if (snap == null) {
            Component waiting = Component.translatable("ui.nerosecurity.waiting");
            ladder.select(null);
            ladder.disableAll(waiting);
            keycardOnly.disable(waiting);
            return;
        }
        ladder.select(snap.minLevel());
        keycardOnly.setChecked(snap.keycardOnly());
        // The server reports an operator as OWNER in viewerLevel (ChestPayloadHandlers), so no admin flag here.
        boolean ownerOrAdmin = snap.viewerLevel() == AccessLevel.OWNER;
        String why = ChestText.editWhy(snap.linked(), snap.minLevel(), ownerOrAdmin);
        if (why != null) {
            Component reason = Component.translatable(why);
            ladder.disableAll(reason);
            keycardOnly.disable(reason);
            return;
        }
        ladder.enableAll();
        ladder.restrictOwner(snap.viewerLevel(), false, Component.translatable(ChestText.OWNER_ONLY_KEY));
        keycardOnly.enable();
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        ChestSnapshot snap = snapshot();
        if (snap != null) {
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
        }
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientChestState.clear();
        super.removed();
    }

    // --- intents (the server re-resolves every one) ---------------------------------------------

    private void setLevel(AccessLevel level) {
        ChestSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new ChestConfigure(snap.pos(), level, snap.keycardOnly()));
        }
    }

    private void setKeycardOnly(boolean on) {
        ChestSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new ChestConfigure(snap.pos(), snap.minLevel(), on));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        LevelMeaning.draw(g, font, x + PAD, y + MEANING_Y, LevelLadder.WIDTH, ladder.selected());
        ChestSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        int right = x + W - PAD;

        String sealed = ChestText.sealedKey(snap.lockdown());
        if (sealed != null) {
            statusRow(g, x + STATE_X, y + KEYCARD_Y + 3, right - (x + STATE_X), Status.BAD, Component.translatable(sealed));
        } else if (!snap.linked()) {
            statusRow(g, x + STATE_X, y + KEYCARD_Y + 3, right - (x + STATE_X), Status.OFF,
                    Component.translatable(ChestText.UNLINKED_KEY));
        }

        ChestText.Automation automation = ChestText.automation(snap.linked(), snap.automation(),
                snap.automationAllowed(), snap.why());
        if (automation == null) {
            return;
        }
        int ax = x + PAD;
        int ay = y + AUTOMATION_Y;
        StatusDot.drawGlyph(g, ax, ay + 1, automation.status());
        ax += StatusDot.SIZE + 4;
        // "Automation: " then the policy as a pill (a level word is never coloured text) or the word "off".
        Component lead = Component.translatable(ChestText.AUTOMATION_KEY, "");
        g.text(font, lead, ax, ay, WidgetColours.TEXT, false);
        ax += font.width(lead) + 2;
        if (automation.policyLevel() != null) {
            LevelPill.draw(g, font, ax, ay - 1, automation.policyLevel());
        } else {
            g.text(font, Component.translatable(ChestText.AUTOMATION_OFF_KEY), ax, ay, WidgetColours.TEXT, false);
        }
        g.text(font, Component.translatable(automation.whyKey()), x + PAD + StatusDot.SIZE + 4, y + WHY_Y,
                WidgetColours.HINT, false);

        int room = W - 2 * PAD;
        hintRow(g, x + PAD, y + HINT_Y, room, Component.translatable("chest.nerosecurity.automation.hint"));
        hintRow(g, x + PAD, y + COMMAND_Y, room, Component.translatable("chest.nerosecurity.automation.hint_command"));
    }

    /** A glyph plus a sentence fitted to {@code width}; the whole sentence is the tooltip when it was cut. */
    private void statusRow(GuiGraphicsExtractor g, int x, int y, int width, Status status, Component text) {
        StatusDot.drawGlyph(g, x, y + 1, status);
        int tx = x + StatusDot.SIZE + 4;
        int room = width - (StatusDot.SIZE + 4);
        WidgetText.drawFitted(g, font, text, tx, y, room, WidgetColours.TEXT);
        if (WidgetText.overflows(font, text, room)) {
            explain(g, x, y, width, 9, text);
        }
    }

    private void hintRow(GuiGraphicsExtractor g, int x, int y, int width, Component text) {
        WidgetText.drawFitted(g, font, text, x, y, width, WidgetColours.HINT);
        if (WidgetText.overflows(font, text, width)) {
            explain(g, x, y, width, 9, text);
        }
    }
}
