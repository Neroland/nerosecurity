package za.co.neroland.nerosecurity.block.monitor;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.item.LinkingToolItem;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.monitor.MonitorPayloadHandlers;
import za.co.neroland.nerosecurity.registry.MonitorRegistrations;

/**
 * The Security Monitor ({@code nerosecurity:security_monitor}): an unpowered, linkable device whose
 * screen lists the installation's cameras and laser grids with their state and its most recent events
 * as plain sentences. It never shows imagery, names or player positions; event rows say only whether a
 * player was involved.
 *
 * <ul>
 *   <li><b>Unlinked</b>, it says so and shows nothing.</li>
 *   <li><b>Using</b> it opens the screen for anyone the resolver lets OPEN a MEMBER device here.</li>
 *   <li>The <b>linking tool</b> passes through to the tool.</li>
 *   <li>Anyone may break it; a forced break is an event, and a removed monitor unlinks itself.</li>
 * </ul>
 */
public class SecurityMonitorBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<SecurityMonitorBlock> CODEC = BlockCodecs.simple(SecurityMonitorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Visual only: the monitor's installation is in lockdown (and lockdown is enabled on this server). Set by
     * the lockdown hook and re-derived from the store by the monitor's visual refresh.
     */
    public static final BooleanProperty LOCKDOWN = BooleanProperty.create("lockdown");

    public SecurityMonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOCKDOWN, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SecurityMonitorBlock> codec() {
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
        return new SecurityMonitorBlockEntity(pos, state);
    }

    /** Server only: the staggered visual refresh of {@link #LOCKDOWN}. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, MonitorRegistrations.BLOCK_ENTITY.get(),
                (lvl, pos, st, monitor) -> monitor.visualTick(lvl, pos));
    }

    /** A freshly linked or unlinked monitor shows its installation's lockdown state at once. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof SecurityMonitorBlockEntity monitor) {
            monitor.refreshVisual(level);
        }
    }

    @Override
    public void onUnlinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof SecurityMonitorBlockEntity monitor) {
            monitor.refreshVisual(level);
        }
    }

    /** The linking tool acts on the monitor as a device; it must not open the screen instead. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof LinkingToolItem) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof SecurityMonitorBlockEntity monitor)) {
            return InteractionResult.SUCCESS;
        }
        DevicePos at = DevicePos.of(serverLevel, pos);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("device.nerosecurity.unlinked"));
            return InteractionResult.SUCCESS;
        }
        AccessDecision decision = MonitorPayloadHandlers.resolveView(serverPlayer, installation, at);
        if (!decision.allowed()) {
            serverPlayer.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(serverPlayer, pos);
            return InteractionResult.SUCCESS;
        }
        MonitorPayloadHandlers.sendSnapshot(serverPlayer, monitor, serverLevel, installation);
        MenuOpener.open(serverPlayer, monitor);
        return InteractionResult.SUCCESS;
    }

    /** Server: a break by a player the monitor would not open for publishes {@code DEVICE_FORCED}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SecurityMonitorBlockEntity monitor) {
            monitor.onPlayerBreak(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
