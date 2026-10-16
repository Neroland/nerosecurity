package za.co.neroland.nerosecurity.client;

import java.util.Locale;

/**
 * Per-frame NeroSecurity block-entity-renderer counters for the BER budget record ({@code docs/BER-BUDGET.md}).
 *
 * <p>Renderer contract (all calls are a few integer adds; render thread only):
 * <ol>
 *   <li>first line of {@code extractRenderState}: {@code BerStats.frame(gameTime, partialTick)} (rolls the frame
 *       over when the pair changes — two real frames never share a game time and partial tick unless the game is
 *       paused, when frames merge, which only lowers the average frame count while paused);</li>
 *   <li>{@code long t = BerStats.start();} … at the end of extract and of submit {@code BerStats.stop(t);}</li>
 *   <li>{@code BerStats.culled()} on every early return that submits nothing (state gating), and from a
 *       {@code shouldRender} that returns false;</li>
 *   <li>{@code BerStats.submitted()} once per BER that submits geometry, and {@code BerStats.vertices(n)} with the
 *       counts the {@link SecurityGeometry} helpers return.</li>
 * </ol>
 * The last {@value #WINDOW} closed frames are kept in a ring; {@link #average()} averages them. {@link #f3Line()}
 * is the debug-screen line and {@link #record()} the {@code /nerosecurity ber} dump. Pure Java, unit-testable.
 */
public final class BerStats {

    /** Frames averaged by {@link #average()}. */
    public static final int WINDOW = 200;

    private static final long[] SUBMITTED = new long[WINDOW];
    private static final long[] CULLED = new long[WINDOW];
    private static final long[] VERTICES = new long[WINDOW];
    private static final long[] NANOS = new long[WINDOW];
    private static int head;
    private static int filled;

    private static long curSubmitted;
    private static long curCulled;
    private static long curVertices;
    private static long curNanos;
    private static long frameGameTime = Long.MIN_VALUE;
    private static float framePartial = Float.NaN;
    private static boolean frameOpen;

    /** An average over the window. */
    public record Snapshot(int frames, double submitted, double culled, double vertices, double millis,
            long peakVertices, double peakMillis) {
    }

    private BerStats() {
    }

    /** Marks the frame the following counts belong to; a new (gameTime, partial) pair closes the previous frame. */
    public static void frame(long gameTime, float partialTick) {
        if (frameOpen && gameTime == frameGameTime && Float.compare(partialTick, framePartial) == 0) {
            return;
        }
        if (frameOpen) {
            endFrame();
        }
        frameGameTime = gameTime;
        framePartial = partialTick;
        frameOpen = true;
    }

    /** Closes the current frame into the ring (also usable from an explicit end-of-frame hook). */
    public static void endFrame() {
        if (!frameOpen) {
            return;
        }
        SUBMITTED[head] = curSubmitted;
        CULLED[head] = curCulled;
        VERTICES[head] = curVertices;
        NANOS[head] = curNanos;
        head = (head + 1) % WINDOW;
        if (filled < WINDOW) {
            filled++;
        }
        curSubmitted = 0;
        curCulled = 0;
        curVertices = 0;
        curNanos = 0;
        frameOpen = false;
    }

    public static void submitted() {
        curSubmitted++;
    }

    public static void culled() {
        curCulled++;
    }

    public static void vertices(int count) {
        if (count > 0) {
            curVertices += count;
        }
    }

    /** A timing start mark ({@code System.nanoTime}). */
    public static long start() {
        return System.nanoTime();
    }

    /** Adds the time since {@code startNanos} to the current frame. */
    public static void stop(long startNanos) {
        long d = System.nanoTime() - startNanos;
        if (d > 0) {
            curNanos += d;
        }
    }

    /** Adds explicit nanoseconds (tests). */
    public static void nanos(long nanos) {
        if (nanos > 0) {
            curNanos += nanos;
        }
    }

    /** Average of the closed frames in the window (all zeros before the first frame closes). */
    public static Snapshot average() {
        int n = filled;
        if (n == 0) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0);
        }
        long s = 0;
        long c = 0;
        long v = 0;
        long t = 0;
        long peakV = 0;
        long peakT = 0;
        for (int i = 0; i < n; i++) {
            s += SUBMITTED[i];
            c += CULLED[i];
            v += VERTICES[i];
            t += NANOS[i];
            peakV = Math.max(peakV, VERTICES[i]);
            peakT = Math.max(peakT, NANOS[i]);
        }
        return new Snapshot(n, (double) s / n, (double) c / n, (double) v / n, t / 1.0e6 / n, peakV, peakT / 1.0e6);
    }

    /** {@code [NeroSecurity] BER n/m v verts t ms}: submitted / (submitted + culled), vertices, ms (window average). */
    public static String f3Line() {
        Snapshot a = average();
        return String.format(Locale.ROOT, "[NeroSecurity] BER %.0f/%.0f %.0f verts %.3f ms",
                a.submitted(), a.submitted() + a.culled(), a.vertices(), a.millis());
    }

    /** The multi-line record pasted into {@code docs/BER-BUDGET.md}. */
    public static String record() {
        Snapshot a = average();
        return String.format(Locale.ROOT,
                "NeroSecurity BER stats over %d frames%n"
                        + "  BERs submitted/frame: %.1f%n"
                        + "  BERs culled/frame:    %.1f%n"
                        + "  vertices/frame:       %.0f (peak %d; budget 60000)%n"
                        + "  BER ms/frame:         %.3f (peak %.3f; budget 1.5)",
                a.frames(), a.submitted(), a.culled(), a.vertices(), a.peakVertices(), a.millis(), a.peakMillis());
    }

    /** Clears everything (tests, and on level change). */
    public static void reset() {
        for (int i = 0; i < WINDOW; i++) {
            SUBMITTED[i] = 0;
            CULLED[i] = 0;
            VERTICES[i] = 0;
            NANOS[i] = 0;
        }
        head = 0;
        filled = 0;
        curSubmitted = 0;
        curCulled = 0;
        curVertices = 0;
        curNanos = 0;
        frameGameTime = Long.MIN_VALUE;
        framePartial = Float.NaN;
        frameOpen = false;
    }
}
