package za.co.neroland.nerosecurity.menu.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.netty.handler.codec.DecoderException;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.LockdownSource;

/**
 * What a Security Monitor shows, built on the server and sent to one viewer (clientbound
 * {@code nerosecurity:monitor_snapshot}). Display only: the client decides nothing from it.
 *
 * <p>It carries no identity by construction: no UUID, no player name, no position of a person. The
 * only text is the installation's own name. Device rows are the owner's linked cameras and laser grid
 * nodes (their block positions and a state word); event rows are {@link AnonymousEvent}s, which hold
 * the actor <em>kind</em> only and a relative age, never an absolute time. Nothing here is imagery.
 *
 * @param pos                  the monitor the viewer opened (the refresh payload names it back)
 * @param installationName     the installation's name
 * @param lockdown             whether a lockdown is in force (and enforced on this server)
 * @param source               what started the lockdown (meaningful only while {@code lockdown})
 * @param lockdownSinceSeconds how long the lockdown has been in force, 0 when none
 * @param autoReleaseInSeconds seconds until an alarm lockdown clears itself, 0 when none
 * @param camerasDisabled      cameras are switched off on this server, so camera rows are omitted
 * @param auditDisabled        the audit log is off on this server, so there are no event rows
 * @param devices              camera and laser rows in the monitor's dimension, cameras first
 * @param events               the newest events, newest first, at most {@link #MAX_EVENTS}
 */
public record MonitorSnapshot(BlockPos pos, String installationName, boolean lockdown, LockdownSource source,
        int lockdownSinceSeconds, int autoReleaseInSeconds, boolean camerasDisabled, boolean auditDisabled,
        List<DeviceRow> devices, List<AnonymousEvent> events) implements CustomPacketPayload {

    /** Device rows one snapshot carries at most (the default {@code maxDevicesPerInstallation}). */
    public static final int MAX_DEVICES = 256;
    /** Event rows one snapshot carries at most (the {@code monitorEventCount} upper bound). */
    public static final int MAX_EVENTS = 50;
    private static final int NAME_MAX_BYTES = Installation.NAME_MAX * 4;

    public static final Type<MonitorSnapshot> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "monitor_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, MonitorSnapshot> STREAM_CODEC =
            StreamCodec.of(MonitorSnapshot::write, MonitorSnapshot::read);

    /** The kind of a listed device. */
    public enum DeviceKind {
        CAMERA,
        LASER
    }

    /** What a listed device is doing, as far as the monitor can tell. */
    public enum DeviceState {
        /** Linked, powered (and for a laser paired): sensing. */
        ARMED,
        /** Not enough energy to sense. */
        UNPOWERED,
        /** A laser node with power but no opposite node in range. */
        UNPAIRED,
        /** The chunk is not loaded (or the block entity is not a sensor any more). */
        UNLOADED;

        /** {@code "monitor.nerosecurity.device.<lower_name>"}. */
        public String translationKey() {
            return "monitor.nerosecurity.device." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** One of the owner's linked sensors: its block position, kind and state. */
    public record DeviceRow(BlockPos pos, DeviceKind kind, DeviceState state) {

        public DeviceRow {
            pos = pos.immutable();
        }
    }

    public MonitorSnapshot {
        pos = pos.immutable();
        installationName = installationName == null ? "" : installationName;
        if (installationName.length() > Installation.NAME_MAX) {
            installationName = installationName.substring(0, Installation.NAME_MAX);
        }
        source = source == null ? LockdownSource.MANUAL : source;
        lockdownSinceSeconds = Math.max(0, lockdownSinceSeconds);
        autoReleaseInSeconds = Math.max(0, autoReleaseInSeconds);
        devices = List.copyOf(devices.size() > MAX_DEVICES ? devices.subList(0, MAX_DEVICES) : devices);
        events = List.copyOf(events.size() > MAX_EVENTS ? events.subList(0, MAX_EVENTS) : events);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // --- wire format ------------------------------------------------------------------------------

    private static void write(FriendlyByteBuf buf, MonitorSnapshot v) {
        buf.writeBlockPos(v.pos);
        buf.writeUtf(v.installationName, NAME_MAX_BYTES);
        buf.writeBoolean(v.lockdown);
        buf.writeVarInt(v.source.ordinal());
        buf.writeVarInt(v.lockdownSinceSeconds);
        buf.writeVarInt(v.autoReleaseInSeconds);
        buf.writeBoolean(v.camerasDisabled);
        buf.writeBoolean(v.auditDisabled);
        buf.writeVarInt(v.devices.size());
        for (DeviceRow row : v.devices) {
            buf.writeBlockPos(row.pos());
            buf.writeVarInt(row.kind().ordinal());
            buf.writeVarInt(row.state().ordinal());
        }
        buf.writeVarInt(v.events.size());
        for (AnonymousEvent event : v.events) {
            AnonymousEvent.STREAM_CODEC.encode(buf, event);
        }
    }

    private static MonitorSnapshot read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(NAME_MAX_BYTES);
        boolean lockdown = buf.readBoolean();
        LockdownSource source = byOrdinal(LockdownSource.values(), buf.readVarInt());
        int since = buf.readVarInt();
        int release = buf.readVarInt();
        boolean camerasDisabled = buf.readBoolean();
        boolean auditDisabled = buf.readBoolean();
        int deviceCount = count(buf, MAX_DEVICES);
        List<DeviceRow> devices = new ArrayList<>(deviceCount);
        for (int i = 0; i < deviceCount; i++) {
            devices.add(new DeviceRow(buf.readBlockPos(), byOrdinal(DeviceKind.values(), buf.readVarInt()),
                    byOrdinal(DeviceState.values(), buf.readVarInt())));
        }
        int eventCount = count(buf, MAX_EVENTS);
        List<AnonymousEvent> events = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            events.add(AnonymousEvent.STREAM_CODEC.decode(buf));
        }
        return new MonitorSnapshot(pos, name, lockdown, source, since, release, camerasDisabled, auditDisabled,
                devices, events);
    }

    private static int count(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("monitor snapshot list too long");
        }
        return n;
    }

    private static <E extends Enum<E>> E byOrdinal(E[] values, int ordinal) {
        return values[Math.floorMod(ordinal, values.length)];
    }
}
