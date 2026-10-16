package za.co.neroland.nerosecurity.menu.door;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.menu.MenuAccessCheck;
import za.co.neroland.nerosecurity.registry.DoorRegistrations;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The door settings menu: the two module slots plus the player inventory, and live status (energy,
 * lockdown, link) through data slots. It binds the open screen to one controller position so every
 * serverbound door payload can be checked against it, and closes when the player walks away, the door
 * is gone, or the player may no longer configure it ({@code CONFIGURE_DEVICE} is re-resolved through
 * {@link MenuAccessCheck}; every module pickup and place re-resolves first). Access settings themselves
 * travel in the door snapshot payload.
 */
public class SecurityDoorMenu extends AbstractContainerMenu {

    public static final int MODULE_X = 116;
    public static final int MODULE_Y = 96;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 118;

    public static final int DATA_ENERGY_LO = 0;
    public static final int DATA_ENERGY_HI = 1;
    public static final int DATA_CAPACITY_LO = 2;
    public static final int DATA_CAPACITY_HI = 3;
    public static final int DATA_FLAGS = 4;
    public static final int DATA_COUNT = 5;

    public static final int FLAG_LOCKDOWN = 1;
    public static final int FLAG_LINKED = 2;
    public static final int FLAG_UNPOWERED = 4;

    private final @Nullable SecurityDoorBlockEntity door;
    private final @Nullable BlockPos pos;
    private final ContainerData data;
    private final int moduleSlots;
    /** The player this menu belongs to; module placement is checked against them. */
    private final Player owner;
    /** Server side only: re-resolves {@code CONFIGURE_DEVICE} while the menu is open. */
    private final @Nullable MenuAccessCheck access;

    /** Client-side factory: the client learns the position from the snapshot. */
    public SecurityDoorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null,
                new ModuleContainer(new SecurityModuleSlots(SecurityDoorBlockEntity.MODULE_SLOTS,
                        EnumSet.of(SecurityModule.HARDNESS, SecurityModule.OPEN_SPEED), null)),
                new SimpleContainerData(DATA_COUNT));
    }

    /** Server-side: bound to {@code door}. */
    public SecurityDoorMenu(int containerId, Inventory inventory, SecurityDoorBlockEntity door) {
        this(containerId, inventory, door, new ModuleContainer(door.modules()), serverData(door));
    }

    private SecurityDoorMenu(int containerId, Inventory inventory, @Nullable SecurityDoorBlockEntity door,
            Container modules, ContainerData data) {
        super(DoorRegistrations.SECURITY_DOOR_MENU.get(), containerId);
        this.door = door;
        this.pos = door == null ? null : door.getBlockPos().immutable();
        this.data = data;
        this.owner = inventory.player;
        this.access = door == null ? null : new MenuAccessCheck(player -> mayConfigure(door, player));
        this.moduleSlots = modules.getContainerSize();
        for (int i = 0; i < moduleSlots; i++) {
            addSlot(new ModuleSlot(this, modules, i, MODULE_X + i * 18, MODULE_Y));
        }
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(data);
    }

    /** The rule the menu was opened with ({@link SecurityDoorBlockEntity#resolveConfigure}). */
    private static boolean mayConfigure(SecurityDoorBlockEntity door, ServerPlayer player) {
        if (!(door.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        return SecurityDoorBlockEntity.resolveConfigure(player, door.installation(), door.devicePos(level)).allowed();
    }

    /** Whether {@code player} may move modules right now (always true on the client: the server decides). */
    boolean mayUseModules(Player player) {
        return access == null || access.allowedNow(player);
    }

    private static ContainerData serverData(SecurityDoorBlockEntity door) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_ENERGY_LO -> low(door.getEnergy().getAmount());
                    case DATA_ENERGY_HI -> high(door.getEnergy().getAmount());
                    case DATA_CAPACITY_LO -> low(door.getEnergy().getCapacity());
                    case DATA_CAPACITY_HI -> high(door.getEnergy().getCapacity());
                    case DATA_FLAGS -> flags(door);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server values are computed; nothing to store.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private static int flags(SecurityDoorBlockEntity door) {
        Installation installation = door.getLevel() instanceof ServerLevel ? door.installation() : null;
        int flags = 0;
        if (SecurityDoorBlockEntity.lockdownActive(installation)) {
            flags |= FLAG_LOCKDOWN;
        }
        if (installation != null) {
            flags |= FLAG_LINKED;
        }
        if (!door.hasPowerToOpen()) {
            flags |= FLAG_UNPOWERED;
        }
        return flags;
    }

    /** Data slots travel as 16-bit values, so energy is split into two halves. */
    private static int low(long value) {
        return (int) (Math.max(0L, Math.min(Integer.MAX_VALUE, value)) & 0xFFFF);
    }

    private static int high(long value) {
        return (int) ((Math.max(0L, Math.min(Integer.MAX_VALUE, value)) >>> 16) & 0xFFFF);
    }

    private long joined(int lo, int hi) {
        return ((long) (data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF);
    }

    // --- reads (client screen) ------------------------------------------------------------------

    public long energy() {
        return joined(DATA_ENERGY_LO, DATA_ENERGY_HI);
    }

    public long capacity() {
        return joined(DATA_CAPACITY_LO, DATA_CAPACITY_HI);
    }

    public boolean lockdown() {
        return (data.get(DATA_FLAGS) & FLAG_LOCKDOWN) != 0;
    }

    public boolean linked() {
        return (data.get(DATA_FLAGS) & FLAG_LINKED) != 0;
    }

    /** True when the server requires power and this door lacks it (only the owner can open). */
    public boolean unpowered() {
        return NeroSecurityConfig.doorsRequirePower() && (data.get(DATA_FLAGS) & FLAG_UNPOWERED) != 0;
    }

    /** The controller position this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    /** The bound door (server side; null on the client). */
    public @Nullable SecurityDoorBlockEntity door() {
        return door;
    }

    // --- slots ----------------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = this.slots.size();
        if (index < moduleSlots) {
            if (!moveItemStackTo(stack, moduleSlots, end, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < moduleSlots && !moved; i++) {
                Slot target = this.slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
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
        return original;
    }

    /**
     * Valid while the controller still stands within eight blocks of the player and the player may still
     * configure it (re-resolved at most every {@link MenuAccessCheck#INTERVAL_TICKS}).
     */
    @Override
    public boolean stillValid(Player player) {
        if (door == null) {
            return true; // client: the server decides
        }
        if (door.isRemoved() || door.getLevel() == null || door.getLevel().getBlockEntity(door.getBlockPos()) != door) {
            return false;
        }
        BlockPos at = door.getBlockPos();
        if (player.distanceToSqr(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return access == null || access.allowed(player);
    }

    /** A module slot: accepts only what the door's module slots accept, one per slot. */
    private static final class ModuleSlot extends Slot {

        private final SecurityDoorMenu menu;

        ModuleSlot(SecurityDoorMenu menu, Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return menu.mayUseModules(menu.owner) && this.container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return menu.mayUseModules(player) && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
