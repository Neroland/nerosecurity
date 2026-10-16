package za.co.neroland.nerosecurity.registry;

import java.util.Set;

import net.minecraft.resources.ResourceKey;
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
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlock;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlockEntity;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraItem;

/**
 * Registrations for the Security Camera: block, block item and block-entity type, at exactly
 * {@link DeviceIds#SECURITY_CAMERA}. The camera has no menu of its own: sneak-use opens the shared sensor
 * settings menu through {@code SensorHost}. Uses the existing NeroSecurity providers; {@link #init()}
 * forces the class to load from common init.
 */
public final class CameraRegistrations {

    public static final String PATH = DeviceIds.SECURITY_CAMERA.getPath();

    public static final RegistryEntry<Block> SECURITY_CAMERA = NeroSecurityBlocks.BLOCKS.register(PATH,
            key -> new SecurityCameraBlock(cameraProperties(key)));

    public static final RegistryEntry<BlockItem> SECURITY_CAMERA_ITEM = NeroSecurityItems.ITEMS.register(PATH,
            key -> new SecurityCameraItem(SECURITY_CAMERA.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockEntityType<SecurityCameraBlockEntity>> SECURITY_CAMERA_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register(PATH,
                    key -> new BlockEntityType<>((pos, state) -> new SecurityCameraBlockEntity(
                            CameraRegistrations.SECURITY_CAMERA_ENTITY.get(), pos, state),
                            Set.of(SECURITY_CAMERA.get())));

    private CameraRegistrations() {
    }

    /**
     * Metal, breakable by anyone with an iron-tier pickaxe (hardness 3, resistance 300: raidable, under
     * obsidian class), pistons refused (26.3 renamed BLOCK to IMMOVEABLE).
     */
    private static BlockBehaviour.Properties cameraProperties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(MapColor.METAL)
                .strength(3.0F, 300.0F)
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

    /** Adds the camera item to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(SECURITY_CAMERA_ITEM::get);
    }
}
