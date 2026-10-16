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

/** The 1x2 Security Door ({@code nerosecurity:security_door}). {@link DoorPart#LOWER} is the controller. */
public class SecurityDoorBlock extends AbstractSecurityDoorBlock {

    public static final MapCodec<SecurityDoorBlock> CODEC = BlockCodecs.simple(SecurityDoorBlock::new);
    public static final EnumProperty<DoorPart> PART = EnumProperty.create("part", DoorPart.class);

    public SecurityDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(ANIMATING, false)
                .setValue(SEALED, false)
                .setValue(PART, DoorPart.LOWER));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SecurityDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, ANIMATING, SEALED, PART);
    }

    @Override
    public boolean isController(BlockState state) {
        return state.getValue(PART) == DoorPart.LOWER;
    }

    @Override
    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == DoorPart.UPPER ? pos.below() : pos;
    }

    @Override
    public List<BlockPos> partPositions(BlockPos controller, Direction facing) {
        return List.of(controller.immutable(), controller.above());
    }

    @Override
    protected BlockState withPart(BlockState state, int index) {
        return state.setValue(PART, DoorPart.values()[index]);
    }

    @Override
    protected int partIndex(BlockState state) {
        return state.getValue(PART).ordinal();
    }

    @Override
    protected BlockEntityType<SecurityDoorBlockEntity> blockEntityType() {
        return DoorRegistrations.SECURITY_DOOR_ENTITY.get();
    }
}
