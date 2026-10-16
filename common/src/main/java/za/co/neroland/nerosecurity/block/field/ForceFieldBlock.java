package za.co.neroland.nerosecurity.block.field;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.client.ClientFieldPassability;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;

/**
 * A force field block ({@code nerosecurity:force_field}), placed only by an emitter into air.
 *
 * <p><b>Collision is the access decision.</b> {@link #getCollisionShape} is the only place a field is ever open:
 * <ul>
 *   <li>anything that is not a player (mobs, projectiles, items, vehicles, an empty context) collides with a full
 *       cube, always: a mounted player is stopped by the vehicle's collision;</li>
 *   <li>a player on the server is judged by {@link FieldPassability} (the emitter's per-tick verdict cache, which asks
 *       the access resolver by UUID);</li>
 *   <li>a player on the client only <em>predicts</em>: the local player passes when the server last said so for that
 *       emitter ({@link ClientFieldPassability}); every other player and every unknown emitter is solid there.</li>
 * </ul>
 * Anything missing (no block entity, no emitter position, an emitter in an unloaded chunk) is solid: fail closed.
 *
 * <p><b>Raidable, never a trap.</b> The block cannot be mined, but every hit, projectile and explosion adds load to its
 * emitter, which collapses the whole field when the meter is full ({@code forceFieldBreachable}); the emitter itself
 * breaks like any device. An explosion never removes the block (the emitter takes the load instead). The block is
 * never suffocating, never view-blocking, never a spawn or path, and removes itself when its emitter no longer
 * projects it (random tick, or a hit).
 */
public class ForceFieldBlock extends BaseEntityBlock {

    public static final MapCodec<ForceFieldBlock> CODEC = BlockCodecs.simple(ForceFieldBlock::new);
    /** Visual only: the field is in a lockdown (painted by the emitter). */
    public static final BooleanProperty LOCKDOWN = BooleanProperty.create("lockdown");
    /** Explosion resistance when fields are not breachable: nothing short of a command gets through. */
    public static final float UNBREACHABLE_RESISTANCE = 3600000.0F;

    public ForceFieldBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LOCKDOWN, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<ForceFieldBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOCKDOWN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForceFieldBlockEntity(FieldRegistrations.FIELD_BE.get(), pos, state);
    }

    // --- shapes ---------------------------------------------------------------------------------

    /** Full cube for targeting and the outline. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** Looks see-through. */
    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Players are judged per player (server: the resolver; client: the server's last word); everything else is solid. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return passes(getter, pos, context) ? Shapes.empty() : Shapes.block();
    }

    private static boolean passes(BlockGetter getter, BlockPos pos, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext)
                || !(entityContext.getEntity() instanceof Player player)) {
            return false; // mobs, projectiles, items, vehicles, empty context: always solid
        }
        try {
            if (!(getter.getBlockEntity(pos) instanceof ForceFieldBlockEntity field)) {
                return false;
            }
            BlockPos emitterPos = field.emitterPos();
            Level level = player.level();
            if (emitterPos == null || level == null) {
                return false;
            }
            if (level.isClientSide()) {
                return player.isLocalPlayer() && ClientFieldPassability.isPassable(level, emitterPos);
            }
            // Never load a chunk from inside a collision query: an emitter that is not loaded is solid.
            return level instanceof ServerLevel serverLevel && serverLevel.isLoaded(emitterPos)
                    && FieldPassability.isPassable(serverLevel, emitterPos, player.getUUID());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Neighbouring field faces are not drawn (like glass). */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Stone-like while breachable (so a blast reaches it and loads the emitter); blast-proof when not. */
    @Override
    public float getExplosionResistance() {
        return NeroSecurityConfig.isForceFieldBreachable()
                ? (float) NeroSecurityConfig.fieldBlastResistance()
                : UNBREACHABLE_RESISTANCE;
    }

    // --- load -----------------------------------------------------------------------------------

    /** A player hits the block: an orphan removes itself; otherwise the emitter takes melee load (if not permitted). */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ForceFieldEmitterBlockEntity emitter = FieldProjector.emitterFor(serverLevel, pos);
        if (emitter == null) {
            serverLevel.removeBlock(pos, false);
            return;
        }
        emitter.absorb(LoadSource.MELEE, player.getUUID(), 0.0D, 0);
    }

    // --- breaking -------------------------------------------------------------------------------

    /**
     * Whether {@code player} may remove the field block at {@code pos} by breaking it. Hardness -1 already stops
     * survival mining, but a creative-mode player destroys a block without hardness or {@code attack}; the three
     * loader break hooks call this before every player break, creative included. Allowed: an orphan (it removes itself
     * anyway), a block of an unlinked emitter, a stored owner of the installation (membership rows only: a keycard or
     * an integration grant never counts) and a server operator. Anyone else is refused, and the attempt loads the
     * emitter exactly like a melee hit (so creative mode is no way round the meter).
     */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null) {
            return false;
        }
        try {
            ForceFieldEmitterBlockEntity emitter = FieldProjector.emitterFor(level, pos);
            if (emitter == null) {
                return true; // an orphan: it would remove itself on the next hit or random tick
            }
            Installation installation = emitter.installationNow(level);
            if (installation == null) {
                return true; // an unlinked emitter projects nothing; its blocks are going anyway
            }
            boolean allowed = AccessResolver.DEFAULT.resolve(player, installation, AccessAction.CONFIGURE_DEVICE,
                    AccessLevel.OWNER, emitter.devicePos(level), Set.of(MembershipSource.ID)).allowed();
            if (!allowed) {
                emitter.absorb(LoadSource.MELEE, player.getUUID(), 0.0D, 0);
            }
            return allowed;
        } catch (RuntimeException e) {
            return false; // fail closed: the block stays
        }
    }

    /** Tells the player a refused break was refused. */
    public static void refuseBreak(ServerPlayer player) {
        player.sendOverlayMessage(Component.translatable("field.nerosecurity.break_refused"));
    }

    /** A projectile hits the block: the emitter takes projectile load unless its shooter is permitted. */
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        super.onProjectileHit(level, state, hit, projectile);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ForceFieldEmitterBlockEntity emitter = FieldProjector.emitterFor(serverLevel, hit.getBlockPos());
        if (emitter == null) {
            return;
        }
        Entity owner = projectile.getOwner();
        UUID shooter = owner instanceof Player player ? player.getUUID() : null;
        emitter.absorb(LoadSource.PROJECTILE, shooter, 0.0D, 0);
    }

    /**
     * An explosion reaches the block: the block stays, the emitter takes {@code fieldExplosionLoadPerRadius x radius}
     * once per explosion. Never calls super (which would remove the block and drop it).
     */
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> onHit) {
        ForceFieldEmitterBlockEntity emitter = FieldProjector.emitterFor(level, pos);
        if (emitter == null) {
            return;
        }
        emitter.absorb(LoadSource.EXPLOSION, null, explosion.radius(), System.identityHashCode(explosion));
    }

    /** An orphan (its emitter gone, unloaded, or no longer projecting this position) removes itself. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (FieldProjector.emitterFor(level, pos) == null) {
            level.removeBlock(pos, false);
        }
    }
}
