package za.co.neroland.nerosecurity.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.menu.SecurityHubMenu;

/** Menu types through Neroland Core's {@link RegistrationProvider}. */
public final class NeroSecurityMenus {

    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroSecurityCommon.MOD_ID);

    public static final RegistryEntry<MenuType<SecurityHubMenu>> SECURITY_HUB = MENUS.register("security_hub",
            key -> new MenuType<>(SecurityHubMenu::new, FeatureFlags.VANILLA_SET));

    private NeroSecurityMenus() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
