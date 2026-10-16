package za.co.neroland.nerosecurity.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;

/**
 * The hub screen's payloads on {@code nerosecurity:main}. One clientbound snapshot for display; four
 * serverbound edit intents, each of which the server re-authorises from scratch. Strings are length-capped
 * on both encode and decode. The installation's signing secret is never part of any payload.
 */
public final class HubPayloads {

    /** Most rows a snapshot carries (owners plus members); the decoder refuses more. */
    public static final int MAX_ROWS = 1100;
    /** Most device rows a snapshot carries (the {@code maxDevicesPerInstallation} ceiling). */
    public static final int MAX_DEVICES = 4096;
    /** Event rows a snapshot carries at most: the hub shows the last five. */
    public static final int MAX_EVENTS = 5;

    private HubPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static AccessLevel readLevel(FriendlyByteBuf buf) {
        return AccessLevel.STREAM_CODEC.decode(buf);
    }

    private static void writeLevel(FriendlyByteBuf buf, AccessLevel level) {
        AccessLevel.STREAM_CODEC.encode(buf, level);
    }

    /** One row on the hub screen: a player id, the name to draw, and their level. */
    public record MemberRow(UUID uuid, String displayName, AccessLevel level) {

        static void write(FriendlyByteBuf buf, MemberRow row) {
            buf.writeUUID(row.uuid);
            buf.writeUtf(clip(row.displayName, PlayerNames.MAX_NAME), PlayerNames.MAX_NAME);
            writeLevel(buf, row.level);
        }

        static MemberRow read(FriendlyByteBuf buf) {
            return new MemberRow(buf.readUUID(), buf.readUtf(PlayerNames.MAX_NAME), readLevel(buf));
        }
    }

    private static void writeRows(FriendlyByteBuf buf, List<MemberRow> rows) {
        int count = Math.min(rows.size(), MAX_ROWS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            MemberRow.write(buf, rows.get(i));
        }
    }

