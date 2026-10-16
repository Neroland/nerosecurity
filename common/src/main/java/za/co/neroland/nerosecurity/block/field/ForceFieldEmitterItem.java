package za.co.neroland.nerosecurity.block.field;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The item form of the force field emitter; it carries the emitter's and the field's tooltip lines. */
public class ForceFieldEmitterItem extends BlockItem {

    public static final String TOOLTIP_KEY = "block.nerosecurity.force_field_emitter.tooltip";
    public static final String FIELD_TOOLTIP_KEY = "block.nerosecurity.force_field.tooltip";

    public ForceFieldEmitterItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(FIELD_TOOLTIP_KEY).withStyle(ChatFormatting.DARK_GRAY));
    }
}
