package za.co.neroland.nerosecurity.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.registry.ProgrammerRegistrations;

/**
 * The programmer screen's menu: the three programmer slots plus the player inventory, and one synced
 * value (whether an encryption module is fitted) so the screen's "Encrypted" toggle follows the module
 * slot live. It binds the open screen to one block position, which every serverbound programmer payload is
 * checked against. Valid while the block stands within eight blocks <b>and</b> is still linked.
 */
public class KeycardProgrammerMenu extends AbstractContainerMenu {

    public static final int CARD_X = 12;
    public static final int BLANK_X = 52;
    public static final int MODULE_X = 92;
    public static final int SLOTS_Y = 108;
    public static final int INV_X = 37;
    public static final int INV_Y = 150;

    private static final int DATA_HAS_MODULE = 0;
    private static final int MACHINE_SLOTS = KeycardProgrammerBlockEntity.SLOT_COUNT;

    private final ContainerLevelAccess access;
    private final @Nullable BlockPos pos;
    private final ContainerData data;

    /** Client-side factory: the position comes from the snapshot, the slot contents from vanilla sync. */
    public KeycardProgrammerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(MACHINE_SLOTS), ContainerLevelAccess.NULL, null,
                new SimpleContainerData(1));
    }

    /** Server-side: bound to {@code programmer}'s position. */
    public KeycardProgrammerMenu(int containerId, Inventory inventory, KeycardProgrammerBlockEntity programmer) {
        this(containerId, inventory, programmer.container(),
                ContainerLevelAccess.create(programmer.getLevel(), programmer.getBlockPos()),
                programmer.getBlockPos().immutable(), new ModuleData(programmer));
    }

    private KeycardProgrammerMenu(int containerId, Inventory inventory, Container container,
            ContainerLevelAccess access, @Nullable BlockPos pos, ContainerData data) {
        super(ProgrammerRegistrations.MENU.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerDataCount(data, 1);
        this.access = access;
        this.pos = pos;
        this.data = data;
        addSlot(new ProgrammerSlot(container, KeycardProgrammerBlockEntity.SLOT_CARD, CARD_X, SLOTS_Y));
        addSlot(new ProgrammerSlot(container, KeycardProgrammerBlockEntity.SLOT_BLANK, BLANK_X, SLOTS_Y));
        addSlot(new ProgrammerSlot(container, KeycardProgrammerBlockEntity.SLOT_MODULE, MODULE_X, SLOTS_Y));
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    /** The programmer position this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    /** Whether an encryption module is fitted (synced; display only on the client). */
    public boolean hasModule() {
        return data.get(DATA_HAS_MODULE) != 0;
    }

    /** The stack in a programmer slot, for the screen's button states (display only). */
    public ItemStack programmerStack(int slot) {
        return getSlot(slot).getItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Only into a slot that takes the stack: module -> module slot, blank -> card slot then blank
            // slot, encoded card -> card slot. Anything else stays where it is.
            boolean moved = false;
            if (KeycardProgrammerBlockEntity.accepts(KeycardProgrammerBlockEntity.SLOT_MODULE, stack)) {
                moved = moveItemStackTo(stack, KeycardProgrammerBlockEntity.SLOT_MODULE,
                        KeycardProgrammerBlockEntity.SLOT_MODULE + 1, false);
            } else if (KeycardProgrammerBlockEntity.accepts(KeycardProgrammerBlockEntity.SLOT_CARD, stack)) {
                moved = moveItemStackTo(stack, KeycardProgrammerBlockEntity.SLOT_CARD,
                        KeycardProgrammerBlockEntity.SLOT_CARD + 1, false);
                if (!moved && KeycardProgrammerBlockEntity.accepts(KeycardProgrammerBlockEntity.SLOT_BLANK, stack)) {
                    moved = moveItemStackTo(stack, KeycardProgrammerBlockEntity.SLOT_BLANK,
                            KeycardProgrammerBlockEntity.SLOT_BLANK + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Valid while the programmer stands within eight blocks of the player and is still linked. */
    @Override
    public boolean stillValid(Player player) {
        if (!stillValid(this.access, player, ProgrammerRegistrations.BLOCK.get())) {
            return false;
        }
        return this.access.evaluate((level, blockPos) -> !(level instanceof ServerLevel serverLevel)
                || SecurityInstallations.get(serverLevel.getServer())
                        .atDevice(DevicePos.of(serverLevel, blockPos)).isPresent(), true);
    }

    /** A programmer slot: one item, and only what {@link KeycardProgrammerBlockEntity#accepts} allows. */
    private static final class ProgrammerSlot extends Slot {

        ProgrammerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return KeycardProgrammerBlockEntity.accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /** Server-side data view: 1 when an encryption module is fitted. */
    private record ModuleData(KeycardProgrammerBlockEntity programmer) implements ContainerData {

        @Override
        public int get(int index) {
            return index == DATA_HAS_MODULE && programmer.hasEncryptionModule() ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            // Server-owned; the client cannot change it.
        }

        @Override
        public int getCount() {
            return 1;
        }
    }
}
