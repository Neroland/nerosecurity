package za.co.neroland.nerosecurity.client.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text helpers shared by the widgets: fit a component to a width (cut with an ellipsis, used only for one-line
 * labels whose full text is also available as a tooltip) and wrap text into lines (used for every sentence, so
 * a sentence is never cut mid-word). CLIENT-ONLY.
 */
public final class WidgetText {

    private static final String ELLIPSIS = "…";

    private WidgetText() {
    }

    /** {@code true} when {@code text} does not fit in {@code width} pixels. */
    public static boolean overflows(Font font, Component text, int width) {
        return font.width(text) > width;
    }

    /** {@code text} as one line no wider than {@code width}; ends in "…" when it had to be cut. */
    public static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        int room = Math.max(0, width - font.width(ELLIPSIS));
        FormattedText cut = font.substrByWidth(text, room);
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS)));
    }

    /** {@code text} wrapped to {@code width}; at least one line. Keeps the component's styles. */
    public static List<FormattedCharSequence> wrap(Font font, Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(8, width));
        return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
    }

    /**
     * Greedy word wrap of a plain string: the first line gets {@code firstWidth} (it may be empty when not even
     * one word fits), later lines {@code restWidth}. A word wider than a whole line goes on a line of its own.
     */
    public static List<String> wrapPlain(Font font, String text, int firstWidth, int restWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int room = firstWidth;
        for (String word : text.trim().split(" +")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= room) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.isEmpty() && lines.isEmpty() && font.width(word) > room) {
                lines.add(""); // nothing fits on the short first line
                room = restWidth;
                if (font.width(word) <= room) {
                    line.append(word);
                    continue;
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                room = restWidth;
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    /** Draws {@code text} wrapped to {@code width}, {@code lineHeight} apart; returns the height used. */
    public static int drawWrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width,
                                  int lineHeight, int colour) {
        List<FormattedCharSequence> lines = wrap(font, text, width);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x, y + i * lineHeight, colour, false);
        }
        return lines.size() * lineHeight;
    }

    /** Draws {@code text} fitted to one line of {@code width}; returns the drawn width. */
    public static int drawFitted(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int colour) {
        FormattedCharSequence line = fit(font, text, width);
        g.text(font, line, x, y, colour, false);
        return font.width(line);
    }

    /** Draws {@code text} fitted to {@code width} and centred on {@code centreX}. */
    public static void drawCentredFitted(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y,
                                         int width, int colour) {
        FormattedCharSequence line = fit(font, text, width);
        g.text(font, line, centreX - font.width(line) / 2, y, colour, false);
    }
}
