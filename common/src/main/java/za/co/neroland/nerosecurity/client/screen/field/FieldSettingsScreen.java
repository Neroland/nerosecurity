package za.co.neroland.nerosecurity.client.screen.field;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;
import za.co.neroland.nerosecurity.client.screen.field.FieldText.Dim;
import za.co.neroland.nerosecurity.client.widget.Gauge;
import za.co.neroland.nerosecurity.client.widget.LevelLadder;
import za.co.neroland.nerosecurity.client.widget.LevelMeaning;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.Segmented;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.Stepper;
import za.co.neroland.nerosecurity.client.widget.Toggle;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldShape;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.menu.field.FieldMenu;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldConfigure;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldSnapshot;

/**
 * "Who can walk through this field?" — the force field emitter screen on the {@link SecurityScreen} hull: the
 * level ladder with its meaning line, the Field on / Keycard only toggles, the wall / shell shape control, the
 * distance, width, height and depth steppers (clamped to the emitter's range and the area cap, sent once after
 * ten quiet ticks), the power and load gauges, the status row, an amber validation line and the UPGRADES box
 * around the four module slots. It draws only what the server sent; every control sends a {@link FieldConfigure}
 * the server re-validates and re-authorises, and the controls are held until the next snapshot. Disabled
 * controls always say why.
 *
 * <p>CLIENT-ONLY.
 */
public class FieldSettingsScreen extends SecurityScreen<FieldMenu> {

    public static final int W = 276;
    public static final int H = 214;

    private static final int LADDER_Y = 30;
    private static final int MEANING_Y = 52;
    private static final int TOGGLES_Y = 76;
    private static final int KEYCARD_X = 84;
    private static final int SHAPE_Y = 92;
    private static final int SHAPE_X = 48;
    private static final int SHAPE_W = 100;
    private static final int STEPPERS_Y = 110;
    private static final int STEPPER_ROW_H = 18;
    private static final int STEPPER_X0 = 62;
    private static final int LABEL_X1 = 134;
    private static final int STEPPER_X1 = 176;
    private static final int GAUGES_Y = 148;
    private static final int POWER_LABEL_W = 36;
    private static final int POWER_W = 128;
    private static final int LOAD_X = 150;
    private static final int LOAD_LABEL_W = 30;
    private static final int STATE_Y = 160;
    private static final int INVALID_Y = 172;
    private static final int BOX_X0 = 184;
    private static final int BOX_X1 = 268;
    private static final int BOX_Y0 = 76;
    private static final int BOX_Y1 = 106;
    private static final int CONTROL_H = 16;
    private static final int LINE = 10;

    private LevelLadder ladder;
    private Toggle enabled;
    private Toggle keycardOnly;
    private Segmented shape;
    private final Map<Dim, Stepper> steppers = new EnumMap<>(Dim.class);

