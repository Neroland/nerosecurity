package za.co.neroland.nerosecurity.client.render.alarm;

/** The alarm beacon's pure maths: blade angle, blade endpoints and light-column alpha. Unit-tested. */
public final class AlarmBeaconMath {

    /** Blade spin, degrees per tick (one revolution per 20 ticks). */
    public static final float DEGREES_PER_TICK = 18f;
    /** Blade hub (dome centre) and reach, model pixels. */
    public static final float HUB_X = 8f;
    public static final float HUB_Z = 8f;
    public static final float BLADE_Y0 = 4f;
    public static final float BLADE_Y1 = 9f;
    public static final float BLADE_RADIUS = 3.5f;
    /** Light column: 0.5 × 0.5 blocks from y = 11/16 up six blocks. */
    public static final float COLUMN_HALF = 0.25f;
    public static final float COLUMN_Y0 = 11f / 16f;
    public static final float COLUMN_HEIGHT = 6f;
    public static final int COLUMN_PERIOD = 10;

    private AlarmBeaconMath() {
    }

    /** Blade angle in degrees, 0..360; 0 when still. Continuous across the wrap. */
    public static float bladeAngle(long gameTime, float partial, boolean animate) {
        if (!animate) {
            return 0f;
        }
        double t = Math.floorMod(gameTime, 20L) + (double) partial;
        return (float) ((DEGREES_PER_TICK * t) % 360.0D);
    }

    /** Blade tip {@code {x, z}} in model pixels for a blade at {@code degrees} (the opposite blade is +180). */
    public static float[] bladeTip(float degrees) {
        double r = Math.toRadians(degrees);
        return new float[] {(float) (HUB_X + BLADE_RADIUS * Math.cos(r)), (float) (HUB_Z + BLADE_RADIUS * Math.sin(r))};
    }

    /** Column alpha {@code 0.18 + 0.08 sin(2 pi t / 10)}; 0.18 when still. */
    public static float columnAlpha(long gameTime, float partial, boolean animate) {
        if (!animate) {
            return 0.18f;
        }
        double t = Math.floorMod(gameTime, (long) COLUMN_PERIOD) + (double) partial;
        return (float) (0.18D + 0.08D * Math.sin(2.0D * Math.PI * t / COLUMN_PERIOD));
    }
}
