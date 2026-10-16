package za.co.neroland.nerosecurity.client.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;

/**
 * The widget colour table meets WCAG AA (4.5:1) for every text-on-background pair the shared widgets draw, and
 * colour is never the only signal. Plain JVM: only the pure {@link WidgetColours}, {@link Status} and
 * {@link SecurityColours} tables are touched.
 */
class WidgetContrastTest {

    private static final double AA = 4.5;

    private static void assertAa(int text, int background, String what) {
        double ratio = SecurityColours.contrast(text, background);
        assertTrue(ratio >= AA, what + " contrast " + String.format("%.2f", ratio) + ":1 is below 4.5:1");
    }

    @Test
    void levelWordsAreInkOnTheirFill() {
        for (AccessLevel level : AccessLevel.values()) {
            assertEquals(SecurityColours.INK, WidgetColours.pillText(level), level + " pill text must be INK");
            assertAa(WidgetColours.pillText(level), WidgetColours.pillFill(level), level + " pill");
        }
        assertAa(WidgetColours.adminText(), WidgetColours.adminFill(), "ADMIN pill");
    }

    @Test
    void ownerRedAsTextOnThePanelFailsWhichIsWhyLevelsArePills() {
        // The reason the pill rule exists: OWNER red text on the panel is below AA.
        double ownerOnPanel = SecurityColours.contrast(SecurityColours.opaque(AccessLevel.OWNER.colour), SecurityColours.PANEL);
        assertTrue(ownerOnPanel < AA, "expected OWNER-on-PANEL below AA, got " + ownerOnPanel);
        // ...and no widget text colour is a level colour.
        for (AccessLevel level : AccessLevel.values()) {
            int levelArgb = SecurityColours.opaque(level.colour);
            for (int text : List.of(WidgetColours.TEXT, WidgetColours.HINT, WidgetColours.BUTTON_TEXT,
                    WidgetColours.BUTTON_TEXT_HOT, WidgetColours.DISABLED_TEXT)) {
                assertFalse(text == levelArgb, "a level colour is used as panel text: " + level);
            }
        }
    }

    @Test
    void panelTextMeetsAa() {
        for (int bg : List.of(SecurityColours.PANEL, SecurityColours.TROUGH, SecurityColours.BUTTON_BODY,
                SecurityColours.BUTTON_HOVER)) {
            assertAa(WidgetColours.TEXT, bg, "meaning/body text on " + Integer.toHexString(bg));
        }
        assertAa(WidgetColours.HINT, SecurityColours.PANEL, "subtitle/hint on panel");
        assertAa(WidgetColours.HINT, SecurityColours.TROUGH, "hint in footer");
        assertAa(SecurityColours.TITLE, SecurityColours.PANEL, "title");
    }

    @Test
    void buttonTextMeetsAaInEveryState() {
        assertAa(WidgetColours.BUTTON_TEXT, SecurityColours.BUTTON_BODY, "button");
        assertAa(WidgetColours.BUTTON_TEXT_HOT, SecurityColours.BUTTON_HOVER, "hovered button");
        assertAa(WidgetColours.BUTTON_TEXT_HOT, WidgetColours.SELECTED_BODY, "selected segment");
        assertAa(WidgetColours.BUTTON_TEXT, SecurityColours.DANGER_BODY, "danger button");
        assertAa(WidgetColours.BUTTON_TEXT_HOT, WidgetColours.DANGER_HOT, "hovered danger button");
        assertAa(WidgetColours.DISABLED_TEXT, WidgetColours.DISABLED_BODY, "disabled control");
        assertAa(SecurityColours.TITLE, SecurityColours.PANEL, "confirm panel title");
        assertAa(SecurityColours.SUBTLE, SecurityColours.PANEL, "inactive tab");
    }

    @Test
    void footerFeedbackMeetsAaAndHasAShape() {
        for (Feedback.Kind kind : Feedback.Kind.values()) {
            assertAa(WidgetColours.footerText(kind), SecurityColours.TROUGH, "footer " + kind);
        }
        assertAa(SecurityColours.DENY_TEXT, SecurityColours.TROUGH, "deny footer");
        assertAa(SecurityColours.OK_TEXT, SecurityColours.TROUGH, "ok footer");
    }

    @Test
    void statusStatesDifferByShapeNotOnlyColour() {
        Set<Status.Shape> shapes = EnumSet.noneOf(Status.Shape.class);
        for (Status status : Status.values()) {
            assertTrue(shapes.add(status.shape()), status + " reuses a shape");
        }
        assertEquals(Status.values().length, shapes.size());
        // ok-green and owner-red never differ by hue alone: their shapes differ.
        assertFalse(Status.OK.shape() == Status.BAD.shape());
    }

    @Test
    void everyDeviceStateHasAStatus() {
        for (DeviceState state : DeviceState.values()) {
            assertNotNull(Status.of(state), state.name());
        }
        assertEquals(Status.OK, Status.of(DeviceState.OK));
        assertEquals(Status.WARN, Status.of(DeviceState.UNPOWERED));
        assertEquals(Status.OFF, Status.of(DeviceState.IDLE));
        assertEquals(Status.OFF, Status.of(DeviceState.UNLOADED));
        assertEquals(Status.BAD, Status.of(DeviceState.OVERLOADED));
    }

    @Test
    void severityMarksDifferByShape() {
        Set<Status.Shape> shapes = EnumSet.noneOf(Status.Shape.class);
        for (SecuritySeverity severity : SecuritySeverity.values()) {
            assertTrue(shapes.add(WidgetColours.severityShape(severity)), severity + " reuses a mark");
        }
    }

    @Test
    void widgetsAndHullUseNoGuiTextures() throws IOException {
        Path root = sources();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root.resolve("widget"))) {
            files = new java.util.ArrayList<>(walk.filter(p -> p.toString().endsWith(".java")).toList());
        }
        files.add(root.resolve("screen").resolve("SecurityScreen.java"));
        assertTrue(files.size() > 10, "expected the widget sources");
        for (Path file : files) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(src.contains("blit"), file.getFileName() + " blits a texture");
            assertFalse(src.contains("textures/gui"), file.getFileName() + " references a GUI texture");
        }
    }

    private static Path sources() {
        Path relative = Path.of("common", "src", "main", "java", "za", "co", "neroland", "nerosecurity", "client");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate.resolve("widget"))) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + relative);
    }
}
