package za.co.neroland.nerosecurity.block;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.item.LinkingToolItem;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloadHandlers;

/**
 * The Keycard Programmer: writes, copies and wipes keycards for the installation it is linked to.
 *
 * <ul>
 *   <li><b>Unlinked</b>, it does nothing but say so: a card can only be signed with an installation's
 *       secret, so there is nothing to write without one.</li>
 *   <li><b>Using</b> it opens the programmer screen for anyone the resolver lets open a MEMBER device here;
 *       every action on the screen is re-authorised on the server at its own level.</li>
 *   <li>The <b>linking tool</b> passes straight through to the tool, so a programmer can be linked with a
 *       plain click like every other device.</li>
 * </ul>
 *
 * <p>Unpowered, no ticker, no installation pointer: the installation is looked up by position on every use.
 */
public class KeycardProgrammerBlock extends BaseEntityBlock implements SecurityDevice {

    public static final MapCodec<KeycardProgrammerBlock> CODEC = BlockCodecs.simple(KeycardProgrammerBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public KeycardProgrammerBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<KeycardProgrammerBlock> codec() {
        return CODEC;
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
        return new KeycardProgrammerBlockEntity(pos, state);
    }

    /** The linking tool acts on the programmer as a device; it must not open the screen instead. */
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
                || !(level.getBlockEntity(pos) instanceof KeycardProgrammerBlockEntity programmer)) {
            return InteractionResult.SUCCESS;
        }
        DevicePos at = DevicePos.of(serverLevel, pos);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("programmer.nerosecurity.unlinked"));
            return InteractionResult.SUCCESS;
        }
        // Anyone who may use members' devices here may look; each action re-resolves its own level.
        AccessDecision decision = ProgrammerPayloadHandlers.resolveView(serverPlayer, installation, at);
        if (!decision.allowed()) {
            serverPlayer.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(serverPlayer, pos);
            return InteractionResult.SUCCESS;
        }
        ProgrammerPayloadHandlers.sendSnapshot(serverPlayer, programmer, installation, decision, null);
        MenuOpener.open(serverPlayer, programmer);
        return InteractionResult.SUCCESS;
    }
}
