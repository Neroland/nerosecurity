package za.co.neroland.nerosecurity.block.door;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.registry.DoorRegistrations;

/**
 * The 2x2 Blast Door ({@code nerosecurity:blast_door}). {@link BlastPart#BOTTOM_LEFT} is the controller;
 * the right-hand column is one block in the {@code facing.getCounterClockWise()} direction.
 */
public class BlastDoorBlock extends AbstractSecurityDoorBlock {

    public static final MapCodec<BlastDoorBlock> CODEC = BlockCodecs.simple(BlastDoorBlock::new);
    public static final EnumProperty<BlastPart> PART = EnumProperty.create("part", BlastPart.class);

    public BlastDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(ANIMATING, false)
                .setValue(SEALED, false)
                .setValue(PART, BlastPart.BOTTOM_LEFT));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<BlastDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, ANIMATING, SEALED, PART);
    }

    /** The direction of the right-hand column, as seen looking at the door's front. */
    private static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Override
    public boolean isController(BlockState state) {
        return state.getValue(PART) == BlastPart.BOTTOM_LEFT;
    }

    @Override
    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        BlastPart part = state.getValue(PART);
        BlockPos controller = part.top ? pos.below() : pos;
        return part.right ? controller.relative(right(state.getValue(FACING)).getOpposite()) : controller.immutable();
    }

    /** Order matches {@link BlastPart}: bottom-left, bottom-right, top-left, top-right. */
    @Override
    public List<BlockPos> partPositions(BlockPos controller, Direction facing) {
        Direction right = right(facing);
        BlockPos above = controller.above();
        return List.of(controller.immutable(), controller.relative(right), above, above.relative(right));
    }

    @Override
    protected BlockState withPart(BlockState state, int index) {
        return state.setValue(PART, BlastPart.values()[index]);
    }

    @Override
    protected int partIndex(BlockState state) {
        return state.getValue(PART).ordinal();
    }

    @Override
    protected BlockEntityType<SecurityDoorBlockEntity> blockEntityType() {
        return DoorRegistrations.BLAST_DOOR_ENTITY.get();
    }
}
