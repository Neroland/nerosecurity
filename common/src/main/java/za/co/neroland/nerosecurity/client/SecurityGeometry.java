package za.co.neroland.nerosecurity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * Custom-geometry helpers for NeroSecurity block entity renderers (the Nerospace {@code box/face/vertex} recipe).
 *
 * <p>Every face is emitted <b>double-sided</b> (front + reversed back, 8 vertices) as complete vertices
 * (position, colour, UV, overlay, light, normal), for use inside
 * {@code collector.order(1).submitCustomGeometry(poseStack, renderType, (pose, c) -> ...)}. Coordinates are in block
 * units (0..1); the {@code *Px} variants take model pixels (0..16). Colours are {@code 0xAARRGGBB}; use
 * {@link SecurityColours#argb(int, int)} to build one from a palette RGB. Light is packed light
 * ({@link SecurityColours#FULL_BRIGHT} for emissives, the render state's {@code lightCoords} for lit metal).
 *
 * <p>Every emitting method <b>returns the number of vertices written</b> so a renderer can add it to
 * {@link BerStats#vertices(int)} without counting by hand. Rotations are applied on the CPU to the emitted
 * vertices (no PoseStack rotation), so the helpers need no per-version branches.
 */
public final class SecurityGeometry {

    /** Vertices per double-sided face. */
    public static final int FACE_VERTICES = 8;
    /** Vertices per double-sided six-face box. */
    public static final int BOX_VERTICES = 6 * FACE_VERTICES;
    /** One model pixel in block units. */
    public static final float PX = 1f / 16f;

    private SecurityGeometry() {
    }

    // ---- render types ----

    /** {@code textures/block/<name>.png} in this mod's namespace. */
    public static Identifier blockTexture(String name) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "textures/block/" + name + ".png");
    }

    /** Lit, alpha-tested (opaque metal, panels, lids). */
    public static RenderType cutout(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    /** Translucent and emissive (LEDs, beams, blades, light columns, cores). Pair with FULL_BRIGHT light. */
    public static RenderType emissive(Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture);
    }

    /** Model pixels → block units. */
    public static float px(float pixels) {
        return pixels * PX;
    }

    // ---- vertices and faces ----

    /** One complete vertex. */
    public static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, float u, float v,
            float nx, float ny, float nz, int argb, int light) {
        c.addVertex(pose, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * A double-sided quad a→b→c→d with the given normal and UV rectangle (a=(u0,v0), b=(u1,v0), c=(u1,v1), d=(u0,v1)).
     *
     * @return {@link #FACE_VERTICES}
     */
    public static int face(VertexConsumer c, PoseStack.Pose pose, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1, int argb, int light) {
        vertex(c, pose, ax, ay, az, u0, v0, nx, ny, nz, argb, light);
        vertex(c, pose, bx, by, bz, u1, v0, nx, ny, nz, argb, light);
        vertex(c, pose, cx, cy, cz, u1, v1, nx, ny, nz, argb, light);
        vertex(c, pose, dx, dy, dz, u0, v1, nx, ny, nz, argb, light);
        vertex(c, pose, dx, dy, dz, u0, v1, -nx, -ny, -nz, argb, light);
        vertex(c, pose, cx, cy, cz, u1, v1, -nx, -ny, -nz, argb, light);
        vertex(c, pose, bx, by, bz, u1, v0, -nx, -ny, -nz, argb, light);
        vertex(c, pose, ax, ay, az, u0, v0, -nx, -ny, -nz, argb, light);
        return FACE_VERTICES;
    }

    /** A double-sided quad whose normal is computed from (b−a)×(d−a). */
    public static int quad(VertexConsumer c, PoseStack.Pose pose,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1, int argb, int light) {
        float[] n = normal(ax, ay, az, bx, by, bz, dx, dy, dz);
        return face(c, pose, n[0], n[1], n[2], ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, u0, v0, u1, v1,
                argb, light);
    }

    // ---- boxes ----

    /** Axis-aligned box in block units, full texture (0..1) on every face. */
    public static int box(VertexConsumer c, PoseStack.Pose pose,
            float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light) {
        return box(c, pose, x0, y0, z0, x1, y1, z1, 0f, 0f, 1f, 1f, argb, light);
    }

    /** Axis-aligned box in block units with one UV rectangle (0..1 texture space) on every face. */
    public static int box(VertexConsumer c, PoseStack.Pose pose,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float u0, float v0, float u1, float v1, int argb, int light) {
        int n = 0;
        n += face(c, pose, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1, argb, light);
        n += face(c, pose, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, argb, light);
        n += face(c, pose, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, u0, v0, u1, v1, argb, light);
        n += face(c, pose, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, argb, light);
        n += face(c, pose, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, u0, v0, u1, v1, argb, light);
        n += face(c, pose, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, u0, v0, u1, v1, argb, light);
        return n;
    }

    /** {@link #box} in model pixels (0..16), full texture per face. */
    public static int boxPx(VertexConsumer c, PoseStack.Pose pose,
            float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light) {
        return box(c, pose, x0 * PX, y0 * PX, z0 * PX, x1 * PX, y1 * PX, z1 * PX, argb, light);
    }

    /** {@link #box} in model pixels with a UV rectangle in texture pixels of a 16-unit sheet (like model JSON). */
    public static int boxPx(VertexConsumer c, PoseStack.Pose pose,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float u0, float v0, float u1, float v1, int argb, int light) {
        return box(c, pose, x0 * PX, y0 * PX, z0 * PX, x1 * PX, y1 * PX, z1 * PX,
                u0 * PX, v0 * PX, u1 * PX, v1 * PX, argb, light);
    }

    /**
     * A box of half-extents {@code (hx, hy, hz)} centred on {@code (cx, cy, cz)}, rotated about its centre by
     * {@code xDeg} (about X), then {@code yDeg} (about Y), then {@code zDeg} (about Z). Block units, full UV.
     * Used for spinning emitter cores and gyro rings.
     */
    public static int cubeRotated(VertexConsumer c, PoseStack.Pose pose, float cx, float cy, float cz,
            float hx, float hy, float hz, float xDeg, float yDeg, float zDeg, int argb, int light) {
        float[][] corners = new float[8][];
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    corners[i++] = rotate(sx * hx, sy * hy, sz * hz, xDeg, yDeg, zDeg);
                }
            }
        }
        // corner index = ((sx>0)?4:0) + ((sy>0)?2:0) + ((sz>0)?1:0)
        int[][] faces = {
            {2, 3, 7, 6}, // +Y
            {0, 4, 5, 1}, // -Y
            {6, 4, 0, 2}, // -Z
            {3, 1, 5, 7}, // +Z
            {2, 0, 1, 3}, // -X
            {7, 5, 4, 6}, // +X
        };
        int n = 0;
        for (int[] f : faces) {
            float[] a = corners[f[0]];
            float[] b = corners[f[1]];
            float[] cc = corners[f[2]];
            float[] d = corners[f[3]];
            n += quad(c, pose, cx + a[0], cy + a[1], cz + a[2], cx + b[0], cy + b[1], cz + b[2],
                    cx + cc[0], cy + cc[1], cz + cc[2], cx + d[0], cy + d[1], cz + d[2], 0f, 0f, 1f, 1f, argb, light);
        }
        return n;
    }

    // ---- beams ----

    /**
     * Two crossed double-sided quads along the segment a→b with half-width {@code halfWidth} (block units): the
     * laser beam. {@code u} runs along the beam from {@code uStart} to {@code uStart + uLength} (scroll it with time);
     * {@code v} spans 0..1 across the width.
     *
     * @return {@code 2 * FACE_VERTICES}, or 0 for a degenerate segment
     */
    public static int quadBetween(VertexConsumer c, PoseStack.Pose pose,
            float ax, float ay, float az, float bx, float by, float bz, float halfWidth,
            float uStart, float uLength, int argb, int light) {
        float[][] p = beamOffsets(ax, ay, az, bx, by, bz, halfWidth);
        if (p == null) {
            return 0;
        }
        float u0 = uStart;
        float u1 = uStart + uLength;
        int n = 0;
        for (float[] o : p) {
            n += quad(c, pose,
                    ax - o[0], ay - o[1], az - o[2],
                    bx - o[0], by - o[1], bz - o[2],
                    bx + o[0], by + o[1], bz + o[2],
                    ax + o[0], ay + o[1], az + o[2],
                    u0, 0f, u1, 1f, argb, light);
        }
        return n;
    }

    // ---- pure math (unit-testable) ----

    /**
     * The two perpendicular half-width offset vectors for crossed beam quads along a→b, each of length
     * {@code halfWidth}; {@code null} when a and b coincide.
     */
    public static float[][] beamOffsets(float ax, float ay, float az, float bx, float by, float bz, float halfWidth) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6f) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        // Reference axis least parallel to the beam.
        float rx = 0f;
        float ry = 1f;
        float rz = 0f;
        if (Math.abs(dy) > 0.9f) {
            rx = 1f;
            ry = 0f;
        }
        float[] p1 = normalise(dy * rz - dz * ry, dz * rx - dx * rz, dx * ry - dy * rx);
        float[] p2 = normalise(dy * p1[2] - dz * p1[1], dz * p1[0] - dx * p1[2], dx * p1[1] - dy * p1[0]);
        return new float[][] {
            {p1[0] * halfWidth, p1[1] * halfWidth, p1[2] * halfWidth},
            {p2[0] * halfWidth, p2[1] * halfWidth, p2[2] * halfWidth},
        };
    }

    /** Rotates a vector about X, then Y, then Z (degrees, right-handed). */
    public static float[] rotate(float x, float y, float z, float xDeg, float yDeg, float zDeg) {
        double a = Math.toRadians(xDeg);
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        double y1 = y * ca - z * sa;
        double z1 = y * sa + z * ca;
        double b = Math.toRadians(yDeg);
        double cb = Math.cos(b);
        double sb = Math.sin(b);
        double x2 = x * cb + z1 * sb;
        double z2 = -x * sb + z1 * cb;
        double g = Math.toRadians(zDeg);
        double cg = Math.cos(g);
        double sg = Math.sin(g);
        double x3 = x2 * cg - y1 * sg;
        double y3 = x2 * sg + y1 * cg;
        return new float[] {(float) x3, (float) y3, (float) z2};
    }

    private static float[] normal(float ax, float ay, float az, float bx, float by, float bz,
            float dx, float dy, float dz) {
        float ux = bx - ax;
        float uy = by - ay;
        float uz = bz - az;
        float vx = dx - ax;
        float vy = dy - ay;
        float vz = dz - az;
        return normalise(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
    }

    private static float[] normalise(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-9f) {
            return new float[] {0f, 1f, 0f};
        }
        return new float[] {x / len, y / len, z / len};
    }
}
