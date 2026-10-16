package za.co.neroland.nerosecurity.client.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.Ages;
import za.co.neroland.nerosecurity.client.screen.monitor.MonitorText;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/**
 * Event sentences, one row each: a leading 7×7 severity mark (INFO outline, WARN filled, CRITICAL cross — shape
 * as well as colour), the sentence in white, wrapped to the column (never cut mid-word), and an optional
 * {@code SUBTLE} suffix. 11 px per line. Scroll with {@link #scroll} or {@link #mouseScrolled} (register the
 * list with {@code SecurityScreen.addScrollRegion}). Read-only. CLIENT-ONLY.
 *
 * <p>{@link #event} builds the shared sentence so the hub and the monitor read identically:
 * {@code monitor.nerosecurity.row} "%s — %s at %s, %s" → phrase, actor kind (never a name), device position,
 * age ({@code time.nerosecurity.ago.*}).
 *
 * <pre>{@code
 * events = addScrollRegion(new SentenceList(leftPos + 10, topPos + 178, 280, 33));
 * events.setEmpty(Component.translatable("monitor.nerosecurity.none"));
 * events.setRows(snap.events().stream().map(e -> SentenceList.event(e, secondsSinceSnapshot)).toList());
 * // in extractPanel():
 * events.extract(g, font);
 * }</pre>
 */
public final class SentenceList implements ScrollRegion {

    public static final int LINE_HEIGHT = 11;
    private static final int MARK = StatusDot.SIZE;
    private static final int MARK_GAP = 5;

    /** One row: severity, the sentence, and an optional dim suffix (e.g. an age). */
    public record Row(SecuritySeverity severity, Component text, @Nullable Component suffix) {
        public Row(SecuritySeverity severity, Component text) {
            this(severity, text, null);
        }
    }

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private List<Row> rows = List.of();
    private @Nullable Component empty;
    /** First visible wrapped line. */
    private int scroll;

    public SentenceList(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * The shared event sentence. {@code extraSeconds} ages the snapshot's {@code agoSeconds} by the time since it
     * arrived, so a long-open screen does not say "just now" for ever.
     */
    public static Row event(AnonymousEvent event, long extraSeconds) {
        Component sentence = Component.translatable("monitor.nerosecurity.row", EventPhrases.phrase(event.type()),
                MonitorText.actor(event.actorKind()), MonitorText.position(event.devicePos()),
                Ages.ago(event.agoSeconds() + Math.max(0L, extraSeconds)));
        return new Row(event.severity(), sentence);
    }

    public void setRows(List<Row> rows) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        this.scroll = Math.min(scroll, Math.max(0, totalLines() - visibleLines()));
    }

    /** What to show when there are no rows (e.g. "No events."). */
    public void setEmpty(@Nullable Component empty) {
        this.empty = empty;
    }

    public List<Row> rows() {
        return rows;
    }

    public int visibleLines() {
        return Math.max(1, height / LINE_HEIGHT);
    }

    private int textWidth() {
        return width - MARK - MARK_GAP;
    }

    private List<List<FormattedCharSequence>> layout(Font font) {
        List<List<FormattedCharSequence>> out = new ArrayList<>(rows.size());
        for (Row row : rows) {
            Component text = row.suffix() == null ? row.text()
                    : row.text().copy().append(" ").append(row.suffix().copy().withColor(SecurityColours.SUBTLE & 0xFFFFFF));
            out.add(WidgetText.wrap(font, text, textWidth()));
        }
        return out;
    }

    private int lastLineCount = -1;

    private int totalLines() {
        return Math.max(0, lastLineCount);
    }

    public boolean canScrollUp() {
        return scroll > 0;
    }

    public boolean canScrollDown() {
        return scroll < totalLines() - visibleLines();
    }

    /** Scrolls by {@code lines} (negative = up). */
    public void scroll(int lines) {
        scroll = Math.max(0, Math.min(scroll + lines, Math.max(0, totalLines() - visibleLines())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scroll(scrollY > 0 ? -1 : 1);
        return true;
    }

    /** Draws the visible lines. */
    public void extract(GuiGraphicsExtractor g, Font font) {
        if (rows.isEmpty()) {
            lastLineCount = 0;
            if (empty != null) {
                WidgetText.drawWrapped(g, font, empty, x, y + 1, width, LINE_HEIGHT, SecurityColours.SUBTLE);
            }
            return;
        }
        List<List<FormattedCharSequence>> wrapped = layout(font);
        int total = 0;
        for (List<FormattedCharSequence> lines : wrapped) {
            total += lines.size();
        }
        lastLineCount = total;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - visibleLines())));
        int line = 0;
        int drawn = 0;
        int max = visibleLines();
        for (int r = 0; r < wrapped.size() && drawn < max; r++) {
            List<FormattedCharSequence> lines = wrapped.get(r);
            for (int i = 0; i < lines.size() && drawn < max; i++, line++) {
                if (line < scroll) {
                    continue;
                }
                int ly = y + drawn * LINE_HEIGHT;
                if (i == 0) {
                    SecuritySeverity sev = rows.get(r).severity();
                    StatusDot.drawShape(g, x, ly + 1, WidgetColours.severityShape(sev), WidgetColours.severity(sev));
                }
                g.text(font, lines.get(i), x + MARK + MARK_GAP, ly, WidgetColours.TEXT, false);
                drawn++;
            }
        }
        if (canScrollUp()) {
            g.fill(x + width - 5, y, x + width - 1, y + 1, SecurityColours.SUBTLE);
        }
        if (canScrollDown()) {
            g.fill(x + width - 5, y + height - 1, x + width - 1, y + height, SecurityColours.SUBTLE);
        }
    }
}
