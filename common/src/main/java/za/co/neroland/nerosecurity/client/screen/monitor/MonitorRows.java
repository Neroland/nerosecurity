package za.co.neroland.nerosecurity.client.screen.monitor;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceKind;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceRow;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceState;

/**
 * The monitor's SENSORS column as an ordered list of rows, built from a {@link MonitorSnapshot} alone: a
 * "Cameras" heading, then every camera (or the one amber line saying cameras are off on this server), a
 * "Laser grids" heading, then every laser node; an empty group shows a dash. Pure (no client classes), so the
 * ordering, the omissions and the status shapes are unit-tested. Text only: a row is a position and a state
 * word, never a picture and never a person.
 */
public final class MonitorRows {

    /** What a row is, which decides how the screen draws it. */
    public enum Kind {
        /** A group caption in the hint colour ({@code key} is the caption key). */
        HEADING,
        /** One sensor: status glyph, position and state word ({@code device} is set). */
        DEVICE,
        /** A plain note in the hint colour, e.g. the dash for an empty group ({@code key}). */
        NOTE,
        /** A note that needs attention, in the WARN colour ({@code key}). */
        WARNING
    }

    /** One row. {@code key} is a lang key for every kind but {@link Kind#DEVICE}, where {@code device} is set. */
    public record Row(Kind kind, @Nullable String key, @Nullable DeviceRow device) {

        public static Row heading(String key) {
            return new Row(Kind.HEADING, key, null);
        }

        public static Row note(String key) {
            return new Row(Kind.NOTE, key, null);
        }

        public static Row warning(String key) {
            return new Row(Kind.WARNING, key, null);
        }

        public static Row device(DeviceRow device) {
            return new Row(Kind.DEVICE, null, device);
        }
    }

    public static final String CAMERAS = "monitor.nerosecurity.cameras";
    public static final String LASERS = "monitor.nerosecurity.lasers";
    public static final String CAMERAS_DISABLED = "monitor.nerosecurity.cameras_disabled";
    public static final String NONE = "monitor.nerosecurity.pos.none";

    private MonitorRows() {
    }

    /** The rows for {@code snap}: cameras first, then lasers, each under its heading. */
    public static List<Row> of(MonitorSnapshot snap) {
        List<Row> out = new ArrayList<>();
        out.add(Row.heading(CAMERAS));
        if (snap.camerasDisabled()) {
            out.add(Row.warning(CAMERAS_DISABLED));
        } else {
            group(out, snap, DeviceKind.CAMERA);
        }
        out.add(Row.heading(LASERS));
        group(out, snap, DeviceKind.LASER);
        return out;
    }

    private static void group(List<Row> out, MonitorSnapshot snap, DeviceKind kind) {
        int before = out.size();
        for (DeviceRow row : snap.devices()) {
            if (row.kind() == kind) {
                out.add(Row.device(row));
            }
        }
        if (out.size() == before) {
            out.add(Row.note(NONE));
        }
    }

    /** How many sensors the snapshot lists (the number in the section caption). */
    public static int sensorCount(MonitorSnapshot snap) {
        return snap.devices().size();
    }

    /**
     * The status glyph for a sensor state: ARMED is a filled green square, UNPOWERED and UNPAIRED an amber
     * hollow square, UNLOADED a grey outline. The state word is drawn beside it, so the shape and the word both
     * carry the meaning.
     */
    public static Status status(DeviceState state) {
        if (state == null) {
            return Status.OFF;
        }
        return switch (state) {
            case ARMED -> Status.OK;
            case UNPOWERED, UNPAIRED -> Status.WARN;
            case UNLOADED -> Status.OFF;
        };
    }

    /** The block name key for a sensor kind, for the row's hover sentence. */
    public static String blockKey(DeviceKind kind) {
        return kind == DeviceKind.CAMERA ? "block.nerosecurity.security_camera" : "block.nerosecurity.laser_grid_node";
    }

    /** The overall status glyph for the title row: a red cross under lockdown, a green square otherwise. */
    public static Status installationStatus(boolean lockdown) {
        return lockdown ? Status.BAD : Status.OK;
    }
}
