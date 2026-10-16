package za.co.neroland.nerosecurity.menu.door;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * A {@link Container} view over a device's {@link SecurityModuleSlots}, so menu slots can show and edit
 * them. One module per slot; only module types the slots accept may be placed. Changes go through the
 * slots' own change callback (which saves and syncs the device).
 */
public final class ModuleContainer implements Container {

    private final SecurityModuleSlots slots;

    public ModuleContainer(SecurityModuleSlots slots) {
        this.slots = slots;
    }

    @Override
    public int getContainerSize() {
        return slots.slots();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : slots.items()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < slots.slots() ? slots.getStack(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(slots.items(), slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(slots.items(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < slots.slots()) {
            slots.setStack(slot, stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slots.isAccepted(stack);
    }

    /** Runs the slots' change callback by re-setting slot 0 to itself. */
    @Override
    public void setChanged() {
        if (slots.slots() > 0) {
            slots.setStack(0, slots.getStack(0));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the menu checks distance and the block entity
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < slots.slots(); i++) {
            slots.items().set(i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
