package za.co.neroland.nerosecurity.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.client.ClientHubState;
import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.screen.HubLayout.Box;
import za.co.neroland.nerosecurity.client.screen.HubRules.LockdownButton;
import za.co.neroland.nerosecurity.client.widget.ConfirmPanel;
import za.co.neroland.nerosecurity.client.widget.LevelLadder;
import za.co.neroland.nerosecurity.client.widget.LevelMeaning;
import za.co.neroland.nerosecurity.client.widget.LevelPill;
import za.co.neroland.nerosecurity.client.widget.ScrollRegion;
import za.co.neroland.nerosecurity.client.widget.SectionBox;
import za.co.neroland.nerosecurity.client.widget.SecurityButton;
import za.co.neroland.nerosecurity.client.widget.SecurityWidget;
import za.co.neroland.nerosecurity.client.widget.SentenceList;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.client.widget.StatusDot;
import za.co.neroland.nerosecurity.client.widget.TabBar;
import za.co.neroland.nerosecurity.client.widget.WidgetColours;
import za.co.neroland.nerosecurity.client.widget.WidgetText;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;
import za.co.neroland.nerosecurity.menu.HubPayloads.DeviceRow;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubAddMember;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubLockdown;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubRename;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSetMember;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSnapshot;
import za.co.neroland.nerosecurity.menu.HubPayloads.MemberRow;
import za.co.neroland.nerosecurity.menu.SecurityHubMenu;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacySnapshot;

/**
 * The Security Hub dashboard: two tabs on the {@link SecurityScreen} hull.
 *
 * <p><b>Overview</b> answers, without scrolling: is the base locked down (STATUS + the big LOCKDOWN button),
 * who is in (MEMBERS with add / promote / demote / remove), what is linked and is it working (DEVICES with a
 * status mark per row) and what happened last (LAST 5 EVENTS, the monitor's own sentence). The installation
 * name is the title; an owner renames it inline with the pencil.
 *
 * <p><b>Privacy</b> ({@link HubPrivacyTab}) says what this installation records and gives the viewer their
 * export / opt-out / erase buttons.
 *
 * <p>Everything drawn comes from the server's last {@link HubSnapshot} / {@link PrivacySnapshot}. Every
 * button sends an intent the server re-authorises; the hull holds the intent widgets until the answer. Greyed
 * controls are a courtesy that names the reason in the server's own words; they never decide anything.
 * Lockdown on, off and keep, member removal and erase each go through a {@link ConfirmPanel}. No player
 * identifier is ever logged by this class.
 *
 * <p>CLIENT-ONLY.
 */
public class SecurityHubScreen extends SecurityScreen<SecurityHubMenu> {

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_PRIVACY = 1;
    private static final int TICKS_PER_SECOND = 20;
    private static final String ERASE_COMMAND = "/neroland data eraseme";
    private static final Component SELECT_FIRST = Component.translatable("hub.nerosecurity.member.select_first");
    private static final Component LOWEST = Component.translatable("hub.nerosecurity.member.already_lowest");
    private static final Component OWNER_NOT_HERE = Component.translatable("hub.nerosecurity.member.owner_not_here");
    private static final Component IS_OWNER = Component.translatable("hub.nerosecurity.member.is_owner");
    private static final Component SELF = Component.translatable("hub.nerosecurity.member.self");
    private static final Component RANK_TOO_HIGH = Component.translatable("hub.nerosecurity.member.rank_too_high");
    private static final Component LOCKDOWN_DISABLED = Component.translatable("hub.nerosecurity.lockdown.disabled");
    private static final Component WAITING = Component.translatable("ui.nerosecurity.waiting");
    private static final Component NAME_EMPTY = Component.translatable("hub.nerosecurity.member.name_prompt");
    private static final Component RENAME_EMPTY = Component.translatable("hub.nerosecurity.rename.prompt");

    // --- widgets (recreated on every init) ---
    private TabBar tabs;
    private SecurityButton pencil;
    private EditBox renameBox;
    private SecurityButton renameSave;
    private TwoLineButton bigButton;
    private SecurityButton clearButton;
    private SecurityButton addOpen;
    private SecurityButton promote;
    private SecurityButton demote;
    private SecurityButton remove;
    private EditBox addName;
    private LevelLadder addLadder;
    private SecurityButton addGo;
    private SecurityButton addCancel;
    private SecurityButton membersUp;
    private SecurityButton membersDown;
    private SecurityButton devicesUp;
    private SecurityButton devicesDown;
    private SentenceList events;
    private HubPrivacyTab privacy;
    private final List<AbstractWidget> overviewWidgets = new ArrayList<>();
    private final List<AbstractWidget> addRowWidgets = new ArrayList<>();
    private final List<AbstractWidget> renameWidgets = new ArrayList<>();

    // --- state that survives a resize ---
    private int tab = TAB_OVERVIEW;
    private boolean renaming;
    private String renameText = "";
    private boolean adding;
    private String addText = "";
    private AccessLevel addLevel = AccessLevel.MEMBER;
    private @Nullable UUID selected;
    private int memberScroll;
    private int deviceScroll;
    private long ticks;
    private long hubSeen = Long.MIN_VALUE;
    private long privacySeen = Long.MIN_VALUE;
    private long hubArrivedTick;
    private long privacyArrivedTick;

