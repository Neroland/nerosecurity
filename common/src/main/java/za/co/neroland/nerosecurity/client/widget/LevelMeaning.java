package za.co.neroland.nerosecurity.client.widget;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * The "colour + word + one-line meaning" rule in one place: {@code [MEMBER] — can open members-only devices}.
 * The level word is a filled {@link LevelPill}; the meaning ({@code access.nerosecurity.level.<l>.meaning}) is
 * white text after it, wrapped at word boundaries (never cut) when it does not fit. Draw it directly under a
 * {@link LevelLadder} with the ladder's selected level. Read-only. CLIENT-ONLY.
 *
 * <pre>{@code
 * LevelMeaning.draw(g, font, leftPos + 8, topPos + 52, 228, ladder.selected());
 * LevelMeaning.draw(g, font, x, y, 134, youAreLead, snap.viewerLevel(), snap.admin());   // "You are [OFFICER] — …"
 * }</pre>
 */
public final class LevelMeaning {

    /** Vertical distance between wrapped meaning lines. */
    public static final int LINE_HEIGHT = 11;
    private static final String DASH = "— ";

    private LevelMeaning() {
    }

    /** The meaning sentence for a level. */
    public static Component meaning(AccessLevel level) {
        return Component.translatable((level == null ? AccessLevel.PUBLIC : level).meaningKey());
    }

    public static Component adminMeaning() {
        return Component.translatable(AccessLevel.ADMIN_MEANING_KEY);
    }

    /** {@code [LEVEL] — meaning} at ({@code x}, {@code y}) within {@code maxWidth}; returns the height used. */
    public static int draw(GuiGraphicsExtractor g, Font font, int x, int y, int maxWidth, @Nullable AccessLevel level) {
        return draw(g, font, x, y, maxWidth, null, level, false);
    }

    /**
     * {@code lead [LEVEL] — meaning}: an optional white lead text ("You are"), then the pill (ADMIN's white pill
     * when {@code admin}), then the meaning. {@code level == null && !admin} draws nothing. Returns the height
     * used (11 for one line).
     */
    public static int draw(GuiGraphicsExtractor g, Font font, int x, int y, int maxWidth, @Nullable Component lead,
                           @Nullable AccessLevel level, boolean admin) {
        if (level == null && !admin) {
            return 0;
        }
        int cx = x;
        if (lead != null) {
            g.text(font, lead, cx, y + 2, WidgetColours.TEXT, false);
            cx += font.width(lead) + 4;
        }
        cx += LevelPill.drawViewer(g, font, cx, y, level, admin) + 4;
        String text = DASH + (admin ? adminMeaning() : meaning(level)).getString();
        int firstRoom = x + maxWidth - cx;
        List<String> lines = WidgetText.wrapPlain(font, text, Math.max(0, firstRoom), maxWidth);
        int lineY = y + 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && line.isEmpty()) {
                continue; // nothing fitted beside the pill; the sentence starts on the next line
            }
            g.text(font, line, i == 0 ? cx : x, lineY + i * LINE_HEIGHT, WidgetColours.TEXT, false);
        }
        return lines.size() * LINE_HEIGHT;
    }
}
