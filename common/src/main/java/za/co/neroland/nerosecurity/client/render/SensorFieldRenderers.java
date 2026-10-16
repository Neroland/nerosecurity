package za.co.neroland.nerosecurity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.Direction;

import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.render.alarm.AlarmBeaconRenderer;
import za.co.neroland.nerosecurity.client.render.camera.SecurityCameraRenderer;
import za.co.neroland.nerosecurity.client.render.field.ForceFieldEmitterRenderer;
import za.co.neroland.nerosecurity.client.render.laser.LaserBeamRenderer;
import za.co.neroland.nerosecurity.registry.AlarmRegistrations;
import za.co.neroland.nerosecurity.registry.CameraRegistrations;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;

/**
 * The sensor and field renderer group: laser grid node, security camera, alarm and force field emitter. The force
 * field block itself has no renderer (its motion is an animated texture strip, and lockdown swaps its model).
 *
 * <p>Loader client init passes {@code SensorFieldRenderers::register} to
 * {@link ClientBlockEntityRenderers#registerAll(ClientBlockEntityRenderers.Sink, ClientBlockEntityRenderers.Contribution...)}.
 */
public final class SensorFieldRenderers {

    private SensorFieldRenderers() {
    }

    /** Registers the four renderers of this group into the loader's sink. */
    public static void register(ClientBlockEntityRenderers.Sink sink) {
        if (sink == null) {
            return;
        }
        sink.register(LaserRegistrations.LASER_GRID_NODE_ENTITY.get(), context -> new LaserBeamRenderer());
        sink.register(CameraRegistrations.SECURITY_CAMERA_ENTITY.get(), context -> new SecurityCameraRenderer());
        sink.register(AlarmRegistrations.ALARM_BLOCK_ENTITY.get(), context -> new AlarmBeaconRenderer());
        sink.register(FieldRegistrations.EMITTER_BE.get(), context -> new ForceFieldEmitterRenderer());
    }

    /**
     * Facing frames shared by the four renderers. Models are authored facing north (front toward -Z, mount on the
     * south face) and the blockstates rotate them with {@code y} 90/180/270 for east/south/west and {@code x} 270/90
     * for up/down; {@link #toBlock} applies exactly that rotation about the block centre, on the CPU, so the BER
     * geometry lines up with the static model on every facing without a PoseStack rotation (no version branches).
     * The point functions are pure and unit-tested.
     */
    public static final class Frame {

        private static final int[][] FACES = {
            {2, 3, 7, 6}, // +Y
            {0, 4, 5, 1}, // -Y
            {6, 4, 0, 2}, // -Z
            {3, 1, 5, 7}, // +Z
            {2, 0, 1, 3}, // -X
            {7, 5, 4, 6}, // +X
        };

        private Frame() {
        }

        /** A direction authored facing north, rotated into {@code facing}'s frame. */
        public static float[] dirToBlock(Direction facing, float x, float y, float z) {
            return switch (facing == null ? Direction.NORTH : facing) {
                case NORTH -> new float[] {x, y, z};
                case SOUTH -> new float[] {-x, y, -z};
                case EAST -> new float[] {-z, y, x};
                case WEST -> new float[] {z, y, -x};
                case UP -> new float[] {x, -z, y};
                case DOWN -> new float[] {x, z, -y};
            };
        }

        /** The inverse of {@link #dirToBlock}: a block-frame direction back into the authored (north) frame. */
        public static float[] dirToLocal(Direction facing, float x, float y, float z) {
            return switch (facing == null ? Direction.NORTH : facing) {
                case NORTH -> new float[] {x, y, z};
                case SOUTH -> new float[] {-x, y, -z};
                case EAST -> new float[] {z, y, -x};
                case WEST -> new float[] {-z, y, x};
                case UP -> new float[] {x, z, -y};
                case DOWN -> new float[] {x, -z, y};
            };
        }

