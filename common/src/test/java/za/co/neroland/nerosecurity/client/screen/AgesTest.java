package za.co.neroland.nerosecurity.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Relative ages bucket down to their largest unit; countdowns round up so they never read zero early. */
class AgesTest {

    private static void assertBucket(long seconds, Ages.Unit unit, long count) {
        assertEquals(new Ages.Bucket(unit, count), Ages.bucket(seconds), seconds + " s");
    }

    @Test
    void bucketsAges() {
        assertBucket(-3, Ages.Unit.NOW, 0);
        assertBucket(0, Ages.Unit.NOW, 0);
        assertBucket(4, Ages.Unit.NOW, 0);
        assertBucket(5, Ages.Unit.SECONDS, 5);
        assertBucket(59, Ages.Unit.SECONDS, 59);
        assertBucket(60, Ages.Unit.MINUTES, 1);
        assertBucket(3599, Ages.Unit.MINUTES, 59);
        assertBucket(3600, Ages.Unit.HOURS, 1);
        assertBucket(86_399, Ages.Unit.HOURS, 23);
        assertBucket(86_400, Ages.Unit.DAYS, 1);
        assertBucket(10L * 86_400, Ages.Unit.DAYS, 10);
    }

    @Test
    void keysMatchTheLangTable() {
        assertEquals("time.nerosecurity.ago.now", Ages.agoKey(Ages.Unit.NOW));
        assertEquals("time.nerosecurity.ago.seconds", Ages.agoKey(Ages.Unit.SECONDS));
        assertEquals("time.nerosecurity.ago.minutes", Ages.agoKey(Ages.Unit.MINUTES));
        assertEquals("time.nerosecurity.ago.hours", Ages.agoKey(Ages.Unit.HOURS));
        assertEquals("time.nerosecurity.ago.days", Ages.agoKey(Ages.Unit.DAYS));
    }

    @Test
    void countdownRoundsUp() {
        assertEquals(1, Ages.ceilMinutes(1));
        assertEquals(1, Ages.ceilMinutes(60));
        assertEquals(2, Ages.ceilMinutes(61));
        assertEquals(0, Ages.ceilMinutes(0));
    }
}
