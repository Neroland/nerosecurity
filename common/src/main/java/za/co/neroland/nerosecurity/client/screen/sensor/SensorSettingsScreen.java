package za.co.neroland.nerosecurity.client.screen.sensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;
import za.co.neroland.nerosecurity.client.widget.Gauge;
import za.co.neroland.nerosecurity.client.widget.LevelLadder;
import za.co.neroland.nerosecurity.client.widget.LevelMeaning;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.Segmented;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads.SensorConfigure;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloads.SensorSnapshot;
import za.co.neroland.nerosecurity.menu.sensor.SensorSettingsMenu;
import za.co.neroland.nerosecurity.sensor.SensorEntityMode;
import za.co.neroland.nerosecurity.sensor.SensorSettings;

/**
 * "Who may cross this beam?" / "Who may pass this camera?" — the shared sensor settings screen on the
 * {@link SecurityScreen} hull: the level ladder with its meaning line, the "Also sense" segmented control, the
 * camera's field-of-view control (hidden for a laser), the power gauge, the one status row (armed in the REC
 * colour, the same notice the LED gives) and the UPGRADES box around the range-module slots. It draws only
 * what the server sent; every control sends a {@link SensorConfigure} the server re-authorises, and the controls
 * are held until the next snapshot. Disabled controls always say why.
 *
 * <p>CLIENT-ONLY.
 */
public class SensorSettingsScreen extends SecurityScreen<SensorSettingsMenu> {

    public static final int W = 248;
    public static final int H = 178;

    private static final int LADDER_Y = 30;
    private static final int MEANING_Y = 52;
    private static final int MODE_LABEL_Y = 76;
    private static final int MODE_Y = 86;
    private static final int FOV_Y = 106;
    private static final int FOV_X = 90;
    private static final int POWER_Y = 126;
    private static final int STATE_Y = 138;
    private static final int LEFT_W = 164;
    private static final int BOX_X0 = 180;
    private static final int BOX_X1 = 240;
    private static final int BOX_Y0 = 126;
    private static final int BOX_Y1 = 154;
    private static final int CONTROL_H = 16;
    private static final int GAUGE_LABEL_W = 36;

    private static final SensorEntityMode[] MODES = SensorEntityMode.values();

    private LevelLadder ladder;
    private Segmented mode;
    private Segmented fov;

