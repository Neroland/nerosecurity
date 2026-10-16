package za.co.neroland.nerosecurity.client.widget;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/**
 * Every text-on-background colour choice the shared widgets make, in one pure table so a plain-JVM test can
 * check each pair against WCAG AA (4.5:1). The rules it encodes:
 * <ul>
 *   <li>A level word is always a <b>filled pill</b>: the level colour as the fill and {@link SecurityColours#inkFor}
 *       (dark {@code INK} for every level) as the text. Level colours are never used as text on the panel:
 *       OWNER red on {@code PANEL} is only about 4.2:1.</li>
 *   <li>Meaning sentences and body text are {@code TITLE} white on the panel.</li>
 *   <li>Footer feedback is {@code OK_TEXT} / {@code DENY_TEXT} / {@code SUBTLE} on the {@code TROUGH} strip, and
 *       always has a shape next to it (filled square / cross) so the colour is not the only signal.</li>
 * </ul>
 * All values are {@code 0xAARRGGBB}. No Minecraft client classes.
 */
public final class WidgetColours {

    /** Body and meaning text on the panel. */
    public static final int TEXT = SecurityColours.TITLE;
    /** Hints, captions, time suffixes on the panel. */
    public static final int HINT = SecurityColours.SUBTLE;
    /** Text of a disabled control. Kept at AA on the disabled body even though WCAG exempts disabled controls. */
    public static final int DISABLED_TEXT = 0xFF8A94A2;
    /** Body of a disabled control. */
    public static final int DISABLED_BODY = 0xFF12161F;
    /** Edge of a disabled control. */
    public static final int DISABLED_EDGE = 0xFF262B33;
    /** Text of an enabled button at rest. */
    public static final int BUTTON_TEXT = 0xFFCFE7FF;
    /** Text of a hovered, focused or selected button. */
    public static final int BUTTON_TEXT_HOT = 0xFFFFFFFF;
    /** Edge of a hovered or focused (non-danger) control. */
    public static final int EDGE_HOT = 0xFF5F8FAD;
    /** Body of a selected (non-level) control such as a segment or tab. */
    public static final int SELECTED_BODY = 0xFF123042;
    /** Danger button body when hovered or focused. */
    public static final int DANGER_HOT = 0xFF561A20;
    /** The dim laid over the whole screen behind a confirm panel. */
    public static final int MODAL_DIM = 0xC0000000;

    private WidgetColours() {
    }

    /** The fill of a level pill or a selected ladder button. */
    public static int pillFill(AccessLevel level) {
        return SecurityColours.opaque(SecurityColours.levelColour(level));
    }

    /** The word on a level pill or a selected ladder button: always dark ink on the fill. */
    public static int pillText(AccessLevel level) {
        return SecurityColours.opaque(SecurityColours.inkFor(SecurityColours.levelColour(level)));
    }

    /** The operator-override pill (white). */
    public static int adminFill() {
        return SecurityColours.opaque(SecurityColours.admin());
    }

    public static int adminText() {
        return SecurityColours.opaque(SecurityColours.inkFor(SecurityColours.admin()));
    }

    /** The 3-px band along the top of an unselected ladder button. Decoration only; the word is {@link #TEXT}. */
    public static int ladderBand(AccessLevel level, boolean enabled) {
        int rgb = SecurityColours.levelColour(level);
        return SecurityColours.opaque(enabled ? rgb : SecurityColours.mix(rgb, DISABLED_BODY & 0xFFFFFF, 0.6f));
    }

    /** Footer text colour by feedback kind. */
    public static int footerText(Feedback.Kind kind) {
        return switch (kind) {
            case OK -> SecurityColours.OK_TEXT;
            case DENY -> SecurityColours.DENY_TEXT;
            case HINT -> SecurityColours.SUBTLE;
        };
    }

    /** The leading mark colour of an event row. */
    public static int severity(SecuritySeverity severity) {
        if (severity == null) {
            return SecurityColours.SUBTLE;
        }
        return switch (severity) {
            case INFO -> SecurityColours.SUBTLE;
            case WARN -> SecurityColours.opaque(SecurityColours.WARN);
            case CRITICAL -> SecurityColours.opaque(SecurityColours.LOCK);
        };
    }

    /** The leading mark shape of an event row; each severity differs in shape as well as colour. */
    public static Status.Shape severityShape(SecuritySeverity severity) {
        if (severity == null) {
            return Status.Shape.OUTLINE;
        }
        return switch (severity) {
            case INFO -> Status.Shape.OUTLINE;
            case WARN -> Status.Shape.FILLED_SQUARE;
            case CRITICAL -> Status.Shape.CROSS;
        };
    }
}
