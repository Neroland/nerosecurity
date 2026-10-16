package za.co.neroland.nerosecurity.upgrade;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** One of the three NeroSecurity upgrade module items. Its {@link #type} is what the slots count. */
public final class SecurityModuleItem extends Item {

    public final SecurityModule type;

    public SecurityModuleItem(SecurityModule type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item." + NeroSecurityCommon.MOD_ID + "." + type.itemPath + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
