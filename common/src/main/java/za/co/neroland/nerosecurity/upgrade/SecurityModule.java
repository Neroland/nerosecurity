package za.co.neroland.nerosecurity.upgrade;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * NeroSecurity's own upgrade modules. They are local to this mod because their effects (encrypted
 * cards, door hardness, door speed, sensor range, field strength) have no counterpart in Neroland Core's shared upgrade types.
 */
public enum SecurityModule {
    /** Fitted to a keycard programmer: lets the owner write and copy encrypted cards. */
    ENCRYPTION("encryption_module", "module.nerosecurity.encryption"),
    /** Fitted to a door: harder to break and more blast resistant (capped by config). */
    HARDNESS("hardness_module", "module.nerosecurity.hardness"),
    /** Fitted to a door: opens and closes faster. */
    OPEN_SPEED("open_speed_module", "module.nerosecurity.open_speed"),
    /** Fitted to a laser grid node or camera: +4 blocks of range each (capped by config). */
    RANGE("range_module", "module.nerosecurity.range"),
    /** Fitted to a force field emitter: +1 field strength each (more load before overload, more power). */
    STRENGTH("strength_module", "module.nerosecurity.strength");

    /** The item's registry path. */
    public final String itemPath;
    public final String translationKey;

    SecurityModule(String itemPath, String translationKey) {
        this.itemPath = itemPath;
        this.translationKey = translationKey;
    }

    /** The module type of {@code stack}, or null when it is empty or not a module. */
    public static @Nullable SecurityModule of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getItem() instanceof SecurityModuleItem module ? module.type : null;
    }
}
