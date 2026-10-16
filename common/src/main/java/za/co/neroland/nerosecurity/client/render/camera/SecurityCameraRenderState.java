package za.co.neroland.nerosecurity.client.render.camera;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** What {@link SecurityCameraRenderer} draws this frame; filled from the camera's synced fields only. */
public class SecurityCameraRenderState extends BlockEntityRenderState {

    public boolean valid;
    public Direction facing = Direction.NORTH;
    /** Body pose about the arm pivot, authored frame (degrees). */
    public float xDeg;
    public float yDeg;
    public float zDeg;
    /** The REC LED (and its bloom) is lit this frame. */
    public boolean ledOn;
    /** LED colour, {@code 0xRRGGBB}. */
    public int ledColour;
}
