package za.co.neroland.nerosecurity.block.alarm;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloadHandlers;
import za.co.neroland.nerosecurity.registry.AlarmRegistrations;

/**
 * The Alarm: reacts to the installation's security events with a redstone pulse, a sound, a light and a
 * chat line to online owners (and officers, when the installation asks), and, only if an owner opts in,
 * a time-boxed lockdown. Unpowered.
 *
 * <ul>
 *   <li><b>Sneak-using</b> (empty hand) opens the settings screen for OFFICER and above.</li>
 *   <li><b>Breaking</b> is open to anyone with the right tool (raidable by design). A breaker who could
 *       not configure it, and any non-player removal, raise {@code DEVICE_FORCED}; the link is dropped.</li>
 * </ul>
 *
 * <p>{@code ACTIVE} is the visual (light now, the beacon renderer later); {@code POWERED} is the redstone
 * pulse, a weak signal of 15 on every side.
 */
public class AlarmBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<AlarmBlock> CODEC = BlockCodecs.simple(AlarmBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public AlarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, Boolean.FALSE)
                .setValue(POWERED, Boolean.FALSE));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<AlarmBlock> codec() {
        return CODEC;
    }

    /** Light level for the block properties: lit while the alarm is active. */
    public static int lightLevel(BlockState state) {
        return state.hasProperty(ACTIVE) && state.getValue(ACTIVE) ? 10 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlarmBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AlarmRegistrations.ALARM_BLOCK_ENTITY.get(), AlarmBlockEntity::serverTick);
    }

    /** Linking resets the alarm's settings and any silence. */
    @Override
    public void onLinked(ServerLevel level, BlockPos pos, UUID installationId) {
        if (level.getBlockEntity(pos) instanceof AlarmBlockEntity alarm) {
            alarm.resetSettings();
        }
    }

    // --- redstone -------------------------------------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    // --- using ----------------------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof AlarmBlockEntity alarm)) {
            return InteractionResult.SUCCESS;
        }
        AlarmPayloadHandlers.openSettings(serverPlayer, alarm);
        return InteractionResult.SUCCESS;
    }

    /** Never cancels: anyone may break an alarm. Reports a forced break. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AlarmBlockEntity alarm) {
            alarm.onPlayerBreak(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
