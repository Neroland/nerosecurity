package za.co.neroland.nerosecurity.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.keycard.KeycardData;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;

/**
 * A keycard: blank until written at a keycard programmer, which adds the signed
 * {@code nerosecurity:keycard} component. The item itself decides nothing; every use is verified on the
 * server against the installation's secret and card ledger.
 *
 * <p>The tooltip is drawn from the component alone and shows no player identity, issuer or position. A
 * basic card shows its level (in the level's colour, with what it means), its serial and whether it is
 * bound; an encrypted card's tooltip shows only that it is encrypted. That is tooltip hiding only: the whole
 * component is synced to clients like any item data (the client and creative mode need the real stack), so
 * nothing may treat an encrypted card's level as secret; the server re-verifies every card on every use.
 * The {@code label} is the installation's name at write time: display text, never trusted.
 *
 * <p>Model selection: {@link #syncDisplay} mirrors the card's display tier into vanilla
 * {@code custom_model_data}: string 0 is the level name, or {@code "encrypted"}; colour 0 is the level
 * colour for basic cards; blanks carry none. It is cosmetic and never read back for any decision.
 */
public class KeycardItem extends Item {

    /** {@code custom_model_data} string for an encrypted card (the level stays hidden). */
    public static final String TIER_ENCRYPTED = "encrypted";

    public KeycardItem(Properties properties) {
        super(properties);
    }

    /** Whether {@code stack} carries card data (written, not blank). */
    public static boolean isEncoded(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(NeroSecurityDataComponents.KEYCARD.get());
    }

    /**
     * The display tier for a card: {@link #TIER_ENCRYPTED} for an encrypted card, else the level's JSON name;
     * null for a blank. Pure, for tests.
     */
    public static @Nullable String displayTier(@Nullable AccessLevel level, boolean encrypted) {
        if (level == null) {
            return null;
        }
        return encrypted ? TIER_ENCRYPTED : level.jsonName();
    }

    /**
     * Brings {@code stack}'s cosmetic model data in line with its card data: set for an encoded card,
     * removed for a blank. Call after anything writes or wipes the card component.
     */
    public static void syncDisplay(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof KeycardItem)) {
            return;
        }
        KeycardData data = stack.get(NeroSecurityDataComponents.KEYCARD.get());
        if (data == null) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        String tier = displayTier(data.level(), data.encrypted());
        List<Integer> colours = data.encrypted() ? List.of() : List.of(data.level().colour);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(tier), colours));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        KeycardData data = stack.get(NeroSecurityDataComponents.KEYCARD.get());
        if (data == null) {
            tooltip.accept(Component.translatable("item.nerosecurity.keycard.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (data.label() != null && !data.label().isBlank()) {
            tooltip.accept(Component.literal(data.label()).withStyle(ChatFormatting.WHITE));
        }
        if (data.encrypted()) {
            tooltip.accept(Component.translatable("item.nerosecurity.keycard.encrypted")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        AccessLevel level = data.level();
        tooltip.accept(Component.translatable("item.nerosecurity.keycard.level", level.displayName(),
                Component.translatable(level.meaningKey())).withStyle(ChatFormatting.WHITE));
        tooltip.accept(Component.translatable("item.nerosecurity.keycard.serial", data.serial())
                .withStyle(ChatFormatting.GRAY));
        if (data.bound()) {
            tooltip.accept(Component.translatable("item.nerosecurity.keycard.bound").withStyle(ChatFormatting.GRAY));
        }
    }
}
