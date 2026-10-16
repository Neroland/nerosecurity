package za.co.neroland.nerosecurity.block;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.feedback.FeedbackPayloads;

/**
 * Marker for blocks that can be linked to an installation with the linking tool. A block counts as a
 * device only when it implements this interface <b>and</b> is in the {@code nerosecurity:devices} block
 * tag; a mismatch between the two is a defect, and such a block is refused.
 *
 * <p>The Security Hub is not a device: it is the controller, never a member of the linked set.
 */
public interface SecurityDevice {

    /** The block tag every device must also be listed in. */
    TagKey<Block> DEVICES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "devices"));

    /**
     * The position that stands for this device in the installation store. Multi-block devices (a 1x2
     * door, a 2x2 blast door) return their controller part's position for any part clicked, so linking,
     * lookups and events always use one position. Single-block devices keep this default.
     */
    default BlockPos devicePos(Level level, BlockPos clicked, BlockState state) {
        return clicked;
    }

    /** Called on the server thread after the device was linked to {@code installationId}. */
    default void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
    }

    /** Called on the server thread after the device was unlinked from {@code installationId}. */
    default void onUnlinked(ServerLevel level, BlockPos pos, UUID installationId) {
    }

    /**
     * The deny feedback every gated device sends in the same code path as its action-bar refusal: one
     * {@code DeviceFeedback(DENIED)} to {@code player} only, so their client plays the deny sound and flashes
     * the device's LED. {@code pos} is the device position (the controller for a multi-block device). The
     * reason travels only in the action-bar text the caller already sent.
     */
    static void denyFeedback(ServerPlayer player, BlockPos pos) {
        FeedbackPayloads.denied(player, pos);
    }

    /** The quiet counterpart for a permitted use: {@code DeviceFeedback(ACCEPTED)} to {@code player} only. */
    static void acceptFeedback(ServerPlayer player, BlockPos pos) {
        FeedbackPayloads.accepted(player, pos);
    }
}
