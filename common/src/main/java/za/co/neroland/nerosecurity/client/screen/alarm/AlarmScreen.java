package za.co.neroland.nerosecurity.client.screen.alarm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.alarm.AlarmResponse;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;
import za.co.neroland.nerosecurity.client.widget.SecurityButton;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.Toggle;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.menu.alarm.AlarmMenu;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmConfigure;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSilence;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSnapshot;

/**
 * "What should this alarm do?" — the alarm settings screen on the {@link SecurityScreen} hull. Left column,
 * WHEN THIS HAPPENS: one check box per subscribable event in its group (Breaches / Denied access / System).
 * Right column, DO THIS: one box per response, the owner-only lockdown warning under them, the armed or
 * silenced row and the Silence button. Every box sends the whole wanted configuration as an
 * {@link AlarmConfigure} the server re-authorises; the lockdown box is locked unless the viewer is an owner (or
 * operator) and the server allows alarm lockdowns, and while the lockdown response is on every other box is
 * owner-only too (officers can still silence). Disabled controls always say why.
 *
 * <p>CLIENT-ONLY.
 */
public class AlarmScreen extends SecurityScreen<AlarmMenu> {

    public static final int W = 310;
    public static final int H = 250;

    private static final int HEADERS_Y = 30;
    private static final int LEFT_X = 8;
    private static final int LEFT_W = 142;
    private static final int RIGHT_X = 158;
    private static final int RIGHT_W = W - PAD - RIGHT_X;
    private static final int GROUP_HEADER_H = 10;
    private static final int GROUP_GAP = 3;
    private static final int BUTTON_H = 16;
    private static final int LINE = 10;

    private record TypeToggle(SecurityEventType type, Toggle toggle) {
    }

    private record ResponseToggle(AlarmResponse response, Toggle toggle) {
    }

    private record Header(Component label, int y) {
    }

    private final List<TypeToggle> typeToggles = new ArrayList<>();
    private final List<ResponseToggle> responseToggles = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private SecurityButton silence;
    /** Panel-relative y where the lockdown warning starts (under the response boxes). */
    private int warningY;

