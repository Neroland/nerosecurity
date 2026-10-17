package za.co.neroland.nerosecurity.link;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per (player, action) request limits for the companion app's actions, independent of the bridge's own
 * token bucket: {@code lockdown} 1 per 10 s and 6 per minute, {@code silence_alarm} 6 per minute,
 * {@code ack_event} 30 per minute. Sliding windows of accepted call times; refused calls are not counted.
 * In memory only, bounded, cleared for a player by erasure. Pure apart from the caller's clock.
 */
final class LinkRateLimiter {

    /** At most {@code count} calls in any {@code windowMs}. */
    record Rule(int count, long windowMs) {
    }

    static final int MAX_KEYS = 4096;

    private record Key(UUID player, String action) {
    }

    private final Map<String, List<Rule>> rules;
    /** Insertion-ordered; when full, the key that has existed longest is evicted first. */
    private final LinkedHashMap<Key, Deque<Long>> calls = new LinkedHashMap<>();

    LinkRateLimiter(Map<String, List<Rule>> rules) {
        this.rules = Map.copyOf(rules);
    }

    /** The shipped limits. */
    static LinkRateLimiter standard() {
        return new LinkRateLimiter(Map.of(
                SecurityLinkModule.ACTION_LOCKDOWN, List.of(new Rule(1, 10_000L), new Rule(6, 60_000L)),
                SecurityLinkModule.ACTION_SILENCE, List.of(new Rule(6, 60_000L)),
                SecurityLinkModule.ACTION_ACK, List.of(new Rule(30, 60_000L))));
    }

    /**
     * Records the call and returns 0 when every rule allows it; otherwise records nothing and returns how
     * many milliseconds until it would be allowed (at least 1).
     */
    synchronized long tryAcquire(UUID player, String action, long now) {
        List<Rule> forAction = rules.get(action);
        if (forAction == null || forAction.isEmpty() || player == null) {
            return 0L;
        }
        long longest = 0L;
        for (Rule rule : forAction) {
            longest = Math.max(longest, rule.windowMs());
        }
        Key key = new Key(player, action);
        Deque<Long> seen = calls.get(key);
        if (seen != null) {
            while (!seen.isEmpty() && now - seen.peekFirst() >= longest) {
                seen.pollFirst();
            }
        }
        long wait = 0L;
        if (seen != null) {
            for (Rule rule : forAction) {
                List<Long> inWindow = new ArrayList<>();
                for (long at : seen) {
                    if (now - at < rule.windowMs()) {
                        inWindow.add(at);
                    }
                }
                if (inWindow.size() >= rule.count()) {
                    // A slot frees when the call that keeps the window full ages out.
                    long freeAt = inWindow.get(inWindow.size() - rule.count()) + rule.windowMs();
                    wait = Math.max(wait, Math.max(1L, freeAt - now));
                }
            }
        }
        if (wait > 0L) {
            return wait;
        }
        if (seen == null) {
            while (calls.size() >= MAX_KEYS) {
                Iterator<Key> oldest = calls.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            seen = new ArrayDeque<>();
            calls.put(key, seen);
        }
        seen.addLast(now);
        return 0L;
    }

    synchronized void forget(UUID player) {
        calls.keySet().removeIf(key -> key.player().equals(player));
    }

    synchronized boolean holdsAnyFor(UUID player) {
        for (Key key : calls.keySet()) {
            if (key.player().equals(player)) {
                return true;
            }
        }
        return false;
    }

    synchronized void clear() {
        calls.clear();
    }
}
