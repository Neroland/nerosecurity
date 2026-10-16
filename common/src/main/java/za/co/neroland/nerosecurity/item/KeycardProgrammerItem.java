package za.co.neroland.nerosecurity.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The Keycard Programmer's item form. Blocks have no hover-text hook in 26.x, so the tooltip lives here. */
public class KeycardProgrammerItem extends BlockItem {

    public KeycardProgrammerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("block.nerosecurity.keycard_programmer.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
