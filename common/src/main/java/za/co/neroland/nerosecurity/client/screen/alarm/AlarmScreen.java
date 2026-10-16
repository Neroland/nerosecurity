package za.co.neroland.nerosecurity.client.screen.alarm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.alarm.AlarmResponse;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.menu.alarm.AlarmMenu;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmConfigure;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSilence;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSnapshot;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * A deliberately plain settings screen for the alarm: one toggle per subscribable event type (grouped
 * "Breaches / Denied access / System"), one per response, the owner-only lockdown warning beneath the
 * lockdown toggle, and a silence button. It draws only what the server's last snapshot says; every
 * toggle sends the whole wanted configuration as an intent the server re-authorises, and the toggles
 * stay disabled until the next snapshot arrives. The lockdown toggle is disabled unless the viewer is an
 * effective owner (or operator) and the server allows alarm lockdowns; the server checks again anyway.
 * While the lockdown response is on, every toggle is owner-only (officers can still silence), and the
 * lockdown toggle's tooltip warns that members also trip sensors during a lockdown.
 *
 * <p>CLIENT-ONLY.
 */
public class AlarmScreen extends AbstractContainerScreen<AlarmMenu> {

    private static final int W = 300;
    private static final int H = 220;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int WARN = 0xFFE0A526;
    private static final int LEFT_X = 8;
    private static final int RIGHT_X = 156;
    private static final int COL_W = 136;
    private static final int ROW_H = 13;
    private static final int TOP = 20;

    private record TypeToggle(SecurityEventType type, Button button) {
    }

    private record ResponseToggle(AlarmResponse response, Button button) {
    }

    private record Header(Component label, int y) {
    }

    private final List<TypeToggle> typeToggles = new ArrayList<>();
    private final List<ResponseToggle> responseToggles = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private Button silenceButton;
    private int warningY;

    /** The snapshot version a sent intent is waiting on; toggles stay disabled until it changes. */
    private long awaiting = -1L;

    public AlarmScreen(AlarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        typeToggles.clear();
        responseToggles.clear();
        headers.clear();
        int x = this.leftPos;
        int y = this.topPos + TOP;
        y = group(Component.translatable("alarm.nerosecurity.group.breaches"), AlarmRules.BREACHES, x + LEFT_X, y);
        y = group(Component.translatable("alarm.nerosecurity.group.denials"), AlarmRules.DENIALS, x + LEFT_X, y);
        group(Component.translatable("alarm.nerosecurity.group.system"), AlarmRules.SYSTEM, x + LEFT_X, y);

        int ry = this.topPos + TOP + 11;
        for (AlarmResponse response : AlarmResponse.values()) {
            Button button = addRenderableWidget(Button.builder(Component.empty(), b -> toggle(response))
                    .bounds(x + RIGHT_X, ry, COL_W, ROW_H - 1).build());
            responseToggles.add(new ResponseToggle(response, button));
            ry += ROW_H + 1;
        }
        this.warningY = ry + 2;
        this.silenceButton = addRenderableWidget(Button.builder(
                Component.translatable("alarm.nerosecurity.button.silence", AlarmRules.SILENCE_BUTTON_MINUTES),
                b -> silence()).bounds(x + RIGHT_X, this.topPos + H - 22, COL_W, 16).build());
        refreshWidgets();
    }

    private int group(Component label, List<SecurityEventType> types, int x, int y) {
        headers.add(new Header(label, y - this.topPos));
        y += 11;
        for (SecurityEventType type : types) {
            Button button = addRenderableWidget(Button.builder(Component.empty(), b -> toggle(type))
                    .bounds(x, y, COL_W, ROW_H - 1).build());
            typeToggles.add(new TypeToggle(type, button));
            y += ROW_H + 1;
        }
        return y + 3;
    }

    private static AlarmSnapshot snapshot() {
        return ClientAlarmState.current();
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientAlarmState.version();
    }

    private static Component checkbox(boolean on, Component label) {
        return Component.literal(on ? "[x] " : "[ ] ").append(label);
    }

