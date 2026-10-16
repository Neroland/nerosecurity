package za.co.neroland.nerosecurity.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.item.KeycardItem;
import za.co.neroland.nerosecurity.item.LinkingToolItem;
import za.co.neroland.nerosecurity.item.SecurityHubItem;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleItem;

/**
 * Item registrations through Neroland Core's {@link RegistrationProvider}. There is no NeroSecurity
 * creative tab: every item joins Core's shared {@code Neroland} tab (the ecosystem convention).
 */
public final class NeroSecurityItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroSecurityCommon.MOD_ID);

    public static final RegistryEntry<BlockItem> SECURITY_HUB = ITEMS.register("security_hub",
            key -> new SecurityHubItem(NeroSecurityBlocks.SECURITY_HUB.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<Item> LINKING_TOOL = ITEMS.register("linking_tool",
            key -> new LinkingToolItem(new Item.Properties().setId(key).stacksTo(1)));

    /** A keycard: blank until written at a programmer (the {@code nerosecurity:keycard} component). */
    public static final RegistryEntry<Item> KEYCARD = ITEMS.register("keycard",
            key -> new KeycardItem(new Item.Properties().setId(key).stacksTo(16)));

    public static final RegistryEntry<Item> ENCRYPTION_MODULE = module(SecurityModule.ENCRYPTION);
    public static final RegistryEntry<Item> HARDNESS_MODULE = module(SecurityModule.HARDNESS);
    public static final RegistryEntry<Item> OPEN_SPEED_MODULE = module(SecurityModule.OPEN_SPEED);
    /** Fitted to a laser grid node or a camera: +4 blocks of range. */
    public static final RegistryEntry<Item> RANGE_MODULE = module(SecurityModule.RANGE);
    /** Fitted to a force field emitter: +1 field strength. */
    public static final RegistryEntry<Item> STRENGTH_MODULE = module(SecurityModule.STRENGTH);

    private static RegistryEntry<Item> module(SecurityModule type) {
        return ITEMS.register(type.itemPath,
                key -> new SecurityModuleItem(type, new Item.Properties().setId(key).stacksTo(16)));
    }

    private NeroSecurityItems() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }

    /** Adds every NeroSecurity item to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(SECURITY_HUB::get);
        CoreCreativeTab.add(LINKING_TOOL::get);
        CoreCreativeTab.add(KEYCARD::get);
        CoreCreativeTab.add(ENCRYPTION_MODULE::get);
        CoreCreativeTab.add(HARDNESS_MODULE::get);
        CoreCreativeTab.add(OPEN_SPEED_MODULE::get);
        CoreCreativeTab.add(RANGE_MODULE::get);
        CoreCreativeTab.add(STRENGTH_MODULE::get);
    }
}
