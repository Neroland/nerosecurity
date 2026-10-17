package za.co.neroland.nerosecurity.client.render.chest;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import za.co.neroland.nerosecurity.client.render.door.DoorLeds;

/** What the chest renderer draws this frame. */
public class SecureChestRenderState extends BlockEntityRenderState {

    /** Facing as quarter turns from north. */
    public int quarterTurns;
    /** Lid angle in degrees, 0 shut .. 90 open. */
    public float lidDegrees;
    /** Lock-plate tint {@code 0xRRGGBB}: the minimum level's colour, OFF while unlinked, or LOCK during a deny flash. */
    public int plate;
    /** The LED {@code 0xRRGGBB}, or {@link DoorLeds#OFF}. */
    public int led = DoorLeds.OFF;
}