    public AlarmScreen(AlarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, false, ClientAlarmState::version);
        setTitleLine(Component.translatable("alarm.nerosecurity.title"));
        setSubtitle(Component.translatable("alarm.nerosecurity.subtitle"));
    }

    private static @Nullable AlarmSnapshot snapshot() {
        return ClientAlarmState.current();
    }

    @Override
    protected void initWidgets() {
        typeToggles.clear();
        responseToggles.clear();
        headers.clear();
        int x = left();
        int y = top();

        int ly = HEADERS_Y + GROUP_HEADER_H + 1;
        ly = group(Component.translatable("alarm.nerosecurity.group.breaches"), AlarmRules.BREACHES, ly);
        ly = group(Component.translatable("alarm.nerosecurity.group.denials"), AlarmRules.DENIALS, ly);
        group(Component.translatable("alarm.nerosecurity.group.system"), AlarmRules.SYSTEM, ly);

        List<Toggle> responses = new ArrayList<>();
        for (AlarmResponse response : AlarmResponse.values()) {
            Toggle toggle = new Toggle(0, 0, Component.translatable(response.translationKey()), on -> toggle(response));
            responses.add(toggle);
            responseToggles.add(new ResponseToggle(response, toggle));
        }
        AlarmLayout.Packed<Toggle> packed = AlarmLayout.pack(responses, Toggle::getWidth, x + RIGHT_X,
                y + HEADERS_Y + GROUP_HEADER_H + 1, RIGHT_W);
        place(packed);
        warningY = packed.bottom() - y + 2;

        silence = addRenderableWidget(SecurityButton.of(x + RIGHT_X, footerTop() - 6 - BUTTON_H, RIGHT_W, BUTTON_H,
                Component.translatable("alarm.nerosecurity.button.silence", AlarmRules.SILENCE_BUTTON_MINUTES),
                b -> silence()));
    }

    /** One group of the left column: a header, then its boxes packed into rows; returns the next free y. */
    private int group(Component label, List<SecurityEventType> types, int panelY) {
        headers.add(new Header(label, panelY));
        List<Toggle> toggles = new ArrayList<>();
        for (SecurityEventType type : types) {
            Toggle toggle = new Toggle(0, 0, EventPhrases.phrase(type), on -> toggle(type));
            toggles.add(toggle);
            typeToggles.add(new TypeToggle(type, toggle));
        }
        AlarmLayout.Packed<Toggle> packed = AlarmLayout.pack(toggles, Toggle::getWidth, left() + LEFT_X,
                top() + panelY + GROUP_HEADER_H, LEFT_W);
        place(packed);
        return packed.bottom() - top() + GROUP_GAP;
    }

    private void place(AlarmLayout.Packed<Toggle> packed) {
        for (AlarmLayout.Placed<Toggle> p : packed.placed()) {
            p.item().setX(p.x());
            p.item().setY(p.y());
            addRenderableWidget(p.item());
        }
    }

    @Override
    protected void refreshWidgets() {
        AlarmSnapshot snap = snapshot();
        if (snap == null) {
            Component waiting = Component.translatable("ui.nerosecurity.waiting");
            typeToggles.forEach(t -> t.toggle().disable(waiting));
            responseToggles.forEach(r -> r.toggle().disable(waiting));
            silence.disable(waiting);
            return;
        }
        boolean ownerOrAdmin = snap.admin() || snap.viewerLevel() == AccessLevel.OWNER;
        String othersWhy = AlarmLayout.othersWhy(ownerOrAdmin, AlarmRules.attemptsLockdown(snap.responses()));
        Component othersReason = othersWhy == null ? null : Component.translatable(othersWhy);
        for (TypeToggle t : typeToggles) {
            t.toggle().setChecked(snap.subscribed().contains(t.type()));
            t.toggle().setEnabled(othersReason == null, othersReason);
        }
        Component members = Component.translatable(AlarmLayout.MEMBERS_TOOLTIP_KEY);
        for (ResponseToggle r : responseToggles) {
            r.toggle().setChecked(snap.responses().contains(r.response()));
            if (r.response() == AlarmResponse.LOCKDOWN) {
                String why = AlarmLayout.lockdownWhy(ownerOrAdmin, snap.lockdownResponseAllowedByServer());
                r.toggle().setHelp(members);
                if (why == null) {
                    r.toggle().enable();
                } else {
                    r.toggle().disable(Component.translatable(why).append("\n").append(members));
                }
            } else {
                r.toggle().setEnabled(othersReason == null, othersReason);
            }
        }
        if (AlarmLayout.maySilence(snap.viewerLevel(), snap.admin())) {
            silence.enable();
        } else {
            silence.disable(Component.translatable(AlarmLayout.DENY_LEVEL_KEY, AccessLevel.OFFICER.displayName(),
                    snap.viewerLevel().displayName()));
        }
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        AlarmSnapshot snap = snapshot();
        if (snap != null) {
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
        }
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientAlarmState.clear();
        super.removed();
    }

    // --- intents (the server re-resolves every one) ---------------------------------------------

    private void toggle(SecurityEventType type) {
        AlarmSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        EnumSet<SecurityEventType> wanted = AlarmRules.sanitiseSubscribed(snap.subscribed());
        if (!wanted.remove(type)) {
            wanted.add(type);
        }
        sendIntent(new AlarmConfigure(snap.pos(), wanted, snap.responses()));
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
        sendIntent(new AlarmConfigure(snap.pos(), snap.subscribed(), wanted));
    }

    private void silence() {
        AlarmSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new AlarmSilence(snap.pos(), AlarmRules.SILENCE_BUTTON_MINUTES));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        g.text(font, Component.translatable("alarm.nerosecurity.when"), x + LEFT_X, y + HEADERS_Y, WidgetColours.HINT, false);
        g.text(font, Component.translatable("alarm.nerosecurity.do"), x + RIGHT_X, y + HEADERS_Y, WidgetColours.HINT, false);
        for (Header header : headers) {
            g.text(font, header.label(), x + LEFT_X, y + header.y(), WidgetColours.HINT, false);
        }
        AlarmSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        int line = y + warningY;
        line += WidgetText.drawWrapped(g, font,
                Component.translatable("alarm.nerosecurity.lockdown.warning", snap.autoReleaseMinutes()),
                x + RIGHT_X, line, RIGHT_W, LINE, SecurityColours.opaque(SecurityColours.WARN));
        if (!snap.lockdownResponseAllowedByServer()) {
            WidgetText.drawWrapped(g, font, Component.translatable(AlarmLayout.SERVER_DISABLED_KEY),
                    x + RIGHT_X, line + 2, RIGHT_W, LINE, WidgetColours.HINT);
        }

        AlarmLayout.StateRow state = AlarmLayout.state(snap.silencedForSeconds());
        Component word = state.minutesLeft() > 0
                ? Component.translatable(state.key(), state.minutesLeft())
                : Component.translatable(state.key());
        int sy = silence.getY() - 12;
        StatusDot.drawGlyph(g, x + RIGHT_X, sy + 1, state.status());
        int tx = x + RIGHT_X + StatusDot.SIZE + 4;
        WidgetText.drawFitted(g, font, word, tx, sy, RIGHT_W - (StatusDot.SIZE + 4), WidgetColours.TEXT);
        if (WidgetText.overflows(font, word, RIGHT_W - (StatusDot.SIZE + 4))) {
            explain(g, x + RIGHT_X, sy, RIGHT_W, 9, word);
        }
    }
}
