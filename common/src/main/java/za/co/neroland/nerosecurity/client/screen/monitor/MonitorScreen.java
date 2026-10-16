package za.co.neroland.nerosecurity.client.screen.monitor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;
import za.co.neroland.nerosecurity.client.screen.monitor.MonitorRows.Row;
import za.co.neroland.nerosecurity.client.widget.ScrollRegion;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.SecurityButton;
import za.co.neroland.nerosecurity.client.widget.SentenceList;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.menu.monitor.MonitorMenu;
import za.co.neroland.nerosecurity.menu.monitor.MonitorPayloads.MonitorRefresh;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceRow;

/**
 * The Security Monitor: text only, never a picture. Title "<installation> — <status>", a status row with a
 * shaped glyph, then two columns: SENSORS on the left (cameras and laser grid nodes, each a status glyph, a
 * block position and a state word, scrollable) and RECENT EVENTS on the right (a {@link SentenceList} of
 * anonymous sentences, scrollable), with a Refresh button bottom-right that asks the server for a new
 * {@link MonitorSnapshot}.
 *
 * <p>It draws only what the server's last snapshot says. No name, UUID or position of a person is anywhere in
 * the snapshot, so none can be drawn: an event sentence says only whether <em>a player</em> was involved. Ages
 * are relative and keep counting while the screen is open. Read-only except for Refresh.
 *
 * <p>CLIENT-ONLY.
 */
public class MonitorScreen extends SecurityScreen<MonitorMenu> {

    private static final int W = 276;
    private static final int H = 210;
    private static final int STATUS_Y = 30;
    private static final int BOX_Y0 = 44;
    private static final int BOX_Y1 = 172;
    private static final int LEFT_X0 = PAD;
    private static final int LEFT_X1 = 122;
    private static final int RIGHT_X0 = 130;
    private static final int RIGHT_X1 = W - PAD;
    private static final int INNER_PAD = 4;
    private static final int LINE = SentenceList.LINE_HEIGHT;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_Y = 176;
    /** Rebuild the aged sentences this often, so "N min ago" keeps counting. */
    private static final int REAGE_TICKS = 20;

    private SensorList sensors;
    private SentenceList events;
    private SecurityButton refreshButton;
    private long builtVersion = Long.MIN_VALUE;
    private int ticks;

