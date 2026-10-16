package za.co.neroland.nerosecurity.client.screen;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.widget.ConfirmPanel;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.SecurityButton;
import za.co.neroland.nerosecurity.client.widget.SecurityWidget;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetGroup;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.HubPrivacyAction;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.HubPrivacyRequest;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacyAction;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacySnapshot;

/**
 * The hub's Privacy tab: what this installation records (server settings and the installation's own counts,
 * never a person) and the viewer's three data buttons. It owns the tab's widgets and drawing; the
 * {@link SecurityHubScreen} registers the widgets, shows or hides the tab and forwards the snapshot.
 *
 * <ul>
 *   <li>Opening the tab asks the server for a {@link PrivacySnapshot}; until it arrives the buttons wait.</li>
 *   <li>Export and the opt-out toggle send a {@link HubPrivacyAction}. While the server says the actions are
 *       not available yet they are disabled, with that sentence as the reason.</li>
 *   <li>Erase never runs anything: after a confirm it closes the hub and opens the chat with the
 *       {@code /neroland} erase command typed in, so the player presses Enter themself.</li>
 * </ul>
 * CLIENT-ONLY.
 */
public final class HubPrivacyTab implements WidgetGroup {

    private static final Component UNAVAILABLE = Component.translatable("privacy.nerosecurity.unavailable");
    private static final Component WAITING = Component.translatable("ui.nerosecurity.waiting");

    private final SecurityHubScreen screen;
    private final SecurityButton export;
    private final SecurityButton optToggle;
    private final SecurityButton erase;
    private final List<SecurityButton> widgets;
    private boolean optedOut;

    HubPrivacyTab(SecurityHubScreen screen) {
        this.screen = screen;
        int left = screen.left();
        int top = screen.top();
        HubLayout.Box b0 = HubLayout.yoursButton(0);
        HubLayout.Box b1 = HubLayout.yoursButton(1);
        HubLayout.Box b2 = HubLayout.yoursButton(2);
        this.export = SecurityButton.of(left + b0.x0(), top + b0.y0(), b0.width(), b0.height(),
                Component.translatable("privacy.nerosecurity.button.export"),
                b -> screen.sendIntent(new HubPrivacyAction(PrivacyAction.EXPORT)));
        this.optToggle = SecurityButton.of(left + b1.x0(), top + b1.y0(), b1.width(), b1.height(),
                Component.translatable("privacy.nerosecurity.button.optout"),
                b -> screen.sendIntent(new HubPrivacyAction(optedOut ? PrivacyAction.OPT_IN : PrivacyAction.OPT_OUT)));
        this.erase = SecurityButton.danger(left + b2.x0(), top + b2.y0(), b2.width(), b2.height(),
                Component.translatable("privacy.nerosecurity.button.erase"), b -> confirmErase());
        // Erase only opens the chat; it is not an intent and the hull never needs to hold it.
        this.erase.setIntent(false);
        this.export.setHelp(Component.translatable("privacy.nerosecurity.explain.export"));
        this.optToggle.setHelp(Component.translatable("privacy.nerosecurity.explain.optout"));
        this.erase.setHelp(Component.translatable("privacy.nerosecurity.explain.erase"));
        this.widgets = List.of(export, optToggle, erase);
    }

    @Override
    public List<? extends SecurityWidget> widgets() {
        return widgets;
    }

    void setVisible(boolean visible) {
        for (SecurityButton w : widgets) {
            w.visible = visible;
        }
    }

    /** Asks the server for the installation's privacy snapshot (sent when the tab is opened). */
    void request() {
        screen.sendIntent(HubPrivacyRequest.INSTANCE);
    }

    /** Mirrors the last snapshot into the buttons (courtesy only; the server decides). */
    void refresh(@Nullable PrivacySnapshot snap) {
        if (snap == null) {
            export.disable(WAITING);
            optToggle.disable(WAITING);
            return;
        }
        optedOut = snap.viewerOptedOut();
        optToggle.setMessage(Component.translatable(optedOut
                ? "privacy.nerosecurity.button.optin" : "privacy.nerosecurity.button.optout"));
        export.setEnabled(snap.actionsAvailable(), UNAVAILABLE);
        optToggle.setEnabled(snap.actionsAvailable(), UNAVAILABLE);
    }

    private void confirmErase() {
        ConfirmPanel.show(screen,
                Component.translatable("privacy.nerosecurity.erase.title"),
                Component.translatable("privacy.nerosecurity.erase.body"),
                Component.translatable("privacy.nerosecurity.erase.yes"),
                screen::openEraseChat);
    }

    // --- drawing --------------------------------------------------------------------------------

