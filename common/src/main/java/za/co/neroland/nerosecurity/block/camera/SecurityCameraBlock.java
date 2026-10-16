package za.co.neroland.nerosecurity.block.camera;

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

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.item.LinkingToolItem;
import za.co.neroland.nerosecurity.registry.CameraRegistrations;
import za.co.neroland.nerosecurity.menu.sensor.SensorPayloadHandlers;

/**
 * The Security Camera block ({@code nerosecurity:security_camera}). {@code FACING} is the direction the
 * lens looks (all six: wall, ceiling and floor mounts are the same block facing differently);
 * {@code ARMED} is true only while the camera can actually sense (linked, powered, cameras enabled) and
 * drives the red lens texture, so an unarmed camera is visibly dark.
 *
 * <ul>
 *   <li><b>Using</b> with an empty hand shows whether it is armed; <b>sneak-using</b> opens the shared
 *       sensor settings screen (CONFIGURE_DEVICE at OFFICER, linked cameras only).</li>
 *   <li>The range module is fitted in the settings screen's module slot.</li>
 *   <li><b>Breaking</b> is open to anyone (raidable); a breaker the camera would not let pass, and any
 *       non-player removal, raise {@code DEVICE_FORCED}, and the camera unlinks itself.</li>
 * </ul>
 */
public class SecurityCameraBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<SecurityCameraBlock> CODEC = BlockCodecs.simple(SecurityCameraBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    public SecurityCameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ARMED, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SecurityCameraBlock> codec() {
        return CODEC;
    }

    /** Linking resets the camera's settings, so nothing set while it stood unlinked carries over. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity camera) {
            camera.resetSettings();
        }
    }

    // --- state ----------------------------------------------------------------------------------

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED);
    }

    /** The lens looks away from the face it was placed against. */
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

    // --- block entity ---------------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityCameraBlockEntity(CameraRegistrations.SECURITY_CAMERA_ENTITY.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, CameraRegistrations.SECURITY_CAMERA_ENTITY.get(),
                (lvl, pos, st, be) -> AbstractMachineBlockEntity.tick(lvl, pos, st, be));
    }

    // --- using ----------------------------------------------------------------------------------

    /** The linking tool must reach its own use handler; anything else falls through to the empty-hand use. */
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
                || !(level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity camera)) {
            return InteractionResult.SUCCESS;
        }
        if (serverPlayer.isShiftKeyDown()) {
            SensorPayloadHandlers.openSettings(serverPlayer, camera);
        } else {
            serverPlayer.sendOverlayMessage(Component.translatable(
                    camera.armed() ? "sensor.nerosecurity.armed" : "sensor.nerosecurity.disarmed"));
        }
        return InteractionResult.SUCCESS;
    }

    // --- breaking -------------------------------------------------------------------------------

    /** Never cancels: anyone may break a camera. Reports a forced break; the module drops as usual. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity camera) {
            camera.onPlayerBreak(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
