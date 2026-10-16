package za.co.neroland.nerosecurity.menu.field;

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

import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.MenuAccessCheck;
import za.co.neroland.nerosecurity.menu.door.ModuleContainer;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The force field settings menu: the emitter's four module slots (strength and range modules only) plus the player
 * inventory, and live status (energy, state, load, cooldown, strength, draw, area, range) through data slots. It binds
 * the open screen to one emitter so every serverbound field payload can be checked against it, and closes when the
 * player walks away, the emitter is gone or unlinked, or the player may no longer configure it ({@code
 * CONFIGURE_DEVICE} re-resolved through {@link MenuAccessCheck}; every module pickup and place re-resolves first).
 * The settings themselves travel in the field snapshot payload. Nothing about any player is in here.
 */
public class FieldMenu extends AbstractContainerMenu {

    public static final int MODULE_SLOTS = ForceFieldEmitterBlockEntity.MODULE_SLOTS;
    public static final int MODULE_X = 98;
    public static final int MODULE_Y = 138;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 164;

    public static final int DATA_ENERGY_LO = 0;
    public static final int DATA_ENERGY_HI = 1;
    public static final int DATA_CAPACITY_LO = 2;
    public static final int DATA_CAPACITY_HI = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_LOAD_PERCENT = 5;
    public static final int DATA_COOLDOWN = 6;
    public static final int DATA_STRENGTH = 7;
    public static final int DATA_EFFECTIVE_STRENGTH = 8;
    public static final int DATA_DRAW_LO = 9;
    public static final int DATA_DRAW_HI = 10;
    public static final int DATA_AREA = 11;
    public static final int DATA_RANGE = 12;
    public static final int DATA_FLAGS = 13;
    public static final int DATA_COUNT = 14;

    public static final int FLAG_LINKED = 1;
    public static final int FLAG_LOCKDOWN = 2;

    private final @Nullable ForceFieldEmitterBlockEntity emitter;
    /** The player this menu belongs to (a menu is per player); module placement is checked against them. */
    private final Player owner;
    private final @Nullable BlockPos pos;
    private final ContainerData data;
    /** Server side only: re-resolves {@code CONFIGURE_DEVICE} while the menu is open. */
    private final @Nullable MenuAccessCheck access;

