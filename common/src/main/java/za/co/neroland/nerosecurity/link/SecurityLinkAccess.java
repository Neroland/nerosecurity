package za.co.neroland.nerosecurity.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The one place the link module decides <em>who sees what</em> and <em>who is told</em>.
 *
 * <ul>
 *   <li><b>Role</b> comes from the installation's stored owners and member rows only. No operator
 *       elevation, no faction, colony or station grant (those are in-game access, not phone access), no
 *       proximity. The tombstone owner never matches.</li>
 *   <li><b>Visibility</b>: OWNER and OFFICER see an installation in full, MEMBER sees its name and lockdown
 *       state, anybody else nothing; an installation id a player may not see answers exactly like an id
 *       that does not exist. A role is still subject to the resolver's denies (a deny-wins source such as
 *       a land claim hides the installation), re-resolved on every call.</li>
 *   <li><b>Recipients</b> of notifications: owners minus the tombstone, plus officers when the owner chose
 *       "notify officers"; never the actor of the event; never anyone because they are nearby.</li>
 * </ul>
 *
 * <p>Nothing here caches a verdict or logs a player.
 */
final class SecurityLinkAccess {

    /** The requester's own stored relation to an installation. */
    enum Role {
        NONE, MEMBER, OFFICER, OWNER;

        String jsonName() {
            return name().toLowerCase(Locale.ROOT);
        }

        boolean atLeastOfficer() {
            return this == OFFICER || this == OWNER;
        }
    }

    private SecurityLinkAccess() {
    }

    /** OWNER from stored owners, OFFICER/MEMBER from stored member rows, else NONE. */
    static Role roleOf(@Nullable Installation installation, @Nullable UUID player) {
        if (installation == null || player == null || Installation.TOMBSTONE_OWNER.equals(player)) {
            return Role.NONE;
        }
        if (installation.isOwner(player)) {
            return Role.OWNER;
        }
        AccessLevel level = installation.members().get(player);
        if (level == AccessLevel.OFFICER) {
            return Role.OFFICER;
        }
        return level == AccessLevel.MEMBER ? Role.MEMBER : Role.NONE;
    }

    /**
     * The role, narrowed to NONE when a resolver source denies this player outright (deny wins). Level and
     * lockdown are not asked here: a lockdown must not hide an installation from its own officers' phones.
     */
    static Role visibleRole(LinkContext ctx, Installation installation, UUID player) {
        Role role = roleOf(installation, player);
        if (role == Role.NONE) {
            return Role.NONE;
        }
        try {
            AccessDecision decision = ctx.resolver().resolve(
                    new AccessQuery(ctx.server(), player, installation, AccessAction.OPEN, null), AccessLevel.PUBLIC);
            return decision.reason() == DenyReason.SOURCE_DENY ? Role.NONE : role;
        } catch (RuntimeException | LinkageError e) {
            return Role.NONE;
        }
    }

    /** Installations the player owns or has a member row on (orphaned included), sorted by name then id. */
    static List<Installation> visible(LinkContext ctx, SecurityInstallations store, UUID player) {
        List<Installation> out = new ArrayList<>();
        for (Installation installation : store.visibleTo(player)) {
            if (visibleRole(ctx, installation, player) != Role.NONE) {
                out.add(installation);
            }
        }
        out.sort(Comparator.comparing(Installation::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> i.id().toString()));
        return out;
    }

    /**
     * The installation an {@code installation} parameter names, or null — for a missing, malformed,
     * unknown or not-visible id alike. Exact canonical UUID only (no prefixes, no names).
     */
    static @Nullable Installation param(LinkContext ctx, SecurityInstallations store, UUID player,
            @Nullable String raw) {
        UUID id = exactUuid(raw);
        if (id == null) {
            return null;
        }
        Installation installation = store.byId(id).orElse(null);
        if (installation == null || visibleRole(ctx, installation, player) == Role.NONE) {
            return null;
        }
        return installation;
    }

    /** {@link #param} for a section's string parameter map. */
    static @Nullable Installation param(LinkContext ctx, SecurityInstallations store, UUID player,
            @Nullable Map<String, String> params) {
        return param(ctx, store, player, params == null ? null : params.get("installation"));
    }

    /** A canonical 36-character UUID string, else null. */
    static @Nullable UUID exactUuid(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() != 36) {
            return null;
        }
        try {
            UUID id = UUID.fromString(trimmed);
            return id.toString().equalsIgnoreCase(trimmed) ? id : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * Who is notified about an event at {@code installation}: its stored owners (never the tombstone),
     * plus its OFFICER rows when {@code notifyOfficers} is on, minus {@code actor}. In stored order.
     */
    static Set<UUID> recipients(Installation installation, @Nullable UUID actor) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID owner : installation.owners()) {
            if (!Installation.TOMBSTONE_OWNER.equals(owner)) {
                out.add(owner);
            }
        }
        if (installation.notifyOfficers()) {
            for (Map.Entry<UUID, AccessLevel> member : installation.members().entrySet()) {
                if (member.getValue() == AccessLevel.OFFICER && !Installation.TOMBSTONE_OWNER.equals(member.getKey())) {
                    out.add(member.getKey());
                }
            }
        }
        if (actor != null) {
            out.remove(actor);
        }
        return out;
    }

    // --- action parameters --------------------------------------------------------------------------

    static @Nullable String string(@Nullable JsonObject params, String key) {
        if (params == null || !params.has(key)) {
            return null;
        }
        JsonElement element = params.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String raw = element.getAsString();
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    static @Nullable Boolean bool(@Nullable JsonObject params, String key) {
        if (params == null || !params.has(key)) {
            return null;
        }
        JsonElement element = params.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return element.getAsBoolean();
    }

    /** An integral number parameter, or null (absent, not a number, fractional or out of int range). */
    static @Nullable Integer integer(@Nullable JsonObject params, String key) {
        if (params == null || !params.has(key)) {
            return null;
        }
        JsonElement element = params.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            double value = element.getAsDouble();
            if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        } catch (RuntimeException notANumber) {
            return null;
        }
    }
}
