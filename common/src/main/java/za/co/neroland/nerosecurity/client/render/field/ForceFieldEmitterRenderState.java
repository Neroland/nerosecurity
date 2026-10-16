package za.co.neroland.nerosecurity.client.render.field;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** What {@link ForceFieldEmitterRenderer} draws this frame; filled from the emitter's synced fields only. */
public class ForceFieldEmitterRenderState extends BlockEntityRenderState {

    public boolean valid;
    public Direction facing = Direction.NORTH;
    /** Core angle about the facing axis, degrees (the gyro turns at minus half). */
    public float angle;
    /** Core tint {@code 0xRRGGBB}. */
    public int tint;
    /** Full-bright core. */
    public boolean lit;
    /** Overloaded: the housing ring LED blinks {@code LOCK}. */
    public boolean overloadLed;
}
