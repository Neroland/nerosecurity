package za.co.neroland.nerosecurity.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
//? if >=1.20.5 {
import net.minecraft.network.codec.ByteBufCodecs;
//?}
import net.minecraft.network.codec.StreamCodec;

import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The linking tool's data component: which installation it is pointed at, plus that installation's
 * display name for the tooltip. No player data. The name is a cached label only; every link re-reads the
 * installation from the store.
 */
public record SelectedHub(UUID installationId, String name) {

    //? if >=1.20.5 {
    private static final Codec<String> NAME_CODEC = Codec.string(0, Installation.NAME_MAX);
    //?} else {
    /*private static final Codec<String> NAME_CODEC = Codec.STRING.comapFlatMap(
            name -> name.length() <= Installation.NAME_MAX
                    ? com.mojang.serialization.DataResult.success(name)
                    : com.mojang.serialization.DataResult.error(() -> "Invalid selected hub name length"),
            name -> name);
    *///?}

    public static final Codec<SelectedHub> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("installation").forGetter(SelectedHub::installationId),
            NAME_CODEC.optionalFieldOf("name", "").forGetter(SelectedHub::name)
    ).apply(inst, SelectedHub::new));

    //? if >=1.20.5 {
    public static final StreamCodec<ByteBuf, SelectedHub> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SelectedHub::installationId,
            ByteBufCodecs.stringUtf8(Installation.NAME_MAX), SelectedHub::name,
            SelectedHub::new);
    //?} else {
    /*public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SelectedHub> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.installationId());
                buf.writeUtf(value.name(), Installation.NAME_MAX);
            },
            buf -> new SelectedHub(buf.readUUID(), buf.readUtf(Installation.NAME_MAX)));
    *///?}
}
