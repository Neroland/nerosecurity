package za.co.neroland.nerosecurity.registry;

import java.util.Set;

import net.minecraft.resources.ResourceKey;
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

import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlock;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlockEntity;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeItem;
import za.co.neroland.nerosecurity.menu.sensor.SensorSettingsMenu;

/**
 * Registrations for the Laser Grid Node (block, block item, block-entity type) and the shared sensor
 * settings menu that both sensors open. Registered at exactly {@link DeviceIds#LASER_GRID_NODE}. Uses the
 * existing NeroSecurity providers; {@link #init()} forces the class to load from common init.
 */
public final class LaserRegistrations {

    public static final RegistryEntry<Block> LASER_GRID_NODE = NeroSecurityBlocks.BLOCKS.register(
            DeviceIds.LASER_GRID_NODE.getPath(), key -> new LaserGridNodeBlock(nodeProperties(key)));

    public static final RegistryEntry<BlockItem> LASER_GRID_NODE_ITEM = NeroSecurityItems.ITEMS.register(
            DeviceIds.LASER_GRID_NODE.getPath(),
            key -> new LaserGridNodeItem(LASER_GRID_NODE.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockEntityType<LaserGridNodeBlockEntity>> LASER_GRID_NODE_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register(DeviceIds.LASER_GRID_NODE.getPath(),
                    key -> new BlockEntityType<>((pos, state) -> new LaserGridNodeBlockEntity(
                            LaserRegistrations.LASER_GRID_NODE_ENTITY.get(), pos, state),
                            Set.of(LASER_GRID_NODE.get())));

    /** The shared sensor settings menu (laser grid node and camera, through {@code SensorMenuHost}). */
    public static final RegistryEntry<MenuType<SensorSettingsMenu>> SENSOR_MENU =
            NeroSecurityMenus.MENUS.register("sensor_settings",
                    key -> new MenuType<>(SensorSettingsMenu::new, FeatureFlags.VANILLA_SET));

    private LaserRegistrations() {
    }

    /** Metal device: iron-tier pickaxe for drops, pistons refused (26.3 renamed BLOCK to IMMOVEABLE). */
    private static BlockBehaviour.Properties nodeProperties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(MapColor.METAL)
                .strength(5.0F, 600.0F)
                .requiresCorrectToolForDrops()
                //? if >=26.3 {
                /*.pushReaction(PushReaction.IMMOVEABLE)
                *///?} else {
                .pushReaction(PushReaction.BLOCK)
                //?}
                .sound(SoundType.METAL);
    }

    /** Classload-forcing no-op; call after the base registry classes have initialised. */
    public static void init() {
    }

    /** Adds the laser grid node item to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(LASER_GRID_NODE_ITEM::get);
    }
}