    /** Client-side factory (the {@code MenuType} constructor): the client learns everything from data and the snapshot. */
    public FieldMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null,
                new ModuleContainer(new SecurityModuleSlots(MODULE_SLOTS,
                        EnumSet.of(SecurityModule.STRENGTH, SecurityModule.RANGE), null)),
                new SimpleContainerData(DATA_COUNT));
    }

    /** Server-side: bound to {@code emitter}. */
    public FieldMenu(int containerId, Inventory inventory, ForceFieldEmitterBlockEntity emitter) {
        this(containerId, inventory, emitter, new ModuleContainer(emitter.modules()), serverData(emitter));
    }

    private FieldMenu(int containerId, Inventory inventory, @Nullable ForceFieldEmitterBlockEntity emitter,
            Container modules, ContainerData data) {
        super(FieldRegistrations.FIELD_MENU.get(), containerId);
        this.owner = inventory.player;
        this.emitter = emitter;
        this.pos = emitter == null ? null : emitter.getBlockPos().immutable();
        this.data = data;
        this.access = emitter == null ? null : new MenuAccessCheck(player -> mayConfigure(emitter, player));
        int real = Math.min(MODULE_SLOTS, modules.getContainerSize());
        for (int i = 0; i < real; i++) {
            addSlot(new ModuleSlot(this, modules, i, MODULE_X + i * 18, MODULE_Y));
        }
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(data);
    }

    private static @Nullable Installation linkedInstallation(ForceFieldEmitterBlockEntity emitter) {
        if (!(emitter.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return SecurityInstallations.get(level.getServer()).atDevice(DevicePos.of(level, emitter.getBlockPos()))
                .orElse(null);
    }

    /** The rule the menu was opened with: linked, and {@code CONFIGURE_DEVICE} at OFFICER. */
    private static boolean mayConfigure(ForceFieldEmitterBlockEntity emitter, ServerPlayer player) {
        Installation installation = linkedInstallation(emitter);
        if (installation == null || !(emitter.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        return FieldPayloadHandlers.resolveConfigure(player, installation, DevicePos.of(level, emitter.getBlockPos()))
                .allowed();
    }

    boolean mayUseModules(Player player) {
        return access == null || access.allowedNow(player);
    }

    private static ContainerData serverData(ForceFieldEmitterBlockEntity emitter) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_ENERGY_LO -> low(emitter.energyAmount());
                    case DATA_ENERGY_HI -> high(emitter.energyAmount());
                    case DATA_CAPACITY_LO -> low(emitter.energyCapacity());
                    case DATA_CAPACITY_HI -> high(emitter.energyCapacity());
                    case DATA_STATE -> emitter.fieldState().ordinal();
                    case DATA_LOAD_PERCENT -> Math.round(emitter.loadFraction() * 100.0F);
                    case DATA_COOLDOWN -> Math.min(0x7FFF, emitter.getLevel() == null ? 0
                            : emitter.cooldownSeconds(emitter.getLevel().getGameTime()));
                    case DATA_STRENGTH -> emitter.strength();
                    case DATA_EFFECTIVE_STRENGTH -> emitter.effectiveStrength();
                    case DATA_DRAW_LO -> low(emitter.drawPerTick());
                    case DATA_DRAW_HI -> high(emitter.drawPerTick());
                    case DATA_AREA -> Math.min(0x7FFF, emitter.area());
                    case DATA_RANGE -> emitter.range();
                    case DATA_FLAGS -> flags(emitter);
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

    private static int flags(ForceFieldEmitterBlockEntity emitter) {
        int flags = 0;
        if (linkedInstallation(emitter) != null) {
            flags |= FLAG_LINKED;
        }
        if (emitter.lockdownBoost()) {
            flags |= FLAG_LOCKDOWN;
        }
        return flags;
    }

    /** Data slots travel as 16-bit values, so larger numbers are split into two halves. */
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

    public FieldState state() {
        FieldState[] states = FieldState.values();
        return states[Math.floorMod(data.get(DATA_STATE), states.length)];
    }

    public int loadPercent() {
        return Math.max(0, Math.min(100, data.get(DATA_LOAD_PERCENT)));
    }

    public int cooldownSeconds() {
        return Math.max(0, data.get(DATA_COOLDOWN));
    }

    public int strength() {
        return Math.max(0, data.get(DATA_STRENGTH));
    }

    public int effectiveStrength() {
        return Math.max(0, data.get(DATA_EFFECTIVE_STRENGTH));
    }

    public long drawPerTick() {
        return joined(DATA_DRAW_LO, DATA_DRAW_HI);
    }

    public int area() {
        return Math.max(0, data.get(DATA_AREA));
    }

    public int range() {
        return Math.max(0, data.get(DATA_RANGE));
    }

    public boolean linked() {
        return (data.get(DATA_FLAGS) & FLAG_LINKED) != 0;
    }

    public boolean lockdown() {
        return (data.get(DATA_FLAGS) & FLAG_LOCKDOWN) != 0;
    }

    /** The emitter position this menu is bound to (server side; null on the client). */
    public @Nullable BlockPos pos() {
        return pos;
    }

    /** The bound emitter (server side; null on the client). */
    public @Nullable ForceFieldEmitterBlockEntity emitter() {
        return emitter;
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
        int moduleSlots = Math.min(MODULE_SLOTS, this.slots.size());
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
     * Valid while the emitter still stands within eight blocks of the player, is still linked, and the player may still
     * configure it (re-resolved at most every {@link MenuAccessCheck#INTERVAL_TICKS}).
     */
    @Override
    public boolean stillValid(Player player) {
        if (emitter == null) {
            return true; // client: the server decides
        }
        if (emitter.isRemoved() || emitter.getLevel() == null
                || emitter.getLevel().getBlockEntity(emitter.getBlockPos()) != emitter) {
            return false;
        }
        BlockPos at = emitter.getBlockPos();
        if (player.distanceToSqr(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        if (linkedInstallation(emitter) == null) {
            return false;
        }
        return access == null || access.allowed(player);
    }

    /** A module slot: one module per slot, only strength and range modules, only while the player may configure. */
    private static final class ModuleSlot extends Slot {

        private final FieldMenu menu;

        ModuleSlot(FieldMenu menu, Container container, int index, int x, int y) {
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
