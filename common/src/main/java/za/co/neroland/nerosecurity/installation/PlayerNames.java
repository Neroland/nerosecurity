package za.co.neroland.nerosecurity.installation;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * Local name/UUID lookups for the hub screen and its messages. Nothing here ever makes a network
 * request, and nothing it returns is stored.
 *
 * <ul>
 *   <li><b>Name to id</b> (adding a member): the <em>online</em> player list only. The server's profile
 *       cache is not consulted by name, because its by-name read falls back to a blocking remote profile
 *       lookup on a miss; a player who is offline must come online to be added.</li>
 *   <li><b>Id to name</b> (drawing rows): the online player list, then the profile cache's by-id read,
 *       which only ever answers from memory; otherwise {@code "?"}.</li>
 * </ul>
 */
public final class PlayerNames {

    /** Longest name the screen draws, and the longest name accepted for lookup. */
    public static final int MAX_NAME = 16;

    /** Placeholder for an id the server has no local name for. */
    public static final String UNKNOWN = "?";

    private PlayerNames() {
    }

    /** A display name for a player id: online list, then the in-memory profile cache, else {@code "?"}. */
    public static String display(MinecraftServer server, UUID id) {
        if (server == null || id == null) {
            return UNKNOWN;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return clean(online.getName().getString());
        }
        // By-id reads of the profile cache are memory-only (no remote fetch on a miss).
        return server.services().nameToIdCache().get(id).map(NameAndId::name).map(PlayerNames::clean).orElse(UNKNOWN);
    }

    /** Resolves a typed player name to the id of an <em>online</em> player. Never touches the network. */
    public static Optional<UUID> lookupOnline(MinecraftServer server, String name) {
        if (server == null) {
            return Optional.empty();
        }
        return lookupIn(name, typed -> {
            ServerPlayer online = server.getPlayerList().getPlayerByName(typed);
            return online == null ? Optional.empty() : Optional.of(online.getUUID());
        });
    }

    /**
     * The pure part of {@link #lookupOnline}: validates the typed name (non-blank, at most
     * {@link #MAX_NAME} characters after trimming) and asks {@code onlineByName} only. Package-visible
     * for tests.
     */
    static Optional<UUID> lookupIn(String name, Function<String, Optional<UUID>> onlineByName) {
        if (name == null) {
            return Optional.empty();
        }
        String typed = name.trim();
        if (typed.isEmpty() || typed.length() > MAX_NAME) {
            return Optional.empty();
        }
        Optional<UUID> found = onlineByName.apply(typed);
        return found == null ? Optional.empty() : found;
    }

    private static String clean(String raw) {
        StringBuilder out = new StringBuilder();
        raw.codePoints().filter(cp -> cp >= 0x20 && cp != 0x7F && cp != 0xA7).limit(MAX_NAME).forEach(out::appendCodePoint);
        return out.toString();
    }
}
