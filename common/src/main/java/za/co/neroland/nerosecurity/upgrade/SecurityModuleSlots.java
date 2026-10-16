package za.co.neroland.nerosecurity.upgrade;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A small bank of module slots a NeroSecurity device embeds, shaped like Neroland Core's upgrade
 * container (same slot list, change callback and per-slot save keys) but counting
 * {@link SecurityModule}s and accepting only the types the host lists.
 *
 * <p>Counts include stack sizes, so two hardness modules in one slot count as two.
 */
public final class SecurityModuleSlots {

    public static final String KEY_PREFIX = "Module";

    private final NonNullList<ItemStack> items;
    private final EnumSet<SecurityModule> accepted;
    private final Runnable onChange;

    public SecurityModuleSlots(int slots, Set<SecurityModule> accepted, Runnable onChange) {
        this.items = NonNullList.withSize(Math.max(0, slots), ItemStack.EMPTY);
        this.accepted = accepted == null || accepted.isEmpty()
                ? EnumSet.noneOf(SecurityModule.class) : EnumSet.copyOf(accepted);
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public int slots() {
        return items.size();
    }

    public ItemStack getStack(int index) {
        return items.get(index);
    }

    public void setStack(int index, ItemStack stack) {
        items.set(index, stack == null ? ItemStack.EMPTY : stack);
        onChange.run();
    }

    /** Whether {@code stack} is a module of a type these slots take. */
    public boolean isAccepted(ItemStack stack) {
        SecurityModule type = SecurityModule.of(stack);
        return type != null && accepted.contains(type);
    }

    public Set<SecurityModule> accepted() {
        return EnumSet.copyOf(accepted);
    }

    /** Total modules of {@code type} across all slots (stack sizes counted). Unaccepted types count 0. */
    public int count(SecurityModule type) {
        if (!accepted.contains(type)) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : items) {
            if (SecurityModule.of(stack) == type) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Whether at least one module of {@code type} is fitted. */
    public boolean has(SecurityModule type) {
        return count(type) > 0;
    }

    // --- persistence (host calls these from saveAdditional / loadAdditional) ----------------------

    public void save(ValueOutput output) {
        for (int i = 0; i < items.size(); i++) {
            output.store(KEY_PREFIX + i, ItemStack.OPTIONAL_CODEC, items.get(i));
        }
    }

    public void load(ValueInput input) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, input.read(KEY_PREFIX + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
    }
}
