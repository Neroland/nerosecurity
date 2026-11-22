package za.co.neroland.nerosecurity.api;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * Public, binary-stable access queries for other mods. Signatures here only ever gain overloads; they are
 * never changed or removed, so a sibling mod can call them without binding to NeroSecurity's internals.
 *
 * <p>Nothing here records, logs or returns player data: the answer is a single boolean computed by the same
 * resolver NeroSecurity's own devices use.
 */
public final class SecurityAccess {

    private SecurityAccess() {
    }

    /**
     * Whether {@code actor} may open the device at {@code pos} in {@code level}, exactly as if they had used it
     * by hand (keycards, membership, faction and colony bindings, lockdown all apply).
     *
     * @return {@code true} when no installation covers the device, otherwise the resolver's verdict
     */
    public static boolean mayOpen(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        DevicePos device = DevicePos.of(level, pos);
        Optional<Installation> installation = SecurityInstallations.get(level.getServer()).atDevice(device);
        if (installation.isEmpty()) {
            return true;
        }
        return AccessResolver.DEFAULT.resolve(actor, installation.get(), AccessAction.OPEN, device).allowed();
    }
}
