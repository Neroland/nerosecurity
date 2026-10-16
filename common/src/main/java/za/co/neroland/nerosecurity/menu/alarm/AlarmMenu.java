package za.co.neroland.nerosecurity.menu.alarm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.registry.AlarmRegistrations;

/**
 * The alarm's settings menu. No slots: it binds the open settings screen to one alarm position, so every
 * serverbound alarm payload can be checked against it, and closes when the player walks away from (or
 * loses) the alarm.
 */
public class AlarmMenu extends AbstractContainerMenu {

    private final @Nullable BlockPos pos;
    private final ContainerLevelAccess access;

    /** Client-side factory: the client learns the alarm from the snapshot, not from the menu. */
    public AlarmMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, ContainerLevelAccess.NULL);
    }

    public AlarmMenu(int containerId, Inventory inventory, @Nullable BlockPos pos, ContainerLevelAccess access) {
        super(AlarmRegistrations.ALARM_MENU.get(), containerId);
        this.pos = pos == null ? null : pos.immutable();
        this.access = access;
    }

    /** The alarm this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the alarm still stands within eight blocks of the player. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, AlarmRegistrations.ALARM.get());
    }
}