    public SecurityHubScreen(SecurityHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, HubLayout.PANEL_W, HubLayout.PANEL_H, false,
                () -> ClientHubState.version() + ClientHubState.privacyVersion());
        setSubtitle(Component.translatable("hub.nerosecurity.subtitle"));
        setTitleRightInset(HubLayout.TITLE_INSET);
    }

    // --- snapshot access ------------------------------------------------------------------------

    private static @Nullable HubSnapshot snapshot() {
        return ClientHubState.current();
    }

    private static @Nullable PrivacySnapshot privacySnapshot() {
        return ClientHubState.privacy();
    }

    /** Owners first, then members: the list as drawn. */
    private static List<MemberRow> rows(@Nullable HubSnapshot snap) {
        if (snap == null) {
            return List.of();
        }
        List<MemberRow> rows = new ArrayList<>(snap.owners().size() + snap.members().size());
        rows.addAll(snap.owners());
        rows.addAll(snap.members());
        return rows;
    }

    private @Nullable MemberRow selectedRow(@Nullable HubSnapshot snap) {
        if (selected == null) {
            return null;
        }
        for (MemberRow row : rows(snap)) {
            if (selected.equals(row.uuid())) {
                return row;
            }
        }
        return null;
    }

    private boolean isSelf(MemberRow row) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getUUID().equals(row.uuid());
    }

    /** Seconds the hub snapshot has been on screen, so ages keep growing while it is open. */
    private long hubExtraSeconds() {
        return Math.max(0L, (ticks - hubArrivedTick) / TICKS_PER_SECOND);
    }

    private long privacyExtraSeconds() {
        return Math.max(0L, (ticks - privacyArrivedTick) / TICKS_PER_SECOND);
    }

    // --- lifecycle ------------------------------------------------------------------------------

    @Override
    protected void initWidgets() {
        overviewWidgets.clear();
        addRowWidgets.clear();
        renameWidgets.clear();
        int left = leftPos;
        int top = topPos;

        tabs = addGroup(new TabBar(left + HubLayout.TABS_X, top + HubLayout.TABS_Y, List.of(
                Component.translatable("hub.nerosecurity.tab.overview"),
                Component.translatable("hub.nerosecurity.tab.privacy")), this::showTab));
        tabs.select(tab);

        // Title row: pencil (owner only) and the inline rename box + Save.
        pencil = addRenderableWidget(new PencilButton(left + HubLayout.PENCIL_X, top + HubLayout.PENCIL_Y,
                b -> toggleRename()));
        pencil.setIntent(false);
        renameBox = new EditBox(this.font, left + HubLayout.RENAME_X, top + HubLayout.RENAME_Y, HubLayout.RENAME_W,
                HubLayout.RENAME_H, RENAME_EMPTY);
        renameBox.setMaxLength(Installation.NAME_MAX);
        renameBox.setHint(RENAME_EMPTY);
        renameBox.setValue(renameText);
        renameBox.setResponder(text -> renameText = text);
        addRenderableWidget(renameBox);
        renameSave = addRenderableWidget(SecurityButton.of(left + HubLayout.RENAME_SAVE_X, top + HubLayout.RENAME_Y,
                HubLayout.RENAME_SAVE_W, HubLayout.RENAME_H, Component.translatable("hub.nerosecurity.button.save"),
                b -> rename()));
        renameWidgets.add(renameBox);
        renameWidgets.add(renameSave);

        // LOCKDOWN
        Box big = HubLayout.BIG_BUTTON;
        bigButton = addRenderableWidget(new TwoLineButton(left + big.x0(), top + big.y0(), big.width(), big.height(),
                Component.empty(), b -> pressLockdown()));
        Box clear = HubLayout.CLEAR_BUTTON;
        clearButton = addRenderableWidget(SecurityButton.danger(left + clear.x0(), top + clear.y0(), clear.width(),
                clear.height(), Component.translatable("hub.nerosecurity.lockdown.button.clear"), b -> confirmLockdownOff()));
        overviewWidgets.add(bigButton);
        overviewWidgets.add(clearButton);

        // MEMBERS buttons
        addOpen = memberButton(0, "hub.nerosecurity.button.add_open", b -> openAdd());
        promote = memberButton(1, "hub.nerosecurity.button.promote", b -> changeSelected(true));
        demote = memberButton(2, "hub.nerosecurity.button.demote", b -> changeSelected(false));
        remove = memberButton(3, "hub.nerosecurity.button.remove", b -> confirmRemove());
        remove.setDanger(true);

        // The inline add row (replaces the button row while adding)
        Box nameBox = HubLayout.ADD_NAME;
        addName = new EditBox(this.font, left + nameBox.x0(), top + nameBox.y0(), nameBox.width(), nameBox.height(),
                NAME_EMPTY);
        addName.setMaxLength(PlayerNames.MAX_NAME);
        addName.setHint(NAME_EMPTY);
        addName.setValue(addText);
        addName.setResponder(text -> addText = text);
        addRenderableWidget(addName);
        addLadder = addGroup(new LevelLadder(left + HubLayout.ADD_LADDER_X, top + HubLayout.BUTTON_ROW_Y,
                List.of(AccessLevel.MEMBER, AccessLevel.OFFICER), HubLayout.ADD_LADDER_BUTTON_W,
                HubLayout.BUTTON_ROW_H, level -> addLevel = level));
        for (SecurityWidget rung : addLadder.widgets()) {
            rung.setIntent(false); // choosing a level for the add row is local; only [Add] sends
        }
        Box go = HubLayout.ADD_GO;
        addGo = addRenderableWidget(SecurityButton.of(left + go.x0(), top + go.y0(), go.width(), go.height(),
                Component.translatable("hub.nerosecurity.button.add"), b -> addMember()));
        Box cancel = HubLayout.ADD_CANCEL;
        addCancel = addRenderableWidget(SecurityButton.of(left + cancel.x0(), top + cancel.y0(), cancel.width(),
                cancel.height(), Component.translatable("hub.nerosecurity.button.cancel"), b -> closeAdd()));
        addCancel.setIntent(false);
        addRowWidgets.add(addName);
        addRowWidgets.addAll(addLadder.widgets());
        addRowWidgets.add(addGo);
        addRowWidgets.add(addCancel);

        // List arrows (navigation, never held)
        membersUp = arrow(HubLayout.MEMBERS, HubLayout.ARROW_UP_Y, true, b -> memberScroll--);
        membersDown = arrow(HubLayout.MEMBERS, HubLayout.ARROW_DOWN_Y, false, b -> memberScroll++);
        devicesUp = arrow(HubLayout.DEVICES, HubLayout.ARROW_UP_Y, true, b -> deviceScroll--);
        devicesDown = arrow(HubLayout.DEVICES, HubLayout.ARROW_DOWN_Y, false, b -> deviceScroll++);

        // EVENTS
        Box ev = HubLayout.EVENT_LIST;
        events = addScrollRegion(new SentenceList(left + ev.x0(), top + ev.y0(), ev.width(), ev.height()));
        events.setEmpty(Component.translatable("monitor.nerosecurity.none"));
        addScrollRegion(new ListWheel(HubLayout.MEMBERS, true));
        addScrollRegion(new ListWheel(HubLayout.DEVICES, false));

        // PRIVACY
        privacy = addGroup(new HubPrivacyTab(this));

        if (feedback() == null) {
            feedbackHint(WAITING);
        }
    }

    private SecurityButton memberButton(int index, String key, java.util.function.Consumer<SecurityButton> onPress) {
        Box b = HubLayout.memberButton(index);
        SecurityButton button = addRenderableWidget(SecurityButton.of(leftPos + b.x0(), topPos + b.y0(), b.width(),
                b.height(), Component.translatable(key), onPress));
        overviewWidgets.add(button);
        return button;
    }

    private SecurityButton arrow(Box list, int y, boolean up, java.util.function.Consumer<SecurityButton> onPress) {
        SecurityButton button = addRenderableWidget(new ArrowButton(leftPos + HubLayout.listArrowX(list), topPos + y,
                up, onPress));
        button.setIntent(false);
        overviewWidgets.add(button);
        return button;
    }

    @Override
    protected void containerTick() {
        ticks++;
        super.containerTick();
    }

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        long hubVersion = ClientHubState.version();
        long privacyVersion = ClientHubState.privacyVersion();
        boolean hubChanged = hubVersion != hubSeen;
        boolean privacyChanged = privacyVersion != privacySeen;
        hubSeen = hubVersion;
        privacySeen = privacyVersion;
        HubSnapshot snap = snapshot();
        PrivacySnapshot priv = privacySnapshot();
        if (hubChanged && snap != null) {
            hubArrivedTick = ticks;
            feedbackFromSnapshot(snap.ok(), snap.message(), answeredIntent);
            if (snap.ok() && answeredIntent) {
                // A successful add or rename is done; fold the inline rows away.
                if (adding && addText.isEmpty()) {
                    adding = false;
                }
                if (renaming && renameText.isEmpty()) {
                    renaming = false;
                }
            }
        }
        if (privacyChanged && priv != null) {
            privacyArrivedTick = ticks;
            feedbackFromSnapshot(priv.ok(), priv.message(), answeredIntent);
        }
    }

    /** The snapshots are display-only and are dropped as soon as the screen closes. */
    @Override
    public void removed() {
        ClientHubState.clear();
        super.removed();
    }

    // --- mirror the snapshot into the widgets ---------------------------------------------------

    @Override
    protected void refreshWidgets() {
        HubSnapshot snap = snapshot();
        boolean overview = tab == TAB_OVERVIEW;
        String name = snap == null ? "" : snap.name();

        // Title and subtitle follow the tab.
        if (overview) {
            setTitleLine(renaming ? Component.empty() : Component.translatable("hub.nerosecurity.title.dashboard",
                    name.isEmpty() ? this.title : Component.literal(name)));
            setSubtitle(Component.translatable("hub.nerosecurity.subtitle"));
        } else {
            setTitleLine(Component.translatable("hub.nerosecurity.privacy.title", name));
            setSubtitle(Component.translatable("hub.nerosecurity.privacy.subtitle"));
        }

        boolean owner = snap != null && HubRules.isOwnerOrAdmin(snap.viewerLevel(), snap.admin());
        pencil.visible = overview && owner; // while renaming, pressing it again cancels
        for (AbstractWidget w : renameWidgets) {
            w.visible = overview && renaming;
        }
        renameSave.setEnabled(!renameText.isBlank(), RENAME_EMPTY);

        for (AbstractWidget w : overviewWidgets) {
            w.visible = overview;
        }
        for (AbstractWidget w : addRowWidgets) {
            w.visible = overview && adding;
        }
        if (overview && adding) {
            addOpen.visible = false;
            promote.visible = false;
            demote.visible = false;
            remove.visible = false;
        }
        privacy.setVisible(!overview);
        privacy.refresh(privacySnapshot());

        if (snap == null) {
            bigButton.setMessage(Component.translatable(LockdownButton.TURN_ON.labelKey()));
            bigButton.disable(WAITING);
            clearButton.visible = false;
            addOpen.disable(WAITING);
            promote.disable(WAITING);
            demote.disable(WAITING);
            remove.disable(WAITING);
            addGo.disable(WAITING);
            membersUp.disable(WAITING);
            membersDown.disable(WAITING);
            devicesUp.disable(WAITING);
            devicesDown.disable(WAITING);
            events.setRows(List.of());
            return;
        }

        AccessLevel viewer = snap.viewerLevel();
        boolean admin = snap.admin();

        // Lockdown
        LockdownButton state = HubRules.lockdownButton(snap.lockdown(), snap.source());
        bigButton.setMessage(Component.translatable(state.labelKey()));
        bigButton.setHelp(Component.translatable("hub.nerosecurity.lockdown.explain"));
        Component lockdownWhy = lockdownWhy(snap, state);
        bigButton.setEnabled(lockdownWhy == null, lockdownWhy == null ? WAITING : lockdownWhy);
        boolean showClear = overview && state == LockdownButton.CONFIRM_ALARM;
        clearButton.visible = showClear;
        if (showClear) {
            Component clearWhy = lockdownWhy(snap, LockdownButton.TURN_OFF);
            clearButton.setEnabled(clearWhy == null, clearWhy == null ? WAITING : clearWhy);
        }

        // Members
        List<MemberRow> rows = rows(snap);
        MemberRow row = selectedRow(snap);
        if (selected != null && row == null) {
            selected = null;
        }
        addOpen.setEnabled(HubRules.mayAddAt(viewer, admin, AccessLevel.MEMBER), denyLevel(AccessLevel.OFFICER, snap));
        if (row == null) {
            promote.disable(SELECT_FIRST);
            demote.disable(SELECT_FIRST);
            remove.disable(SELECT_FIRST);
        } else if (row.level() == AccessLevel.OWNER) {
            promote.disable(IS_OWNER);
            demote.disable(IS_OWNER);
            remove.disable(IS_OWNER);
        } else if (isSelf(row)) {
            promote.disable(SELF);
            demote.disable(SELF);
            remove.disable(SELF);
        } else {
            AccessLevel up = HubRules.promoted(row.level());
            if (up == null) {
                promote.disable(OWNER_NOT_HERE);
            } else {
                promote.setEnabled(HubRules.mayEdit(viewer, admin, row.level(), up), RANK_TOO_HIGH);
            }
            AccessLevel down = HubRules.demoted(row.level());
            if (down == null) {
                demote.disable(LOWEST);
            } else {
                demote.setEnabled(HubRules.mayEdit(viewer, admin, row.level(), down), RANK_TOO_HIGH);
            }
            remove.setEnabled(HubRules.mayEdit(viewer, admin, row.level(), AccessLevel.PUBLIC), RANK_TOO_HIGH);
        }
        if (!HubRules.mayAddAt(viewer, admin, AccessLevel.OFFICER)) {
            addLadder.disable(AccessLevel.OFFICER, RANK_TOO_HIGH);
            if (addLevel == AccessLevel.OFFICER) {
                addLevel = AccessLevel.MEMBER;
            }
        } else {
            addLadder.enable(AccessLevel.OFFICER);
        }
        addLadder.select(addLevel);
        addGo.setEnabled(!addText.isBlank(), NAME_EMPTY);

        // Scroll clamps and arrows
        memberScroll = HubRules.clampScroll(memberScroll, rows.size(), HubLayout.LIST_ROWS);
        deviceScroll = HubRules.clampScroll(deviceScroll, snap.devices().size(), HubLayout.LIST_ROWS);
        Component atTop = Component.translatable("ui.nerosecurity.scroll.top");
        Component atEnd = Component.translatable("ui.nerosecurity.scroll.end");
        membersUp.setEnabled(memberScroll > 0, atTop);
        membersDown.setEnabled(memberScroll < rows.size() - HubLayout.LIST_ROWS, atEnd);
        devicesUp.setEnabled(deviceScroll > 0, atTop);
        devicesDown.setEnabled(deviceScroll < snap.devices().size() - HubLayout.LIST_ROWS, atEnd);

        // Events: the monitor's sentence, aged by the time this snapshot has been on screen.
        long extra = hubExtraSeconds();
        List<SentenceList.Row> eventRows = new ArrayList<>(snap.events().size());
        for (AnonymousEvent event : snap.events()) {
            eventRows.add(SentenceList.event(event, extra));
        }
        events.setRows(eventRows);
    }

    /** The reason the viewer may not press {@code button}, or {@code null} when they may (courtesy only). */
    private static @Nullable Component lockdownWhy(HubSnapshot snap, LockdownButton button) {
        if (!snap.lockdownEnabled()) {
            return LOCKDOWN_DISABLED;
        }
        if (!HubRules.mayLockdown(button, snap.viewerLevel(), snap.admin())) {
            return denyLevel(button.needs(), snap);
        }
        return null;
    }

    /** "Needs OWNER — you are OFFICER", in the resolver's own words. */
    private static Component denyLevel(AccessLevel needs, HubSnapshot snap) {
        MutableComponent have = snap.admin() ? AccessLevel.adminDisplayName() : snap.viewerLevel().displayName();
        return Component.translatable("access.nerosecurity.deny.level", needs.displayName(), have);
    }

    // --- actions --------------------------------------------------------------------------------

    private void showTab(int index) {
        if (index == tab) {
            return;
        }
        tab = index;
        tabs.select(index);
        setFocused(null);
        clearFeedback();
        if (index == TAB_PRIVACY) {
            privacy.request();
        }
        refreshWidgets();
    }

    private void toggleRename() {
        HubSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        renaming = !renaming;
        if (renaming) {
            renameText = snap.name();
            renameBox.setValue(renameText);
            setFocused(renameBox);
        } else {
            setFocused(null);
        }
        refreshWidgets();
    }

    private void rename() {
        HubSnapshot snap = snapshot();
        String name = renameText.trim();
        if (snap == null || name.isEmpty()) {
            return;
        }
        renaming = false;
        renameText = "";
        renameBox.setValue("");
        setFocused(null);
        sendIntent(new HubRename(snap.installationId(), name));
    }

    private void pressLockdown() {
        HubSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        Component name = Component.literal(snap.name());
        switch (HubRules.lockdownButton(snap.lockdown(), snap.source())) {
            case TURN_ON -> ConfirmPanel.show(this,
                    Component.translatable("hub.nerosecurity.lockdown.confirm.on.title", name),
                    Component.translatable("hub.nerosecurity.lockdown.confirm.on.body"),
                    Component.translatable("hub.nerosecurity.lockdown.confirm.on.yes"),
                    () -> sendLockdown(true));
            case TURN_OFF -> confirmLockdownOff();
            case CONFIRM_ALARM -> {
                long minutes = Ages.ceilMinutes(HubRules.remaining(snap.autoReleaseInSeconds(), hubExtraSeconds()));
                ConfirmPanel.show(this,
                        Component.translatable("hub.nerosecurity.lockdown.confirm.confirm.title", name),
                        Component.translatable("hub.nerosecurity.lockdown.confirm.confirm.body", minutes),
                        Component.translatable("hub.nerosecurity.lockdown.confirm.confirm.yes"),
                        () -> sendLockdown(true));
            }
        }
    }

    private void confirmLockdownOff() {
        HubSnapshot snap = snapshot();
        if (snap == null) {
            return;
        }
        ConfirmPanel.show(this,
                Component.translatable("hub.nerosecurity.lockdown.confirm.off.title", Component.literal(snap.name())),
                Component.translatable("hub.nerosecurity.lockdown.confirm.off.body"),
                Component.translatable("hub.nerosecurity.lockdown.confirm.off.yes"),
                () -> sendLockdown(false));
    }

    private void sendLockdown(boolean enabled) {
        HubSnapshot snap = snapshot();
        if (snap != null) {
            sendIntent(new HubLockdown(snap.installationId(), enabled));
        }
    }

    private void openAdd() {
        adding = true;
        addText = "";
        addName.setValue("");
        setFocused(addName);
        refreshWidgets();
    }

    private void closeAdd() {
        adding = false;
        addText = "";
        addName.setValue("");
        setFocused(null);
        refreshWidgets();
    }

    private void addMember() {
        HubSnapshot snap = snapshot();
        String name = addText.trim();
        if (snap == null || name.isEmpty()) {
            return;
        }
        addText = "";
        addName.setValue("");
        setFocused(null);
        sendIntent(new HubAddMember(snap.installationId(), name, addLevel));
    }

    private void changeSelected(boolean up) {
        HubSnapshot snap = snapshot();
        MemberRow row = selectedRow(snap);
        if (snap == null || row == null) {
            return;
        }
        AccessLevel next = up ? HubRules.promoted(row.level()) : HubRules.demoted(row.level());
        if (next != null) {
            sendIntent(new HubSetMember(snap.installationId(), row.uuid(), next));
        }
    }

    private void confirmRemove() {
        HubSnapshot snap = snapshot();
        MemberRow row = selectedRow(snap);
        if (snap == null || row == null) {
            return;
        }
        UUID target = row.uuid();
        ConfirmPanel.show(this,
                Component.translatable("hub.nerosecurity.member.remove.title", Component.literal(row.displayName())),
                Component.translatable("hub.nerosecurity.member.remove.body"),
                Component.translatable("hub.nerosecurity.member.remove.yes"),
                () -> {
                    selected = null;
                    sendIntent(new HubSetMember(snap.installationId(), target, AccessLevel.PUBLIC));
                });
    }

    /**
     * The erase path: close this screen properly (the server's menu closes with it), then open the chat with
     * the erase command typed in. Nothing is sent; the player presses Enter themself.
     */
    void openEraseChat() {
        Minecraft minecraft = Minecraft.getInstance();
        onClose();
        //? if >=26.2 {
        minecraft.gui.setScreen(new ChatScreen(ERASE_COMMAND, false));
        //?} else {
        /*minecraft.setScreen(new ChatScreen(ERASE_COMMAND, false));
        *///?}
    }

    // --- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!hasConfirm() && tab == TAB_OVERVIEW) {
            double lx = event.x() - leftPos;
            double ly = event.y() - topPos;
            Box window = HubLayout.listRows(HubLayout.MEMBERS);
            if (window.contains(lx, ly)) {
                HubSnapshot snap = snapshot();
                List<MemberRow> rows = rows(snap);
                int index = HubRules.rowAt(ly - window.y0(), HubLayout.ROW_H, memberScroll, rows.size(),
                        HubLayout.LIST_ROWS);
                selected = index < 0 ? null : rows.get(index).uuid();
                setFocused(null);
                refreshWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Mouse-wheel scrolling over a list box. */
    private final class ListWheel implements ScrollRegion {

        private final Box window;
        private final boolean members;

        ListWheel(Box list, boolean members) {
            this.window = HubLayout.listRows(list);
            this.members = members;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
            if (tab != TAB_OVERVIEW || !window.contains(mouseX - leftPos, mouseY - topPos)) {
                return false;
            }
            int delta = scrollY > 0 ? -1 : 1;
            if (members) {
                memberScroll = Math.max(0, memberScroll + delta);
            } else {
                deviceScroll = Math.max(0, deviceScroll + delta);
            }
            refreshWidgets();
            return true;
        }
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (tab == TAB_PRIVACY) {
            privacy.extract(g, this.font, privacySnapshot(), privacyExtraSeconds());
            return;
        }
        HubSnapshot snap = snapshot();
        Font font = this.font;
        drawBox(g, HubLayout.STATUS, Component.translatable("hub.nerosecurity.section.status"));
        drawBox(g, HubLayout.LOCKDOWN, Component.translatable("hub.nerosecurity.section.lockdown"));
        drawBox(g, HubLayout.MEMBERS, Component.translatable("hub.nerosecurity.section.members",
                snap == null ? 0 : rows(snap).size()));
        drawBox(g, HubLayout.DEVICES, Component.translatable("hub.nerosecurity.section.devices",
                snap == null ? 0 : snap.deviceCount()));
        drawBox(g, HubLayout.EVENTS, Component.translatable("hub.nerosecurity.section.events"));
        events.extract(g, font);
        if (snap == null) {
            return;
        }
        drawStatus(g, font, snap);
        if (!clearButton.visible) {
            Box ld = HubLayout.LOCKDOWN;
            int x = leftPos + HubLayout.BIG_BUTTON.x0();
            int w = HubLayout.BIG_BUTTON.width();
            drawLines(g, font, Component.translatable("hub.nerosecurity.lockdown.explain"), x,
                    topPos + HubLayout.EXPLAIN_Y, w, SecurityColours.SUBTLE, topPos + ld.y1() - 2);
        }
        drawMembers(g, font, snap);
        drawDevices(g, font, snap);
    }

    private void drawBox(GuiGraphicsExtractor g, Box box, Component caption) {
        SectionBox.draw(g, this.font, leftPos + box.x0(), topPos + box.y0(), leftPos + box.x1(), topPos + box.y1(),
                caption);
    }

    private void drawStatus(GuiGraphicsExtractor g, Font font, HubSnapshot snap) {
        Box box = HubLayout.STATUS;
        int x = leftPos + box.x0() + 4;
        int w = box.width() - 8;
        long extra = hubExtraSeconds();
        LockdownButton state = HubRules.lockdownButton(snap.lockdown(), snap.source());
        Component status = switch (state) {
            case TURN_ON -> Component.translatable(HubRules.statusKey(false, null));
            case TURN_OFF -> Component.translatable(HubRules.statusKey(true, snap.source()),
                    Ages.ago(HubRules.aged(snap.lockdownSinceSeconds(), extra)));
            case CONFIRM_ALARM -> Component.translatable(HubRules.statusKey(true, snap.source()),
                    Ages.in(HubRules.remaining(snap.autoReleaseInSeconds(), extra)));
        };
        Status mark = state == LockdownButton.TURN_ON ? Status.OK : Status.BAD;
        int y1 = topPos + HubLayout.STATUS_LINE_1;
        StatusDot.drawGlyph(g, x, y1 + 1, mark);
        int tx = x + StatusDot.SIZE + 4;
        int statusColour = state == LockdownButton.TURN_ON ? WidgetColours.TEXT : SecurityColours.DENY_TEXT;
        WidgetText.drawFitted(g, font, status, tx, y1, w - (tx - x), statusColour);
        if (WidgetText.overflows(font, status, w - (tx - x))) {
            explain(g, tx, y1, w - (tx - x), 9, status);
        }
        Component summary = Component.translatable("hub.nerosecurity.devices.summary", snap.deviceCount(), snap.devicesOk());
        g.text(font, summary, x, topPos + HubLayout.STATUS_LINE_2, SecurityColours.SUBTLE, false);

        // "You are [PILL]" then the meaning on its own lines, so the pill and the sentence are both visible.
        int yy = topPos + HubLayout.STATUS_YOU_ARE;
        Component lead = Component.translatable("hub.nerosecurity.you_are.lead");
        g.text(font, lead, x, yy + 2, WidgetColours.TEXT, false);
        int px = x + font.width(lead) + 4;
        int pillW = LevelPill.drawViewer(g, font, px, yy, snap.viewerLevel(), snap.admin());
        Component meaning = snap.admin() ? LevelMeaning.adminMeaning() : LevelMeaning.meaning(snap.viewerLevel());
        drawLines(g, font, meaning, x, topPos + HubLayout.STATUS_MEANING, w, WidgetColours.TEXT, topPos + box.y1() - 2);
        explain(g, x, yy, px + pillW - x, LevelPill.HEIGHT, Component.translatable("hub.nerosecurity.you_are",
                snap.admin() ? LevelPill.adminWord() : LevelPill.word(snap.viewerLevel()), meaning));
    }

    private void drawMembers(GuiGraphicsExtractor g, Font font, HubSnapshot snap) {
        Box list = HubLayout.MEMBERS;
        List<MemberRow> rows = rows(snap);
        int x = leftPos + HubLayout.listTextX(list);
        int textW = HubLayout.listTextWidth(list);
        int rowsX0 = leftPos + list.x0() + 1;
        int rowsX1 = leftPos + HubLayout.listArrowX(list) - HubLayout.LIST_ARROW_GAP;
        for (int i = 0; i < HubLayout.LIST_ROWS; i++) {
            int index = memberScroll + i;
            if (index >= rows.size()) {
                break;
            }
            MemberRow row = rows.get(index);
            int ry = topPos + HubLayout.LIST_TOP + i * HubLayout.ROW_H;
            if (row.uuid().equals(selected)) {
                g.fill(rowsX0, ry, rowsX1, ry + HubLayout.ROW_H, SecurityColours.SELECTED);
            }
            int pillW = LevelPill.width(font, row.level());
            int pillX = x + textW - pillW;
            LevelPill.draw(g, font, pillX, ry, row.level());
            int nameRoom = HubRules.nameRoom(textW, pillW, 4);
            Component name = Component.literal(row.displayName());
            WidgetText.drawFitted(g, font, name, x, ry + 2, nameRoom, WidgetColours.TEXT);
            Component tip = Component.translatable("hub.nerosecurity.member.row", name, LevelPill.word(row.level()),
                    LevelMeaning.meaning(row.level()));
            explain(g, rowsX0, ry, rowsX1 - rowsX0, HubLayout.ROW_H, tip);
        }
    }

    private void drawDevices(GuiGraphicsExtractor g, Font font, HubSnapshot snap) {
        Box list = HubLayout.DEVICES;
        List<DeviceRow> devices = snap.devices();
        int x = leftPos + HubLayout.listTextX(list);
        int textW = HubLayout.listTextWidth(list);
        int rowsX0 = leftPos + list.x0() + 1;
        int rowsX1 = leftPos + HubLayout.listArrowX(list) - HubLayout.LIST_ARROW_GAP;
        for (int i = 0; i < HubLayout.LIST_ROWS; i++) {
            int index = deviceScroll + i;
            if (index >= devices.size()) {
                break;
            }
            DeviceRow row = devices.get(index);
            int ry = topPos + HubLayout.LIST_TOP + i * HubLayout.ROW_H;
            Status status = Status.of(row.state());
            StatusDot.drawGlyph(g, x, ry + 2, status);
            int tx = x + StatusDot.SIZE + 3;
            int room = textW - (tx - x);
            Component name = blockName(row.block());
            Component pos = Component.literal(row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ());
            Component stateWord = Component.translatable(row.state().translationKey());
            boolean hasState = row.state() != DeviceState.OK && !stateWord.getString().isEmpty();
            // Position and state are drawn in full; the block name takes what is left (full text on hover).
            int tailW = font.width(pos) + (hasState ? 4 + font.width(stateWord) : 0);
            int nameRoom = Math.max(0, room - tailW - 4);
            int nameW = WidgetText.drawFitted(g, font, name, tx, ry + 2, nameRoom, WidgetColours.TEXT);
            int px = tx + nameW + 4;
            g.text(font, pos, px, ry + 2, SecurityColours.SUBTLE, false);
            if (hasState) {
                int sx = px + font.width(pos) + 4;
                int colour = switch (status) {
                    case OK -> SecurityColours.SUBTLE;
                    case WARN -> SecurityColours.opaque(SecurityColours.WARN);
                    case OFF -> SecurityColours.SUBTLE;
                    case BAD -> SecurityColours.DENY_TEXT;
                };
                g.text(font, stateWord, sx, ry + 2, colour, false);
            }
            Component sentence = hasState
                    ? Component.translatable("device.nerosecurity.tooltip", name, pos, stateWord)
                    : Component.translatable("device.nerosecurity.tooltip.ok", name, pos);
            explain(g, rowsX0, ry, rowsX1 - rowsX0, HubLayout.ROW_H,
                    sentence.copy().append("\n").append(Component.translatable("device.nerosecurity.unlink_hint")
                            .withColor(SecurityColours.SUBTLE & 0xFFFFFF)));
        }
    }

    /** The block's display name from its registry id (the same words as the item). */
    private static Component blockName(Identifier block) {
        return Component.translatable("block." + block.getNamespace() + "." + block.getPath());
    }

    /** Wrapped lines, never drawn under {@code bottom}; the full text becomes a hover sentence when clipped. */
    private int drawLines(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int colour,
                          int bottom) {
        List<FormattedCharSequence> lines = WidgetText.wrap(font, text, width);
        int drawn = 0;
        for (FormattedCharSequence line : lines) {
            int ly = y + drawn * HubLayout.TEXT_LINE;
            if (ly + 9 > bottom) {
                break;
            }
            g.text(font, line, x, ly, colour, false);
            drawn++;
        }
        if (drawn < lines.size()) {
            explain(g, x, y, width, Math.max(1, drawn) * HubLayout.TEXT_LINE, text);
        }
        return Math.max(1, drawn) * HubLayout.TEXT_LINE;
    }

    // --- small widgets owned by this screen -----------------------------------------------------

    /** The big lockdown button: its long label wraps onto two centred lines instead of being cut. */
    private static final class TwoLineButton extends SecurityButton {

        TwoLineButton(int x, int y, int w, int h, Component label, java.util.function.Consumer<SecurityButton> onPress) {
            super(x, y, w, h, label, onPress);
        }

        @Override
        protected void extractLabel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int colour) {
            List<FormattedCharSequence> lines = WidgetText.wrap(font, getMessage(), w - 6);
            int n = Math.min(2, lines.size());
            int lineH = 9;
            int y0 = y + (h - n * lineH) / 2 + 1;
            for (int i = 0; i < n; i++) {
                FormattedCharSequence line = lines.get(i);
                g.text(font, line, x + (w - font.width(line)) / 2, y0 + i * lineH, colour, false);
            }
        }
    }

    /** The ✎ next to the title, drawn as a short diagonal stroke so it needs no glyph. */
    private static final class PencilButton extends SecurityButton {

        PencilButton(int x, int y, java.util.function.Consumer<SecurityButton> onPress) {
            super(x, y, HubLayout.PENCIL_SIZE, HubLayout.PENCIL_SIZE, Component.translatable("hub.nerosecurity.button.rename"),
                    onPress);
            setHelp(Component.translatable("hub.nerosecurity.button.rename"));
        }

        @Override
        protected void extractLabel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int colour) {
            int s = 5;
            int ox = x + (w - s) / 2;
            int oy = y + (h - s) / 2;
            for (int i = 0; i < s; i++) {
                g.fill(ox + s - 1 - i, oy + i, ox + s - i, oy + i + 1, colour);
                if (i < s - 1) {
                    g.fill(ox + s - 1 - i, oy + i + 1, ox + s - i, oy + i + 2, colour);
                }
            }
            g.fill(ox, oy + s - 1, ox + 2, oy + s + 1, colour);
        }
    }

    /** A 9×9 list arrow. */
    private static final class ArrowButton extends SecurityButton {

        private final boolean up;

        ArrowButton(int x, int y, boolean up, java.util.function.Consumer<SecurityButton> onPress) {
            super(x, y, HubLayout.ARROW, HubLayout.ARROW,
                    Component.translatable(up ? "ui.nerosecurity.scroll.up" : "ui.nerosecurity.scroll.down"), onPress);
            this.up = up;
            setHelp(getMessage());
        }

        @Override
        protected void extractLabel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int colour) {
            int cx = x + w / 2;
            int cy = y + h / 2;
            for (int i = 0; i < 3; i++) {
                int row = up ? cy - 1 + i : cy + 1 - i;
                g.fill(cx - i, row, cx + i + 1, row + 1, colour);
            }
        }
    }
}
