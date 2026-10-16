package za.co.neroland.nerosecurity.compat;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** Shared plain-JVM fixtures for the integration sources: a real store, server null, fake bridges. */
public final class CompatFixtures {

    public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    public static final DevicePos DEVICE = new DevicePos(OVERWORLD, new BlockPos(3, 64, 3));

    private CompatFixtures() {
    }

    public static Installation create(SecurityInstallations store, UUID owner, int x) {
        return store.create(owner, "T" + x, new DevicePos(OVERWORLD, new BlockPos(x, 64, 0)), 0L, 8).installation();
    }

    /**
     * A store holding one installation with every given owner (the store has no "add owner" mutator, so this goes
     * through the on-disk codec, exactly as a saved world would load it). Returns that store.
     */
    public static SecurityInstallations storeWithOwners(UUID installationId, UUID... owners) {
        JsonObject row = new JsonObject();
        row.addProperty("id", installationId.toString());
        row.addProperty("name", "Shared");
        JsonArray list = new JsonArray();
        for (UUID owner : owners) {
            list.add(owner.toString());
        }
        row.add("owners", list);
        JsonArray installations = new JsonArray();
        installations.add(row);
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SecurityInstallations.SCHEMA_VERSION);
        root.add("installations", installations);
        return SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, root).getOrThrow();
    }

    public static AccessQuery query(UUID actor, Installation installation, AccessAction action) {
        return new AccessQuery(null, actor, installation, action, DEVICE);
    }

    public static AccessResolver resolver(List<AccessSource> sources) {
        return new AccessResolver(() -> sources, AdminCheck.NONE, () -> true);
    }

    public static boolean isAbstain(AccessVote vote) {
        return vote == AccessVote.Abstain.INSTANCE;
    }
}
