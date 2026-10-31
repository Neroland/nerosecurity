package za.co.neroland.nerosecurity.client.render.compat;

// Minecraft before 1.21.9 renders block entities immediately (render(..) into a MultiBufferSource) instead of
// through render states and a submit collector. Below 1.21.9 the build points the vanilla imports at these
// look-alikes (see legacyRenames), so the renderers stay one copy; the shim BlockEntityRenderer runs the
// extract + submit pair inside the old render call. Empty on 1.21.9 and later.
//? if <1.21.9 {
/*import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntityRenderState {

    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState = Blocks.AIR.defaultBlockState();
    public BlockEntityType<?> blockEntityType;
    public int lightCoords;
    public ModelFeatureRenderer.CrumblingOverlay breakProgress;
}
*///?}
