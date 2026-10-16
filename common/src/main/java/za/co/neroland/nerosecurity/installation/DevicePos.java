package za.co.neroland.nerosecurity.installation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * A block position in a dimension: the key of the device index. A small record codec of its own
 * ({@code dim}, {@code x}, {@code y}, {@code z}) keeps the store and its tests free of game bootstrap.
 */
public record DevicePos(Identifier dimension, BlockPos pos) {

    public static final Codec<DevicePos> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("dim").forGetter(DevicePos::dimension),
            Codec.INT.fieldOf("x").forGetter(p -> p.pos().getX()),
            Codec.INT.fieldOf("y").forGetter(p -> p.pos().getY()),
            Codec.INT.fieldOf("z").forGetter(p -> p.pos().getZ())
    ).apply(inst, (dim, x, y, z) -> new DevicePos(dim, new BlockPos(x, y, z))));

    public DevicePos {
        pos = pos.immutable();
    }

    public static DevicePos of(ServerLevel level, BlockPos pos) {
        return new DevicePos(level.dimension().identifier(), pos);
    }

    /** Whether {@code other} is in the same dimension and within {@code maxBlocks} Manhattan distance. */
    public boolean withinManhattan(DevicePos other, int maxBlocks) {
        return other != null && dimension.equals(other.dimension) && pos.distManhattan(other.pos) <= maxBlocks;
    }
}
