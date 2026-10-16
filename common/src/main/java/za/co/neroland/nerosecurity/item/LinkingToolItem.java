package za.co.neroland.nerosecurity.item;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerosecurity.block.entity.SecurityHubBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;
import za.co.neroland.nerosecurity.sound.SecuritySounds;

/**
 * The Linking Tool. Sneak-use on a hub selects it; use on a device links that device to the selected
 * installation; sneak-use on a linked device unlinks it. Every outcome is an action-bar message to the
 * user only. All checks run on the server through {@link InstallationService}.
 */
public class LinkingToolItem extends Item {

    public LinkingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        SelectedHub selected = stack.get(NeroSecurityDataComponents.SELECTED_HUB.get());
        if (selected != null) {
            tooltip.accept(Component.translatable("item.nerosecurity.linking_tool.selected", selected.name())
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.accept(Component.translatable("item.nerosecurity.linking_tool.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack tool = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        SecurityInstallations store = SecurityInstallations.get(level.getServer());
        boolean sneaking = player.isShiftKeyDown();

        // Sneak + hub: select it.
        if (sneaking && level.getBlockEntity(pos) instanceof SecurityHubBlockEntity hub) {
            Installation installation = hub.installation(level.getServer());
            if (installation == null) {
                player.sendOverlayMessage(Component.translatable("hub.nerosecurity.inert"));
            } else {
                player.sendOverlayMessage(InstallationService.selectHub(player, tool, installation).message());
            }
            return InteractionResult.SUCCESS;
        }

        // Multi-block devices are stored under their controller part, whichever part was clicked.
        DevicePos device = InstallationService.devicePosOf(level, pos);
        // Sneak + a linked device: unlink it.
        if (sneaking) {
            Optional<Installation> linked = store.atDevice(device);
            if (linked.isPresent()) {
                ServiceResult unlinked = InstallationService.unlinkDevice(player, linked.get(), device);
                player.sendOverlayMessage(unlinked.message());
                if (unlinked.ok()) {
                    SecuritySounds.LINK.play(level, pos);
                }
            } else {
                player.sendOverlayMessage(Component.translatable("tool.nerosecurity.not_a_device"));
            }
            return InteractionResult.SUCCESS;
        }

        // Plain use on a device: link it to the selected installation.
        if (!InstallationService.isDevice(level.getBlockState(pos))) {
            player.sendOverlayMessage(Component.translatable("tool.nerosecurity.not_a_device"));
            return InteractionResult.SUCCESS;
        }
        SelectedHub selected = tool.get(NeroSecurityDataComponents.SELECTED_HUB.get());
        if (selected == null) {
            player.sendOverlayMessage(Component.translatable("tool.nerosecurity.no_hub"));
            return InteractionResult.SUCCESS;
        }
        Optional<Installation> installation = store.byId(selected.installationId());
        if (installation.isEmpty()) {
            tool.remove(NeroSecurityDataComponents.SELECTED_HUB.get());
            player.sendOverlayMessage(Component.translatable("tool.nerosecurity.hub_gone"));
            return InteractionResult.SUCCESS;
        }
        ServiceResult result = InstallationService.linkDevice(player, installation.get(), level, pos);
        player.sendOverlayMessage(result.message());
        if (result.ok()) {
            SecuritySounds.LINK.play(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
