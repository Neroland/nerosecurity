package za.co.neroland.nerosecurity.client.screen.alarm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.widget.Status;

/**
 * The alarm screen's pure layout and wording rules. {@link #pack} lays check boxes out left to right in rows
 * of a column, starting a new row when the next box would not fit (so short labels such as "Sound" and
 * "Light" share a row while a long phrase gets its own); the rest decides which status row and which lock
 * reasons the screen shows. No Minecraft client classes, so it is plain-JVM tested.
 */
public final class AlarmLayout {

    /** Row pitch for a 14-px check box. */
    public static final int ROW = 14;
    /** Horizontal gap between two boxes on one row. */
    public static final int GAP = 8;

    public static final String ARMED_KEY = "alarm.nerosecurity.armed";
    public static final String SILENCED_KEY = "alarm.nerosecurity.silenced_until";
    public static final String OWNER_ONLY_KEY = "alarm.nerosecurity.lockdown.owner_only";
    public static final String SERVER_DISABLED_KEY = "alarm.nerosecurity.lockdown.server_disabled";
    public static final String OWNER_ONLY_ARMED_KEY = "alarm.nerosecurity.lockdown.owner_only_armed";
    public static final String MEMBERS_TOOLTIP_KEY = "alarm.nerosecurity.lockdown.members_tooltip";
    public static final String DENY_LEVEL_KEY = "access.nerosecurity.deny.level";

    /** One packed item with its top-left corner. */
    public record Placed<T>(T item, int x, int y) {
    }

    /** The packed items and the first free {@code y} under the last row. */
    public record Packed<T>(List<Placed<T>> placed, int bottom) {
    }

    /** The armed / silenced status row. */
    public record StateRow(Status status, String key, long minutesLeft) {
    }

    private AlarmLayout() {
    }

    /**
     * Packs {@code items} into rows of {@code columnWidth} px starting at ({@code x}, {@code y}). An item wider
     * than the column still gets a row of its own (it is cut by the widget, never dropped).
     */
    public static <T> Packed<T> pack(List<T> items, ToIntFunction<T> width, int x, int y, int columnWidth) {
        List<Placed<T>> placed = new ArrayList<>();
        int cx = x;
        int cy = y;
        boolean rowStarted = false;
        for (T item : items) {
            int w = Math.max(0, width.applyAsInt(item));
            if (rowStarted && cx - x + GAP + w > columnWidth) {
                cx = x;
                cy += ROW;
                rowStarted = false;
            }
            if (rowStarted) {
                cx += GAP;
            }
            placed.add(new Placed<>(item, cx, cy));
            cx += w;
            rowStarted = true;
        }
        return new Packed<>(placed, rowStarted ? cy + ROW : cy);
    }

    /** "Armed" (filled OK square) or "Silenced — %s min left" (amber hollow square), minutes rounded up. */
    public static StateRow state(int silencedForSeconds) {
        if (silencedForSeconds > 0) {
            return new StateRow(Status.WARN, SILENCED_KEY, Math.max(1L, (silencedForSeconds + 59L) / 60L));
        }
        return new StateRow(Status.OK, ARMED_KEY, 0L);
    }

    /**
     * Why the "Trigger lockdown" box is locked, or {@code null} when the viewer may tick it: the server forbids
     * alarm lockdowns, or the viewer is not an owner (or operator).
     */
    public static @Nullable String lockdownWhy(boolean ownerOrAdmin, boolean allowedByServer) {
        if (!allowedByServer) {
            return SERVER_DISABLED_KEY;
        }
        return ownerOrAdmin ? null : OWNER_ONLY_KEY;
    }

    /**
     * Why every other box is locked, or {@code null}: an alarm that can trigger a lockdown may only be changed
     * by an owner (or operator), so an officer cannot quietly unsubscribe it.
     */
    public static @Nullable String othersWhy(boolean ownerOrAdmin, boolean attemptsLockdown) {
        return attemptsLockdown && !ownerOrAdmin ? OWNER_ONLY_ARMED_KEY : null;
    }

    /** Whether the viewer may press Silence (SILENCE_ALARM at OFFICER; the server re-resolves). */
    public static boolean maySilence(AccessLevel viewerLevel, boolean admin) {
        return admin || (viewerLevel != null && viewerLevel.atLeast(AccessLevel.OFFICER));
    }
}
