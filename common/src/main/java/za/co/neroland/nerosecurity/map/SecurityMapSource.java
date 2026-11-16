package za.co.neroland.nerosecurity.map;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AccessSources;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldShape;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * What the map provider reads, behind one seam so tests can seed it without a server. The live source reads the
 * installation store, the same no-operator resolver the link module uses, and force-field emitters only in chunks
 * that are already loaded (it never loads a chunk).
 */
public interface SecurityMapSource {

    /** A field's horizontal footprint, inclusive block coordinates. */
    record Footprint(int minX, int minZ, int maxX, int maxZ) {
    }

    @Nullable MinecraftServer server();

    @Nullable SecurityInstallations installations();

    AccessResolver resolver();

    /** The footprint of a loaded, enabled, validly configured emitter at {@code emitter}, else empty. */
    Optional<Footprint> field(ResourceKey<Level> dimension, DevicePos emitter);

    /** The link module's resolver: every access source, no operator override. */
    AccessResolver NO_OPERATOR = new AccessResolver(AccessSources::all, AdminCheck.NONE,
            NeroSecurityConfig::isLockdownEnabled);

    /** The live source for {@code server}, or {@code null} without one. */
    static @Nullable SecurityMapSource of(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return new SecurityMapSource() {
            @Override
            public MinecraftServer server() {
                return server;
            }

            @Override
            public SecurityInstallations installations() {
                return SecurityInstallations.get(server);
            }

            @Override
            public AccessResolver resolver() {
                return NO_OPERATOR;
            }

            @Override
            public Optional<Footprint> field(ResourceKey<Level> dimension, DevicePos emitter) {
                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    return Optional.empty();
                }
                BlockPos pos = emitter.pos();
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                BlockEntity be = chunk == null ? null : chunk.getBlockEntity(pos);
                if (!(be instanceof ForceFieldEmitterBlockEntity field) || !field.settingsValid()
                        || !field.settings().enabled()) {
                    return Optional.empty();
                }
                Direction facing = field.facing();
                return Optional.of(footprint(pos.getX(), pos.getZ(), facing.getStepX(), facing.getStepY(),
                        facing.getStepZ(), field.settings()));
            }
        };
    }

    /**
     * The horizontal footprint of a field ({@code FieldGeometry.positions} as a box): centred {@code offset} blocks
     * in front of the emitter, {@code width} across (east-west when the emitter faces up or down, else to its
     * right), {@code height} north-south when facing up or down (else vertical), and for a box {@code depth} along
     * the facing. Pure.
     */
    static Footprint footprint(int ex, int ez, int fx, int fy, int fz, FieldSettings s) {
        int hw = half(s.width());
        int hh = half(s.height());
        int hd = s.shape() == FieldShape.BOX ? half(s.depth()) : 0;
        int cx = ex + fx * s.offset();
        int cz = ez + fz * s.offset();
        if (fy != 0) {
            return new Footprint(cx - hw, cz - hh, cx + hw, cz + hh);
        }
        int spanX = Math.abs(fz) * hw + Math.abs(fx) * hd;
        int spanZ = Math.abs(fx) * hw + Math.abs(fz) * hd;
        return new Footprint(cx - spanX, cz - spanZ, cx + spanX, cz + spanZ);
    }

    private static int half(int n) {
        return Math.max(0, (n - 1) / 2);
    }
}
