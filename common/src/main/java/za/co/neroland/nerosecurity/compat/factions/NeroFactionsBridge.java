package za.co.neroland.nerosecurity.compat.factions;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * The <b>only</b> class in NeroSecurity that imports {@code za.co.neroland.nerofactions}. NeroFactions is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link FactionsCompat} only after {@code isModLoaded("nerofactions")}, and left out of a build with
 * {@code nerofactions_compat=false}.
 *
 * <p>Every answer is neutral (false, empty, null) for a null server and whenever NeroFactions throws, so a broken
 * sibling can only take grants away. Takes the player id the caller already holds and never logs it.
 */
public final class NeroFactionsBridge implements FactionsBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroFactionsBridge() {
    }

    @Override
    public boolean isMember(@Nullable MinecraftServer server, UUID player, Identifier faction) {
        if (server == null || player == null || faction == null) {
            return false;
        }
        try {
            Set<Identifier> memberships = FactionMembership.membershipOf(server, player);
            return memberships != null && memberships.contains(faction);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public Set<Identifier> membershipsOf(@Nullable MinecraftServer server, UUID player) {
        if (server == null || player == null) {
            return Set.of();
        }
        try {
            Set<Identifier> memberships = FactionMembership.membershipOf(server, player);
            return memberships == null ? Set.of() : Set.copyOf(memberships);
        } catch (RuntimeException | LinkageError e) {
            return Set.of();
        }
    }

    @Override
    public @Nullable Tier tierOf(@Nullable MinecraftServer server, UUID player, Identifier faction) {
        if (server == null || player == null || faction == null) {
            return null;
        }
        try {
            Map<Identifier, FactionDefinition> definitions = FactionDefinitions.factionsForServer(server);
            FactionDefinition definition = definitions == null ? null : definitions.get(faction);
            if (definition == null) {
                return null;
            }
            FactionTier tier = FactionTiers.tierOf(definition, ReputationApi.getReputation(player, faction));
            return tier == null ? null : Tier.valueOf(tier.name());
        } catch (RuntimeException | LinkageError e) {
            // Includes an unknown tier name (IllegalArgumentException): no tier, so the source abstains.
            return null;
        }
    }
}
