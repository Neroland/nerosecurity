package za.co.neroland.nerosecurity.block.entity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.SecurityHubMenu;
import za.co.neroland.nerosecurity.registry.NeroSecurityBlockEntities;

/**
 * The hub's block entity. It stores exactly one thing: the id of the installation it controls (a
 * pointer, never a copy of the record). A hub whose id resolves to nothing is inert.
 */
public class SecurityHubBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_INSTALLATION = "installation";

    private @Nullable UUID installationId;

    /** Transient: set by a player break so the removal hook does not also orphan the installation. */
    private boolean dissolving;

    public SecurityHubBlockEntity(BlockPos pos, BlockState state) {
        super(NeroSecurityBlockEntities.SECURITY_HUB.get(), pos, state);
    }

    public @Nullable UUID installationId() {
        return installationId;
    }

    public void bind(UUID id) {
        this.installationId = id;
        setChanged();
    }

    public void markDissolving() {
        this.dissolving = true;
    }

    /** The installation this hub controls, if it still exists. */
    public @Nullable Installation installation(MinecraftServer server) {
        if (installationId == null || server == null) {
            return null;
        }
        return SecurityInstallations.get(server).byId(installationId).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (installationId != null) {
            output.putString(TAG_INSTALLATION, installationId.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr(TAG_INSTALLATION, "");
        try {
            this.installationId = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            this.installationId = null;
        }
    }

    /**
     * The block is being replaced (not unloaded). A player break has already dissolved the installation;
     * any other removal (explosion, command, another mod) orphans it. Store bookkeeping only; the world
     * is not touched.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (dissolving || installationId == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Installation installation = installation(serverLevel.getServer());
        if (installation != null && installation.hubPos() != null
                && DevicePos.of(serverLevel, pos).equals(installation.hubPos())) {
            InstallationService.orphanByLoss(serverLevel.getServer(), installation);
        }
    }

    // --- menu -----------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("hub.nerosecurity.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (installationId == null || level == null) {
            return null;
        }
        return new SecurityHubMenu(containerId, inventory, installationId,
                ContainerLevelAccess.create(level, worldPosition));
    }
}
