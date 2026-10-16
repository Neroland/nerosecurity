package za.co.neroland.nerosecurity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.client.SecurityGeometry;
import za.co.neroland.nerosecurity.client.render.chest.SecureChestRenderer;
import za.co.neroland.nerosecurity.client.render.door.SecurityDoorRenderer;
import za.co.neroland.nerosecurity.client.render.hub.SecurityHubRenderer;
import za.co.neroland.nerosecurity.registry.ChestRegistrations;
import za.co.neroland.nerosecurity.registry.DoorRegistrations;
import za.co.neroland.nerosecurity.registry.NeroSecurityBlockEntities;

/**
 * The door, chest and hub renderer group, plus the facing helpers those renderers share.
 *
 * <p>Loader client init calls {@code ClientBlockEntityRenderers.registerAll(sink, DoorChestHubRenderers::register,
 * ...)}. The facing helpers rotate geometry authored facing north (front = -Z, the blockstate's {@code y: 0}) by
 * whole quarter turns on the CPU, exactly as the blockstate rotates the static model, so no renderer needs a
 * per-version {@code PoseStack} rotation.
 */
public final class DoorChestHubRenderers {

    /**
     * A plain light texture for single-colour emissive LEDs (the vertex colour carries the signal colour). A vanilla
     * texture, so an LED never shows the missing-texture checker.
     */
    public static final Identifier LED_TEXTURE = SecurityGeometry.blockTexture("security_led");

    private DoorChestHubRenderers() {
    }

    /** Registers the Security Door, Blast Door, Secure Chest and Security Hub renderers. */
    public static void register(ClientBlockEntityRenderers.Sink sink) {
        sink.register(DoorRegistrations.SECURITY_DOOR_ENTITY.get(), context -> new SecurityDoorRenderer(false));
        sink.register(DoorRegistrations.BLAST_DOOR_ENTITY.get(), context -> new SecurityDoorRenderer(true));
        sink.register(ChestRegistrations.SECURE_CHEST_BLOCK_ENTITY.get(), context -> new SecureChestRenderer());
        sink.register(NeroSecurityBlockEntities.SECURITY_HUB.get(), context -> new SecurityHubRenderer());
    }

    // ---- facing (pure) ----

    /** Quarter turns clockwise (seen from above) of a horizontal facing: N 0, E 1, S 2, W 3 (the blockstate's y / 90). */
    public static int quarterTurns(Direction facing) {
        if (facing == null) {
            return 0;
        }
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Rotates the model-pixel point {@code (x, z)} about the block centre (8, 8) by {@code q} quarter turns. */
    public static float[] turn(float x, float z, int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {16f - z, x};
            case 2 -> new float[] {16f - x, 16f - z};
            case 3 -> new float[] {z, 16f - x};
            default -> new float[] {x, z};
        };
    }

    // ---- faced emitters (model pixels in, block units out) ----

    /**
     * An axis-aligned box authored facing north (model pixels), turned by {@code q}; UVs in 16-unit texture space
     * on every face.
     */
    public static int box(VertexConsumer c, PoseStack.Pose pose, int q, float x0, float y0, float z0, float x1,
            float y1, float z1, float u0, float v0, float u1, float v1, int argb, int light) {
        float[] a = turn(x0, z0, q);
        float[] b = turn(x1, z1, q);
        return SecurityGeometry.boxPx(c, pose, Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), y1, Math.max(a[1], b[1]), u0, v0, u1, v1, argb, light);
    }

    /**
     * A double-sided rectangle in the authored XY plane at depth {@code z} (model pixels), turned by {@code q}: the
     * front of an LED, a strip or a clamp band. Full UV (0..1) unless the texture rect is given.
     */
    public static int plate(VertexConsumer c, PoseStack.Pose pose, int q, float x0, float y0, float x1, float y1,
            float z, float u0, float v0, float u1, float v1, int argb, int light) {
        float[] a = turn(x1, z, q);
        float[] b = turn(x0, z, q);
        float px = SecurityGeometry.PX;
        return SecurityGeometry.quad(c, pose,
                a[0] * px, y1 * px, a[1] * px,
                b[0] * px, y1 * px, b[1] * px,
                b[0] * px, y0 * px, b[1] * px,
                a[0] * px, y0 * px, a[1] * px,
                u0, v0, u1, v1, argb, light);
    }

    /** {@link #plate} with the full texture. */
    public static int plate(VertexConsumer c, PoseStack.Pose pose, int q, float x0, float y0, float x1, float y1,
            float z, int argb, int light) {
        return plate(c, pose, q, x0, y0, x1, y1, z, 0f, 0f, 1f, 1f, argb, light);
    }
}
