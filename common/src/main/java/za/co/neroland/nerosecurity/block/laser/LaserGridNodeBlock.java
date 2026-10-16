package za.co.neroland.nerosecurity.block.laser;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.item.LinkingToolItem;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloadHandlers;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;

/**
 * The Laser Grid Node ({@code nerosecurity:laser_grid_node}). Two nodes facing each other across open
 * space form a beam; someone the installation does not permit crossing it raises {@code LASER_TRIPPED}.
 *
 * <ul>
 *   <li>{@code FACING} (all six) is the face it was placed against's opposite: the lens points away from
 *       the wall it is mounted on.</li>
 *   <li>{@code ARMED} = linked, paired and powered. An unarmed node is visibly dark and senses nothing.</li>
 *   <li>Using it opens the shared sensor settings screen ({@code CONFIGURE_DEVICE} at OFFICER).</li>
 *   <li>Anyone may break it (raidability). A breaker the installation would not let past publishes
 *       {@code DEVICE_FORCED}; any removal of a linked node unlinks it.</li>
 * </ul>
 */
public class LaserGridNodeBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<LaserGridNodeBlock> CODEC = BlockCodecs.simple(LaserGridNodeBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    public LaserGridNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ARMED, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<LaserGridNodeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(ARMED, false);
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

    /** Linking resets the node's settings, so nothing set while it stood unlinked carries over. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof LaserGridNodeBlockEntity node) {
            node.resetSettings();
        }
    }

    /** A block appeared or vanished next to the node: re-check the pair on its next tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserGridNodeBlockEntity node) {
            node.markPairDirty();
        }
    }

    // --- block entity ---------------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserGridNodeBlockEntity(LaserRegistrations.LASER_GRID_NODE_ENTITY.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, LaserRegistrations.LASER_GRID_NODE_ENTITY.get(),
                (lvl, pos, st, be) -> AbstractMachineBlockEntity.tick(lvl, pos, st, be));
    }

    // --- using ----------------------------------------------------------------------------------

    /** The linking tool must reach its own use handler; anything else opens the settings. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof LinkingToolItem) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof LaserGridNodeBlockEntity node)) {
            return InteractionResult.SUCCESS;
        }
        SensorPayloadHandlers.openSettings(serverPlayer, node);
        return InteractionResult.SUCCESS;
    }

    // --- breaking -------------------------------------------------------------------------------

    /** Never cancels: anyone may break a laser grid node. Reports a forced break. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LaserGridNodeBlockEntity node) {
            node.onPlayerBreak(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
