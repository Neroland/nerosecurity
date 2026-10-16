package za.co.neroland.nerosecurity.menu.chest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.registry.ChestRegistrations;

/**
 * The secure chest's settings menu. It has no slots: it binds the open settings screen to one chest
 * position, so every serverbound chest payload can be checked against it, and closes when the player
 * walks away from (or loses) the chest.
 */
public class ChestSettingsMenu extends AbstractContainerMenu {

    private final @Nullable BlockPos pos;
    private final ContainerLevelAccess access;

    /** Client-side factory: the client learns the chest from the snapshot, not from the menu. */
    public ChestSettingsMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, ContainerLevelAccess.NULL);
    }

    public ChestSettingsMenu(int containerId, Inventory inventory, @Nullable BlockPos pos,
            ContainerLevelAccess access) {
        super(ChestRegistrations.SECURE_CHEST_SETTINGS_MENU.get(), containerId);
        this.pos = pos == null ? null : pos.immutable();
        this.access = access;
    }

    /** The chest this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the secure chest still stands within eight blocks of the player. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ChestRegistrations.SECURE_CHEST.get());
    }
}
