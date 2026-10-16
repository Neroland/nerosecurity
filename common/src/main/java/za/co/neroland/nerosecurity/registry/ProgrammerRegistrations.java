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

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.block.KeycardProgrammerBlock;
import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.item.KeycardProgrammerItem;
import za.co.neroland.nerosecurity.menu.KeycardProgrammerMenu;

/**
 * The Keycard Programmer's block, block entity type, block item and menu type. Each goes through the
 * existing NeroSecurity providers ({@link NeroSecurityBlocks#BLOCKS}, {@link NeroSecurityBlockEntities#BLOCK_ENTITIES},
 * {@link NeroSecurityItems#ITEMS}, {@link NeroSecurityMenus#MENUS}), so the loader entry points that attach
 * those providers pick these up with no extra wiring.
 *
 * <p>The programmer is an early-tier, unpowered block: no energy buffer, no side configuration, no ticker.
 */
public final class ProgrammerRegistrations {

    public static final RegistryEntry<Block> BLOCK = NeroSecurityBlocks.BLOCKS.register("keycard_programmer",
            key -> new KeycardProgrammerBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)));

    public static final RegistryEntry<BlockEntityType<KeycardProgrammerBlockEntity>> BLOCK_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register("keycard_programmer",
                    key -> new BlockEntityType<>(KeycardProgrammerBlockEntity::new, Set.of(BLOCK.get())));

    public static final RegistryEntry<BlockItem> ITEM = NeroSecurityItems.ITEMS.register("keycard_programmer",
            key -> new KeycardProgrammerItem(BLOCK.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<MenuType<KeycardProgrammerMenu>> MENU =
            NeroSecurityMenus.MENUS.register("keycard_programmer",
                    key -> new MenuType<>(KeycardProgrammerMenu::new, FeatureFlags.VANILLA_SET));

    private ProgrammerRegistrations() {
    }

    /** Classload-forcing no-op: registers the four entries above. */
    public static void init() {
    }

    /** Adds the programmer's block item to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(ITEM::get);
    }
}
