package za.co.neroland.nerosecurity.installation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.protection.ClaimCheck;
import za.co.neroland.nerolandcore.protection.ClaimChecks;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlock;

/**
 * NeroSecurity's answer to Neroland Core's land-protection SPI. NeroSecurity protects devices, not areas: a block
 * is protected when it is a hub or a device linked to an installation, or a standing force-field block.
 *
 * <ul>
 *   <li>{@link #isProtected}: world effects with no player (radiation, explosions, automated breaking) leave
 *       hubs, linked devices and force-field blocks alone.</li>
 *   <li>{@link #mayInteract}: the same verdict NeroSecurity's own blocks give: {@code OPEN} for a linked device,
 *       {@code OPEN_HUB} for a hub. Anywhere else NeroSecurity has no opinion. Force-field blocks keep their own
 *       pass rules.</li>
 * </ul>
 *
 * <p>Cheap: one indexed lookup in the installation store, and a block-state read only when the chunk is already
 * loaded. Nothing is logged or recorded.
 */
public final class SecurityClaimCheck implements ClaimCheck {

    public static final SecurityClaimCheck INSTANCE = new SecurityClaimCheck();

    private SecurityClaimCheck() {
    }

    /** Registers with Core at common init. Idempotent (Core ignores a second registration). */
    public static void register() {
        ClaimChecks.register(INSTANCE);
    }

    /** Whether {@code pos} is a hub or a linked device in {@code store} (plain JVM, unit-tested). */
    public static boolean covers(SecurityInstallations store, DevicePos pos) {
        return store.atDevice(pos).isPresent() || store.atHub(pos).isPresent();
    }

    @Override
    public boolean isProtected(ServerLevel level, BlockPos pos) {
        if (covers(SecurityInstallations.get(level.getServer()), DevicePos.of(level, pos))) {
            return true;
        }
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof ForceFieldBlock;
    }

    @Override
    public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SecurityInstallations store = SecurityInstallations.get(level.getServer());
        DevicePos device = DevicePos.of(level, pos);
        var hub = store.atHub(device);
        if (hub.isPresent()) {
            return AccessResolver.DEFAULT.resolve(player, hub.get(), AccessAction.OPEN_HUB, device).allowed();
        }
        var linked = store.atDevice(device);
        if (linked.isPresent()) {
            return AccessResolver.DEFAULT.resolve(player, linked.get(), AccessAction.OPEN, device).allowed();
        }
        return true;
    }
}
