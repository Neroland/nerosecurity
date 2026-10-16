package za.co.neroland.nerosecurity.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The linking tool's data component: which installation it is pointed at, plus that installation's
 * display name for the tooltip. No player data. The name is a cached label only; every link re-reads the
 * installation from the store.
 */
public record SelectedHub(UUID installationId, String name) {

    public static final Codec<SelectedHub> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("installation").forGetter(SelectedHub::installationId),
            Codec.string(0, Installation.NAME_MAX).optionalFieldOf("name", "").forGetter(SelectedHub::name)
    ).apply(inst, SelectedHub::new));

    public static final StreamCodec<ByteBuf, SelectedHub> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SelectedHub::installationId,
            ByteBufCodecs.stringUtf8(Installation.NAME_MAX), SelectedHub::name,
            SelectedHub::new);
}
