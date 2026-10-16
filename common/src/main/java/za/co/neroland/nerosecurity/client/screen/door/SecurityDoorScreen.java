package za.co.neroland.nerosecurity.client.screen.door;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.door.RedstoneMode;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.SecurityScreen;
import za.co.neroland.nerosecurity.client.widget.Gauge;
import za.co.neroland.nerosecurity.client.widget.LevelLadder;
import za.co.neroland.nerosecurity.client.widget.LevelMeaning;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.Toggle;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorConfigure;
import za.co.neroland.nerosecurity.menu.door.DoorPayloads.DoorSnapshot;
import za.co.neroland.nerosecurity.menu.door.SecurityDoorMenu;

/**
 * "Who can open this door?" — the security door and blast door settings screen on the {@link SecurityScreen}
 * hull: the level ladder with its meaning line, the keycard-only toggle, the redstone toggle (only when the
 * server allows door redstone), the power gauge, the link and condition rows, and the UPGRADES box around the
 * two module slots. It draws only what the server sent: every control sends a {@link DoorConfigure} the server
 * re-authorises, and the controls are held until the next snapshot. Disabled controls always say why.
 *
 * <p>CLIENT-ONLY.
 */
public class SecurityDoorScreen extends SecurityScreen<SecurityDoorMenu> {

    public static final int W = 248;
    public static final int H = 168;

    private static final int LADDER_Y = 30;
    private static final int MEANING_Y = 52;
    private static final int KEYCARD_Y = 76;
    private static final int REDSTONE_Y = 92;
    private static final int POWER_Y = 108;
    private static final int LINK_Y = 120;
    private static final int CONDITION_Y = 132;
    /** Width of the left column (gauge and status rows) beside the UPGRADES box. */
    private static final int LEFT_W = 164;
    private static final int BOX_X0 = 180;
    private static final int BOX_X1 = 240;
    private static final int BOX_Y0 = 116;
    private static final int BOX_Y1 = 148;
    private static final int GAUGE_LABEL_W = 36;

    private LevelLadder ladder;
    private Toggle keycardOnly;
    private Toggle redstone;