    public MonitorScreen(MonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, false, ClientMonitorState::version);
        setSubtitle(Component.translatable("monitor.nerosecurity.subtitle"));
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int y = top();
        int contentTop = SectionBox.contentTop(y + BOX_Y0);
        int contentHeight = y + BOX_Y1 - INNER_PAD - contentTop;
        this.sensors = addScrollRegion(new SensorList(x + LEFT_X0 + INNER_PAD, contentTop,
                LEFT_X1 - LEFT_X0 - 2 * INNER_PAD, contentHeight));
        this.events = addScrollRegion(new SentenceList(x + RIGHT_X0 + INNER_PAD, contentTop,
                RIGHT_X1 - RIGHT_X0 - 2 * INNER_PAD, contentHeight));
        this.refreshButton = addRenderableWidget(SecurityButton.of(x + RIGHT_X1 - BUTTON_W, y + BUTTON_Y,
                BUTTON_W, BUTTON_H, Component.translatable("monitor.nerosecurity.button.refresh"), b -> refresh()));
        this.builtVersion = Long.MIN_VALUE;
    }

    // --- state ----------------------------------------------------------------------------------

    private static @Nullable MonitorSnapshot snapshot() {
        return ClientMonitorState.current();
    }

    /** Whole seconds since the snapshot arrived, so ages keep counting between refreshes. */
    private static long elapsedSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - ClientMonitorState.receivedAt()) / 1000L);
    }

    private static Component statusText(MonitorSnapshot snap) {
        int release = (int) Math.max(0L, snap.autoReleaseInSeconds() - elapsedSeconds());
        return MonitorText.status(snap.lockdown(), snap.source(), release);
    }

    @Override
    protected void refreshWidgets() {
        this.ticks++;
        MonitorSnapshot snap = snapshot();
        this.refreshButton.setEnabled(snap != null, Component.translatable("ui.nerosecurity.waiting"));
        if (snap == null) {
            setTitleLine(this.title);
            return;
        }
        setTitleLine(Component.translatable("monitor.nerosecurity.title", snap.installationName(), statusText(snap)));
        long version = ClientMonitorState.version();
        if (version != this.builtVersion || this.ticks % REAGE_TICKS == 0) {
            this.builtVersion = version;
            rebuild(snap);
        }
    }

    private void rebuild(MonitorSnapshot snap) {
        this.sensors.setRows(MonitorRows.of(snap));
        long extra = elapsedSeconds();
        List<SentenceList.Row> rows = new ArrayList<>(snap.events().size());
        if (!snap.auditDisabled()) {
            for (AnonymousEvent event : snap.events()) {
                rows.add(SentenceList.event(event, extra));
            }
        }
        this.events.setEmpty(Component.translatable(snap.auditDisabled()
                ? "monitor.nerosecurity.audit_disabled" : "monitor.nerosecurity.none"));
        this.events.setRows(rows);
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        MonitorSnapshot snap = snapshot();
        if (snap != null) {
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
        }
    }

    private void refresh() {
        MonitorSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new MonitorRefresh(snap.pos()));
        }
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientMonitorState.clear();
        super.removed();
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        MonitorSnapshot snap = snapshot();
        if (snap != null) {
            Status status = MonitorRows.installationStatus(snap.lockdown());
            StatusDot.draw(g, font(), x + PAD, y + STATUS_Y, status, statusText(snap));
        }
        Component sensorsCaption = Component.translatable("monitor.nerosecurity.section.sensors",
                snap == null ? 0 : MonitorRows.sensorCount(snap));
        SectionBox.draw(g, font(), x + LEFT_X0, y + BOX_Y0, x + LEFT_X1, y + BOX_Y1, sensorsCaption);
        SectionBox.draw(g, font(), x + RIGHT_X0, y + BOX_Y0, x + RIGHT_X1, y + BOX_Y1,
                Component.translatable("monitor.nerosecurity.section.events"));
        if (snap != null) {
            this.sensors.extract(g);
            this.events.extract(g, font());
        }
    }

    /**
     * The SENSORS column: headings, one line per sensor (status glyph, position, state word) and wrapped notes,
     * 11 px a line, scrolled by the wheel. Hovering a sensor line shows the full sentence
     * ("Security Camera at 10, 67, -1, unpowered").
     */
    private final class SensorList implements ScrollRegion {

        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private List<Row> rows = List.of();
        private int scroll;
        private int lineCount;

        private record Line(Row row, FormattedCharSequence text, @Nullable Component full) {
        }

        SensorList(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void setRows(List<Row> rows) {
            this.rows = List.copyOf(rows);
        }

        private int visibleLines() {
            return Math.max(1, height / LINE);
        }

        private List<Line> layout() {
            List<Line> out = new ArrayList<>();
            for (Row row : rows) {
                switch (row.kind()) {
                    case HEADING -> out.add(new Line(row, Component.translatable(row.key()).getVisualOrderText(), null));
                    case NOTE, WARNING -> {
                        for (FormattedCharSequence part : WidgetText.wrap(font(), Component.translatable(row.key()), width)) {
                            out.add(new Line(row, part, null));
                        }
                    }
                    case DEVICE -> {
                        DeviceRow device = row.device();
                        Component state = Component.translatable(device.state().translationKey());
                        Component text = MonitorText.position(device.pos()).copy().append("  ").append(state);
                        Component full = Component.translatable("device.nerosecurity.tooltip",
                                Component.translatable(MonitorRows.blockKey(device.kind())),
                                MonitorText.position(device.pos()), state);
                        int room = width - StatusDot.SIZE - 4;
                        out.add(new Line(row, WidgetText.fit(font(), text, room), full));
                    }
                }
            }
            return out;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
            if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
                return false;
            }
            int max = Math.max(0, lineCount - visibleLines());
            scroll = Math.max(0, Math.min(scroll + (scrollY > 0 ? -1 : 1), max));
            return true;
        }

        void extract(GuiGraphicsExtractor g) {
            List<Line> lines = layout();
            lineCount = lines.size();
            int max = visibleLines();
            scroll = Math.max(0, Math.min(scroll, Math.max(0, lineCount - max)));
            int end = Math.min(lineCount, scroll + max);
            for (int i = scroll; i < end; i++) {
                Line line = lines.get(i);
                int ly = y + (i - scroll) * LINE;
                switch (line.row().kind()) {
                    case HEADING -> g.text(font(), line.text(), x, ly, SecurityColours.SUBTLE, false);
                    case NOTE -> g.text(font(), line.text(), x, ly, SecurityColours.SUBTLE, false);
                    case WARNING -> g.text(font(), line.text(), x, ly, SecurityColours.opaque(SecurityColours.WARN), false);
                    case DEVICE -> {
                        DeviceRow device = line.row().device();
                        StatusDot.drawGlyph(g, x, ly + 1, MonitorRows.status(device.state()));
                        g.text(font(), line.text(), x + StatusDot.SIZE + 4, ly, WidgetColours.TEXT, false);
                        explain(g, x, ly, width, LINE, line.full());
                    }
                }
            }
            if (scroll > 0) {
                g.fill(x + width - 5, y, x + width - 1, y + 1, SecurityColours.SUBTLE);
            }
            if (scroll < lineCount - max) {
                g.fill(x + width - 5, y + height - 1, x + width - 1, y + height, SecurityColours.SUBTLE);
            }
        }
    }
}
