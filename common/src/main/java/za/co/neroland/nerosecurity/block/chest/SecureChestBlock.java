package za.co.neroland.nerosecurity.block.chest;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.menu.chest.ChestPayloadHandlers;

/**
 * The Secure Chest: a 27-slot chest that checks access when opened and lets automation in only when
 * the installation's policy allows it.
 *
 * <ul>
 *   <li><b>Using</b> opens it for anyone the resolver allows OPEN at the chest's minimum level; a
 *       keycard-only chest counts presented keycards only. An unlinked chest is a plain chest.</li>
 *   <li><b>Sneak-using</b> (empty hand) opens the settings screen for OFFICER and above, never keycard
 *       filtered, so an owner can always turn keycard-only back off.</li>
 *   <li><b>Breaking</b> is open to anyone with the right tool (raidable by design); the contents always
 *       drop. A breaker who could not open it, and any non-player removal, raise {@code CHEST_FORCED}.</li>
 * </ul>
 *
 * <p>No ticker, no comparator output (a fill level is information about the contents), unpowered.
 */
public class SecureChestBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<SecureChestBlock> CODEC = BlockCodecs.simple(SecureChestBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SecureChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SecureChestBlock> codec() {
        return CODEC;
    }

    /** Linking resets the chest's settings, so nothing set while it stood unlinked carries over. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof SecureChestBlockEntity chest) {
            chest.applySettings(DeviceAccessSettings.DEFAULT);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return new SecureChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof SecureChestBlockEntity chest)) {
            return InteractionResult.SUCCESS;
        }
        if (serverPlayer.isShiftKeyDown()) {
            ChestPayloadHandlers.openSettings(serverPlayer, chest);
        } else {
            chest.tryOpen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /** Never cancels: anyone may break a secure chest. Reports a forced break; contents drop as usual. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SecureChestBlockEntity chest) {
            chest.onPlayerBreak(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
