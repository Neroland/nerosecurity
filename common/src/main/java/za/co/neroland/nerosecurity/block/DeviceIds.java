package za.co.neroland.nerosecurity.block;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * Registry ids of devices that shared code recognises by id in the installation's device list (which
 * stores block ids, not classes), so the foundation never depends on the device classes themselves.
 * Each device's registration must use exactly these paths.
 */
public final class DeviceIds {

    public static final Identifier LASER_GRID_NODE = id("laser_grid_node");
    public static final Identifier SECURITY_CAMERA = id("security_camera");
    public static final Identifier ALARM = id("alarm");
    public static final Identifier SECURITY_MONITOR = id("security_monitor");

    private DeviceIds() {
    }

    /** Whether {@code blockId} is a sensor (camera or laser grid node): something that records presence. */
    public static boolean isSensor(Identifier blockId) {
        return LASER_GRID_NODE.equals(blockId) || SECURITY_CAMERA.equals(blockId);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
    }
}
