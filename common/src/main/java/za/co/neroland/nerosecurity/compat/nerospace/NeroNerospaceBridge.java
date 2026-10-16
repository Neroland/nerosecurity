package za.co.neroland.nerosecurity.compat.nerospace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.api.NerospaceStations;
import za.co.neroland.nerospace.api.StationInfo;

/**
 * The <b>only</b> class in NeroSecurity that imports {@code za.co.neroland.nerospace}. Nerospace is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link NerospaceCompat} only after {@code isModLoaded("nerospace")}, and left out of a build with
 * {@code nerospace_compat=false}.
 *
 * <p>Public station API only ({@code api.NerospaceStations}): explicit station ids, no position lookup and no
 * knowledge of how Nerospace lays stations out. Every answer is neutral (false, empty) for a null server and
 * whenever Nerospace throws. Never logs a player id.
 */
public final class NeroNerospaceBridge implements NerospaceBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroNerospaceBridge() {
    }

    @Override
    public boolean ownedBy(@Nullable MinecraftServer server, int station, UUID player) {
        if (server == null || player == null) {
            return false;
        }
        try {
            return NerospaceStations.ownedBy(server, station, player);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public List<Integer> stationsOwnedBy(@Nullable MinecraftServer server, UUID player) {
        if (server == null || player == null) {
            return List.of();
        }
        try {
            List<Integer> owned = new ArrayList<>();
            for (StationInfo station : NerospaceStations.all(server)) {
                if (station != null && NerospaceStations.ownedBy(server, station.id(), player)) {
                    owned.add(station.id());
                }
            }
            owned.sort(null);
            return List.copyOf(owned);
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }
}
