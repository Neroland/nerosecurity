package za.co.neroland.nerosecurity.block.laser;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The item form of the laser grid node; it carries the block's tooltip line. */
public class LaserGridNodeItem extends BlockItem {

    public static final String TOOLTIP_KEY = "block.nerosecurity.laser_grid_node.tooltip";

    public LaserGridNodeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }
}
