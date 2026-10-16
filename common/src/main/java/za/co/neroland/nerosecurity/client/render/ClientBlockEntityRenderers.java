package za.co.neroland.nerosecurity.client.render;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Cross-loader block-entity-renderer wiring (the Nerospace {@code Sink} seam).
 *
 * <p>The renderer set is identical on every loader, so each loader passes its own registration function as a
 * {@link Sink} (Fabric {@code BlockEntityRenderers.register}, NeoForge/Forge
 * {@code EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer}) and the renderer groups
 * register into it. Each group owns one static {@code register(Sink)} in its own class
 * ({@code DoorChestHubRenderers}, {@code SensorFieldRenderers}); loader client init calls:
 *
 * <pre>{@code
 * ClientBlockEntityRenderers.registerAll(sink,
 *         DoorChestHubRenderers::register,
 *         SensorFieldRenderers::register);
 * }</pre>
 *
 * Contributions run in the order given; a type registered twice keeps the loader's behaviour (last wins on
 * every supported loader), so each block entity type belongs to exactly one group.
 */
public final class ClientBlockEntityRenderers {

    /** A loader's BER-registration entry point. */
    public interface Sink {
        <T extends BlockEntity, S extends BlockEntityRenderState> void register(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);
    }

    /** One renderer group's registrations ({@code DoorChestHubRenderers::register}). */
    @FunctionalInterface
    public interface Contribution {
        void register(Sink sink);
    }

    private ClientBlockEntityRenderers() {
    }

    /** Registers every contribution into {@code sink}, in order. Null contributions are skipped. */
    public static void registerAll(Sink sink, Contribution... contributions) {
        if (sink == null || contributions == null) {
            return;
        }
        for (Contribution contribution : contributions) {
            if (contribution != null) {
                contribution.register(sink);
            }
        }
    }
}
