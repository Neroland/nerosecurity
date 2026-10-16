package za.co.neroland.nerosecurity.menu.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.monitor.SecurityMonitorBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.registry.MonitorRegistrations;

/**
 * The monitor screen's menu. No slots: it binds the open screen to one monitor position so the refresh
 * payload can be checked against it, and closes when the player walks away, the monitor is gone or it
 * is no longer linked. What the screen shows travels in {@link MonitorSnapshot}.
 */
public class MonitorMenu extends AbstractContainerMenu {

    /** The fewest ticks between two refreshes the server answers for one open menu. */
    public static final int REFRESH_INTERVAL_TICKS = 10;

    private final @Nullable SecurityMonitorBlockEntity monitor;
    private final @Nullable BlockPos pos;
    private long lastRefresh = Long.MIN_VALUE / 2;

    /** Client-side factory: the client learns the position from the snapshot. */
    public MonitorMenu(int containerId, Inventory inventory) {
        this(containerId, (SecurityMonitorBlockEntity) null);
    }

    /** Server-side: bound to {@code monitor}. */
    public MonitorMenu(int containerId, Inventory inventory, SecurityMonitorBlockEntity monitor) {
        this(containerId, monitor);
    }

    private MonitorMenu(int containerId, @Nullable SecurityMonitorBlockEntity monitor) {
        super(MonitorRegistrations.MENU.get(), containerId);
        this.monitor = monitor;
        this.pos = monitor == null ? null : monitor.getBlockPos().immutable();
    }

    /** The monitor position this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    /** The bound monitor (server side; null on the client). */
    public @Nullable SecurityMonitorBlockEntity monitor() {
        return monitor;
    }

    /** Whether a refresh at {@code gameTime} may be answered; records it if so. */
    public boolean tryRefresh(long gameTime) {
        if (!refreshDue(lastRefresh, gameTime)) {
            return false;
        }
        lastRefresh = gameTime;
        return true;
    }

    /** Pure rate-limit rule. */
    public static boolean refreshDue(long last, long now) {
        return now - last >= REFRESH_INTERVAL_TICKS || now < last;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the monitor stands within eight blocks of the player and is still linked. */
    @Override
    public boolean stillValid(Player player) {
        if (monitor == null) {
            return true; // client: the server decides
        }
        if (monitor.isRemoved() || !(monitor.getLevel() instanceof ServerLevel level)
                || level.getBlockEntity(monitor.getBlockPos()) != monitor) {
            return false;
        }
        BlockPos at = monitor.getBlockPos();
        if (player.distanceToSqr(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return SecurityInstallations.get(level.getServer()).atDevice(DevicePos.of(level, at)).isPresent();
    }
}
