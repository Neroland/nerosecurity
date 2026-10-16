package za.co.neroland.nerosecurity.client.render.door;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** What the door renderer draws this frame, copied from the controller's synced fields in extract. */
public class SecurityDoorRenderState extends BlockEntityRenderState {

    /** Facing as quarter turns from north. */
    public int quarterTurns;
    /** Blast door (2x2 iris) rather than security door (1x2 sliding panels). */
    public boolean blast;
    /** Panels are drawn (the block state says ANIMATING and something is still visible). */
    public boolean panels;
    /** Eased open progress 0 (shut) .. 1 (fully retracted). */
    public float progress;
    /** Lockdown: the clamp bars glow. */
    public boolean sealed;
    /** Status LED {@code 0xRRGGBB}, or {@link DoorLeds#OFF}. */
    public int status = DoorLeds.OFF;
    /** Recording LED {@code 0xRRGGBB}, or {@link DoorLeds#OFF}. */
    public int recording = DoorLeds.OFF;
}
