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

import za.co.neroland.nerosecurity.block.alarm.AlarmBlock;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.block.alarm.AlarmItem;
import za.co.neroland.nerosecurity.menu.alarm.AlarmMenu;

/**
 * The alarm's block (registered at exactly {@code DeviceIds.ALARM}), block entity type, block item and
 * settings menu type, through the existing NeroSecurity providers.
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor needs the accesswidener
 * entry the hub relies on; the new type needs its own line if the widener lists types individually.
 */
public final class AlarmRegistrations {

    /** Breakable by anyone with an iron pickaxe; pistons cannot move it (that would orphan its link). */
    public static final RegistryEntry<Block> ALARM = NeroSecurityBlocks.BLOCKS.register("alarm",
            key -> new AlarmBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 300.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(AlarmBlock::lightLevel)
                    //? if >=26.3 {
                    /*.pushReaction(PushReaction.IMMOVEABLE)
                    *///?} else {
                    .pushReaction(PushReaction.BLOCK)
                    //?}
                    .sound(SoundType.METAL)));

    public static final RegistryEntry<BlockEntityType<AlarmBlockEntity>> ALARM_BLOCK_ENTITY =
            NeroSecurityBlockEntities.BLOCK_ENTITIES.register("alarm",
                    key -> new BlockEntityType<>(AlarmBlockEntity::new, Set.of(ALARM.get())));

    public static final RegistryEntry<BlockItem> ALARM_ITEM = NeroSecurityItems.ITEMS.register("alarm",
            key -> new AlarmItem(ALARM.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<MenuType<AlarmMenu>> ALARM_MENU = NeroSecurityMenus.MENUS.register("alarm",
            key -> new MenuType<>(AlarmMenu::new, FeatureFlags.VANILLA_SET));

    private AlarmRegistrations() {
    }

    /** Classload-forcing no-op: registers the entries above with the providers. */
    public static void init() {
    }

    /** Adds the alarm to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(ALARM_ITEM::get);
    }
}