    public FieldSettingsScreen(FieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, true, ClientFieldState::version);
        setTitleLine(Component.translatable("field.nerosecurity.title"));
        setSubtitle(Component.translatable("field.nerosecurity.subtitle"));
    }

    private static @Nullable FieldSnapshot snapshot() {
        return ClientFieldState.current();
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int y = top();
        ladder = addGroup(new LevelLadder(x + PAD, y + LADDER_Y, this::setLevel));
        enabled = addRenderableWidget(new Toggle(x + PAD, y + TOGGLES_Y,
                Component.translatable("field.nerosecurity.enabled"), this::setEnabled));
        keycardOnly = addRenderableWidget(new Toggle(x + KEYCARD_X, y + TOGGLES_Y,
                Component.translatable("door.nerosecurity.toggle.keycard_only"), this::setKeycardOnly));
        keycardOnly.setHelp(Component.translatable("door.nerosecurity.toggle.keycard_only.hint"));
        shape = addGroup(new Segmented(x + SHAPE_X, y + SHAPE_Y, SHAPE_W, CONTROL_H, List.of(
                Component.translatable("field.nerosecurity.shape.plane"),
                Component.translatable("field.nerosecurity.shape.box")), this::setShape));
        steppers.clear();
        for (Dim dim : Dim.values()) {
            int column = dim.ordinal() % 2;
            int row = dim.ordinal() / 2;
            Stepper stepper = new Stepper(x + (column == 0 ? STEPPER_X0 : STEPPER_X1),
                    y + STEPPERS_Y + row * STEPPER_ROW_H, 1, 1, v -> stepped(dim, v));
            steppers.put(dim, addRenderableWidget(stepper));
        }
    }

    @Override
    protected void refreshWidgets() {
        FieldSnapshot snap = snapshot();
        if (snap == null) {
            Component waiting = Component.translatable("ui.nerosecurity.waiting");
            ladder.select(null);
            ladder.disableAll(waiting);
            enabled.disable(waiting);
            keycardOnly.disable(waiting);
            shape.select(-1);
            shape.disableAll(waiting);
            steppers.values().forEach(s -> s.disable(waiting));
            return;
        }
        FieldSettings settings = snap.settings();
        ladder.select(settings.access().minLevel());
        enabled.setChecked(settings.enabled());
        keycardOnly.setChecked(settings.access().keycardOnly());
        shape.select(settings.shape().ordinal());
        for (Dim dim : Dim.values()) {
            Stepper stepper = steppers.get(dim);
            stepper.visible = dim != Dim.DEPTH || settings.shape() == FieldShape.BOX;
            stepper.setRange(FieldText.min(dim, settings.shape()),
                    FieldText.max(settings, dim, snap.range(), snap.maxArea()));
            stepper.setValue(FieldText.value(settings, dim));
        }

        boolean ownerOrAdmin = snap.admin() || snap.viewerLevel() == AccessLevel.OWNER;
        String why = FieldText.editWhy(snap.serverEnabled(), menu.linked(), settings.access().minLevel(), ownerOrAdmin);
        if (why != null) {
            Component reason = Component.translatable(why);
            ladder.disableAll(reason);
            enabled.disable(reason);
            keycardOnly.disable(reason);
            shape.disableAll(reason);
            steppers.values().forEach(s -> s.disable(reason));
            return;
        }
        ladder.enableAll();
        ladder.restrictOwner(snap.viewerLevel(), snap.admin(), Component.translatable(FieldText.OWNER_ONLY_KEY));
        enabled.enable();
        keycardOnly.enable();
        steppers.values().forEach(Stepper::enable);
        // The other shape is offered only if it would fit; otherwise its segment says which rule it breaks.
        shape.enableAll();
        FieldShape other = settings.shape() == FieldShape.PLANE ? FieldShape.BOX : FieldShape.PLANE;
        Optional<String> broken = FieldGeometry.validate(reshaped(settings, other), snap.range(), snap.maxArea());
        broken.ifPresent(key -> shape.disable(other.ordinal(), invalidText(key, snap)));
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        // FieldSnapshot carries no (ok, message) tail yet; an answered intent reads "Saved.".
        feedbackFromSnapshot(true, null, answeredIntent);
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientFieldState.clear();
        super.removed();
    }

    // --- settings arithmetic --------------------------------------------------------------------

    private static FieldSettings reshaped(FieldSettings s, FieldShape wanted) {
        if (wanted == FieldShape.BOX) {
            return s.withShape(FieldShape.BOX).withSize(s.width(), s.height(), Math.max(3, s.depth()));
        }
        return s.withShape(FieldShape.PLANE).withSize(s.width(), s.height(), 1);
    }

    /** The snapshot's settings with every stepper's current (possibly still pending) value applied. */
    private FieldSettings candidate(FieldSnapshot snap) {
        FieldSettings s = snap.settings();
        for (Dim dim : Dim.values()) {
            Stepper stepper = steppers.get(dim);
            if (stepper != null && stepper.visible) {
                s = FieldText.with(s, dim, FieldText.snap(dim, stepper.value(), FieldText.value(snap.settings(), dim)));
            }
        }
        return s;
    }

    private Component invalidText(String key, FieldSnapshot snap) {
        Integer argument = FieldText.invalidArgument(key, snap.range(), snap.maxArea());
        return argument == null ? Component.translatable(key) : Component.translatable(key, argument);
    }

    // --- intents (the server re-validates and re-resolves every one) ----------------------------

    private void send(FieldSettings wanted) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new FieldConfigure(snap.pos(), wanted));
        }
    }

    private void setLevel(AccessLevel level) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withAccess(snap.settings().access().withMinLevel(level)));
        }
    }

    private void setEnabled(boolean on) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withEnabled(on));
        }
    }

    private void setKeycardOnly(boolean on) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.settings().withAccess(snap.settings().access().withKeycardOnly(on)));
        }
    }

    private void setShape(int index) {
        FieldSnapshot snap = snapshot();
        FieldShape[] shapes = FieldShape.values();
        if (snap != null && index >= 0 && index < shapes.length) {
            send(reshaped(snap.settings(), shapes[index]));
        }
    }

    /** A stepper settled (ten quiet ticks): send every dimension as the steppers now show it. */
    private void stepped(Dim dim, int value) {
        FieldSnapshot snap = snapshot();
        if (snap != null) {
            send(candidate(snap));
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        LevelMeaning.draw(g, font, x + PAD, y + MEANING_Y, LevelLadder.WIDTH, ladder.selected());
        FieldSnapshot snap = snapshot();
        FieldSettings settings = snap == null ? FieldSettings.DEFAULT : snap.settings();

        // Labels take the existing "Shape: %s" / "Distance: %s" keys with an empty argument: the control after
        // each one shows the value.
        g.text(font, Component.translatable("field.nerosecurity.shape", ""), x + PAD, y + SHAPE_Y + 4, WidgetColours.TEXT, false);
        stepperLabel(g, x + PAD, y + STEPPERS_Y, "field.nerosecurity.offset");
        stepperLabel(g, x + LABEL_X1, y + STEPPERS_Y, "field.nerosecurity.width");
        stepperLabel(g, x + PAD, y + STEPPERS_Y + STEPPER_ROW_H, "field.nerosecurity.height");
        if (settings.shape() == FieldShape.BOX) {
            stepperLabel(g, x + LABEL_X1, y + STEPPERS_Y + STEPPER_ROW_H, "field.nerosecurity.depth");
        }

        long energy = menu.energy();
        long capacity = menu.capacity();
        g.text(font, Component.translatable("field.nerosecurity.power"), x + PAD, y + GAUGES_Y, WidgetColours.TEXT, false);
        Gauge.draw(g, font, x + PAD + POWER_LABEL_W, y + GAUGES_Y, POWER_W - POWER_LABEL_W,
                capacity <= 0 ? 0f : energy / (float) capacity,
                menu.state() == FieldState.BROWNOUT ? SecurityColours.WARN : SecurityColours.INK_CYAN,
                Component.literal(String.format(Locale.ROOT, "%,d / %,d", energy, capacity)));
        int load = menu.loadPercent();
        g.text(font, Component.translatable("field.nerosecurity.load"), x + LOAD_X, y + GAUGES_Y, WidgetColours.TEXT, false);
        Gauge.draw(g, font, x + LOAD_X + LOAD_LABEL_W, y + GAUGES_Y, W - PAD - LOAD_X - LOAD_LABEL_W, load / 100f,
                load >= 100 ? SecurityColours.LOCK : (load >= 80 ? SecurityColours.WARN : SecurityColours.FIELD),
                Component.literal(String.format(Locale.ROOT, "%d %%", load)));

        boolean serverEnabled = snap == null || snap.serverEnabled();
        FieldText.Row row = FieldText.state(serverEnabled, menu.linked(), menu.lockdown(), menu.state());
        Component text;
        if (row.detailed()) {
            int maxStrength = snap == null ? menu.effectiveStrength() : snap.maxStrength();
            text = Component.translatable(FieldText.STATUS_KEY, Component.translatable(row.key()), menu.effectiveStrength(),
                    maxStrength, menu.placed(), menu.area(), menu.drawPerTick(), load);
        } else if (row.cooldown()) {
            text = Component.translatable(row.key(), menu.cooldownSeconds());
        } else {
            text = Component.translatable(row.key());
        }
        int room = W - 2 * PAD;
        StatusDot.drawGlyph(g, x + PAD, y + STATE_Y + 1, row.status());
        int tx = x + PAD + StatusDot.SIZE + 4;
        WidgetText.drawFitted(g, font, text, tx, y + STATE_Y, room - (StatusDot.SIZE + 4), WidgetColours.TEXT);
        if (WidgetText.overflows(font, text, room - (StatusDot.SIZE + 4))) {
            explain(g, x + PAD, y + STATE_Y, room, 9, text);
        }

        if (snap != null) {
            FieldGeometry.validate(candidate(snap), snap.range(), snap.maxArea()).ifPresent(key -> {
                Component warning = invalidText(key, snap);
                StatusDot.drawGlyph(g, x + PAD, y + INVALID_Y + 1, Status.WARN);
                WidgetText.drawWrapped(g, font, warning, tx, y + INVALID_Y, room - (StatusDot.SIZE + 4), LINE,
                        SecurityColours.opaque(SecurityColours.WARN));
            });
        }

        SectionBox.draw(g, font, x + BOX_X0, y + BOX_Y0, x + BOX_X1, y + BOX_Y1,
                Component.translatable("field.nerosecurity.section.upgrades"));
    }

    private void stepperLabel(GuiGraphicsExtractor g, int x, int y, String key) {
        g.text(font, Component.translatable(key, ""), x, y + 4, WidgetColours.TEXT, false);
    }
}
