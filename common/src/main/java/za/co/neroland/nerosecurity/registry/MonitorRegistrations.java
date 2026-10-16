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

import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.block.door.SecurityDoorItem;
import za.co.neroland.nerosecurity.block.monitor.SecurityMonitorBlock;
import za.co.neroland.nerosecurity.block.monitor.SecurityMonitorBlockEntity;
import za.co.neroland.nerosecurity.menu.monitor.MonitorMenu;

/**
 * The Security Monitor's block (at exactly {@link DeviceIds#SECURITY_MONITOR}), block entity type, block
 * item and menu type, through the existing NeroSecurity providers. Unpowered: no energy, no ticker.
 */
public final class MonitorRegistrations {

    private static final String PATH = DeviceIds.SECURITY_MONITOR.getPath();

    public static final RegistryEntry<Block> BLOCK = NeroSecurityBlocks.BLOCKS.register(PATH,
            key -> new SecurityMonitorBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 300.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final RegistryEntry<BlockEntityType<SecurityMonitorBlockEntity>> BLOCK_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register(PATH,
                    key -> new BlockEntityType<>(SecurityMonitorBlockEntity::new, Set.of(BLOCK.get())));

    public static final RegistryEntry<BlockItem> ITEM = NeroSecurityItems.ITEMS.register(PATH,
            key -> new SecurityDoorItem(BLOCK.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "block.nerosecurity.security_monitor.tooltip"));

    public static final RegistryEntry<MenuType<MonitorMenu>> MENU = NeroSecurityMenus.MENUS.register(PATH,
            key -> new MenuType<>(MonitorMenu::new, FeatureFlags.VANILLA_SET));

    private MonitorRegistrations() {
    }

    /** Classload-forcing no-op: registers the four entries above. */
    public static void init() {
    }

    /** Adds the monitor's block item to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(ITEM::get);
    }
}
