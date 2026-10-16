package za.co.neroland.nerosecurity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * One source of truth for colour: {@link SecurityColours} reads the level colours from {@link AccessLevel}, the
 * texture generator's {@code PALETTE} dict mirrors both byte-for-byte, and the measured contrast rule holds:
 * dark {@code INK} text on every level fill is at least 4.5:1 (WCAG AA), so every level word is drawn as a filled
 * pill rather than coloured text on the panel.
 */
class SecurityColoursTest {

    private static final Pattern ENTRY = Pattern.compile("^\\s*\"([A-Z_0-9]+)\":\\s*\"#([0-9A-Fa-f]{6})\",?\\s*$");

    @Test
    void levelColoursComeFromAccessLevel() {
        for (AccessLevel level : AccessLevel.values()) {
            assertEquals(level.colour, SecurityColours.levelColour(level), level.name());
        }
        assertEquals(AccessLevel.ADMIN_COLOUR, SecurityColours.admin());
        assertEquals(0xA0A0A0, AccessLevel.PUBLIC.colour);
        assertEquals(0x55D55A, AccessLevel.MEMBER.colour);
        assertEquals(0xE0A526, AccessLevel.OFFICER.colour);
        assertEquals(0xE04545, AccessLevel.OWNER.colour);
        assertEquals(0xFFFFFF, AccessLevel.ADMIN_COLOUR);
    }

    @Test
    void generatorPaletteMirrorsTheJavaConstants() throws IOException {
        Map<String, Integer> py = generatorPalette();
        Map<String, Integer> java = new LinkedHashMap<>();
        java.put("G_DARK", SecurityColours.G_DARK);
        java.put("GRAPHITE", SecurityColours.GRAPHITE);
        java.put("GRAPHITE_2", SecurityColours.GRAPHITE_2);
        java.put("GRAPHITE_3", SecurityColours.GRAPHITE_3);
        java.put("GRAPHITE_4", SecurityColours.GRAPHITE_4);
        java.put("G_LIGHT", SecurityColours.G_LIGHT);
        java.put("TRIM", SecurityColours.TRIM);
        java.put("HAZ_Y", SecurityColours.HAZ_Y);
        java.put("HAZ_K", SecurityColours.HAZ_K);
        java.put("GLASS", SecurityColours.GLASS);
        java.put("INK_CYAN", SecurityColours.INK_CYAN);
        java.put("REC", SecurityColours.REC);
        java.put("LOCK", SecurityColours.LOCK);
        java.put("BEAM", SecurityColours.BEAM);
        java.put("BEAM_GLOW", SecurityColours.BEAM_GLOW);
        java.put("FIELD", SecurityColours.FIELD);
        java.put("FIELD_HEX", SecurityColours.FIELD_HEX);
        java.put("FIELD_LOCK_HEX", SecurityColours.FIELD_LOCK_HEX);
        java.put("ENC", SecurityColours.ENC);
        java.put("L_PUBLIC", AccessLevel.PUBLIC.colour);
        java.put("L_MEMBER", AccessLevel.MEMBER.colour);
        java.put("L_OFFICER", AccessLevel.OFFICER.colour);
        java.put("L_OWNER", AccessLevel.OWNER.colour);
        java.put("L_ADMIN", AccessLevel.ADMIN_COLOUR);
        java.put("OK", SecurityColours.OK);
        java.put("WARN", SecurityColours.WARN);
        java.put("OFF", SecurityColours.OFF);
        java.put("GOLD", SecurityColours.GOLD);
        assertEquals(java.keySet(), py.keySet(), "PALETTE keys");
        for (Map.Entry<String, Integer> e : java.entrySet()) {
            assertEquals(String.format("#%06X", e.getValue()), String.format("#%06X", py.get(e.getKey())), e.getKey());
        }
        // One vocabulary: lockdown red is OWNER red; ok/warn are MEMBER green / OFFICER amber.
        assertEquals(AccessLevel.OWNER.colour, SecurityColours.LOCK);
        assertEquals(AccessLevel.MEMBER.colour, SecurityColours.OK);
        assertEquals(AccessLevel.OFFICER.colour, SecurityColours.WARN);
    }

