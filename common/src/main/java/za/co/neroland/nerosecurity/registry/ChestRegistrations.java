package za.co.neroland.nerosecurity.registry;

import java.util.Set;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.block.chest.SecureChestBlock;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.block.chest.SecureChestItem;
import za.co.neroland.nerosecurity.menu.chest.ChestSettingsMenu;

/**
 * The secure chest's block, block entity type, block item and settings menu type, registered through
 * the existing NeroSecurity providers. The chest's item screen is the vanilla 3-row chest menu, so it
 * needs no menu type of its own.
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor is widened by the
 * existing accesswidener entry, the same one the hub relies on.
 */
public final class ChestRegistrations {

    /** Raid-resistant but breakable by anyone with an iron pickaxe; pistons cannot move it. */
    public static final RegistryEntry<Block> SECURE_CHEST = NeroSecurityBlocks.BLOCKS.register("secure_chest",
            key -> new SecureChestBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    //? if >=26.3 {
                    /*.pushReaction(PushReaction.IMMOVEABLE)
                    *///?} else {
                    .pushReaction(PushReaction.BLOCK)
                    //?}
                    .sound(SoundType.METAL)));

    public static final RegistryEntry<BlockEntityType<SecureChestBlockEntity>> SECURE_CHEST_BLOCK_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register("secure_chest",
                    key -> new BlockEntityType<>(SecureChestBlockEntity::new, Set.of(SECURE_CHEST.get())));

    public static final RegistryEntry<BlockItem> SECURE_CHEST_ITEM = NeroSecurityItems.ITEMS.register("secure_chest",
            key -> new SecureChestItem(SECURE_CHEST.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<MenuType<ChestSettingsMenu>> SECURE_CHEST_SETTINGS_MENU =
            NeroSecurityMenus.MENUS.register("secure_chest_settings",
                    key -> new MenuType<>(ChestSettingsMenu::new, FeatureFlags.VANILLA_SET));

    private ChestRegistrations() {
    }

    /** Classload-forcing no-op: registers the entries above with the providers. */
    public static void init() {
    }

    /** Adds the secure chest to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(SECURE_CHEST_ITEM::get);
    }
}
