package za.co.neroland.nerosecurity.client.render.hub;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import za.co.neroland.nerosecurity.client.render.door.DoorLeds;

/** What the hub renderer draws this frame. */
public class SecurityHubRenderState extends BlockEntityRenderState {

    /** Facing as quarter turns from north. */
    public int quarterTurns;
    /** Recording LED {@code 0xRRGGBB}, or {@link DoorLeds#OFF}. */
    public int recording = DoorLeds.OFF;
    /** Lockdown strip {@code 0xRRGGBB}, or {@link DoorLeds#OFF}. */
    public int strip = DoorLeds.OFF;
}