    @Test
    void inkOnEveryLevelFillPassesAa() {
        for (AccessLevel level : AccessLevel.values()) {
            double ratio = SecurityColours.contrast(SecurityColours.INK, level.colour);
            assertTrue(ratio >= 4.5, level + " INK-on-fill contrast " + ratio);
            assertEquals(SecurityColours.INK, SecurityColours.inkFor(level.colour), level.name());
        }
        assertTrue(SecurityColours.contrast(SecurityColours.INK, AccessLevel.ADMIN_COLOUR) >= 4.5);
        assertEquals(SecurityColours.INK, SecurityColours.inkFor(AccessLevel.ADMIN_COLOUR));
        // The measured figures the design records (sRGB WCAG 2.x), to one decimal.
        assertEquals(4.9, round1(SecurityColours.contrast(SecurityColours.INK, AccessLevel.OWNER.colour)), 0.051);
        assertEquals(10.6, round1(SecurityColours.contrast(SecurityColours.INK, AccessLevel.MEMBER.colour)), 0.051);
        // ... and the reason level words are pills: OWNER red as text on the panel fails AA.
        assertTrue(SecurityColours.contrast(AccessLevel.OWNER.colour, SecurityColours.PANEL) < 4.5);
    }

    @Test
    void chromeTextIsReadableAndInkForPicksTheLighterInkOnDarkBackgrounds() {
        assertTrue(SecurityColours.contrast(SecurityColours.TITLE, SecurityColours.PANEL) >= 4.5);
        assertTrue(SecurityColours.contrast(SecurityColours.SUBTLE, SecurityColours.PANEL) >= 4.5);
        assertTrue(SecurityColours.contrast(SecurityColours.DENY_TEXT, SecurityColours.TROUGH) >= 4.5);
        assertTrue(SecurityColours.contrast(SecurityColours.OK_TEXT, SecurityColours.TROUGH) >= 4.5);
        assertEquals(SecurityColours.TITLE, SecurityColours.inkFor(SecurityColours.PANEL));
        assertEquals(SecurityColours.TITLE, SecurityColours.inkFor(SecurityColours.DANGER_BODY));
    }

    @Test
    void argbHelpers() {
        assertEquals(0x80E04545, SecurityColours.argb(SecurityColours.LOCK, 128));
        assertEquals(0xFFE04545, SecurityColours.opaque(SecurityColours.LOCK));
        assertEquals(0x00E04545, SecurityColours.argb(SecurityColours.LOCK, -5));
        assertEquals(0xFFE04545, SecurityColours.argb(SecurityColours.LOCK, 1.0f));
        assertEquals(0x33123456, SecurityColours.withAlpha(0xFF123456, 0x33));
        assertEquals(0x000000, SecurityColours.mix(0x000000, 0xFFFFFF, 0f));
        assertEquals(0xFFFFFF, SecurityColours.mix(0x000000, 0xFFFFFF, 2f));
        assertEquals(0x808080, SecurityColours.mix(0x000000, 0xFFFFFF, 0.5f));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Map<String, Integer> generatorPalette() throws IOException {
        Path script = locate(Path.of("tools", "gen_security_textures.py"));
        Map<String, Integer> out = new LinkedHashMap<>();
        boolean inside = false;
        for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            if (line.startsWith("PALETTE = {")) {
                inside = true;
                continue;
            }
            if (inside && line.startsWith("}")) {
                break;
            }
            if (inside) {
                Matcher m = ENTRY.matcher(line);
                if (m.matches()) {
                    out.put(m.group(1), Integer.parseInt(m.group(2), 16));
                }
            }
        }
        assertTrue(!out.isEmpty(), "PALETTE not found in " + script);
        return out;
    }

    static Path locate(Path relative) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + relative + " from " + System.getProperty("user.dir"));
    }
}