    private void refreshWidgets() {
        AlarmSnapshot snap = snapshot();
        boolean ready = snap != null && !waiting();
        // An alarm that can lock the base down is owner-only to edit (the server enforces it too).
        boolean ownerView = snap != null && (snap.admin() || snap.viewerLevel() == AccessLevel.OWNER);
        boolean armedLockdown = snap != null && AlarmRules.attemptsLockdown(snap.responses());
        boolean lockedForViewer = armedLockdown && !ownerView;
        Tooltip armedTip = lockedForViewer
                ? Tooltip.create(Component.translatable("alarm.nerosecurity.lockdown.owner_only_armed")) : null;
        for (TypeToggle t : typeToggles) {
            boolean on = snap != null && snap.subscribed().contains(t.type());
            t.button().active = ready && !lockedForViewer;
            t.button().setMessage(checkbox(on, EventPhrases.phrase(t.type())));
            t.button().setTooltip(armedTip);
        }
        for (ResponseToggle r : responseToggles) {
            boolean on = snap != null && snap.responses().contains(r.response());
            boolean lockdown = r.response() == AlarmResponse.LOCKDOWN;
            boolean editable = lockdown ? snap != null && snap.mayEditLockdown() : !lockedForViewer;
            r.button().active = ready && editable;
            r.button().setMessage(checkbox(on, Component.translatable(r.response().translationKey())));
            if (lockdown) {
                Component members = Component.translatable("alarm.nerosecurity.lockdown.members_tooltip");
                if (snap == null || editable) {
                    r.button().setTooltip(Tooltip.create(members));
                } else {
                    r.button().setTooltip(Tooltip.create(Component.translatable(
                            snap.lockdownResponseAllowedByServer() ? "alarm.nerosecurity.lockdown.owner_only"
                                    : "alarm.nerosecurity.lockdown.server_disabled")
                            .append("\n").append(members)));
                }
            } else {
                r.button().setTooltip(armedTip);
            }
        }
        this.silenceButton.active = ready;
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientAlarmState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    // --- intents --------------------------------------------------------------------------------

    private void send(CustomPacketPayload payload) {
        this.awaiting = ClientAlarmState.version();
        Services.NETWORK.sendToServer(payload);
        refreshWidgets();
    }

    private void toggle(SecurityEventType type) {
        AlarmSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        EnumSet<SecurityEventType> wanted = AlarmRules.sanitiseSubscribed(snap.subscribed());
        if (!wanted.remove(type)) {
            wanted.add(type);
        }
        send(new AlarmConfigure(snap.pos(), wanted, snap.responses()));
    }

    private void toggle(AlarmResponse response) {
        AlarmSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        Set<AlarmResponse> wanted = AlarmRules.copyResponses(snap.responses());
        if (!wanted.remove(response)) {
            wanted.add(response);
        }
        send(new AlarmConfigure(snap.pos(), snap.subscribed(), wanted));
    }

    private void silence() {
        AlarmSnapshot snap = snapshot();
        if (snap != null) {
            send(new AlarmSilence(snap.pos(), AlarmRules.SILENCE_BUTTON_MINUTES));
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
        int x = this.leftPos;
        int y = this.topPos;
        for (Header header : headers) {
            g.text(this.font, header.label(), x + LEFT_X, y + header.y(), SUBTLE, false);
        }
        AlarmSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        int line = this.warningY;
        List<FormattedCharSequence> warning = this.font.split(
                Component.translatable("alarm.nerosecurity.lockdown.warning", snap.autoReleaseMinutes()), COL_W);
        for (FormattedCharSequence text : warning) {
            g.text(this.font, text, x + RIGHT_X, line, WARN, false);
            line += 10;
        }
        if (!snap.lockdownResponseAllowedByServer()) {
            for (FormattedCharSequence text : this.font.split(
                    Component.translatable("alarm.nerosecurity.lockdown.server_disabled"), COL_W)) {
                g.text(this.font, text, x + RIGHT_X, line, SUBTLE, false);
                line += 10;
            }
        }
        if (snap.silencedForSeconds() > 0) {
            int minutes = Math.max(1, (snap.silencedForSeconds() + 59) / 60);
            g.text(this.font, Component.translatable("alarm.nerosecurity.silenced_until", minutes), x + LEFT_X,
                    y + H - 18, SUBTLE, false);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
    }
}
