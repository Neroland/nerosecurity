package za.co.neroland.nerosecurity.menu.sensor;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerosecurity.sensor.SensorHost;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * What the shared sensor settings menu needs on top of {@link SensorHost}: the block entity itself (for
 * the open-menu binding and the distance check) and its module slots. The laser grid node and the camera
 * block entities implement this and return {@code new SensorSettingsMenu(id, inventory, this)} from
 * {@link MenuProvider#createMenu}.
 *
 * <p>The menu shows at most {@link SensorSettingsMenu#MAX_MODULE_SLOTS} module slots; a host with fewer
 * gets the rest as inactive placeholders.
 */
public interface SensorMenuHost extends SensorHost, MenuProvider {

    /** The block entity this host is (always {@code this}). */
    BlockEntity sensorBlockEntity();

    /** The host's module slots (range modules). */
    SecurityModuleSlots modules();

    /** For a laser: whether a facing partner node is in range. Cameras keep the default. */
    default boolean paired() {
        return true;
    }
}
