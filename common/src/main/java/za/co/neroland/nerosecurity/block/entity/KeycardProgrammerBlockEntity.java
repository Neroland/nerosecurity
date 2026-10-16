package za.co.neroland.nerosecurity.block.entity;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.keycard.KeycardService;
import za.co.neroland.nerosecurity.menu.KeycardProgrammerMenu;
import za.co.neroland.nerosecurity.registry.ProgrammerRegistrations;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The programmer's block entity: three slots and nothing else. It holds no installation pointer.
 *
 * <ul>
 *   <li>Slot {@value #SLOT_CARD} &mdash; CARD: the keycard to write, wipe or copy from (any keycard).</li>
 *   <li>Slot {@value #SLOT_BLANK} &mdash; BLANK: the copy target (blank keycards only).</li>
 *   <li>Slot {@value #SLOT_MODULE} &mdash; MODULE: one encryption module ({@link SecurityModuleSlots}).</li>
 * </ul>
 *
 * <p>Saved as {@code Items} (the two card slots, {@link ContainerHelper}) plus {@code Modules}. Every slot
 * holds one item. Contents drop when the block is removed, and a removed programmer is unlinked from its
 * installation so no ghost device squats the position.
 */
public class KeycardProgrammerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_CARD = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_MODULE = 2;
    public static final int SLOT_COUNT = 3;

    private static final String TAG_MODULES = "Modules";

    private final NonNullList<ItemStack> cards = NonNullList.withSize(2, ItemStack.EMPTY);
    private final SecurityModuleSlots modules = new SecurityModuleSlots(1, Set.of(SecurityModule.ENCRYPTION),
            this::setChanged);
    private final Container container = new ProgrammerContainer();

    public KeycardProgrammerBlockEntity(BlockPos pos, BlockState state) {
        super(ProgrammerRegistrations.BLOCK_ENTITY.get(), pos, state);
    }

    /** The three slots as a vanilla container (the menu's view). */
    public Container container() {
        return container;
    }

    /** Whether an encryption module is fitted. Read on the server; never taken from the client. */
    public boolean hasEncryptionModule() {
        return modules.has(SecurityModule.ENCRYPTION);
    }

    public ItemStack cardStack() {
        return cards.get(SLOT_CARD);
    }

    public ItemStack blankStack() {
        return cards.get(SLOT_BLANK);
    }

    /** Which stacks each slot takes. Shared by the server container and the client menu's slots. */
    public static boolean accepts(int slot, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case SLOT_CARD -> KeycardService.isKeycard(stack);
            case SLOT_BLANK -> KeycardService.isBlank(stack);
            case SLOT_MODULE -> SecurityModule.of(stack) == SecurityModule.ENCRYPTION;
            default -> false;
        };
    }

    // --- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, cards);
        modules.save(output.child(TAG_MODULES));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < cards.size(); i++) {
            cards.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, cards);
        modules.load(input.childOrEmpty(TAG_MODULES));
    }

    /**
     * The block is being replaced (not unloaded): drop the contents, and unlink the position so a new block
     * placed there later does not inherit the installation. Store bookkeeping plus the item drop only.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        Containers.dropContents(level, pos, container);
        if (level instanceof ServerLevel serverLevel) {
            DevicePos at = DevicePos.of(serverLevel, pos);
            Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
            if (installation != null) {
                InstallationService.unlinkByLoss(serverLevel.getServer(), installation, at);
            }
        }
    }

    // --- menu -----------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("programmer.nerosecurity.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (level == null) {
            return null;
        }
        return new KeycardProgrammerMenu(containerId, inventory, this);
    }

    /** Slots 0..1 are the card list, slot 2 is the module bank. Every slot holds one item. */
    private final class ProgrammerContainer implements Container {

        @Override
        public int getContainerSize() {
            return SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            if (slot == SLOT_MODULE) {
                return modules.getStack(0);
            }
            return slot >= 0 && slot < cards.size() ? cards.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (current.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = current.split(amount);
            if (current.isEmpty()) {
                put(slot, ItemStack.EMPTY);
            }
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = getItem(slot);
            put(slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            ItemStack value = stack == null ? ItemStack.EMPTY : stack;
            value.limitSize(getMaxStackSize());
            put(slot, value);
            setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }

        @Override
        public void setChanged() {
            KeycardProgrammerBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(KeycardProgrammerBlockEntity.this, player);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < cards.size(); i++) {
                cards.set(i, ItemStack.EMPTY);
            }
            modules.setStack(0, ItemStack.EMPTY);
        }

        private void put(int slot, ItemStack stack) {
            if (slot == SLOT_MODULE) {
                modules.setStack(0, stack);
            } else if (slot >= 0 && slot < cards.size()) {
                cards.set(slot, stack);
            }
        }
    }
}
