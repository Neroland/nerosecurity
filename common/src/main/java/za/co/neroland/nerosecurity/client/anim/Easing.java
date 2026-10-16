package za.co.neroland.nerosecurity.client.anim;

/** Pure easing curves for device animation. Input and output are 0..1 (input clamped). */
public final class Easing {

    private Easing() {
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public static double clamp01(double t) {
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Cubic ease-in-out: security door panels. */
    public static float easeInOutCubic(float t) {
        float x = clamp01(t);
        if (x < 0.5f) {
            return 4f * x * x * x;
        }
        float f = -2f * x + 2f;
        return 1f - f * f * f / 2f;
    }

    /** Quartic ease-in-out: the heavier blast door. */
    public static float easeInOutQuart(float t) {
        float x = clamp01(t);
        if (x < 0.5f) {
            return 8f * x * x * x * x;
        }
        float f = -2f * x + 2f;
        return 1f - f * f * f * f / 2f;
    }

    /** Cubic ease-out: the chest lid. */
    public static float easeOutCubic(float t) {
        float f = 1f - clamp01(t);
        return 1f - f * f * f;
    }
}
