package za.co.neroland.nerosecurity.compat.factions;

import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * The seam between NeroSecurity and NeroFactions, in NeroSecurity types only: no NeroFactions type crosses it.
 * The one implementation ({@code NeroFactionsBridge}) is the only class that imports NeroFactions; it is listed in
 * {@code META-INF/services} and found by {@link FactionsCompat} only when NeroFactions is loaded, and a build with
 * {@code nerofactions_compat=false} leaves it (and the services line) out.
 *
 * <p>Implementations never throw on purpose and return the neutral value (false, empty, null) for a null server,
 * which only plain-JVM tests pass.
 */
public interface FactionsBridge {

    /** A player's standing with a faction (lower-cased in Locale.ROOT, the names match NeroFactions' tier ids). */
    enum Tier {
        OUTSIDER, ASSOCIATE, MEMBER, TRUSTED, INNER_CIRCLE
    }

    /** Whether {@code player} is currently a member of {@code faction}. */
    boolean isMember(@Nullable MinecraftServer server, UUID player, Identifier faction);

    /** Every faction {@code player} is currently a member of (for the bind command's suggestions). */
    Set<Identifier> membershipsOf(@Nullable MinecraftServer server, UUID player);

    /** {@code player}'s tier in {@code faction}, or null when the faction or the tier id is unknown (abstain). */
    @Nullable Tier tierOf(@Nullable MinecraftServer server, UUID player, Identifier faction);
}
