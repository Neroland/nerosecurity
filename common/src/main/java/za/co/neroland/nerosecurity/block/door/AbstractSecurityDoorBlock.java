package za.co.neroland.nerosecurity.block.door;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;

import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.item.LinkingToolItem;

/**
 * Shared behaviour of the multi-block security doors (the 1x2 {@link SecurityDoorBlock} and the 2x2
 * {@link BlastDoorBlock}).
 *
 * <ul>
 *   <li>Only the <b>controller</b> part holds the block entity; every part redirects to it for use,
 *       linking ({@link #devicePos}), events and energy.</li>
 *   <li>Removing any part removes all of them exactly once (the vanilla door pattern, generalised to a
 *       part list); only the controller drops the door item.</li>
 *   <li>{@code OPEN} turns true only when the opening animation finishes and false the moment a close
 *       starts, so the door is passable only while fully open.</li>
 *   <li>Anyone can break a door (raidability): hardness modules slow breaking and raise blast
 *       resistance, capped by {@code doorMaxBlastResistance}. Nothing makes a door unbreakable.</li>
 *   <li>{@link #ANIMATING} and {@link #SEALED} are visual only (which model the client draws): animating
 *       is true from the start of an open or close until the door is shut and still again; sealed is true
 *       while the installation is in lockdown. Nothing reads them for access or collision.</li>
 * </ul>
 */
