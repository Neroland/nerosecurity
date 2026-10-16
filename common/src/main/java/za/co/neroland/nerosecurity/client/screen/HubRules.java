package za.co.neroland.nerosecurity.client.screen;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.MemberEditRule;
import za.co.neroland.nerosecurity.installation.LockdownSource;

/**
 * The hub screen's display rules, kept pure (no Minecraft client classes) so they are unit-tested: which
 * lockdown button a snapshot shows, which level it needs, which member edits the viewer may be offered, and
 * the list scroll arithmetic.
 *
 * <p><b>Mirror only.</b> Every answer here is a courtesy that greys a control out and names the reason; the
 * server re-resolves every payload and may still refuse. Nothing here grants anything.
 */
public final class HubRules {

    /** What the big lockdown button offers. */
    public enum LockdownButton {
        /** No lockdown: pressing starts a manual one (OFFICER). */
        TURN_ON("hub.nerosecurity.lockdown.button.off", AccessLevel.OFFICER),
        /** A manual lockdown holds: pressing clears it (OWNER). */
        TURN_OFF("hub.nerosecurity.lockdown.button.on", AccessLevel.OWNER),
        /** An alarm lockdown holds: pressing keeps it (OWNER); a second button clears it. */
        CONFIRM_ALARM("hub.nerosecurity.lockdown.button.alarm", AccessLevel.OWNER);

        private final String labelKey;
        private final AccessLevel needs;

        LockdownButton(String labelKey, AccessLevel needs) {
            this.labelKey = labelKey;
            this.needs = needs;
        }

        /** The button's label key. */
        public String labelKey() {
            return labelKey;
        }

        /** The level the action needs (the server's rule, mirrored). */
        public AccessLevel needs() {
            return needs;
        }
    }

    private HubRules() {
    }

    // --- lockdown -------------------------------------------------------------------------------

    public static LockdownButton lockdownButton(boolean lockdown, @Nullable LockdownSource source) {
        if (!lockdown) {
            return LockdownButton.TURN_ON;
        }
        return source == LockdownSource.ALARM ? LockdownButton.CONFIRM_ALARM : LockdownButton.TURN_OFF;
    }

    /** The STATUS line key: normal, manual lockdown, or alarm lockdown. */
    public static String statusKey(boolean lockdown, @Nullable LockdownSource source) {
        return switch (lockdownButton(lockdown, source)) {
            case TURN_ON -> "hub.nerosecurity.status.normal";
            case TURN_OFF -> "hub.nerosecurity.status.lockdown";
            case CONFIRM_ALARM -> "hub.nerosecurity.status.lockdown_alarm";
        };
    }

    /**
     * The level the viewer acts with for display purposes: an operator override reports as OWNER, exactly as
     * the server's decision does.
     */
    public static AccessLevel actorLevel(@Nullable AccessLevel viewer, boolean admin) {
        if (admin) {
            return AccessLevel.OWNER;
        }
        return viewer == null ? AccessLevel.PUBLIC : viewer;
    }

    /** Whether the viewer may be offered {@code button} (courtesy only). */
    public static boolean mayLockdown(LockdownButton button, @Nullable AccessLevel viewer, boolean admin) {
        return actorLevel(viewer, admin).atLeast(button.needs());
    }

    public static boolean isOwnerOrAdmin(@Nullable AccessLevel viewer, boolean admin) {
        return actorLevel(viewer, admin) == AccessLevel.OWNER;
    }

    // --- members --------------------------------------------------------------------------------

    /** The level a promote press asks for, or {@code null} when the row cannot be promoted here. */
    public static @Nullable AccessLevel promoted(@Nullable AccessLevel current) {
        return current == AccessLevel.MEMBER ? AccessLevel.OFFICER : null;
    }

    /** The level a demote press asks for, or {@code null} when the row cannot be demoted. */
    public static @Nullable AccessLevel demoted(@Nullable AccessLevel current) {
        return current == AccessLevel.OFFICER ? AccessLevel.MEMBER : null;
    }

    /** Mirrors the server's member-edit rule for the viewer. */
    public static boolean mayEdit(@Nullable AccessLevel viewer, boolean admin, @Nullable AccessLevel current,
                                  @Nullable AccessLevel next) {
        return MemberEditRule.canEdit(actorLevel(viewer, admin), current, next);
    }

    /** Whether the add row may offer OFFICER (adding is an edit from PUBLIC). */
    public static boolean mayAddAt(@Nullable AccessLevel viewer, boolean admin, AccessLevel level) {
        return mayEdit(viewer, admin, AccessLevel.PUBLIC, level);
    }

    // --- lists ----------------------------------------------------------------------------------

    /** The first visible row, kept within {@code [0, rows - visible]}. */
    public static int clampScroll(int scroll, int rows, int visible) {
        int max = Math.max(0, rows - Math.max(1, visible));
        return Math.max(0, Math.min(scroll, max));
    }

    /**
     * The row index under a point {@code dy} pixels below the first row, or -1 when the point is below the
     * last row or outside the visible window.
     */
    public static int rowAt(double dy, int rowHeight, int scroll, int rows, int visible) {
        if (dy < 0 || rowHeight <= 0) {
            return -1;
        }
        int slot = (int) (dy / rowHeight);
        if (slot >= visible) {
            return -1;
        }
        int index = scroll + slot;
        return index < rows ? index : -1;
    }

    /** The width a name may use beside a right-aligned pill: {@code column - pill - gap}, never negative. */
    public static int nameRoom(int columnWidth, int pillWidth, int gap) {
        return Math.max(0, columnWidth - pillWidth - gap);
    }

    // --- time -----------------------------------------------------------------------------------

    /** A snapshot countdown aged by the time the screen has been open; never below zero. */
    public static long remaining(int seconds, long extraSeconds) {
        return Math.max(0L, seconds - Math.max(0L, extraSeconds));
    }

    /** A snapshot age grown by the time the screen has been open. */
    public static long aged(int seconds, long extraSeconds) {
        return Math.max(0L, seconds) + Math.max(0L, extraSeconds);
    }
}
