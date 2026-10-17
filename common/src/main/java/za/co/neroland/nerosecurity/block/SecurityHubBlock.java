package za.co.neroland.nerosecurity.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.entity.SecurityHubBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.menu.HubPayloadHandlers;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.registry.NeroSecurityBlockEntities;

/**
 * The Security Hub: placing one registers an installation owned by the placer; it is the controller
 * every device links to, and the screen where members and lockdown are managed.
 *
 * <ul>
 *   <li><b>Placing</b> registers the installation. A placer over the per-player limit gets the hub back.</li>
 *   <li><b>Using</b> opens the hub screen for OFFICER and above (the screen edits members).</li>
 *   <li><b>Breaking</b> works only for an OWNER or operator who is sneaking; anyone else makes no
 *       progress. A player break dissolves the installation.</li>
 *   <li><b>Any other removal</b> (explosion, command, another mod) orphans the installation instead,
 *       so it can be found and cleaned up by an operator.</li>
 * </ul>
 *
 * <p>Unpowered. Its only ticker is the visual refresh ({@link #LOCKDOWN}, the recording LED), every
 * {@link VisualState#REFRESH_TICKS} ticks. Obsidian-class blast resistance; pistons cannot move it.
 */
public class SecurityHubBlock extends BaseEntityBlock {

    public static final MapCodec<SecurityHubBlock> CODEC = BlockCodecs.simple(SecurityHubBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Visual only: the installation is in lockdown (and lockdown is enabled on this server). Set by the
     * lockdown hook and re-derived from the store by the hub's visual refresh; nothing reads it for access.
     */
    public static final BooleanProperty LOCKDOWN = BooleanProperty.create("lockdown");

    public SecurityHubBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOCKDOWN, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SecurityHubBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCKDOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LOCKDOWN, false);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityHubBlockEntity(pos, state);
    }

    /** Server only: the staggered visual refresh (lockdown look, recording LED). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, NeroSecurityBlockEntities.SECURITY_HUB.get(),
                (lvl, pos, st, hub) -> hub.visualTick(lvl, pos));
    }

    // --- placing --------------------------------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(placer instanceof ServerPlayer player)) {
            // Only a player can own an installation: anything else gets the block back as an item.
            serverLevel.destroyBlock(pos, true);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SecurityHubBlockEntity hub)) {
            return;
        }
        InstallationService.CreateOutcome outcome = InstallationService.createForHub(player, serverLevel, pos);
        if (outcome.installation() == null) {
            refuse(serverLevel, pos, player, stack, outcome.result().message());
            return;
        }
        hub.bind(outcome.installation().id());
        hub.refreshVisual(serverLevel);
        player.sendSystemMessage(outcome.result().message());
    }

    /** Undoes a refused placement: remove the block, hand the item back, explain why. */
    private static void refuse(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack, Component reason) {
        level.removeBlock(pos, false);
        if (!player.isCreative()) {
            ItemStack refund = stack.copy();
            refund.setCount(1);
            if (!player.getInventory().add(refund)) {
                //? if >=26.3 {
                /*player.drop(refund, false, net.minecraft.util.Prediction.SERVER_ONLY);
                *///?} else {
                player.drop(refund, false);
                //?}
            }
        }
        player.sendSystemMessage(reason);
    }

    // --- using ----------------------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof SecurityHubBlockEntity hub)) {
            return InteractionResult.SUCCESS;
        }
        Installation installation = hub.installation(serverLevel.getServer());
        if (installation == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("hub.nerosecurity.inert"));
            return InteractionResult.SUCCESS;
        }
        // The screen edits members, so even reading it is OFFICER and above; membership only, never a card.
        AccessDecision decision = AccessResolver.DEFAULT.resolve(new AccessQuery(serverLevel.getServer(),
                serverPlayer.getUUID(), installation, AccessAction.OPEN_HUB, DevicePos.of(serverLevel, pos)),
                AccessAction.OPEN_HUB.defaultMinimum);
        if (!decision.allowed()) {
            serverPlayer.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(serverPlayer, pos);
            return InteractionResult.SUCCESS;
        }
        HubPayloadHandlers.sendSnapshot(serverPlayer, installation, decision);
        MenuOpener.open(serverPlayer, hub);
        return InteractionResult.SUCCESS;
    }

    // --- breaking -------------------------------------------------------------------------------

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float base = super.getDestroyProgress(state, player, level, pos);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return base; // client: animate normally; the server is the authority
        }
        return mayDissolve(serverLevel, pos, serverPlayer) ? base : 0.0F;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && !mayDissolve(serverLevel, pos, serverPlayer)) {
            serverPlayer.sendOverlayMessage(Component.translatable("hub.nerosecurity.break_hint"));
        }
    }

    /**
     * Whether {@code player} may break the hub at {@code pos}: sneaking, and the resolver allows DISSOLVE
     * (OWNER, or an operator). The loader break hooks call this before any player break, creative included,
     * so creative mode is not a bypass; {@link #getDestroyProgress} also uses it as defence in depth.
     */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return mayDissolve(level, pos, player);
    }

    /** Sends the "sneak and break as the owner" hint after a refused break. */
    public static void refuseBreak(ServerPlayer player) {
        player.sendOverlayMessage(Component.translatable("hub.nerosecurity.break_hint"));
    }

    private static boolean mayDissolve(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!player.isShiftKeyDown()) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof SecurityHubBlockEntity hub)) {
            return true; // no record to protect
        }
        Installation installation = hub.installation(level.getServer());
        if (installation == null) {
            return true; // an inert hub is just a block
        }
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.DISSOLVE,
                DevicePos.of(level, pos)).allowed();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SecurityHubBlockEntity hub) {
            Installation installation = hub.installation(serverLevel.getServer());
            if (installation != null && mayDissolve(serverLevel, pos, serverPlayer)) {
                hub.markDissolving();
                ServiceResult result = InstallationService.dissolveByBreak(serverPlayer, installation);
                serverPlayer.sendSystemMessage(result.message());
            }
            // Unauthorised player breaks are cancelled by the loader break hooks before this runs; only a
            // non-player removal reaches the block entity's removal hook, which orphans the installation.
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
