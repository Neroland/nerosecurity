package za.co.neroland.nerosecurity.block.field;

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
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.item.LinkingToolItem;
import za.co.neroland.nerosecurity.menu.field.FieldPayloadHandlers;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;

/**
 * The Force Field Emitter ({@code nerosecurity:force_field_emitter}). Projects a wall ({@code PLANE}) or a hollow shell
 * ({@code BOX}) of {@link ForceFieldBlock}s in front of it that permitted players walk through and nothing else does.
 *
 * <ul>
 *   <li>{@code FACING} (all six) points away from the face it was placed against; the field forms that way.</li>
 *   <li>{@code ACTIVE} = projecting (linked, switched on, powered, not overloaded).</li>
 *   <li>Using it opens the field settings ({@code CONFIGURE_DEVICE} at OFFICER); an unlinked emitter has none.</li>
 *   <li>Anyone may break it (raidability). A breaker the installation would not let past publishes
 *       {@code DEVICE_FORCED}; any removal takes the field down and unlinks the emitter.</li>
 * </ul>
 */
public class ForceFieldEmitterBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<ForceFieldEmitterBlock> CODEC = BlockCodecs.simple(ForceFieldEmitterBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ForceFieldEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<ForceFieldEmitterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(ACTIVE, false);
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

    /** Linking resets the field settings, so nothing set under a previous link carries over. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof ForceFieldEmitterBlockEntity emitter) {
            emitter.resetSettings();
        }
    }

    /** Unlinked: the field comes down at once (the state machine keeps it idle from then on). */
    @Override
    public void onUnlinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof ForceFieldEmitterBlockEntity emitter) {
            emitter.collapse(ForceFieldEmitterBlockEntity.CollapseReason.UNLINKED);
        }
    }

    // --- block entity ---------------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForceFieldEmitterBlockEntity(FieldRegistrations.EMITTER_BE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, FieldRegistrations.EMITTER_BE.get(),
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
                || !(level.getBlockEntity(pos) instanceof ForceFieldEmitterBlockEntity emitter)) {
            return InteractionResult.SUCCESS;
        }
        FieldPayloadHandlers.openSettings(serverPlayer, emitter);
        return InteractionResult.SUCCESS;
    }

    // --- breaking -------------------------------------------------------------------------------

    /** Never cancels: anyone may break an emitter. Reports a forced break. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ForceFieldEmitterBlockEntity emitter) {
            emitter.onPlayerBreak(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