    /** Draws both boxes; {@code extraSeconds} ages the snapshot's "oldest" figure by the time it has been open. */
    void extract(GuiGraphicsExtractor g, Font font, @Nullable PrivacySnapshot snap, long extraSeconds) {
        int left = screen.left();
        int top = screen.top();
        HubLayout.Box kept = HubLayout.KEPT;
        SectionBox.draw(g, font, left + kept.x0(), top + kept.y0(), left + kept.x1(), top + kept.y1(),
                Component.translatable("privacy.nerosecurity.section.kept"));
        int y = top + HubLayout.KEPT_FIRST_Y;
        int labelX = left + HubLayout.KEPT_LABEL_X;
        int valueX = left + HubLayout.KEPT_VALUE_X;
        int valueW = HubLayout.KEPT_VALUE_W;
        int bottom = top + kept.y1() - 2;

        // Audit log
        g.text(font, Component.translatable("privacy.nerosecurity.audit"), labelX, y, SecurityColours.SUBTLE, false);
        if (snap == null) {
            y += drawValue(g, font, valueX, y, valueW, null, WAITING, SecurityColours.SUBTLE, bottom);
        } else if (!snap.auditEnabled()) {
            y += drawValue(g, font, valueX, y, valueW, Status.OFF,
                    Component.translatable("privacy.nerosecurity.audit.off"), WidgetColours.TEXT, bottom);
        } else if (snap.entryCount() == 0) {
            y += drawValue(g, font, valueX, y, valueW, Status.OK,
                    Component.translatable("privacy.nerosecurity.audit.empty", snap.retentionDays(), snap.maxEntries()),
                    WidgetColours.TEXT, bottom);
        } else {
            y += drawValue(g, font, valueX, y, valueW, Status.OK,
                    Component.translatable("privacy.nerosecurity.audit.on", snap.retentionDays(), snap.maxEntries(),
                            snap.entryCount(), Ages.ago(HubRules.aged(snap.oldestAgeSeconds(), extraSeconds))),
                    WidgetColours.TEXT, bottom);
        }
        y += HubLayout.KEPT_ROW_GAP;

        // Cameras
        g.text(font, Component.translatable("privacy.nerosecurity.cameras"), labelX, y, SecurityColours.SUBTLE, false);
        if (snap == null) {
            y += drawValue(g, font, valueX, y, valueW, null, WAITING, SecurityColours.SUBTLE, bottom);
        } else if (!snap.camerasEnabled()) {
            y += drawValue(g, font, valueX, y, valueW, Status.OFF,
                    Component.translatable("privacy.nerosecurity.cameras.off"), WidgetColours.TEXT, bottom);
        } else if (snap.cameraLogsMembers()) {
            // Amber words as well as the amber mark: a visible nudge, never colour alone.
            y += drawValue(g, font, valueX, y, valueW, Status.WARN,
                    Component.translatable("privacy.nerosecurity.cameras.members_logged"),
                    SecurityColours.opaque(SecurityColours.WARN), bottom);
        } else {
            y += drawValue(g, font, valueX, y, valueW, Status.OK,
                    Component.translatable("privacy.nerosecurity.cameras.members_not_logged"), WidgetColours.TEXT, bottom);
        }
        y += HubLayout.KEPT_ROW_GAP;

        // Who can read it
        g.text(font, Component.translatable("privacy.nerosecurity.readers"), labelX, y, SecurityColours.SUBTLE, false);
        y += drawValue(g, font, valueX, y, valueW, null,
                Component.translatable("privacy.nerosecurity.readers.value"), WidgetColours.TEXT, bottom);
        y += HubLayout.KEPT_ROW_GAP;

        // Notice
        g.text(font, Component.translatable("privacy.nerosecurity.notice"), labelX, y, SecurityColours.SUBTLE, false);
        drawValue(g, font, valueX, y, valueW, null,
                Component.translatable("privacy.nerosecurity.notice.value"), WidgetColours.TEXT, bottom);

        HubLayout.Box yours = HubLayout.YOURS;
        SectionBox.draw(g, font, left + yours.x0(), top + yours.y0(), left + yours.x1(), top + yours.y1(),
                Component.translatable("privacy.nerosecurity.section.yours"));
        int ty = top + HubLayout.YOURS_TEXT_Y;
        int tx = left + HubLayout.YOURS_TEXT_X;
        int tw = HubLayout.YOURS_TEXT_W;
        int limit = top + yours.y1() - 2;
        ty += drawClipped(g, font, Component.translatable("privacy.nerosecurity.explain.export"), tx, ty, tw, limit)
                + HubLayout.YOURS_PARAGRAPH_GAP;
        ty += drawClipped(g, font, Component.translatable("privacy.nerosecurity.explain.optout"), tx, ty, tw, limit)
                + HubLayout.YOURS_PARAGRAPH_GAP;
        drawClipped(g, font, Component.translatable("privacy.nerosecurity.explain.erase"), tx, ty, tw, limit);
    }

    /**
     * A value cell: an optional status mark, then the sentence wrapped to the column. Returns the height used.
     * Lines that would run under the box edge are not drawn; the full sentence is a hover tooltip instead.
     */
    private int drawValue(GuiGraphicsExtractor g, Font font, int x, int y, int width, @Nullable Status mark,
                          Component text, int colour, int bottom) {
        int tx = x;
        int tw = width;
        if (mark != null) {
            StatusDot.drawGlyph(g, x, y + 1, mark);
            tx += StatusDot.SIZE + 4;
            tw -= StatusDot.SIZE + 4;
        }
        return drawLines(g, font, text, tx, y, tw, colour, bottom);
    }

    private int drawClipped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int bottom) {
        return drawLines(g, font, text, x, y, width, SecurityColours.SUBTLE, bottom);
    }

    private int drawLines(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int colour,
                          int bottom) {
        var lines = WidgetText.wrap(font, text, width);
        int drawn = 0;
        for (var line : lines) {
            int ly = y + drawn * HubLayout.TEXT_LINE;
            if (ly + 9 > bottom) {
                break;
            }
            g.text(font, line, x, ly, colour, false);
            drawn++;
        }
        if (drawn < lines.size()) {
            screen.explain(g, x, y, width, Math.max(1, drawn) * HubLayout.TEXT_LINE, text);
        }
        return Math.max(1, drawn) * HubLayout.TEXT_LINE;
    }
}