    public SecurityDoorScreen(SecurityDoorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, true, ClientDoorState::version);
        setTitleLine(Component.translatable("door.nerosecurity.title"));
        setSubtitle(Component.translatable("door.nerosecurity.subtitle"));
    }

    private static @Nullable DoorSnapshot snapshot() {
        return ClientDoorState.current();
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int y = top();
        ladder = addGroup(new LevelLadder(x + PAD, y + LADDER_Y, this::setLevel));
        keycardOnly = addRenderableWidget(new Toggle(x + PAD, y + KEYCARD_Y,
                Component.translatable("door.nerosecurity.toggle.keycard_only"), this::setKeycardOnly));
        keycardOnly.setHelp(Component.translatable("door.nerosecurity.toggle.keycard_only.hint"));
        redstone = addRenderableWidget(new Toggle(x + PAD, y + REDSTONE_Y,
                Component.translatable("door.nerosecurity.redstone.toggle"), this::setRedstone));
        // Redstone is location-blind: say so where the owner turns it on.
        redstone.setHelp(Component.translatable("door.nerosecurity.redstone.warning"));
    }

    @Override
    protected void refreshWidgets() {
        DoorSnapshot snap = snapshot();
        if (snap == null) {
            Component waiting = Component.translatable("ui.nerosecurity.waiting");
            ladder.select(null);
            ladder.disableAll(waiting);
            keycardOnly.disable(waiting);
            redstone.disable(waiting);
            redstone.visible = false;
            return;
        }
        ladder.select(snap.minLevel());
        keycardOnly.setChecked(snap.keycardOnly());
        redstone.setChecked(snap.mode() != RedstoneMode.IGNORE);
        // Hidden, not disabled: on a server that forbids door redstone the setting does not exist.
        redstone.visible = snap.redstoneAllowed();

        // The server reports an operator as OWNER in viewerLevel (DoorPayloadHandlers), so no admin flag here.
        boolean ownerOrAdmin = snap.viewerLevel() == AccessLevel.OWNER;
        String why = DoorText.editWhy(snap.linked(), snap.minLevel(), ownerOrAdmin);
        if (why != null) {
            Component reason = Component.translatable(why);
            ladder.disableAll(reason);
            keycardOnly.disable(reason);
            redstone.disable(reason);
            return;
        }
        ladder.enableAll();
        ladder.restrictOwner(snap.viewerLevel(), false, Component.translatable(DoorText.OWNER_ONLY_KEY));
        keycardOnly.enable();
        redstone.enable();
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
        }
    }

    /** The snapshot is display-only and is dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientDoorState.clear();
        super.removed();
    }

    // --- intents (the server re-resolves every one) ---------------------------------------------

    private void send(AccessLevel level, boolean keycardOnlyWanted, RedstoneMode mode) {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new DoorConfigure(snap.pos(), level, keycardOnlyWanted, mode));
        }
    }

    private void setLevel(AccessLevel level) {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            send(level, snap.keycardOnly(), snap.mode());
        }
    }

    private void setKeycardOnly(boolean on) {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.minLevel(), on, snap.mode());
        }
    }

    private void setRedstone(boolean on) {
        DoorSnapshot snap = snapshot();
        if (snap != null) {
            send(snap.minLevel(), snap.keycardOnly(), on ? RedstoneMode.OPEN_WHILE_POWERED : RedstoneMode.IGNORE);
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        LevelMeaning.draw(g, font, x + PAD, y + MEANING_Y, LevelLadder.WIDTH, ladder.selected());

        // Live values come from the menu's data slots; the snapshot carries the name and the rules.
        DoorSnapshot snap = snapshot();
        long energy = menu.energy();
        long capacity = menu.capacity();
        boolean unpowered = menu.unpowered();
        boolean linked = menu.linked() || (snap != null && snap.linked());
        boolean lockdown = menu.lockdown();

        g.text(font, Component.translatable("field.nerosecurity.power"), x + PAD, y + POWER_Y, WidgetColours.TEXT, false);
        Gauge.draw(g, font, x + PAD + GAUGE_LABEL_W, y + POWER_Y, LEFT_W - GAUGE_LABEL_W,
                capacity <= 0 ? 0f : energy / (float) capacity,
                unpowered ? SecurityColours.WARN : SecurityColours.INK_CYAN, energyLabel(energy, capacity));

        DoorText.Row link = DoorText.link(linked);
        Component linkText = linked
                ? Component.translatable(link.key(), snap == null ? "" : snap.installationName())
                : Component.translatable(link.key());
        statusRow(g, x + PAD, y + LINK_Y, LEFT_W, link.status(), linkText);

        DoorText.Row condition = DoorText.condition(lockdown, unpowered);
        if (condition != null) {
            statusRow(g, x + PAD, y + CONDITION_Y, LEFT_W, condition.status(), Component.translatable(condition.key()));
        }

        SectionBox.draw(g, font, x + BOX_X0, y + BOX_Y0, x + BOX_X1, y + BOX_Y1,
                Component.translatable("door.nerosecurity.section.upgrades"));
    }

    /** A glyph plus a sentence fitted to {@code width}; the whole sentence is the tooltip when it was cut. */
    private void statusRow(GuiGraphicsExtractor g, int x, int y, int width, Status status, Component text) {
        StatusDot.drawGlyph(g, x, y + 1, status);
        int tx = x + StatusDot.SIZE + 4;
        int room = width - (StatusDot.SIZE + 4);
        WidgetText.drawFitted(g, font, text, tx, y, room, WidgetColours.TEXT);
        if (WidgetText.overflows(font, text, room)) {
            explain(g, x, y, width, 9, text);
        }
    }

    /** "14,000 / 20,000" — the exact numbers, grouped so they read at a glance. */
    static Component energyLabel(long energy, long capacity) {
        return Component.literal(String.format(Locale.ROOT, "%,d / %,d", energy, capacity));
    }
}
