package za.co.neroland.nerosecurity.client.screen.monitor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.menu.monitor.MonitorMenu;
import za.co.neroland.nerosecurity.menu.monitor.MonitorPayloads.MonitorRefresh;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceKind;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceRow;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceState;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * A deliberately plain, text-only monitor screen (a later stage adds the animated screen face): a
 * one-line title (installation name and status), then the cameras, the laser grids and the recent
 * events as sentences, in one scrollable list, and a Refresh button. It draws only what the server's
 * last snapshot says. There is no imagery and no name anywhere: event sentences say only whether a
 * player was involved.
 *
 * <p>CLIENT-ONLY.
 */
public class MonitorScreen extends AbstractContainerScreen<MonitorMenu> {

    private static final int W = 256;
    private static final int H = 192;
    private static final int PANEL = 0xFF141C26;
    private static final int EDGE = 0xFF2A3A4D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int HEADING = 0xFFB8D4F0;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int OK = 0xFF7FD68A;
    private static final int WARN = 0xFFFFB454;
    private static final int ALERT = 0xFFFF5A5A;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 22;
    private static final int LIST_W = 224;
    private static final int ROW_H = 10;
    private static final int ROWS = 14;
    /** Ticks the Refresh button stays disabled waiting for an answer before it gives up. */
    private static final int REFRESH_TIMEOUT_TICKS = 40;

    private record Line(FormattedCharSequence text, int colour) {
    }

    private final List<Line> lines = new ArrayList<>();
    private Button refreshButton;
    private Button upButton;
    private Button downButton;
    private int scroll;
    private long builtVersion = -1L;
    private int ticks;
    /** The snapshot version a refresh is waiting on; the button stays disabled until it changes. */
    private long awaiting = -1L;
    private int awaitingSince;

    public MonitorScreen(MonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        this.upButton = addRenderableWidget(Button.builder(Component.literal("^"), b -> scrollBy(-1))
                .bounds(x + LIST_X + LIST_W + 2, y + LIST_Y, 14, 14).build());
        this.downButton = addRenderableWidget(Button.builder(Component.literal("v"), b -> scrollBy(1))
                .bounds(x + LIST_X + LIST_W + 2, y + LIST_Y + ROWS * ROW_H - 14, 14, 14).build());
        this.refreshButton = addRenderableWidget(Button.builder(
                Component.translatable("monitor.nerosecurity.button.refresh"), b -> refresh())
                .bounds(x + W - 8 - 80, y + H - 24, 80, 16).build());
        rebuild();
        refreshWidgets();
    }

    private static MonitorSnapshot snapshot() {
        return ClientMonitorState.current();
    }

    /** Whole seconds since the snapshot arrived, so ages keep counting between refreshes. */
    private static long elapsedSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - ClientMonitorState.receivedAt()) / 1000L);
    }

    private boolean waiting() {
        return awaiting >= 0 && awaiting == ClientMonitorState.version() && ticks - awaitingSince < REFRESH_TIMEOUT_TICKS;
    }

    private void refreshWidgets() {
        this.refreshButton.active = snapshot() != null && !waiting();
        int max = maxScroll();
        this.scroll = Math.max(0, Math.min(this.scroll, max));
        this.upButton.active = this.scroll > 0;
        this.downButton.active = this.scroll < max;
    }

    private int maxScroll() {
        return Math.max(0, this.lines.size() - ROWS);
    }

    private void scrollBy(int delta) {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll + delta));
        refreshWidgets();
    }

    private void refresh() {
        MonitorSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        this.awaiting = ClientMonitorState.version();
        this.awaitingSince = this.ticks;
        Services.NETWORK.sendToServer(new MonitorRefresh(snap.pos()));
        refreshWidgets();
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientMonitorState.clear();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.ticks++;
        // Rebuild when a snapshot arrives, and once a second so "N minutes ago" keeps ageing.
        if (this.builtVersion != ClientMonitorState.version() || this.ticks % 20 == 0) {
            rebuild();
        }
        refreshWidgets();
    }

    // --- content --------------------------------------------------------------------------------

    private void rebuild() {
        this.builtVersion = ClientMonitorState.version();
        this.lines.clear();
        MonitorSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        long extra = elapsedSeconds();
        heading(Component.translatable("monitor.nerosecurity.cameras"));
        if (snap.camerasDisabled()) {
            add(Component.translatable("monitor.nerosecurity.cameras_disabled"), SUBTLE);
        } else {
            devices(snap, DeviceKind.CAMERA);
        }
        heading(Component.translatable("monitor.nerosecurity.lasers"));
        devices(snap, DeviceKind.LASER);
        heading(Component.translatable("monitor.nerosecurity.events"));
        if (snap.auditDisabled()) {
            add(Component.translatable("monitor.nerosecurity.audit_disabled"), SUBTLE);
        } else if (snap.events().isEmpty()) {
            add(Component.translatable("monitor.nerosecurity.none"), SUBTLE);
        } else {
            for (AnonymousEvent event : snap.events()) {
                add(MonitorText.sentence(event, extra), severityColour(event.severity()));
            }
        }
    }

    private void devices(MonitorSnapshot snap, DeviceKind kind) {
        boolean any = false;
        for (DeviceRow row : snap.devices()) {
            if (row.kind() != kind) {
                continue;
            }
            any = true;
            add(Component.literal("  ").append(MonitorText.position(row.pos())).append(" — ")
                    .append(Component.translatable(row.state().translationKey())), stateColour(row.state()));
        }
        if (!any) {
            add(Component.literal("  ").append(Component.translatable("monitor.nerosecurity.pos.none")), SUBTLE);
        }
    }

    private void heading(Component text) {
        add(text, HEADING);
    }

    private void add(Component text, int colour) {
        for (FormattedCharSequence part : this.font.split(text, LIST_W - 4)) {
            this.lines.add(new Line(part, colour));
        }
    }

    private static int stateColour(DeviceState state) {
        return switch (state) {
            case ARMED -> OK;
            case UNPOWERED, UNPAIRED -> WARN;
            case UNLOADED -> SUBTLE;
        };
    }

    private static int severityColour(SecuritySeverity severity) {
        if (severity == SecuritySeverity.CRITICAL) {
            return ALERT;
        }
        return severity == SecuritySeverity.WARN ? WARN : TITLE;
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, EDGE);
        g.fill(x, y, x + W, y + H, PANEL);
        g.fill(x + LIST_X - 1, y + LIST_Y - 1, x + LIST_X + LIST_W, y + LIST_Y + ROWS * ROW_H + 1, TROUGH);
        super.extractContents(g, mouseX, mouseY, partialTick);
        int end = Math.min(this.lines.size(), this.scroll + ROWS);
        for (int i = this.scroll; i < end; i++) {
            Line line = this.lines.get(i);
            g.text(this.font, line.text(), x + LIST_X + 2, y + LIST_Y + 1 + (i - this.scroll) * ROW_H, line.colour(), false);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        MonitorSnapshot snap = snapshot();
        if (snap == null) {
            g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
            return;
        }
        int release = (int) Math.max(0L, snap.autoReleaseInSeconds() - elapsedSeconds());
        Component title = Component.translatable("monitor.nerosecurity.title", snap.installationName(),
                MonitorText.status(snap.lockdown(), snap.source(), release));
        List<FormattedCharSequence> parts = this.font.split(title, W - 16);
        if (!parts.isEmpty()) {
            g.text(this.font, parts.get(0), this.titleLabelX, this.titleLabelY, snap.lockdown() ? ALERT : TITLE, false);
        }
    }
}
