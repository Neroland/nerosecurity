package za.co.neroland.nerosecurity.client.render.laser;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** What {@link LaserBeamRenderer} draws this frame; filled from the node's synced fields only. */
public class LaserBeamRenderState extends BlockEntityRenderState {

    /** Armed and paired: anything to draw at all. */
    public boolean active;
    public Direction facing = Direction.NORTH;
    /** Lens → midpoint, block-relative. */
    public float fromX;
    public float fromY;
    public float fromZ;
    public float toX;
    public float toY;
    public float toZ;
    /** 0..1 slow pulse of the glow. */
    public float pulse;
    /** UV start of the scrolling texture. */
    public float uStart;
    /** The trip ring is drawn. */
    public boolean trip;
    /** The core is in the white half of the trip strobe. */
    public boolean white;
}
