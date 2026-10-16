package za.co.neroland.nerosecurity.sensor;

/**
 * What the shared sensor settings menu and screen need from a sensor block entity. Both the laser grid
 * node and the camera implement it, so one menu/screen pair serves both.
 *
 * <p>Server-side contract: {@link #setSensorSettings} is called only after the payload handler has
 * re-resolved {@code CONFIGURE_DEVICE} and {@link SensorSettings#mayApply} allowed the change; the
 * implementation stores the value, marks itself changed and does nothing else.
 */
public interface SensorHost {

    SensorSettings sensorSettings();

    void setSensorSettings(SensorSettings settings);

    /**
     * Whether the sensor can sense right now (linked, powered, and for a laser paired; for a camera also
     * {@code camerasEnabled}). Drives the {@code ARMED} blockstate: an unarmed sensor is visibly dark.
     */
    boolean armed();

    long energyAmount();

    long energyCapacity();

    /** True for a laser grid node (no field of view), false for a camera. */
    boolean isLaser();
}
