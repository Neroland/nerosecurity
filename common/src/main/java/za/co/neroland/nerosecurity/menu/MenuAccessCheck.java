package za.co.neroland.nerosecurity.menu;

import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * Re-authorises an open settings menu that holds live item slots (device modules). Opening the menu
 * resolved access once; this keeps asking, so a player who is demoted, removed, or loses the device's
 * link while the screen is open can no longer move modules and is closed out.
 *
 * <ul>
 *   <li>{@link #allowed} answers from a cached decision at most {@link #INTERVAL_TICKS} old: the menu's
 *       {@code stillValid} uses it, so the server closes the menu within a second of access ending.</li>
 *   <li>{@link #allowedNow} re-resolves at most once per game tick: module slots use it for every
 *       pickup and place, so no item moves on a stale answer.</li>
 * </ul>
 *
 * <p>The rule is the same resolver call the menu was opened with. A rule that throws fails closed.
 * Client-side menus (no server player) always answer true: the server decides.
 */
public final class MenuAccessCheck {

    /** Longest a cached answer is used by {@code stillValid}, in game ticks. */
    public static final int INTERVAL_TICKS = 20;

    private final Predicate<ServerPlayer> rule;
    private long checkedAt = Long.MIN_VALUE;
    private boolean lastAllowed;

    public MenuAccessCheck(Predicate<ServerPlayer> rule) {
        this.rule = rule;
    }

    /** The cached answer, refreshed when it is {@link #INTERVAL_TICKS} old (or the clock went backwards). */
    public boolean allowed(@Nullable Player player) {
        return check(player, INTERVAL_TICKS);
    }

    /** A fresh answer (at most one resolve per game tick). */
    public boolean allowedNow(@Nullable Player player) {
        return check(player, 1);
    }

    private boolean check(@Nullable Player player, int maxAge) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        long now = serverPlayer.level().getGameTime();
        if (stale(checkedAt, now, maxAge)) {
            checkedAt = now;
            lastAllowed = evaluate(serverPlayer);
        }
        return lastAllowed;
    }

    private boolean evaluate(ServerPlayer player) {
        try {
            return rule.test(player);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** The interval rule as a pure function (tests): whether an answer cached at {@code checkedAt} is stale. */
    static boolean stale(long checkedAt, long now, int maxAge) {
        return checkedAt == Long.MIN_VALUE || now < checkedAt || now - checkedAt >= maxAge;
    }
}