    private static List<MemberRow> readRows(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ROWS) {
            throw new io.netty.handler.codec.DecoderException("Too many hub rows");
        }
        List<MemberRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(MemberRow.read(buf));
        }
        return rows;
    }

    /**
     * One linked device on the hub's DEVICES list: where it is, what block it is, and its status word.
     * Never a person, never what it sensed.
     */
    public record DeviceRow(BlockPos pos, Identifier block, DeviceState state) {

        public DeviceRow {
            pos = pos.immutable();
            state = state == null ? DeviceState.UNLOADED : state;
        }
    }

    private static int count(FriendlyByteBuf buf, int max, String what) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("Too many hub " + what);
        }
        return n;
    }

    private static <E extends Enum<E>> E byOrdinal(E[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("hub snapshot value out of range");
        }
        return values[ordinal];
    }

    /**
     * Server truth for the open hub screen (v2). Sent only to a player the resolver just allowed to open this
     * hub; carries display names for the installation's own rows only. Display only: the client never
     * decides anything from it.
     *
     * <ul>
     *   <li>{@code lockdown} is the enforced state ({@code lockdownEnabled} and active); {@code source},
     *       {@code lockdownSinceSeconds} and {@code autoReleaseInSeconds} are meaningful only while it holds
     *       (relative seconds, never an absolute time).</li>
     *   <li>{@code devices} are the linked devices with a server-computed status ({@link DeviceStates}).</li>
     *   <li>{@code events} are the newest {@link #MAX_EVENTS} audit rows with every identity removed
     *       ({@link AnonymousEvent}: type, severity, age, device position, actor <em>kind</em>), read without a
     *       {@code LOG_READ}, exactly as the monitor reads them.</li>
     *   <li>{@code ok}/{@code message}: the outcome of the action that produced this snapshot (null when
     *       none), for the screen's footer.</li>
     * </ul>
     */
    public record HubSnapshot(UUID installationId, String name, boolean lockdown, LockdownSource source,
            int lockdownSinceSeconds, int autoReleaseInSeconds, boolean lockdownEnabled, List<MemberRow> members,
            List<MemberRow> owners, AccessLevel viewerLevel, boolean admin, List<DeviceRow> devices,
            List<AnonymousEvent> events, boolean ok, @Nullable Component message) implements CustomPacketPayload {

        public static final Type<HubSnapshot> TYPE = new Type<>(id("hub_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HubSnapshot> STREAM_CODEC =
                StreamCodec.of(HubSnapshot::write, HubSnapshot::read);

        public HubSnapshot {
            name = name == null ? "" : clip(name, Installation.NAME_MAX);
            source = source == null ? LockdownSource.MANUAL : source;
            lockdownSinceSeconds = Math.max(0, lockdownSinceSeconds);
            autoReleaseInSeconds = Math.max(0, autoReleaseInSeconds);
            members = List.copyOf(members);
            owners = List.copyOf(owners);
            viewerLevel = viewerLevel == null ? AccessLevel.PUBLIC : viewerLevel;
            devices = List.copyOf(devices.size() > MAX_DEVICES ? devices.subList(0, MAX_DEVICES) : devices);
            events = List.copyOf(events.size() > MAX_EVENTS ? events.subList(0, MAX_EVENTS) : events);
        }

        /** How many devices are linked (the rows the snapshot carries). */
        public int deviceCount() {
            return devices.size();
        }

        /** How many listed devices report {@link DeviceState#OK}. */
        public int devicesOk() {
            int ok = 0;
            for (DeviceRow row : devices) {
                if (row.state() == DeviceState.OK) {
                    ok++;
                }
            }
            return ok;
        }

        private static void write(RegistryFriendlyByteBuf buf, HubSnapshot value) {
            buf.writeUUID(value.installationId);
            buf.writeUtf(value.name, Installation.NAME_MAX);
            buf.writeBoolean(value.lockdown);
            buf.writeVarInt(value.source.ordinal());
            buf.writeVarInt(value.lockdownSinceSeconds);
            buf.writeVarInt(value.autoReleaseInSeconds);
            buf.writeBoolean(value.lockdownEnabled);
            writeRows(buf, value.members);
            writeRows(buf, value.owners);
            writeLevel(buf, value.viewerLevel);
            buf.writeBoolean(value.admin);
            buf.writeVarInt(value.devices.size());
            for (DeviceRow row : value.devices) {
                buf.writeBlockPos(row.pos());
                Identifier.STREAM_CODEC.encode(buf, row.block());
                buf.writeVarInt(row.state().ordinal());
            }
            buf.writeVarInt(value.events.size());
            for (AnonymousEvent event : value.events) {
                AnonymousEvent.STREAM_CODEC.encode(buf, event);
            }
            buf.writeBoolean(value.ok);
            buf.writeBoolean(value.message != null);
            if (value.message != null) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.message);
            }
        }

        private static HubSnapshot read(RegistryFriendlyByteBuf buf) {
            UUID installationId = buf.readUUID();
            String name = buf.readUtf(Installation.NAME_MAX);
            boolean lockdown = buf.readBoolean();
            LockdownSource source = byOrdinal(LockdownSource.values(), buf.readVarInt());
            int since = buf.readVarInt();
            int release = buf.readVarInt();
            boolean lockdownEnabled = buf.readBoolean();
            List<MemberRow> members = readRows(buf);
            List<MemberRow> owners = readRows(buf);
            AccessLevel viewer = readLevel(buf);
            boolean admin = buf.readBoolean();
            int deviceCount = count(buf, MAX_DEVICES, "devices");
            List<DeviceRow> devices = new ArrayList<>(deviceCount);
            for (int i = 0; i < deviceCount; i++) {
                devices.add(new DeviceRow(buf.readBlockPos(), Identifier.STREAM_CODEC.decode(buf),
                        byOrdinal(DeviceState.values(), buf.readVarInt())));
            }
            int eventCount = count(buf, MAX_EVENTS, "events");
            List<AnonymousEvent> events = new ArrayList<>(eventCount);
            for (int i = 0; i < eventCount; i++) {
                events.add(AnonymousEvent.STREAM_CODEC.decode(buf));
            }
            boolean ok = buf.readBoolean();
            Component message = buf.readBoolean() ? ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf) : null;
            return new HubSnapshot(installationId, name, lockdown, source, since, release, lockdownEnabled, members,
                    owners, viewer, admin, devices, events, ok, message);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set an existing row's level; PUBLIC removes the row. */
    public record HubSetMember(UUID installationId, UUID target, AccessLevel level) implements CustomPacketPayload {

        public static final Type<HubSetMember> TYPE = new Type<>(id("hub_set_member"));
        public static final StreamCodec<FriendlyByteBuf, HubSetMember> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUUID(v.installationId);
                    buf.writeUUID(v.target);
                    writeLevel(buf, v.level);
                },
                buf -> new HubSetMember(buf.readUUID(), buf.readUUID(), readLevel(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Add a player by name at MEMBER or OFFICER. */
    public record HubAddMember(UUID installationId, String playerName, AccessLevel level) implements CustomPacketPayload {

        public static final Type<HubAddMember> TYPE = new Type<>(id("hub_add_member"));
        public static final StreamCodec<FriendlyByteBuf, HubAddMember> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUUID(v.installationId);
                    buf.writeUtf(clip(v.playerName, PlayerNames.MAX_NAME), PlayerNames.MAX_NAME);
                    writeLevel(buf, v.level);
                },
                buf -> new HubAddMember(buf.readUUID(), buf.readUtf(PlayerNames.MAX_NAME), readLevel(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Enter ({@code true}) or leave ({@code false}) lockdown. */
    public record HubLockdown(UUID installationId, boolean enabled) implements CustomPacketPayload {

        public static final Type<HubLockdown> TYPE = new Type<>(id("hub_lockdown"));
        public static final StreamCodec<FriendlyByteBuf, HubLockdown> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUUID(v.installationId);
                    buf.writeBoolean(v.enabled);
                },
                buf -> new HubLockdown(buf.readUUID(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Rename the installation (OWNER). */
    public record HubRename(UUID installationId, String name) implements CustomPacketPayload {

        public static final Type<HubRename> TYPE = new Type<>(id("hub_rename"));
        public static final StreamCodec<FriendlyByteBuf, HubRename> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUUID(v.installationId);
                    buf.writeUtf(clip(v.name, Installation.NAME_MAX), Installation.NAME_MAX);
                },
                buf -> new HubRename(buf.readUUID(), buf.readUtf(Installation.NAME_MAX)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
