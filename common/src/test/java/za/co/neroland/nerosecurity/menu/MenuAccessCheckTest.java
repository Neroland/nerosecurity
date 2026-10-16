package za.co.neroland.nerosecurity.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Open module menus re-resolve access on a short clock. Plain JVM. */
class MenuAccessCheckTest {

    @Test
    void cachedAnswersExpire() {
        assertEquals(20, MenuAccessCheck.INTERVAL_TICKS);
        assertTrue(MenuAccessCheck.stale(Long.MIN_VALUE, 0L, 20), "never checked");
        assertFalse(MenuAccessCheck.stale(100L, 119L, 20));
        assertTrue(MenuAccessCheck.stale(100L, 120L, 20), "stillValid re-resolves within a second");
        assertFalse(MenuAccessCheck.stale(100L, 100L, 1), "module slots: at most one resolve per tick");
        assertTrue(MenuAccessCheck.stale(100L, 101L, 1));
        assertTrue(MenuAccessCheck.stale(100L, 99L, 20), "a clock that steps back re-resolves");
    }

    @Test
    void clientSideMenusLeaveTheDecisionToTheServer() {
        MenuAccessCheck check = new MenuAccessCheck(player -> false);
        assertTrue(check.allowed(null));
        assertTrue(check.allowedNow(null));
    }
}
