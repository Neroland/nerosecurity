package za.co.neroland.nerosecurity.client.render.alarm;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** What {@link AlarmBeaconRenderer} draws this frame; filled from the alarm's own {@code ACTIVE} blockstate. */
public class AlarmBeaconRenderState extends BlockEntityRenderState {

    public boolean active;
    /** Blade angle, degrees. */
    public float angle;
    /** Light column alpha 0..1. */
    public float columnAlpha;
}