        /** A point authored facing north (block units, 0..1) → block units for {@code facing} (rotated about the centre). */
        public static float[] toBlock(Direction facing, float x, float y, float z) {
            float[] d = dirToBlock(facing, x - 0.5f, y - 0.5f, z - 0.5f);
            return new float[] {d[0] + 0.5f, d[1] + 0.5f, d[2] + 0.5f};
        }

        /**
         * A model-pixel point (0..16, authored north) turned about {@code pivot} (pixels) by {@code xDeg}, then
         * {@code yDeg}, then {@code zDeg} after scaling about the pivot by {@code scale}, then placed for
         * {@code facing}. Returns block units.
         */
        public static float[] placePx(Direction facing, float x, float y, float z, float[] pivot,
                float xDeg, float yDeg, float zDeg, float scale) {
            float[] r = SecurityGeometry.rotate((x - pivot[0]) * scale, (y - pivot[1]) * scale, (z - pivot[2]) * scale,
                    xDeg, yDeg, zDeg);
            return toBlock(facing, (r[0] + pivot[0]) / 16f, (r[1] + pivot[1]) / 16f, (r[2] + pivot[2]) / 16f);
        }

        /**
         * A box [x0,y0,z0]→[x1,y1,z1] in model pixels, turned and placed as {@link #placePx}; full UV
         * {@code (u0,v0)-(u1,v1)} on each face. Double-sided.
         *
         * @return {@link SecurityGeometry#BOX_VERTICES}
         */
        public static int box(VertexConsumer c, PoseStack.Pose pose, Direction facing,
                float x0, float y0, float z0, float x1, float y1, float z1, float[] pivot,
                float xDeg, float yDeg, float zDeg, float scale,
                float u0, float v0, float u1, float v1, int argb, int light) {
            float[][] k = new float[8][];
            int i = 0;
            for (int sx = 0; sx <= 1; sx++) {
                for (int sy = 0; sy <= 1; sy++) {
                    for (int sz = 0; sz <= 1; sz++) {
                        k[i++] = placePx(facing, sx == 0 ? x0 : x1, sy == 0 ? y0 : y1, sz == 0 ? z0 : z1, pivot,
                                xDeg, yDeg, zDeg, scale);
                    }
                }
            }
            int n = 0;
            for (int[] f : FACES) {
                float[] a = k[f[0]];
                float[] b = k[f[1]];
                float[] cc = k[f[2]];
                float[] d = k[f[3]];
                n += SecurityGeometry.quad(c, pose, a[0], a[1], a[2], b[0], b[1], b[2], cc[0], cc[1], cc[2],
                        d[0], d[1], d[2], u0, v0, u1, v1, argb, light);
            }
            return n;
        }

        /**
         * A flat quad in the plane {@code z} (model pixels, authored north) spanning [x0,y0]→[x1,y1], turned and
         * placed as {@link #placePx}. Double-sided.
         *
         * @return {@link SecurityGeometry#FACE_VERTICES}
         */
        public static int quadZ(VertexConsumer c, PoseStack.Pose pose, Direction facing,
                float x0, float y0, float x1, float y1, float z, float[] pivot,
                float xDeg, float yDeg, float zDeg,
                float u0, float v0, float u1, float v1, int argb, int light) {
            float[] a = placePx(facing, x0, y1, z, pivot, xDeg, yDeg, zDeg, 1f);
            float[] b = placePx(facing, x1, y1, z, pivot, xDeg, yDeg, zDeg, 1f);
            float[] cc = placePx(facing, x1, y0, z, pivot, xDeg, yDeg, zDeg, 1f);
            float[] d = placePx(facing, x0, y0, z, pivot, xDeg, yDeg, zDeg, 1f);
            return SecurityGeometry.quad(c, pose, a[0], a[1], a[2], b[0], b[1], b[2], cc[0], cc[1], cc[2],
                    d[0], d[1], d[2], u0, v0, u1, v1, argb, light);
        }
    }
}
