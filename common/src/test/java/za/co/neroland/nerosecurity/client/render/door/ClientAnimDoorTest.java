package za.co.neroland.nerosecurity.client.render.door;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.anim.ClientAnim;

/** The door timeline from its synced anchor ({@code OpenTicks / Opening / SyncTime / Duration}). */
class ClientAnimDoorTest {

    private static final float EPS = 1.0e-5f;

    @Test
    void opensFromTheAnchorAtOneTickPerTick() {
        assertEquals(0f, ClientAnim.progress(0, 1, 100, 100, 0f, 20, true), EPS);
        assertEquals(0.25f, ClientAnim.progress(0, 1, 100, 105, 0f, 20, true), EPS);
        assertEquals(0.275f, ClientAnim.progress(0, 1, 100, 105, 0.5f, 20, true), EPS);
        assertEquals(1f, ClientAnim.progress(0, 1, 100, 500, 0f, 20, true), EPS);
    }

    @Test
    void closesFromAPartlyOpenAnchor() {
        assertEquals(0.5f, ClientAnim.progress(10, -1, 0, 0, 0f, 20, true), EPS);
        assertEquals(0.25f, ClientAnim.progress(10, -1, 0, 5, 0f, 20, true), EPS);
        assertEquals(0f, ClientAnim.progress(10, -1, 0, 50, 0f, 20, true), EPS);
    }

    @Test
    void aClientClockBehindTheServerNeverRunsBackwards() {
        assertEquals(0.5f, ClientAnim.progress(10, 1, 100, 90, 0f, 20, true), EPS);
    }

    @Test
    void stillFrameIsTheSnapshot() {
        assertEquals(0.5f, ClientAnim.progress(10, 1, 0, 1000, 0.9f, 20, false), EPS);
        assertEquals(0f, ClientAnim.progress(0, -1, 0, 1000, 0.9f, 20, false), EPS);
    }

    @Test
    void zeroDurationSitsAtAnEnd() {
        assertEquals(1f, ClientAnim.progress(0, 1, 0, 0, 0f, 0, true), EPS);
        assertEquals(0f, ClientAnim.progress(0, -1, 0, 0, 0f, 0, true), EPS);
    }
}
