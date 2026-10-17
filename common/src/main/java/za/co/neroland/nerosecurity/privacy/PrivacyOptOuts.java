package za.co.neroland.nerosecurity.privacy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.audit.ActorPolicy;
import za.co.neroland.nerosecurity.data.SecurityData;

/**
 * The per-player privacy opt-out store {@code nerosecurity:privacy}: the set of players who asked not to be
 * named ({@code /nerosecurity privacy optout}, or the hub's Privacy tab). Nothing else: no time, no name, no
 * reason.
 *
 * <p>An opted-out player is withheld from every new audit row from the moment they opt out, for every event
 * type except {@code LOG_READ} (the record of their reading a log stays attributable; the {@link ActorPolicy}
 * this class provides is installed into the audit recorder at init). The opt-out is prospective only: rows
 * recorded earlier keep the id until retention or erasure removes them. The security event itself still fires:
 * a tripped laser is still a tripped laser, only the identity is gone. The named views (log command, the app's
 * "Show who") show "anonymous"; identity-free surfaces (hub, monitor, app event list, push) show every player
 * alike, so they never reveal who has opted out.
 *
 * <p>Overworld-resident, read only through {@link #get(MinecraftServer)} (Core's {@code SavedDataRecovery}).
 * The row is personal data in itself, so it joins the composite eraser as store {@code "privacy"}: an erasure
 * request removes it and refreshes the backup. Logs never carry a UUID.
 */
public final class PrivacyOptOuts extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "privacy");
    public static final int SCHEMA_VERSION = 1;

    private record Root(int schemaVersion, List<String> optedOut) {
    }

    /** On disk: {@code { schema_version, opted_out: [uuid, ...] }}. Malformed ids are dropped on load. */
    public static final Codec<PrivacyOptOuts> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            Codec.STRING.listOf().optionalFieldOf("opted_out", List.of()).forGetter(Root::optedOut)
    ).apply(inst, Root::new)).xmap(PrivacyOptOuts::fromData, PrivacyOptOuts::toData);

    public static final SavedDataType<PrivacyOptOuts> TYPE = new SavedDataType<>(ID, PrivacyOptOuts::new, CODEC, null);

    private static final SecurityData.StoreEraser ERASER = eraser(PrivacyOptOuts::get);

    private final Set<UUID> optedOut = new LinkedHashSet<>();

    public PrivacyOptOuts() {
    }

    /** The only accessor. */
    public static PrivacyOptOuts get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, PrivacyOptOuts::new, ID.toString());
    }

    /** Joins the composite eraser. Idempotent. */
    public static void registerEraser() {
        SecurityData.addStore(ERASER);
    }

    /**
     * The audit policy backed by the live store: withhold a player who opted out. With no server (tests, or a
     * call between servers) nobody is known to have opted out, so nothing is withheld by this policy; the
     * recorder's own rule withholds when the lookup itself fails.
     */
    public static ActorPolicy policy() {
        return policy(server -> server == null ? null : get(server));
    }

    /** {@link #policy()} over an injected lookup (tests pass a store built with {@code new}). */
    public static ActorPolicy policy(Function<@Nullable MinecraftServer, @Nullable PrivacyOptOuts> lookup) {
        return (server, actor) -> {
            PrivacyOptOuts store = lookup.apply(server);
            return store != null && store.isOptedOut(actor);
        };
    }

    /** A store eraser over the store {@code lookup} returns: drops the subject's row, then backs up. */
    public static SecurityData.StoreEraser eraser(Function<MinecraftServer, PrivacyOptOuts> lookup) {
        return new SecurityData.StoreEraser() {
            @Override
            public String name() {
                return "privacy";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                lookup.apply(server).remove(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                SavedDataRecovery.backupNow(server.overworld(), TYPE, lookup.apply(server), ID.toString());
            }
        };
    }

    // --- state ------------------------------------------------------------------------------------------

    public synchronized boolean isOptedOut(@Nullable UUID player) {
        return player != null && optedOut.contains(player);
    }

    /** Records the player's choice. Returns true when it changed anything. */
    public synchronized boolean set(UUID player, boolean out) {
        if (player == null) {
            return false;
        }
        boolean changed = out ? optedOut.add(player) : optedOut.remove(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Erasure: forgets the player entirely. */
    public synchronized boolean remove(UUID player) {
        return set(player, false);
    }

    /** Conformance probe. */
    public synchronized boolean holds(UUID player) {
        return isOptedOut(player);
    }

    public synchronized int size() {
        return optedOut.size();
    }

    // --- persistence --------------------------------------------------------------------------------------

    private synchronized Root toData() {
        List<String> ids = new ArrayList<>(optedOut.size());
        optedOut.forEach(id -> ids.add(id.toString()));
        return new Root(SCHEMA_VERSION, ids);
    }

    private static PrivacyOptOuts fromData(Root root) {
        PrivacyOptOuts store = new PrivacyOptOuts();
        int dropped = 0;
        for (String raw : root.optedOut()) {
            try {
                store.optedOut.add(UUID.fromString(raw));
            } catch (IllegalArgumentException malformed) {
                dropped++;
            }
        }
        if (dropped > 0) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] privacy store: dropped {} malformed row(s) on load.", dropped);
        }
        return store;
    }
}
