package za.co.neroland.nerosecurity.client;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * The NeroSecurity palette in one place: world signal colours, GUI chrome and the level colour code.
 *
 * <p>Two conventions, chosen by use:
 * <ul>
 *   <li><b>World / texture palette</b> constants are {@code 0xRRGGBB} (no alpha). Renderers pass them through
 *       {@link #argb(int, int)} when they need an alpha. The texture generator's {@code PALETTE} dict mirrors these
 *       byte-for-byte.</li>
 *   <li><b>GUI chrome</b> constants are {@code 0xAARRGGBB} and opaque unless noted, ready for {@code fill}/{@code text}.</li>
 * </ul>
 *
 * <p>Level colours are never redefined here: {@link #levelColour(AccessLevel)} reads {@link AccessLevel#colour}, and
 * {@link #admin()} reads {@link AccessLevel#ADMIN_COLOUR}. Pure Java (no Minecraft client classes), so it is unit-testable.
 */
public final class SecurityColours {

    // ---- world / texture palette (0xRRGGBB) ----
    public static final int G_DARK = 0x1A1D22;
    public static final int GRAPHITE = 0x2C3137;
    public static final int GRAPHITE_2 = 0x33393F;
    public static final int GRAPHITE_3 = 0x262B30;
    public static final int GRAPHITE_4 = 0x3A4047;
    public static final int G_LIGHT = 0x8E98A4;
    public static final int TRIM = 0x5B6670;
    public static final int HAZ_Y = 0xFFCE2C;
    public static final int HAZ_K = 0x18181E;
    public static final int GLASS = 0x0B1119;
    public static final int INK_CYAN = 0x7FD7FF;
    /** Recording red: recording LEDs, camera lens ring, armed laser lens. */
    public static final int REC = 0xFF2020;
    /** Lockdown red (= OWNER): strobes, sealed clamps, lockdown field, deny flash. */
    public static final int LOCK = 0xE04545;
    public static final int BEAM = 0xFF3B3B;
    public static final int BEAM_GLOW = 0xFF8A8A;
    public static final int FIELD = 0x3FA9F5;
    public static final int FIELD_HEX = 0x9ADCFF;
    public static final int FIELD_LOCK_HEX = 0xFF9A9A;
    public static final int ENC = 0xB07CFF;
    public static final int OK = 0x55D55A;
    public static final int WARN = 0xE0A526;
    public static final int OFF = 0x6A7280;
    /** Dark grey core tint for an IDLE / OVERLOADED emitter. */
    public static final int DORMANT = 0x3A3F46;
    /** Gold chip legs / card chip. */
    public static final int GOLD = 0xD9B24C;

    /** Packed light for emissive quads (sky 15, block 15). */
    public static final int FULL_BRIGHT = 0x00F000F0;

    // ---- GUI chrome (0xAARRGGBB) ----
    public static final int PANEL = 0xFF141C26;
    public static final int EDGE = 0xFF2A3A4D;
    public static final int TROUGH = 0xFF0B1119;
    public static final int INK = 0xFF05080D;
    public static final int TITLE = 0xFFD6ECFF;
    public static final int SUBTLE = 0xFF8DA0B4;
    /** Translucent selection tint. */
    public static final int SELECTED = 0x553A6EA5;
    public static final int BUTTON_BODY = 0xFF0C1E2B;
    public static final int BUTTON_HOVER = 0xFF16344A;
    public static final int BUTTON_EDGE = 0xFF2E4A5A;
    public static final int DANGER_BODY = 0xFF3A1216;
    public static final int DANGER_EDGE = 0xFFE04545;
    public static final int DENY_TEXT = 0xFFFF7A7A;
    public static final int OK_TEXT = 0xFF7CE08A;

    private SecurityColours() {
    }

    /** The level's colour, {@code 0xRRGGBB} (straight from {@link AccessLevel#colour}). */
    public static int levelColour(AccessLevel level) {
        return level == null ? AccessLevel.PUBLIC.colour : level.colour;
    }

    /** The operator-override colour, {@code 0xRRGGBB}. ADMIN is not a level. */
    public static int admin() {
        return AccessLevel.ADMIN_COLOUR;
    }

    /** {@code 0xRRGGBB} plus an alpha 0..255 → {@code 0xAARRGGBB}. */
    public static int argb(int rgb, int alpha) {
        return (clampByte(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    /** {@code 0xRRGGBB} plus an alpha 0..1 → {@code 0xAARRGGBB}. */
    public static int argb(int rgb, float alpha) {
        return argb(rgb, Math.round(alpha * 255f));
    }

    /** Replaces the alpha of an {@code 0xAARRGGBB} colour. */
    public static int withAlpha(int argb, int alpha) {
        return argb(argb, alpha);
    }

    /** Opaque {@code 0xFFRRGGBB} from an {@code 0xRRGGBB} colour. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Red/green/blue channels of a packed colour as 0..1 floats (alpha ignored). */
    public static float red(int colour) {
        return ((colour >> 16) & 0xFF) / 255f;
    }

    public static float green(int colour) {
        return ((colour >> 8) & 0xFF) / 255f;
    }

    public static float blue(int colour) {
        return (colour & 0xFF) / 255f;
    }

    /** Linear blend of two {@code 0xRRGGBB} colours, {@code t} clamped to 0..1. */
    public static int mix(int a, int b, float t) {
        float k = t < 0f ? 0f : (t > 1f ? 1f : t);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * The text colour to draw on background {@code bg} (RGB or ARGB; alpha ignored): {@link #INK} or {@link #TITLE},
     * whichever has the higher WCAG contrast. Every level fill returns {@code INK}.
     */
    public static int inkFor(int bg) {
        return contrast(INK, bg) >= contrast(TITLE, bg) ? INK : TITLE;
    }

    /** WCAG 2.x relative luminance of a packed sRGB colour (alpha ignored). */
    public static double luminance(int colour) {
        return 0.2126 * linear((colour >> 16) & 0xFF)
                + 0.7152 * linear((colour >> 8) & 0xFF)
                + 0.0722 * linear(colour & 0xFF);
    }

    /** WCAG contrast ratio between two packed colours, 1..21. */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