public abstract class AbstractSecurityDoorBlock extends BaseEntityBlock implements SecurityDevice {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Visual: the panels are moving or open, so the renderer (not the static model) draws them. */
    public static final BooleanProperty ANIMATING = BooleanProperty.create("animating");
    /** Visual: the installation is in lockdown; the model adds the clamp bars. */
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    /** Open = a thin panel against the back of the frame, so the door can still be clicked to close. */
    private static final VoxelShape OPEN_BACK_NORTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D);
    private static final VoxelShape OPEN_BACK_SOUTH = Block.box(0.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape OPEN_BACK_WEST = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 16.0D);
    private static final VoxelShape OPEN_BACK_EAST = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    protected AbstractSecurityDoorBlock(Properties properties) {
        super(properties);
    }

    // --- part geometry (subclasses) -------------------------------------------------------------

    /** Whether {@code state} is the controller part (the one with the block entity). */
    public abstract boolean isController(BlockState state);

    /** The controller position of the door that the part at {@code pos} belongs to. */
    public abstract BlockPos controllerPos(BlockPos pos, BlockState state);

    /** Every part position of a door whose controller is at {@code controller}, controller first. */
    public abstract List<BlockPos> partPositions(BlockPos controller, Direction facing);

    /** {@code state} as the part at {@code index} of {@link #partPositions}. */
    protected abstract BlockState withPart(BlockState state, int index);

    /** The index in {@link #partPositions} of the part {@code state} is. */
    protected abstract int partIndex(BlockState state);

    /** The block-entity type of this door's controller. */
    protected abstract BlockEntityType<SecurityDoorBlockEntity> blockEntityType();

    /** Every part position of the door the part at {@code pos} belongs to, controller first. */
    public List<BlockPos> partsOf(BlockPos pos, BlockState state) {
        return partPositions(controllerPos(pos, state), state.getValue(FACING));
    }

    @Override
    public BlockPos devicePos(Level level, BlockPos clicked, BlockState state) {
        return state.getBlock() == this ? controllerPos(clicked, state) : clicked;
    }

    /** Linking resets the door's settings, so nothing set while it stood unlinked carries over. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        SecurityDoorBlockEntity door = controllerEntity(level, pos, level.getBlockState(pos));
        if (door != null) {
            door.resetSettings();
        }
    }

    /** The controller's block entity for the part at {@code pos}, if it is loaded and intact. */
    public static @Nullable SecurityDoorBlockEntity controllerEntity(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AbstractSecurityDoorBlock door)) {
            return null;
        }
        return level.getBlockEntity(door.controllerPos(pos, state)) instanceof SecurityDoorBlockEntity entity
                ? entity : null;
    }

    // --- state ----------------------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos controller = context.getClickedPos();
        Level level = context.getLevel();
        for (BlockPos part : partPositions(controller, facing)) {
            if (level.isOutsideBuildHeight(part) || !level.getWorldBorder().isWithinBounds(part)
                    || !level.getBlockState(part).canBeReplaced(context)) {
                return null;
            }
        }
        return withPart(defaultBlockState().setValue(FACING, facing).setValue(OPEN, false)
                .setValue(ANIMATING, false).setValue(SEALED, false), 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        List<BlockPos> parts = partPositions(pos, state.getValue(FACING));
        for (int i = 1; i < parts.size(); i++) {
            level.setBlock(parts.get(i), withPart(state, i), Block.UPDATE_ALL);
        }
    }

    /** A part whose sibling is gone (or is not this door's matching part any more) becomes air. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Direction facing = state.getValue(FACING);
        List<BlockPos> parts = partPositions(controllerPos(pos, state), facing);
        int index = parts.indexOf(neighbourPos);
        if (index >= 0 && !isPart(neighbourState, facing, index)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    private boolean isPart(BlockState candidate, Direction facing, int index) {
        return candidate.getBlock() == this && candidate.getValue(FACING) == facing && partIndex(candidate) == index;
    }

    /** Sets {@code OPEN} on every part of the door whose controller is at {@code controller}. */
    public void setOpen(Level level, BlockPos controller, boolean open) {
        BlockState controllerState = level.getBlockState(controller);
        if (controllerState.getBlock() != this) {
            return;
        }
        for (BlockPos part : partPositions(controller, controllerState.getValue(FACING))) {
            BlockState current = level.getBlockState(part);
            if (current.getBlock() == this && current.getValue(OPEN) != open) {
                level.setBlock(part, current.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Sets the visual properties on every part of the door whose controller is at {@code controller}, only
     * where they differ. Clients only: no neighbour or shape update, because neither property changes shape.
     */
    public void setVisual(Level level, BlockPos controller, boolean animating, boolean sealed) {
        BlockState controllerState = level.getBlockState(controller);
        if (controllerState.getBlock() != this) {
            return;
        }
        for (BlockPos part : partPositions(controller, controllerState.getValue(FACING))) {
            BlockState current = level.getBlockState(part);
            if (current.getBlock() == this
                    && (current.getValue(ANIMATING) != animating || current.getValue(SEALED) != sealed)) {
                level.setBlock(part, current.setValue(ANIMATING, animating).setValue(SEALED, sealed),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Whether any part of the door whose controller is at {@code controller} receives a redstone signal. */
    public boolean anyPartPowered(Level level, BlockPos controller, Direction facing) {
        for (BlockPos part : partPositions(controller, facing)) {
            if (level.hasNeighborSignal(part)) {
                return true;
            }
        }
        return false;
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(OPEN)) {
            return Shapes.block();
        }
        return switch (state.getValue(FACING).getOpposite()) {
            case NORTH -> OPEN_BACK_NORTH;
            case SOUTH -> OPEN_BACK_SOUTH;
            case WEST -> OPEN_BACK_WEST;
            default -> OPEN_BACK_EAST;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    // --- block entity ---------------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new SecurityDoorBlockEntity(blockEntityType(), pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || !isController(state)) {
            return null;
        }
        return createTickerHelper(type, blockEntityType(),
                (lvl, pos, st, be) -> AbstractMachineBlockEntity.tick(lvl, pos, st, be));
    }

    // --- using ----------------------------------------------------------------------------------

    /** The linking tool must reach its own use handler; anything else (a keycard) uses the door. */
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
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        SecurityDoorBlockEntity door = controllerEntity(level, pos, state);
        if (door != null) {
            door.use(serverPlayer, serverPlayer.isShiftKeyDown());
        }
        return InteractionResult.SUCCESS;
    }

    // --- breaking and explosions ----------------------------------------------------------------

    private static int hardnessModules(BlockGetter level, BlockPos pos, BlockState state) {
        SecurityDoorBlockEntity door = controllerEntity(level, pos, state);
        return door == null ? 0 : door.hardnessModules();
    }

    /** Vanilla break speed divided by {@code 1.5^hardnessModules} (limited by the blast-resistance cap). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float base = super.getDestroyProgress(state, player, level, pos);
        return DoorRules.destroyProgress(base, this.explosionResistance, hardnessModules(level, pos, state),
                NeroSecurityConfig.doorMaxBlastResistance());
    }

    /** The base resistance, never above {@code doorMaxBlastResistance} (the path vanilla explosions read). */
    @Override
    public float getExplosionResistance() {
        return (float) Math.min(super.getExplosionResistance(), NeroSecurityConfig.doorMaxBlastResistance());
    }

    /**
     * Per-door resistance with hardness modules, capped by {@code doorMaxBlastResistance}. Declared with
     * the NeoForge/Forge block-extension signature, so on those loaders explosions call it; on Fabric
     * explosions read {@link #getExplosionResistance()} (base, capped) and modules only slow breaking.
     */
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return DoorRules.blastResistance(this.explosionResistance, hardnessModules(level, pos, state),
                NeroSecurityConfig.doorMaxBlastResistance());
    }

    /**
     * Server: records the break on the controller (a forced break publishes {@code DOOR_FORCED}), and for
     * a creative or wrong-tool break of a non-controller part removes the controller without a drop (the
     * vanilla door pattern), so a door never drops more than once.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            SecurityDoorBlockEntity door = controllerEntity(level, pos, state);
            if (door != null) {
                door.onPlayerBreak(serverPlayer);
            }
            if (!isController(state) && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
                BlockPos controller = controllerPos(pos, state);
                BlockState controllerState = level.getBlockState(controller);
                if (controllerState.getBlock() == this && isController(controllerState)) {
                    level.setBlock(controller, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, controller, Block.getId(controllerState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
