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

import za.co.neroland.nerosecurity.block.door.BlastDoorBlock;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlock;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.block.door.SecurityDoorItem;
import za.co.neroland.nerosecurity.menu.door.SecurityDoorMenu;

/**
 * Registrations for the Security Door (1x2) and Blast Door (2x2): blocks, block items, one
 * block-entity type per door (the controller part only) and the door settings menu. Uses the existing
 * NeroSecurity providers; {@link #init()} forces the class to load from common init.
 */
public final class DoorRegistrations {

    public static final RegistryEntry<Block> SECURITY_DOOR = NeroSecurityBlocks.BLOCKS.register("security_door",
            key -> new SecurityDoorBlock(doorProperties(key, 25.0F, 600.0F)));

    public static final RegistryEntry<Block> BLAST_DOOR = NeroSecurityBlocks.BLOCKS.register("blast_door",
            key -> new BlastDoorBlock(doorProperties(key, 50.0F, 1200.0F)));

    public static final RegistryEntry<BlockItem> SECURITY_DOOR_ITEM = NeroSecurityItems.ITEMS.register("security_door",
            key -> new SecurityDoorItem(SECURITY_DOOR.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "block.nerosecurity.security_door.tooltip"));

    public static final RegistryEntry<BlockItem> BLAST_DOOR_ITEM = NeroSecurityItems.ITEMS.register("blast_door",
            key -> new SecurityDoorItem(BLAST_DOOR.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "block.nerosecurity.blast_door.tooltip"));

    public static final RegistryEntry<BlockEntityType<SecurityDoorBlockEntity>> SECURITY_DOOR_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register("security_door",
                    key -> new BlockEntityType<>((pos, state) -> new SecurityDoorBlockEntity(
                            DoorRegistrations.SECURITY_DOOR_ENTITY.get(), pos, state),
                            Set.of(SECURITY_DOOR.get())));

    public static final RegistryEntry<BlockEntityType<SecurityDoorBlockEntity>> BLAST_DOOR_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register("blast_door",
                    key -> new BlockEntityType<>((pos, state) -> new SecurityDoorBlockEntity(
                            DoorRegistrations.BLAST_DOOR_ENTITY.get(), pos, state),
                            Set.of(BLAST_DOOR.get())));

    public static final RegistryEntry<MenuType<SecurityDoorMenu>> SECURITY_DOOR_MENU =
            NeroSecurityMenus.MENUS.register("security_door",
                    key -> new MenuType<>(SecurityDoorMenu::new, FeatureFlags.VANILLA_SET));

    private DoorRegistrations() {
    }

    /**
     * Metal doors: iron-tier pickaxe for drops, no occlusion (the open state is a thin panel), pistons
     * refused (26.3 renamed BLOCK to IMMOVEABLE).
     */
    private static BlockBehaviour.Properties doorProperties(net.minecraft.resources.ResourceKey<Block> key,
            float hardness, float resistance) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(MapColor.METAL)
                .strength(hardness, resistance)
                .requiresCorrectToolForDrops()
                .noOcclusion()
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

    /** Adds both door items to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(SECURITY_DOOR_ITEM::get);
        CoreCreativeTab.add(BLAST_DOOR_ITEM::get);
    }
}
