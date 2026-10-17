package za.co.neroland.nerosecurity.link;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkActionResult;

/**
 * Mod-side idempotency for companion-app actions: a replayed {@code requestId} returns the first call's
 * result without a second state transition, whatever the bridge does. Per player, the newest
 * {@value #PER_PLAYER} ids for {@value #TTL_MS} ms. Keys are the action id and the request id; nothing
 * else is kept. In memory only, cleared for a player by erasure.
 */
final class LinkRequestDedup {

    static final int PER_PLAYER = 64;
    static final long TTL_MS = 10L * 60_000L;
    static final int MAX_PLAYERS = 4096;
    static final int MAX_REQUEST_ID = 128;

    private record Remembered(LinkActionResult result, long at) {
    }

    /** Access-ordered, so the least recently used player is evicted first when full. */
    private final LinkedHashMap<UUID, LinkedHashMap<String, Remembered>> byPlayer = new LinkedHashMap<>(16, 0.75f, true);

    /** The cached result for this request, or null (no id, unknown, or expired). */
    synchronized @Nullable LinkActionResult lookup(UUID player, String action, @Nullable String requestId, long now) {
        String key = key(action, requestId);
        if (key == null || player == null) {
            return null;
        }
        LinkedHashMap<String, Remembered> mine = byPlayer.get(player);
        if (mine == null) {
            return null;
        }
        prune(mine, now);
        Remembered hit = mine.get(key);
        return hit == null ? null : hit.result();
    }

    synchronized void remember(UUID player, String action, @Nullable String requestId, LinkActionResult result,
            long now) {
        String key = key(action, requestId);
        if (key == null || player == null || result == null) {
            return;
        }
        LinkedHashMap<String, Remembered> mine = byPlayer.get(player);
        if (mine == null) {
            while (byPlayer.size() >= MAX_PLAYERS) {
                Iterator<UUID> oldest = byPlayer.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            mine = new LinkedHashMap<>();
            byPlayer.put(player, mine);
        }
        prune(mine, now);
        mine.remove(key);
        mine.put(key, new Remembered(result, now));
        while (mine.size() > PER_PLAYER) {
            Iterator<String> oldest = mine.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    synchronized void forget(UUID player) {
        byPlayer.remove(player);
    }

    synchronized boolean holdsAnyFor(UUID player) {
        LinkedHashMap<String, Remembered> mine = byPlayer.get(player);
        return mine != null && !mine.isEmpty();
    }

    synchronized void clear() {
        byPlayer.clear();
    }

    private static void prune(LinkedHashMap<String, Remembered> mine, long now) {
        mine.values().removeIf(r -> now - r.at() >= TTL_MS);
    }

    private static @Nullable String key(String action, @Nullable String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID) {
            return null;
        }
        return action + "\u0000" + requestId;
    }
}
