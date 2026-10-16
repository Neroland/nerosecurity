package za.co.neroland.nerosecurity.registry;

import java.util.Set;

import net.minecraft.resources.Identifier;
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

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlockEntity;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterItem;
import za.co.neroland.nerosecurity.menu.field.FieldMenu;

/**
 * Registrations for the Force Field Emitter (block, item, block-entity type), the Force Field block (block and its
 * tiny block-entity type; no item, no loot) and the field settings menu. Uses the existing NeroSecurity providers;
 * {@link #init()} forces the class to load from common init.
 */
public final class FieldRegistrations {

    /** The emitter's registry id, as the installation's device list stores it. */
    public static final Identifier EMITTER_ID =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "force_field_emitter");
    public static final Identifier FIELD_ID =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "force_field");

    public static final RegistryEntry<Block> EMITTER_BLOCK = NeroSecurityBlocks.BLOCKS.register(
            EMITTER_ID.getPath(), key -> new ForceFieldEmitterBlock(emitterProperties(key)));

    public static final RegistryEntry<Block> FIELD_BLOCK = NeroSecurityBlocks.BLOCKS.register(
            FIELD_ID.getPath(), key -> new ForceFieldBlock(fieldProperties(key)));

    public static final RegistryEntry<BlockItem> EMITTER_ITEM = NeroSecurityItems.ITEMS.register(
            EMITTER_ID.getPath(),
            key -> new ForceFieldEmitterItem(EMITTER_BLOCK.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockEntityType<ForceFieldEmitterBlockEntity>> EMITTER_BE =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register(EMITTER_ID.getPath(),
                    key -> new BlockEntityType<>((pos, state) -> new ForceFieldEmitterBlockEntity(
                            FieldRegistrations.EMITTER_BE.get(), pos, state),
                            Set.of(EMITTER_BLOCK.get())));

    public static final RegistryEntry<BlockEntityType<ForceFieldBlockEntity>> FIELD_BE =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register(FIELD_ID.getPath(),
                    key -> new BlockEntityType<>((pos, state) -> new ForceFieldBlockEntity(
                            FieldRegistrations.FIELD_BE.get(), pos, state),
                            Set.of(FIELD_BLOCK.get())));

    /** The field settings menu (four module slots, live status). */
    public static final RegistryEntry<MenuType<FieldMenu>> FIELD_MENU =
            NeroSecurityMenus.MENUS.register("force_field_emitter",
                    key -> new MenuType<>(FieldMenu::new, FeatureFlags.VANILLA_SET));

    private FieldRegistrations() {
    }

    /** Metal device: iron-tier pickaxe for drops, pistons refused (26.3 renamed BLOCK to IMMOVEABLE). */
    private static BlockBehaviour.Properties emitterProperties(ResourceKey<Block> key) {
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

    /**
     * The field: unmineable (hardness -1; raided through its emitter's overload meter), see-through, lit, never
     * suffocating, view-blocking, a redstone conductor or a spawn, no loot, random ticks for orphan clean-up, a dynamic
     * shape (collision depends on who asks) and immovable. Explosion resistance is read from config by the block.
     */
    private static BlockBehaviour.Properties fieldProperties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(-1.0F, 6.0F)
                .noOcclusion()
                .noLootTable()
                .lightLevel(state -> 5)
                .isSuffocating((state, getter, pos) -> false)
                //? if >=26.3 {
                /*.isViewBlocking((state, getter, pos, context) -> false)
                *///?} else {
                .isViewBlocking((state, getter, pos) -> false)
                //?}
                .isRedstoneConductor((state, getter, pos) -> false)
                .isValidSpawn((state, getter, pos, type) -> false)
                .dynamicShape()
                .randomTicks()
                //? if >=26.3 {
                /*.pushReaction(PushReaction.IMMOVEABLE)
                *///?} else {
                .pushReaction(PushReaction.BLOCK)
                //?}
                .sound(SoundType.GLASS);
    }

    /** Classload-forcing no-op; call after the base registry classes have initialised. */
    public static void init() {
    }

    /** Adds the emitter item to Core's shared creative tab (the field block has no item). */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(EMITTER_ITEM::get);
    }
}
