package za.co.neroland.nerosecurity.compat.factions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerofactions.api.FactionTierView;
import za.co.neroland.nerofactions.api.NeroFactionsApi;

/**
 * The <b>only</b> class in NeroSecurity that imports {@code za.co.neroland.nerofactions}, and it uses NeroFactions'
 * public API ({@code za.co.neroland.nerofactions.api}, NeroFactions 0.7.0-beta.1 or newer) and nothing else.
 * NeroFactions is compiled {@code compileOnly} and is optional: this class is listed in {@code META-INF/services},
 * instantiated by {@link FactionsCompat} only after {@code isModLoaded("nerofactions")}, and left out of a build with
 * {@code nerofactions_compat=false}.
 *
 * <p>Every answer is neutral (false, empty, null) for a null server and whenever NeroFactions throws, so a broken
 * sibling can only take grants away. A missing faction or a tier id this build does not know is no tier, so the
 * faction source abstains. Takes the player id the caller already holds and never logs it.
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
            Set<Identifier> memberships = NeroFactionsApi.membershipOf(server, player);
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
            Set<Identifier> memberships = NeroFactionsApi.membershipOf(server, player);
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
            // Empty for an unknown faction; negative standing comes back as "outsider".
            Optional<FactionTierView> view = NeroFactionsApi.tierOf(server, player, faction);
            if (view == null || view.isEmpty()) {
                return null;
            }
            return FactionTierMapping.tierFromId(view.get().id()).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