    public SensorSettingsScreen(SensorSettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, true, ClientSensorState::version);
        setSubtitle(Component.translatable("sensor.nerosecurity.subtitle"));
        applyTitle(null);
    }

    private static @Nullable SensorSnapshot snapshot() {
        return ClientSensorState.current();
    }

    private boolean isLaser(@Nullable SensorSnapshot snap) {
        return snap != null ? snap.laser() : menu.laser();
    }

    private void applyTitle(@Nullable SensorSnapshot snap) {
        setTitleLine(Component.translatable(SensorText.titleKey(isLaser(snap))));
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int y = top();
        ladder = addGroup(new LevelLadder(x + PAD, y + LADDER_Y, this::setLevel));

        List<Component> modeLabels = new ArrayList<>();
        for (SensorEntityMode m : MODES) {
            modeLabels.add(Component.translatable(m.translationKey()));
        }
        mode = addGroup(new Segmented(x + PAD, y + MODE_Y, W - 2 * PAD, CONTROL_H, modeLabels, this::setMode));
        for (int i = 0; i < MODES.length; i++) {
            // A long phrase is cut with "…" on its segment; the tooltip and the row above carry it in full.
            mode.setHelp(i, modeLabels.get(i));
        }

        List<Component> fovLabels = new ArrayList<>();
        for (int degrees : SensorSettings.FOV_OPTIONS) {
            fovLabels.add(Component.literal(String.format(Locale.ROOT, "%d°", degrees)));
        }
        fov = addGroup(new Segmented(x + FOV_X, y + FOV_Y, W - PAD - FOV_X, CONTROL_H, fovLabels, this::setFov));
    }

    @Override
    protected void refreshWidgets() {
        SensorSnapshot snap = snapshot();
        applyTitle(snap);
        boolean laser = isLaser(snap);
        fov.setVisible(!laser);
        if (snap == null) {
            Component waiting = Component.translatable("ui.nerosecurity.waiting");
            ladder.select(null);
            ladder.disableAll(waiting);
            mode.select(-1);
            mode.disableAll(waiting);
            fov.select(-1);
            fov.disableAll(waiting);
            return;
        }
        SensorSettings settings = snap.settings();
        ladder.select(settings.minLevel());
        mode.select(settings.entityMode().ordinal());
        fov.select(SensorText.indexOf(SensorSettings.FOV_OPTIONS, settings.fovDegrees()));

        // The server reports an operator as OWNER in viewerLevel (SensorPayloadHandlers), so no admin flag here.
        boolean ownerOrAdmin = snap.viewerLevel() == AccessLevel.OWNER;
        String why = SensorText.editWhy(snap.linked(), settings.minLevel(), ownerOrAdmin);
        if (why != null) {
            Component reason = Component.translatable(why);
            ladder.disableAll(reason);
            mode.disableAll(reason);
            fov.disableAll(reason);
            return;
        }
        ladder.enableAll();
        ladder.restrictOwner(snap.viewerLevel(), false, Component.translatable(SensorText.OWNER_ONLY_KEY));
        mode.enableAll();
        fov.enableAll();
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        SensorSnapshot snap = snapshot();
        if (snap != null) {
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
        }
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientSensorState.clear();
        super.removed();
    }

    // --- intents (the server re-resolves every one) ---------------------------------------------

    private void send(SensorSettings wanted) {
        SensorSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new SensorConfigure(snap.pos(), wanted));
        }
    }

    private void setLevel(AccessLevel level) {
        SensorSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withMinLevel(level));
        }
    }

    private void setMode(int index) {
        SensorSnapshot snap = snapshot();
        if (snap != null && index >= 0 && index < MODES.length) {
            send(snap.settings().withEntityMode(MODES[index]));
        }
    }

    private void setFov(int index) {
        SensorSnapshot snap = snapshot();
        if (snap != null && !snap.laser() && index >= 0 && index < SensorSettings.FOV_OPTIONS.length) {
            send(snap.settings().withFov(SensorSettings.FOV_OPTIONS[index]));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        LevelMeaning.draw(g, font, x + PAD, y + MEANING_Y, LevelLadder.WIDTH, ladder.selected());

        SensorSnapshot snap = snapshot();
        boolean laser = isLaser(snap);
        int room = W - 2 * PAD;

        // "Also sense: players and hostile mobs" — the current choice in full above the segments.
        Component modeLine = snap == null
                ? Component.translatable("sensor.nerosecurity.mode.label")
                : Component.translatable("sensor.nerosecurity.mode",
                        Component.translatable(snap.settings().entityMode().translationKey()));
        WidgetText.drawFitted(g, font, modeLine, x + PAD, y + MODE_LABEL_Y, room, WidgetColours.TEXT);
        if (!laser) {
            g.text(font, Component.translatable("sensor.nerosecurity.fov.label"), x + PAD, y + FOV_Y + 4,
                    WidgetColours.TEXT, false);
        }

        long energy = menu.energy();
        long capacity = menu.capacity();
        g.text(font, Component.translatable("field.nerosecurity.power"), x + PAD, y + POWER_Y, WidgetColours.TEXT, false);
        Gauge.draw(g, font, x + PAD + GAUGE_LABEL_W, y + POWER_Y, LEFT_W - GAUGE_LABEL_W,
                capacity <= 0 ? 0f : energy / (float) capacity,
                energy <= 0 ? SecurityColours.WARN : SecurityColours.INK_CYAN,
                Component.literal(String.format(Locale.ROOT, "%,d / %,d", energy, capacity)));

        boolean linked = menu.linked() || (snap != null && snap.linked());
        SensorText.Row state = SensorText.state(linked, menu.armed(), laser, menu.paired(), energy > 0);
        statusRow(g, x + PAD, y + STATE_Y, LEFT_W, state);

        if (menu.moduleSlots() > 0) {
            SectionBox.draw(g, font, x + BOX_X0, y + BOX_Y0, x + BOX_X1, y + BOX_Y1,
                    Component.translatable("door.nerosecurity.section.upgrades"));
        }
    }

    /** A glyph (REC-red filled square while recording) plus the sentence fitted to {@code width}. */
    private void statusRow(GuiGraphicsExtractor g, int x, int y, int width, SensorText.Row row) {
        if (row.recording()) {
            StatusDot.drawShape(g, x, y + 1, Status.Shape.FILLED_SQUARE, SecurityColours.opaque(SecurityColours.REC));
        } else {
            StatusDot.drawGlyph(g, x, y + 1, row.status());
        }
        Component text = Component.translatable(row.key());
        int tx = x + StatusDot.SIZE + 4;
        int room = width - (StatusDot.SIZE + 4);
        WidgetText.drawFitted(g, font, text, tx, y, room, WidgetColours.TEXT);
        if (WidgetText.overflows(font, text, room)) {
            explain(g, x, y, width, 9, text);
        }
    }
}
