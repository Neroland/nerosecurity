package za.co.neroland.nerosecurity.block.monitor;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.block.VisualState;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.monitor.MonitorMenu;
import za.co.neroland.nerosecurity.menu.monitor.MonitorPayloadHandlers;
import za.co.neroland.nerosecurity.notice.RecordingNotice;
import za.co.neroland.nerosecurity.registry.MonitorRegistrations;

/**
 * The Security Monitor's block entity. It stores nothing: no installation pointer, no event cache, no
 * imagery. It exists to be the menu provider, to report how the block was removed, and to anchor
 * its screen rendering. Every lookup goes to the store by position.
 */
public class SecurityMonitorBlockEntity extends BlockEntity implements MenuProvider {

    /** Transient: a player break already reported itself, so removal must not report it again. */
    private boolean brokenByPlayer;

    public SecurityMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(MonitorRegistrations.BLOCK_ENTITY.get(), pos, state);
    }

    /** Server tick: re-derive {@link SecurityMonitorBlock#LOCKDOWN} on the staggered cadence. */
    public void visualTick(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && VisualState.refreshDue(level.getGameTime(), pos.asLong())) {
            refreshVisual(serverLevel);
        }
    }

    /**
     * Server: sets {@link SecurityMonitorBlock#LOCKDOWN} from the store (looked up by position now) when it
     * differs. Called by the visual refresh, on link/unlink and by the lockdown hook.
     */
    public void refreshVisual(ServerLevel serverLevel) {
        if (isRemoved()) {
            return;
        }
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof SecurityMonitorBlock)) {
            return;
        }
        Installation installation = SecurityInstallations.get(serverLevel.getServer())
                .atDevice(DevicePos.of(serverLevel, worldPosition)).orElse(null);
        boolean lockdown = VisualState.lockdownShown(installation);
        if (state.getValue(SecurityMonitorBlock.LOCKDOWN) != lockdown) {
            serverLevel.setBlock(worldPosition, state.setValue(SecurityMonitorBlock.LOCKDOWN, lockdown),
                    Block.UPDATE_CLIENTS);
        }
    }

    /**
     * A player is breaking the monitor (server). Anyone may; if it is linked and the resolver would not
     * let this player view it (OPEN at MEMBER), the break is published as {@code DEVICE_FORCED}.
     */
    public void onPlayerBreak(ServerPlayer player) {
        brokenByPlayer = true;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DevicePos at = DevicePos.of(serverLevel, worldPosition);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            return;
        }
        AccessDecision decision = MonitorPayloadHandlers.resolveView(player, installation, at);
        if (!decision.allowed()) {
            publish(SecurityEventType.DEVICE_FORCED, installation, at, player.getUUID());
        }
    }

    /**
     * The block is being replaced (not unloaded). If it was linked: a non-player removal (explosion,
     * command, another mod) publishes {@code DEVICE_FORCED} with no actor, and the link is dropped so no
     * ghost device squats the position. Store and event work only.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        DevicePos at = DevicePos.of(serverLevel, pos);
        Installation installation = SecurityInstallations.get(server).atDevice(at).orElse(null);
        if (installation == null) {
            return;
        }
        if (!brokenByPlayer) {
            publish(SecurityEventType.DEVICE_FORCED, installation, at, null);
        }
        InstallationService.unlinkByLoss(server, installation, at);
    }

    private void publish(SecurityEventType type, Installation installation, DevicePos at, @Nullable UUID actor) {
        // Notice before logging: a named player who has not had this installation's notice gets it first.
        RecordingNotice.noticeBeforeLogging(level instanceof ServerLevel serverLevel ? serverLevel.getServer() : null,
                actor, installation);
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), at, actor, System.currentTimeMillis()));
    }

    // --- menu -----------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerosecurity.security_monitor");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (level == null) {
            return null;
        }
        return new MonitorMenu(containerId, inventory, this);
    }
}
