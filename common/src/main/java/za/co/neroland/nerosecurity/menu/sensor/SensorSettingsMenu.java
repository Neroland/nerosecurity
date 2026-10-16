package za.co.neroland.nerosecurity.menu.sensor;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.MenuAccessCheck;
import za.co.neroland.nerosecurity.menu.door.ModuleContainer;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The one settings menu both sensors (laser grid node, camera) open, through {@link SensorMenuHost}.
 * Holds up to {@link #MAX_MODULE_SLOTS} range-module slots plus the player inventory, and live status
 * (energy, armed, linked, paired, kind) through data slots. It binds the open screen to one sensor
 * position so every serverbound sensor payload can be checked against it, and closes when the player
 * walks away, the sensor is gone or unlinked, or the player may no longer configure it ({@code
 * CONFIGURE_DEVICE} is re-resolved through {@link MenuAccessCheck}; every module pickup and place
 * re-resolves first). The settings themselves travel in the sensor snapshot payload.
 *
 * <p>Nothing about what the sensor sensed is in here: no entity, no name, no position but the sensor's.
 */
public class SensorSettingsMenu extends AbstractContainerMenu {

    /** Module slot positions always present; a host with fewer gets inactive placeholders. */
    public static final int MAX_MODULE_SLOTS = 2;
    /** Slot positions match the screen hull: modules inside the UPGRADES box, inventory at inventoryRowsY(178). */
    public static final int MODULE_X = 206;
    public static final int MODULE_Y = 132;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 192;

    public static final int DATA_ENERGY_LO = 0;
    public static final int DATA_ENERGY_HI = 1;
    public static final int DATA_CAPACITY_LO = 2;
    public static final int DATA_CAPACITY_HI = 3;
    public static final int DATA_FLAGS = 4;
    public static final int DATA_MODULE_SLOTS = 5;
    public static final int DATA_COUNT = 6;

    public static final int FLAG_ARMED = 1;
    public static final int FLAG_LINKED = 2;
    public static final int FLAG_LASER = 4;
    public static final int FLAG_PAIRED = 8;

    private final @Nullable SensorMenuHost host;
    /** The player this menu belongs to (a menu is per player); module placement is checked against them. */
    private final Player owner;
    private final @Nullable BlockPos pos;
    private final ContainerData data;
    /** Server side only: re-resolves {@code CONFIGURE_DEVICE} while the menu is open. */
    private final @Nullable MenuAccessCheck access;

    /** Client-side factory (the {@code MenuType} constructor): the client learns everything from data and the snapshot. */
    public SensorSettingsMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null,
                new ModuleContainer(new SecurityModuleSlots(MAX_MODULE_SLOTS, EnumSet.of(SecurityModule.RANGE), null)),
                new SimpleContainerData(DATA_COUNT));
    }

    /** Server-side: bound to {@code host}. */
    public SensorSettingsMenu(int containerId, Inventory inventory, SensorMenuHost host) {
        this(containerId, inventory, host, new ModuleContainer(host.modules()), serverData(host));
    }

    private SensorSettingsMenu(int containerId, Inventory inventory, @Nullable SensorMenuHost host,
            Container modules, ContainerData data) {
        super(LaserRegistrations.SENSOR_MENU.get(), containerId);
        this.owner = inventory.player;
        this.host = host;
        this.pos = host == null ? null : host.sensorBlockEntity().getBlockPos().immutable();
        this.data = data;
        this.access = host == null ? null : new MenuAccessCheck(player -> mayConfigure(host, player));
        int real = Math.min(MAX_MODULE_SLOTS, modules.getContainerSize());
        for (int i = 0; i < MAX_MODULE_SLOTS; i++) {
            int x = MODULE_X - (MAX_MODULE_SLOTS - 1 - i) * 18;
            if (i < real) {
                addSlot(new ModuleSlot(this, modules, i, x, MODULE_Y));
            } else {
                // Same slot layout on both sides whatever the host: a placeholder that holds nothing.
                addSlot(new ModuleSlot(this, new SimpleContainer(1), 0, x, MODULE_Y, i));
            }
        }
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(data);
    }

    /** The installation the sensor is linked to right now, or null (server side). */
    private static @Nullable Installation linkedInstallation(SensorMenuHost host) {
        BlockEntity be = host.sensorBlockEntity();
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return SecurityInstallations.get(level.getServer()).atDevice(DevicePos.of(level, be.getBlockPos())).orElse(null);
    }

    /** The rule the menu was opened with: linked, and {@code CONFIGURE_DEVICE} at OFFICER. */
    private static boolean mayConfigure(SensorMenuHost host, ServerPlayer player) {
        BlockEntity be = host.sensorBlockEntity();
        Installation installation = linkedInstallation(host);
        if (installation == null || !(be.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        return SensorPayloadHandlers.resolveConfigure(player, installation, DevicePos.of(level, be.getBlockPos()))
                .allowed();
    }

    /** Whether {@code player} may move modules right now (always true on the client: the server decides). */
    boolean mayUseModules(Player player) {
        return access == null || access.allowedNow(player);
    }

    private static ContainerData serverData(SensorMenuHost host) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_ENERGY_LO -> low(host.energyAmount());
                    case DATA_ENERGY_HI -> high(host.energyAmount());
                    case DATA_CAPACITY_LO -> low(host.energyCapacity());
                    case DATA_CAPACITY_HI -> high(host.energyCapacity());
                    case DATA_FLAGS -> flags(host);
                    case DATA_MODULE_SLOTS -> Math.min(MAX_MODULE_SLOTS, host.modules().slots());
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

    private static int flags(SensorMenuHost host) {
        int flags = 0;
        if (host.armed()) {
            flags |= FLAG_ARMED;
        }
        if (linkedInstallation(host) != null) {
            flags |= FLAG_LINKED;
        }
        if (host.isLaser()) {
            flags |= FLAG_LASER;
        }
        if (host.paired()) {
            flags |= FLAG_PAIRED;
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

    public boolean armed() {
        return (data.get(DATA_FLAGS) & FLAG_ARMED) != 0;
    }

    public boolean linked() {
        return (data.get(DATA_FLAGS) & FLAG_LINKED) != 0;
    }

    public boolean laser() {
        return (data.get(DATA_FLAGS) & FLAG_LASER) != 0;
    }

    public boolean paired() {
        return (data.get(DATA_FLAGS) & FLAG_PAIRED) != 0;
    }

    /** How many of the module slot positions are real for this sensor (0..{@value #MAX_MODULE_SLOTS}). */
    public int moduleSlots() {
        return Math.max(0, Math.min(MAX_MODULE_SLOTS, data.get(DATA_MODULE_SLOTS)));
    }

    /** The sensor position this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    /** The bound sensor (server side; null on the client). */
    public @Nullable SensorMenuHost host() {
        return host;
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
        if (index < MAX_MODULE_SLOTS) {
            if (!moveItemStackTo(stack, MAX_MODULE_SLOTS, end, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < MAX_MODULE_SLOTS && !moved; i++) {
                Slot target = this.slots.get(i);
                if (target.isActive() && !target.hasItem() && target.mayPlace(stack)) {
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
     * Valid while the sensor still stands within eight blocks of the player, is still linked, and the
     * player may still configure it (re-resolved at most every {@link MenuAccessCheck#INTERVAL_TICKS}).
     */
    @Override
    public boolean stillValid(Player player) {
        if (host == null) {
            return true; // client: the server decides
        }
        BlockEntity be = host.sensorBlockEntity();
        if (be.isRemoved() || be.getLevel() == null || be.getLevel().getBlockEntity(be.getBlockPos()) != be) {
            return false;
        }
        BlockPos at = be.getBlockPos();
        if (player.distanceToSqr(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        if (linkedInstallation(host) == null) {
            return false;
        }
        return access == null || access.allowed(player);
    }

    /** A module slot: one module per slot, only what the sensor accepts; a placeholder accepts nothing. */
    private static final class ModuleSlot extends Slot {

        private final SensorSettingsMenu menu;
        private final int position;
        private final boolean placeholder;

        ModuleSlot(SensorSettingsMenu menu, Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.menu = menu;
            this.position = index;
            this.placeholder = false;
        }

        ModuleSlot(SensorSettingsMenu menu, Container container, int index, int x, int y, int position) {
            super(container, index, x, y);
            this.menu = menu;
            this.position = position;
            this.placeholder = true;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !placeholder && isActive() && menu.mayUseModules(menu.owner)
                    && this.container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !placeholder && menu.mayUseModules(player) && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return !placeholder && position < menu.moduleSlotsForSlots();
        }
    }

    /**
     * The real slot count as seen by the slots: on the server the host's own count (data slots are
     * computed), on the client the synced value.
     */
    private int moduleSlotsForSlots() {
        if (host != null) {
            return Math.min(MAX_MODULE_SLOTS, host.modules().slots());
        }
        return moduleSlots();
    }
}
