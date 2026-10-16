package za.co.neroland.nerosecurity.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.PlayerNames;

/**
 * The hub screen's payloads on {@code nerosecurity:main}. One clientbound snapshot for display; four
 * serverbound edit intents, each of which the server re-authorises from scratch. Strings are length-capped
 * on both encode and decode. The installation's signing secret is never part of any payload.
 */
public final class HubPayloads {

    /** Most rows a snapshot carries (owners plus members); the decoder refuses more. */
    public static final int MAX_ROWS = 1100;

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
     * Server truth for the open hub screen. Sent only to a player the resolver just allowed to open
     * this hub; carries display names for the installation's own rows only. Display only: the client
     * never decides anything from it.
     */
    public record HubSnapshot(UUID installationId, String name, boolean lockdown, List<MemberRow> members,
            List<MemberRow> owners, int deviceCount, AccessLevel viewerLevel) implements CustomPacketPayload {

        public static final Type<HubSnapshot> TYPE = new Type<>(id("hub_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, HubSnapshot> STREAM_CODEC =
                StreamCodec.of(HubSnapshot::write, HubSnapshot::read);

        public HubSnapshot {
            members = List.copyOf(members);
            owners = List.copyOf(owners);
        }

        private static void write(FriendlyByteBuf buf, HubSnapshot value) {
            buf.writeUUID(value.installationId);
            buf.writeUtf(clip(value.name, Installation.NAME_MAX), Installation.NAME_MAX);
            buf.writeBoolean(value.lockdown);
            writeRows(buf, value.members);
            writeRows(buf, value.owners);
            buf.writeVarInt(Math.max(0, value.deviceCount));
            writeLevel(buf, value.viewerLevel);
        }

        private static HubSnapshot read(FriendlyByteBuf buf) {
            return new HubSnapshot(buf.readUUID(), buf.readUtf(Installation.NAME_MAX), buf.readBoolean(),
                    readRows(buf), readRows(buf), Math.max(0, buf.readVarInt()), readLevel(buf));
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
